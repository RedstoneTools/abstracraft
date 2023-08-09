package tools.redstone.abstracraft.core.analysis;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import tools.redstone.abstracraft.core.AbstractionManager;
import tools.redstone.abstracraft.core.usage.NotImplementedException;
import tools.redstone.abstracraft.core.usage.Usage;
import tools.redstone.abstracraft.core.util.ASMUtil;
import tools.redstone.abstracraft.core.util.CollectionUtil;
import tools.redstone.abstracraft.core.util.Container;
import tools.redstone.abstracraft.core.util.ReflectUtil;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static tools.redstone.abstracraft.core.util.CollectionUtil.addIfNotNull;

/**
 * Analyzes given class bytes for usage of abstraction methods.
 *
 * @author orbyfied
 */
public class ClassDependencyAnalyzer {

    static final Set<String> specialMethods = Set.of("unimplemented", "isImplemented", "<init>");     // Special methods on abstractions

    // The result of the dependency analysis on a class
    public static class ClassAnalysis {
        public boolean completed = false;                                               // Whether this analysis is complete
        public final Map<ReferenceInfo, ReferenceAnalysis> analyzedMethods = new HashMap<>(); // All analysis objects for the methods in this class
        public Set<MethodDependency> dependencies = new HashSet<>();                    // All method dependencies recorded in this class
        public List<OneOfDependency> switchDependencies = new ArrayList<>();            // All oneOf dependencies

        // Check whether all direct and switch dependencies are implemented
        public boolean areAllImplemented(AbstractionManager abstractionManager) {
            for (MethodDependency dep : dependencies)
                if (!dep.isImplemented(abstractionManager))
                    return false;
            for (OneOfDependency dep : switchDependencies)
                if (!dep.implemented())
                    return false;
            return true;
        }
    }

    public static class ReferenceAnalysis {
        public final ClassDependencyAnalyzer analyzer;                            // The analyzer instance.
        public final ReferenceInfo ref;                                           // The reference this analysis covers
        public List<ReferenceInfo> requiredDependencies = new ArrayList<>();      // All recorded required dependencies used by this method
        public int optionalReferenceNumber = 0;                                   // Whether this method is referenced in an optionally() block
        public List<ReferenceAnalysis> allAnalyzedReferences = new ArrayList<>(); // The analysis objects of all methods/fields normally called by this method
        public boolean complete = false;                                          // Whether this analysis has completed all mandatory tasks
        public boolean partial = false;                                           // Whether this analysis is used purely to store meta or if it is actually analyzed with bytecode analysis
        public final boolean field;

        public Set<DependencyAnalysisHook> hooksSet = new HashSet<>();
        public List<DependencyAnalysisHook.ReferenceHook> refHooks = new ArrayList<>();

        public ReferenceAnalysis(ClassDependencyAnalyzer analyzer, ReferenceInfo ref) {
            this.analyzer = analyzer;
            this.ref = ref;
            this.field = ref.isField();
        }

        // Checked refHooks.add
        private void addRefHook(DependencyAnalysisHook hook, Supplier<DependencyAnalysisHook.ReferenceHook> supplier) {
            // create new ref hook
            addIfNotNull(refHooks, supplier.get());
        }

        // Register and propagate that this method is part of an optional block
        public void referenceOptional(AnalysisContext context) {
            for (var hook : analyzer.hooks) addRefHook(hook, () -> hook.optionalReference(context, this));
            for (var refHook : refHooks) refHook.optionalReference(context);

            this.optionalReferenceNumber += 2;
            for (ReferenceAnalysis analysis : allAnalyzedReferences) {
                analysis.referenceOptional(context);
            }
        }

        // Register and propagate that this method is required
        public void referenceRequired(AnalysisContext context) {
            for (var hook : analyzer.hooks) addRefHook(hook, () -> hook.requiredReference(context, this));
            for (var refHook : refHooks) refHook.requiredReference(context);

            this.optionalReferenceNumber -= 1;
            for (ReferenceAnalysis analysis : allAnalyzedReferences) {
                analysis.referenceRequired(context);
            }
        }

