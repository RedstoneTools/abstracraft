package tools.redstone.abstracraft.core;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Analyzes given class bytes for usage of abstraction methods.
 *
 * @author orbyfied
 */
public class ClassDependencyAnalyzer {

    public static ClassLoader transformingLoader(Predicate<String> namePredicate, AbstractionManager abstractionManager, ClassLoader parent) {
        return ReflectUtil.transformingClassLoader(namePredicate, parent, (name, reader, writer) -> {
            var analyzer = new ClassDependencyAnalyzer(abstractionManager, reader);
            analyzer.analyzeAndTransform();
            analyzer.getClassNode().accept(writer);
            System.out.println("ClassAnalysis(" + name + ") = " + analyzer.getClassAnalysis());
        }, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    }

    static final int ASMV = Opcodes.ASM9;
    static final Set<String> specialMethods = Set.of("isImplemented", "<init>");     // Special methods on abstractions
    static Map<String, Boolean> isAbstractionClassCache = new HashMap<>(); // Cache result of `isAbstractionClass`

    /**
     * Checks whether the class by the given name implements
     * {@link Abstraction}.
     *
     * @param name The class name.
     * @return Whether it is an abstraction class.
     */
    static boolean isAbstractionClass(String name) {
        Boolean b = isAbstractionClassCache.get(name);
        if (b != null)
            return b;

        try {
            isAbstractionClassCache.put(name,
                    b = Abstraction.class.isAssignableFrom(ReflectUtil.getClass(name)));
            return b;
        } catch (Exception e) {
            return false;
        }
    }

    // The result of the dependency analysis on a class
    public static class ClassAnalysis {
        public boolean completed = false;                                               // Whether this analysis is complete
        public final Map<MethodInfo, MethodAnalysis> analyzedMethods = new HashMap<>(); // All analysis objects for the methods in this class
        public Set<MethodDependency> dependencies = new HashSet<>();                    // All method dependencies recorded in this class
    }

    public static class MethodAnalysis {
        public final MethodInfo method;                                           // The method this analysis covers
        public List<MethodInfo> requiredDependencies = new ArrayList<>();         // All recorded required dependencies used by this method
        public int optionalReferenceNumber = 0;                                   // Whether this method is referenced in an optionally() block
        public List<MethodAnalysis> allAnalyzedMethodsCalled = new ArrayList<>(); // The analysis objects of all methods normally called by this method
        public boolean complete = false;

        MethodAnalysis(MethodInfo method) {
            this.method = method;
        }

        // Register and propagate that this method is part of an optional block
        public void referenceOptional() {
            this.optionalReferenceNumber += 1;
            for (MethodAnalysis analysis : allAnalyzedMethodsCalled) {
                analysis.referenceOptional();
            }
        }

        // Register and propagate that this method is required
        public void referenceRequired() {
            this.optionalReferenceNumber -= 1000;
            for (MethodAnalysis analysis : allAnalyzedMethodsCalled) {
                analysis.referenceRequired();
            }
        }
    }

    /* Stack Tracking */
    record InstanceOf(Type type) { }
    record ReturnValue(MethodInfo method, Type type) { public static ReturnValue of(MethodInfo info) { return new ReturnValue(info, info.asmType().getReturnType()); } }
    record FromVar(int varIndex) { }

    /* Compute Stack Tracking */
    record Lambda(boolean direct, MethodInfo methodInfo, Container<Boolean> discard) { }

    static final Type TYPE_Usage = Type.getType(Usage.class);
    static final String NAME_Usage = TYPE_Usage.getInternalName();
    static final Type TYPE_InternalSubstituteMethods = Type.getType(Usage.InternalSubstituteMethods.class);
    static final String NAME_InternalSubstituteMethods = TYPE_InternalSubstituteMethods.getInternalName();
    static final Type TYPE_NotImplementedException = Type.getType(NotImplementedException.class);
    static final String NAME_NotImplementedException = TYPE_NotImplementedException.getInternalName();
    static final Type TYPE_MethodInfo = Type.getType(MethodInfo.class);
    static final String NAME_MethodInfo = TYPE_MethodInfo.getInternalName();

    private final AbstractionManager abstractionManager;       // The abstraction manager
    private final String internalName;                         // The internal name of this class
    private final String className;                            // The public name of this class
    private final ClassReader classReader;                     // The class reader for the bytecode
    private final ClassNode classNode;                         // The class node to be written

    private ClassAnalysis classAnalysis = new ClassAnalysis(); // The result of analysis

    public ClassDependencyAnalyzer(AbstractionManager manager,
                                   ClassReader classReader) {
        this.abstractionManager = manager;
        this.internalName = classReader.getClassName();
        this.className = internalName.replace('/', '.');
        this.classReader = classReader;
        this.classNode = new ClassNode(ASMV);
        classReader.accept(classNode, 0);
    }

    // Make a MethodInfo instance on the stack
    private static void makeMethodInfo(MethodVisitor visitor, String owner, String name, String desc) {
        visitor.visitLdcInsn(owner);
        visitor.visitLdcInsn(name);
        visitor.visitLdcInsn(desc);
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, TYPE_MethodInfo.getInternalName(), "forInfo",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)L" + NAME_MethodInfo + ";", false);
    }