        // Register and propagate that this method was dropped from an optionally() block
        public void optionalReferenceDropped(AnalysisContext context) {
            for (var refHook : refHooks) refHook.optionalBlockDiscarded(context);
            for (ReferenceAnalysis analysis : allAnalyzedReferences) {
                analysis.optionalReferenceDropped(context);
            }
        }

        // Finish analysis of the method
        public void postAnalyze() {
            for (var refHook : refHooks) refHook.postAnalyze();
            for (ReferenceAnalysis analysis : allAnalyzedReferences) {
                analysis.postAnalyze();
            }
        }

        public void registerReference(ReferenceInfo info) {
            addIfNotNull(allAnalyzedReferences, analyzer.getReferenceAnalysis(info));
        }

        public void registerReference(ReferenceAnalysis analysis) {
            allAnalyzedReferences.add(analysis);
        }

        public boolean isPartial() {
            return partial;
        }

        public boolean isComplete() {
            return complete;
        }

        public boolean isField() {
            return field;
        }
    }

    /* Stack Tracking */
    public record FieldValue(ReferenceInfo fieldInfo, boolean isStatic) { }
    public record InstanceOf(Type type) { }
    public record ReturnValue(ReferenceInfo method, Type type) { public static ReturnValue of(ReferenceInfo info) { return new ReturnValue(info, info.type().getReturnType()); } }
    public record FromVar(int varIndex, Type type) { }

    /* Compute Stack Tracking */
    public record Lambda(boolean direct, ReferenceInfo methodInfo, Container<Boolean> discard) { }

    static final Type TYPE_Usage = Type.getType(Usage.class);
    static final String NAME_Usage = TYPE_Usage.getInternalName();
    static final Type TYPE_InternalSubstituteMethods = Type.getType(Usage.InternalSubstituteMethods.class);
    static final String NAME_InternalSubstituteMethods = TYPE_InternalSubstituteMethods.getInternalName();
    static final Type TYPE_NotImplementedException = Type.getType(NotImplementedException.class);
    static final String NAME_NotImplementedException = TYPE_NotImplementedException.getInternalName();
    static final Type TYPE_MethodInfo = Type.getType(ReferenceInfo.class);
    static final String NAME_MethodInfo = TYPE_MethodInfo.getInternalName();

    private final AbstractionManager abstractionManager;                  // The abstraction manager
    private String internalName;                                          // The internal name of this class
    private String className;                                             // The public name of this class
    private ClassReader classReader;                                      // The class reader for the bytecode
    private ClassNode classNode;                                          // The class node to be written
    public final List<DependencyAnalysisHook> hooks = new ArrayList<>();  // The analysis hooks

    private ClassAnalysis classAnalysis = new ClassAnalysis(); // The result of analysis

    public ClassDependencyAnalyzer addHook(DependencyAnalysisHook hook) {
        this.hooks.add(hook);
        return this;
    }

    public ClassDependencyAnalyzer(AbstractionManager manager,
                                   ClassReader classReader) {
        this.abstractionManager = manager;
        if (classReader != null) {
            this.internalName = classReader.getClassName();
            this.className = internalName.replace('/', '.');
            this.classReader = classReader;
            this.classNode = new ClassNode(ASMUtil.ASM_V);
            classReader.accept(classNode, 0);
        }
    }

    // Make a ReferenceInfo to a method on the stack
    private static void makeMethodInfo(MethodVisitor visitor, String owner, String name, String desc, boolean isStatic) {
        visitor.visitLdcInsn(owner);
        visitor.visitLdcInsn(name);
        visitor.visitLdcInsn(desc);
        visitor.visitIntInsn(Opcodes.BIPUSH, isStatic ? 1 : 0);
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, TYPE_MethodInfo.getInternalName(), "forMethodInfo",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)L" + NAME_MethodInfo + ";", false);
    }

    // Make a ReferenceInfo to a field on the stack
    private static void makeFieldInfo(MethodVisitor visitor, String owner, String name, String desc, boolean isStatic) {
        visitor.visitLdcInsn(owner);
        visitor.visitLdcInsn(name);
        visitor.visitLdcInsn(desc);
        visitor.visitIntInsn(Opcodes.BIPUSH, isStatic ? 1 : 0);
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, TYPE_MethodInfo.getInternalName(), "forFieldInfo",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)L" + NAME_MethodInfo + ";", false);
    }

    /** Get a method analysis if present */
    public ReferenceAnalysis getReferenceAnalysis(ReferenceInfo info) {
        return abstractionManager.getMethodAnalysis(info);
    }

    /** Analyzes and transforms a local method */
    public ReferenceAnalysis localMethod(AnalysisContext context, ReferenceInfo info) {
        try {
            if (!info.ownerInternalName().equals(this.internalName))
                throw new AssertionError();

            // check for cached
            var analysis = getReferenceAnalysis(info);
            if (analysis != null && analysis.complete)
                return analysis;

            // check for recursion
            if (context.analysisStack.contains(info))
                return null;

            // find method node
            MethodNode m = ASMUtil.findMethod(classNode, info.name(), info.desc());
            if (m == null)
                return null;

            // create analysis, visit method and register result
            analysis = new ReferenceAnalysis(this, info);
            MethodVisitor v = methodVisitor(context, info, analysis, m);
            m.accept(v); // the visitor registers the result automatically
            return analysis;
        } catch (Exception e) {
            throw new RuntimeException("Error while analyzing local method " + info, e);
        }
    }

    /** Analyzes and transforms a method from any class */
    public ReferenceAnalysis publicReference(AnalysisContext context, ReferenceInfo info) {
        // check for local method
        if (info.ownerInternalName().equals(this.internalName) && !info.isField())
            return localMethod(context, info);
        return abstractionManager.publicReference(context, info);
    }

    /** Check whether the given reference could be a dependency */
    public boolean isDependencyReference(AnalysisContext context, ReferenceInfo info) {
        for (var hook : this.hooks) {
            var res = hook.isDependencyCandidate(context, info);
            if (res == null) continue;
            return res;
        }

        // assume no
        return false;
    }

    /**
     * Creates a method visitor which analyzes and transforms a local method.
     *
     * @param currentMethodInfo The method descriptor.
     * @return The visitor.
     */
    public MethodVisitor methodVisitor(AnalysisContext context, ReferenceInfo currentMethodInfo, ReferenceAnalysis methodAnalysis, MethodNode oldMethod) {
        String name = currentMethodInfo.name();
        String descriptor = currentMethodInfo.desc();

        // check old method
        if (oldMethod == null) {
            throw new IllegalArgumentException("No local method by " + currentMethodInfo + " in class " + internalName);
        }

        abstractionManager.registerAnalysis(methodAnalysis);
        classAnalysis.analyzedMethods.put(currentMethodInfo, methodAnalysis);

//        classNode.accept(new TraceClassVisitor(new PrintWriter(System.out)));

        // create method visitor
        MethodNode newMethod = new MethodNode(oldMethod.access, name, descriptor, oldMethod.signature, oldMethod.exceptions.toArray(new String[0]));
        var visitor = new MethodVisitor(ASMUtil.ASM_V, newMethod) {
            // The compute stack of lambda's
            Stack<Object> computeStack = new Stack<>();

            {
                context.analysisStack.push(currentMethodInfo);
                context.computeStacks.push(computeStack);
                context.enteredMethod();
                for (var hook : hooks) hook.enterMethod(context);
            }

            public void addInsn(InsnNode node) {
                newMethod.instructions.add(node);
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                // check for lambda factory
                if (
                        !bootstrapMethodHandle.getOwner().equals("java/lang/invoke/LambdaMetafactory") ||
                                !bootstrapMethodHandle.getName().equals("metafactory")
                ) {
                    super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
                    return;
                }

                // check whether its a lambda or a
                // method referenced as a lambda argument
                Handle lambdaImpl = (Handle) bootstrapMethodArguments[1];
                Type lambdaImplType = Type.getMethodType(lambdaImpl.getDesc());
                int argCount = lambdaImplType.getArgumentTypes().length +
                        (lambdaImpl.getTag() != Opcodes.H_INVOKESTATIC && lambdaImpl.getTag() != Opcodes.H_GETSTATIC ? 1 : 0);
                boolean isDirect = !lambdaImpl.getName().startsWith("lambda$");
                var lambda = new Lambda(isDirect, new ReferenceInfo(
                        lambdaImpl.getOwner(),
                        lambdaImpl.getOwner().replace('/', '.'), lambdaImpl.getName(),
                        lambdaImpl.getDesc(), Type.getMethodType(lambdaImpl.getDesc()),
                        lambdaImpl.getTag() == Opcodes.H_INVOKESTATIC
                ), new Container<>(false));

                addInsn(new InsnNode(-1) {
                    @Override
                    public void accept(MethodVisitor methodVisitor) {
                        // check if it should be discarded
                        if (lambda.discard.value) {
                            for (int i = 0; i < argCount; i++)
                                methodVisitor.visitInsn(Opcodes.POP);
                            methodVisitor.visitInsn(Opcodes.ACONST_NULL);
                        } else {
                            methodVisitor.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
                        }
                    }
                });

                for (int i = 0; i < argCount; i++)
                    computeStack.pop();
                computeStack.push(lambda);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                final ReferenceInfo calledMethodInfo = new ReferenceInfo(owner, owner.replace('/', '.'), name, descriptor, Type.getMethodType(descriptor), opcode == Opcodes.INVOKESTATIC);
                /* Check for usage of dependencies through proxy methods */

                // check for Usage.optionally(Supplier<T>)
                if (NAME_Usage.equals(owner) && "optionally".equals(name)) {
                    var lambda = (Lambda) computeStack.pop();
                    ReferenceAnalysis analysis;
                    analysis = publicReference(context, lambda.methodInfo);
                    analysis.referenceOptional(context);

                    List<ReferenceInfo> dependencies = lambda.direct() ?
                            List.of(lambda.methodInfo) :
                            analysis.requiredDependencies;
                    if (dependencies != null) {
                        dependencies.forEach(dep ->
                                classAnalysis.dependencies.add(new MethodDependency(true, dep, null)));
                    }

                    // discard lambda if the dependencies arent fulfilled
                    boolean allImplemented = abstractionManager.areAllImplemented(dependencies);
                    if (!allImplemented) {
                        if (!lambda.direct()) {
                            analysis.optionalReferenceDropped(context);
                        }

                        lambda.discard.value = true;
                    }

                    if ("(Ljava/util/function/Supplier;)Ljava/util/Optional;".equals(descriptor)) {
                        // transform bytecode
                        if (!allImplemented) {
                            // the methods are not all implemented,
                            // substitute call with notPresentOptional
                            super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    NAME_InternalSubstituteMethods, "notPresentOptional",
                                    "(Ljava/util/function/Supplier;)Ljava/util/Optional;", false
                            );
                        } else {
                            // the methods are implemented, dont substitute
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        }

                        computeStack.push(ReturnValue.of(calledMethodInfo));
                        return;
                    }

                    if ("(Ljava/lang/Runnable;)B".equals(descriptor)) {
                        // transform bytecode
                        if (!allImplemented) {
                            // the methods are not all implemented,
                            // substitute call with notPresentBoolean
                            super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    NAME_InternalSubstituteMethods, "notPresentBoolean",
                                    "(Ljava/lang/Runnable;)B", false
                            );
                        } else {
                            // the methods are implemented, dont substitute
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        }

                        computeStack.push(ReturnValue.of(calledMethodInfo));
                        return;
                    }

                    return;
                }

                // check for Usage.oneOf(Optional<T>...)
                if (NAME_Usage.equals(owner) && "requireAtLeastOne".equals(name) && "([Ljava/util/function/Supplier;)Ljava/lang/Object;".equals(descriptor)) {
                    // get array of lambdas
                    Lambda[] lambdas = ReflectUtil.arrayCast((Object[]) computeStack.pop(), Lambda.class);
                    Lambda chosen = null;                                            // The chosen lambda
                    List<MethodDependency> chosenDependencies = new ArrayList<>();   // The method dependencies of the chosen lambda
                    List<MethodDependency> optionalDependencies = new ArrayList<>(); // The optional dependencies of this switch
                    for (Lambda lambda : lambdas) {
                        ReferenceAnalysis analysis;
                        analysis = publicReference(context, lambda.methodInfo);

                        // get dependencies as methods
                        List<ReferenceInfo> dependencies = lambda.direct() ?
                                List.of(lambda.methodInfo) :
                                analysis.requiredDependencies;

                        // if not implemented, add as optional dependencies
                        if (!abstractionManager.areAllImplemented(dependencies) || chosen != null) {
                            CollectionUtil.mapImmediate(dependencies, dep -> new MethodDependency(true, dep, null), classAnalysis.dependencies, optionalDependencies);
                            lambda.discard.value = true;
                            continue;
                        }

                        // if one is implemented, add as required dependencies
                        chosen = lambda;
                        analysis.referenceRequired(context);
                        if (dependencies != null) {
                            CollectionUtil.mapImmediate(dependencies, dep -> new MethodDependency(false, dep, false), classAnalysis.dependencies, chosenDependencies);
                        }
                    }

                    // register switch
                    classAnalysis.switchDependencies.add(new OneOfDependency(chosenDependencies, optionalDependencies, chosen != null));

                    // replace method call
                    if (chosen != null) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, NAME_InternalSubstituteMethods,
                                "onePresent", "([Ljava/util/function/Supplier;)Ljava/lang/Object;",
                                false);
                    } else {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, NAME_InternalSubstituteMethods,
                                "nonePresent", "([Ljava/util/function/Supplier;)Ljava/lang/Object;",
                                false);
                    }

                    return;
                }

                // analyze public method
                var analysis = publicReference(context, calledMethodInfo);
                if (analysis != null) {
                    methodAnalysis.requiredDependencies.addAll(analysis.requiredDependencies);
                    methodAnalysis.allAnalyzedReferences.add(analysis);
                }

                /* Check for direct usage of dependencies */
                if (isDependencyReference(context, calledMethodInfo) && !specialMethods.contains(name)) {
                    classAnalysis.dependencies.add(new MethodDependency(false, calledMethodInfo, null));

                    // dont transform required if the method its called in
                    // is a block used by Usage.optionally
                    if (methodAnalysis.optionalReferenceNumber <= 0) {
                        // insert runtime throw
                        if (!abstractionManager.isImplemented(calledMethodInfo)) {
                            addInsn(new InsnNode(-1) {
                                @Override
                                public void accept(MethodVisitor mv) {
                                    if (methodAnalysis.optionalReferenceNumber < 0) {
                                        mv.visitTypeInsn(Opcodes.NEW, NAME_NotImplementedException);
                                        mv.visitInsn(Opcodes.DUP);
                                        makeMethodInfo(mv, calledMethodInfo.ownerInternalName(), calledMethodInfo.name(), calledMethodInfo.desc(), calledMethodInfo.isStatic());
                                        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, NAME_NotImplementedException, "<init>", "(L" + NAME_MethodInfo + ";)V", false);
                                        mv.visitInsn(Opcodes.ATHROW);
                                    }
                                }
                            });
                        }
                    }

                    methodAnalysis.requiredDependencies.add(calledMethodInfo);
                }

                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                Type mt = Type.getMethodType(descriptor);
                if (opcode != Opcodes.INVOKESTATIC)
                    computeStack.pop(); // instance on the stack
                for (int i = 0, n = mt.getArgumentTypes().length; i < n; i++) {
                    computeStack.pop();
                }

                if (!mt.getReturnType().equals(Type.VOID_TYPE)) {
                    computeStack.push(ReturnValue.of(calledMethodInfo));
                }
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                super.visitTypeInsn(opcode, type);
                if (opcode == Opcodes.ANEWARRAY) {
                    int size = (int) computeStack.pop();
                    computeStack.push(new Object[size]);
                }

                if (opcode == Opcodes.NEW) {
                    computeStack.push(new InstanceOf(Type.getObjectType(type)));
                }
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) {
                    var fieldInfo = ReferenceInfo.forFieldInfo(owner, name, descriptor, opcode == Opcodes.GETSTATIC);

                    // pop instance if necessary
                    if (opcode == Opcodes.GETFIELD) {
                        computeStack.pop();
                    }

                    computeStack.push(new FieldValue(fieldInfo, false));

                    // register reference
                    var analysis = publicReference(context, fieldInfo);
                    context.currentAnalysis().registerReference(analysis);
                    analysis.referenceRequired(context);

                    /* Check for direct usage of dependencies */
                    if (isDependencyReference(context, fieldInfo) && !specialMethods.contains(name)) {
                        classAnalysis.dependencies.add(new MethodDependency(false, fieldInfo, null));

                        // dont transform required if the method its called in
                        // is a block used by Usage.optionally
                        if (methodAnalysis.optionalReferenceNumber <= 0) {
                            // insert runtime throw
                            if (!abstractionManager.isImplemented(fieldInfo)) {
                                addInsn(new InsnNode(-1) {
                                    @Override
                                    public void accept(MethodVisitor mv) {
                                        if (methodAnalysis.optionalReferenceNumber < 0) {
                                            mv.visitTypeInsn(Opcodes.NEW, NAME_NotImplementedException);
                                            mv.visitInsn(Opcodes.DUP);
                                            makeFieldInfo(mv, fieldInfo.ownerInternalName(), fieldInfo.name(), fieldInfo.desc(), fieldInfo.isStatic());
                                            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, NAME_NotImplementedException, "<init>", "(L" + NAME_MethodInfo + ";)V", false);
                                            mv.visitInsn(Opcodes.ATHROW);
                                        }
                                    }
                                });
                            }
                        }

                        methodAnalysis.requiredDependencies.add(fieldInfo);
                    }

                    super.visitFieldInsn(opcode, owner, name, descriptor);
                    return;
                }

                super.visitFieldInsn(opcode, owner, name, descriptor);
            }

            @Override
            public void visitVarInsn(int opcode, int varIndex) {
                super.visitVarInsn(opcode, varIndex);
                Type t = Type.getType(oldMethod.localVariables.get(varIndex).desc);
                switch (opcode) {
                    case Opcodes.ALOAD, Opcodes.ILOAD, Opcodes.DLOAD, Opcodes.FLOAD -> computeStack.push(new FromVar(varIndex, t));
                    case Opcodes.ASTORE, Opcodes.ISTORE, Opcodes.DSTORE, Opcodes.FSTORE -> computeStack.pop();
                }
            }

            @Override public void visitLdcInsn(Object value) { super.visitLdcInsn(value); computeStack.push(value); }
            @Override public void visitIntInsn(int opcode, int operand) { super.visitIntInsn(opcode, operand); computeStack.push(operand); }
            @Override public void visitInsn(int opcode) {
                super.visitInsn(opcode);
                switch (opcode) {
                    case Opcodes.DUP -> computeStack.push(computeStack.peek());
                    case Opcodes.ACONST_NULL -> computeStack.push(null);
                    case Opcodes.ICONST_0 -> computeStack.push(0);
                    case Opcodes.ICONST_1 -> computeStack.push(1);
                    case Opcodes.ICONST_2 -> computeStack.push(2);
                    case Opcodes.ICONST_3 -> computeStack.push(3);
                    case Opcodes.ICONST_4 -> computeStack.push(4);
                    case Opcodes.ICONST_5 -> computeStack.push(5);
                    case Opcodes.POP, Opcodes.ARETURN, Opcodes.IRETURN, Opcodes.DRETURN, Opcodes.FRETURN -> { if (!computeStack.isEmpty()) computeStack.pop(); }
                    case Opcodes.POP2 -> { computeStack.pop(); computeStack.pop(); }
                    case Opcodes.AASTORE -> {
                        Object val = computeStack.pop();
                        int idx = (int) computeStack.pop();
                        Object[] arr = (Object[]) computeStack.pop();
                        arr[idx] = val;
                    }
                }
            }

            @Override
            public void visitEnd() {
                for (var hook : hooks) hook.leaveMethod(context);
                context.leaveMethod();
                methodAnalysis.complete = true;
            }
        };

        classNode.methods.set(classNode.methods.indexOf(oldMethod), newMethod);
        return visitor;
    }

    /**
     * Analyze the class to locate and analyze it's dependencies, and set
     * the result to be retrievable by {@link #getClassAnalysis()}.
     *
     * @return This.
     */
    public ClassDependencyAnalyzer analyzeAndTransform() {
        /* find dependencies */
        classNode.accept(new ClassVisitor(ASMUtil.ASM_V) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                ReferenceInfo info = new ReferenceInfo(internalName, className, name, descriptor, Type.getMethodType(descriptor), Modifier.isStatic(access));

                // check for cached
                var analysis = getReferenceAnalysis(info);
                if (analysis != null && analysis.complete)
                    return null;

                // create analysis, visit method and register result
                return methodVisitor(new AnalysisContext(abstractionManager), info, new ReferenceAnalysis(ClassDependencyAnalyzer.this, info), ASMUtil.findMethod(classNode, name, descriptor));
            }

            @Override
            public void visitEnd() {
                // post-analyze all methods
                for (MethodNode methodNode : classNode.methods) {
                    ReferenceAnalysis analysis = getReferenceAnalysis(ReferenceInfo.forMethodInfo(internalName, methodNode.name, methodNode.desc, Modifier.isStatic(methodNode.access)));
                    if (analysis.optionalReferenceNumber < 0 || abstractionManager.getRequiredMethodPredicate().test(analysis)) {
                        analysis.referenceRequired(new AnalysisContext(abstractionManager));
                    }

                    analysis.postAnalyze();
                }

                // filter dependencies
                classAnalysis.dependencies = classAnalysis.dependencies.stream()
                        .map(d -> d.optional() ? d : d.asOptional(getReferenceAnalysis(d.info()).optionalReferenceNumber >= 0))
                        .collect(Collectors.toSet());

                // reduce dependencies
                Set<MethodDependency> finalDependencySet = new HashSet<>();
                for (MethodDependency dependency : classAnalysis.dependencies) {
                    final MethodDependency mirror = dependency.asOptional(!dependency.optional());

                    if (!dependency.optional()) {
                        finalDependencySet.remove(mirror);
                        finalDependencySet.add(dependency);
                        continue;
                    }

                    if (!finalDependencySet.contains(mirror)) {
                        finalDependencySet.add(dependency);
                        continue;
                    }
                }

                classAnalysis.dependencies = finalDependencySet;

                // mark complete
                classAnalysis.completed = true;
            }
        });

        return this;
    }

    public ClassAnalysis getClassAnalysis() {
        return classAnalysis;
    }

    public ClassNode getClassNode() {
        return classNode;
    }
}