    /** Get a method analysis if present */
    public MethodAnalysis getMethodAnalysis(MethodInfo info) {
        return abstractionManager.getMethodAnalysis(info);
    }

    /** Analyzes and transforms a local method */
    public MethodAnalysis localMethod(MethodInfo info) {
        try {
            if (!info.ownerInternalName().equals(this.internalName))
                throw new AssertionError();

            // check for cached
            var analysis = getMethodAnalysis(info);
            if (analysis != null && analysis.complete)
                return analysis;

            // find method node
            MethodNode m = ASMUtil.findMethod(classNode, info.name(), info.desc());
            if (m == null)
                return null;

            // create analysis, visit method and register result
            analysis = new MethodAnalysis(info);
            MethodVisitor v = methodVisitor(info, analysis, m);
            m.accept(v); // the visitor registers the result automatically
            return analysis;
        } catch (Exception e) {
            throw new RuntimeException("Error while analyzing local method " + info, e);
        }
    }

    /** Analyzes and transforms a method from any class */
    public MethodAnalysis publicMethod(MethodInfo info) {
        // check for local method
        if (info.ownerInternalName().equals(this.internalName))
            return localMethod(info);

        // check for cached
        var analysis = getMethodAnalysis(info);
        if (analysis != null && analysis.complete)
            return analysis;

        // analyze through owner class
        ClassDependencyAnalyzer analyzer = abstractionManager.analyzer(info.ownerInternalName(), true);
        if (analyzer == null)
            return null;
        return analyzer.localMethod(info);
    }

    /**
     * Creates a method visitor which analyzes and transforms a local method.
     *
     * @param currentMethodInfo The method descriptor.
     * @return The visitor.
     */
    public MethodVisitor methodVisitor(MethodInfo currentMethodInfo, MethodAnalysis methodAnalysis, MethodNode oldMethod) {
        String name = currentMethodInfo.name();
        String descriptor = currentMethodInfo.desc();

        // check old method
        if (oldMethod == null) {
            throw new IllegalArgumentException("No local method by " + currentMethodInfo + " in class " + internalName);
        }

        abstractionManager.registerAnalysis(methodAnalysis);
        classAnalysis.analyzedMethods.put(currentMethodInfo, methodAnalysis);

        // create method visitor
        MethodNode newMethod = new MethodNode(oldMethod.access, name, descriptor, oldMethod.signature, oldMethod.exceptions.toArray(new String[0]));
        var visitor = new MethodVisitor(ASMV, newMethod) {
            // The compute stack of lambda's
            Stack<Object> computeStack = new Stack<>();

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
                var lambda = new Lambda(isDirect, new MethodInfo(
                        lambdaImpl.getOwner(),
                        lambdaImpl.getOwner().replace('/', '.'), lambdaImpl.getName(),
                        lambdaImpl.getDesc(), Type.getMethodType(lambdaImpl.getDesc())
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
                final MethodInfo calledMethodInfo = new MethodInfo(owner, owner.replace('/', '.'), name, descriptor, Type.getMethodType(descriptor));
                /* Check for usage of dependencies through proxy methods */

                // check for Usage.optionally(Supplier<T>)
                if (NAME_Usage.equals(owner) && "optionally".equals(name)) {
                    var lambda = (Lambda) computeStack.pop();
                    MethodAnalysis analysis;
                    if (!lambda.direct()) {
                        // visit/transform lambda function
                        analysis = localMethod(lambda.methodInfo);
                        analysis.referenceOptional();
                    } else {
                        analysis = null;
                    }

                    List<MethodInfo> dependencies = lambda.direct() ?
                            List.of(lambda.methodInfo) :
                            analysis.requiredDependencies;
                    if (dependencies != null) {
                        dependencies.forEach(dep ->
                                classAnalysis.dependencies.add(new MethodDependency(true, dep, currentMethodInfo)));
                    }

                    // discard lambda if the dependencies arent fulfilled
                    if (!abstractionManager.areAllImplemented(dependencies)) {
                        lambda.discard.value = true;
                    }

                    if ("(Ljava/util/function/Supplier;)Ljava/util/Optional;".equals(descriptor)) {
                        // transform bytecode
                        if (!abstractionManager.areAllImplemented(dependencies)) {
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
                        if (!abstractionManager.areAllImplemented(dependencies)) {
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
                    Lambda implemented = null;
                    boolean oneImplemented = false;
                    for (Lambda lambda : lambdas) {
                        MethodAnalysis analysis;
                        if (!lambda.direct()) {
                            // visit/transform lambda function
                            analysis = localMethod(lambda.methodInfo);
                            analysis.referenceOptional();
                        } else {
                            analysis = null;
                        }

                        List<MethodInfo> dependencies = lambda.direct() ?
                                List.of(lambda.methodInfo) :
                                analysis.requiredDependencies;
                        if (!abstractionManager.areAllImplemented(dependencies) || oneImplemented) {
                            if (analysis != null) analysis.referenceOptional();
                            lambda.discard.value = true;
                            continue;
                        }

                        oneImplemented = true;
                        implemented = lambda;
                        if (analysis != null) analysis.referenceRequired();
                        if (dependencies != null) {
                            dependencies.forEach(dep -> classAnalysis.dependencies.add(new MethodDependency(false, dep, currentMethodInfo)));
                        }
                    }

                    // replace method call
                    if (oneImplemented) {
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
                var analysis = publicMethod(calledMethodInfo);
                if (analysis != null) {
                    methodAnalysis.allAnalyzedMethodsCalled.add(analysis);

                    if (methodAnalysis.optionalReferenceNumber <= 0) {
                        methodAnalysis.requiredDependencies.addAll(analysis.requiredDependencies);
                    }
                }

                /* Check for direct usage of dependencies */
                if (isAbstractionClass(calledMethodInfo.ownerClassName()) && !specialMethods.contains(name)) {
                    // dont transform required if the method its called in
                    // is a block used by Usage.optionally
                    if (methodAnalysis.optionalReferenceNumber <= 0) {
                        classAnalysis.dependencies.add(new MethodDependency(false, calledMethodInfo, currentMethodInfo));

                        // insert runtime throw
                        if (!abstractionManager.isImplemented(calledMethodInfo)) {
                            super.visitTypeInsn(Opcodes.NEW, NAME_NotImplementedException);
                            super.visitInsn(Opcodes.DUP);
                            makeMethodInfo(this, calledMethodInfo.ownerInternalName(), calledMethodInfo.name(), calledMethodInfo.desc());
                            super.visitMethodInsn(Opcodes.INVOKESPECIAL, NAME_NotImplementedException, "<init>", "(L" + NAME_MethodInfo + ";)V", false);
                            super.visitInsn(Opcodes.ATHROW);
                        }
                    }

                    methodAnalysis.requiredDependencies.add(calledMethodInfo);
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    return;
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
            public void visitVarInsn(int opcode, int varIndex) {
                super.visitVarInsn(opcode, varIndex);
                switch (opcode) {
                    case Opcodes.ALOAD -> computeStack.push(new FromVar(varIndex));
                    case Opcodes.ASTORE -> computeStack.pop();
                }
            }

            @Override public void visitLdcInsn(Object value) { super.visitLdcInsn(value); computeStack.push(value); }
            @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) { super.visitFieldInsn(opcode, owner, name, descriptor); computeStack.push(null /* todo */); }
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
                    case Opcodes.POP -> computeStack.pop();
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
        classAnalysis = new ClassAnalysis();

//        classNode.accept(new TraceClassVisitor(new PrintWriter(System.out)));

        /* find dependencies */
        classNode.accept(new ClassVisitor(ASMV) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodInfo info = new MethodInfo(internalName, className, name, descriptor, Type.getMethodType(descriptor));

                // check for cached
                var analysis = getMethodAnalysis(info);
                if (analysis != null && analysis.complete)
                    return null;

                // create analysis, visit method and register result
                return methodVisitor(info, new MethodAnalysis(info), ASMUtil.findMethod(classNode, name, descriptor));
            }

            @Override
            public void visitEnd() {
                // post-analyze all methods
                for (MethodNode methodNode : classNode.methods) {
                    MethodAnalysis analysis = getMethodAnalysis(MethodInfo.forInfo(internalName, methodNode.name, methodNode.desc));
                    if (analysis.optionalReferenceNumber <= 0) {
                        analysis.referenceRequired();
                    }
                }

                // filter dependencies
                classAnalysis.dependencies = classAnalysis.dependencies.stream()
                        .map(d -> d.optional() ? d : d.asOptional(getMethodAnalysis(d.calledIn()).optionalReferenceNumber > 0))
                        .peek(d -> System.out.println(d.info() + ": " + getMethodAnalysis(d.calledIn()).optionalReferenceNumber))
                        .collect(Collectors.toSet());

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
