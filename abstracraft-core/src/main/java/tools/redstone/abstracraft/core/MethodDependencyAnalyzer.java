package tools.redstone.abstracraft.core;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Predicate;

public class MethodDependencyAnalyzer {

    public static ClassLoader transformingLoader(Predicate<String> namePredicate, AbstractionManager abstractionManager, ClassLoader parent) {
        return ReflectUtil.transformingClassLoader(namePredicate, parent, (name, reader, writer) -> {
            var analyzer = new MethodDependencyAnalyzer(abstractionManager, reader);
            analyzer.analyzeAndTransform();
            analyzer.getClassNode().accept(writer);
            System.out.println(analyzer.getAnalysis());
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
    public record ClassAnalysis(
            List<MethodDependency> methodDependencies,
            boolean allDependenciesImplemented
    ) { }

    /* Represents an invokedynamic instruction */
    record InvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
        public void visit(MethodVisitor v) {
            v.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
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

    private final AbstractionManager abstractionManager; // The abstraction manager
    private final String internalName;                   // The internal name of this class
    private final ClassReader classReader;               // The class reader for the bytecode
    private final ClassNode classNode;                   // The class node to be written

    private ClassAnalysis analysis;                      // The result of analysis

    public MethodDependencyAnalyzer(AbstractionManager manager,
                                    ClassReader classReader) {
        this.abstractionManager = manager;
        this.internalName = classReader.getClassName();
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

    /**
     * Analyze the class to locate and analyze it's dependencies, and set
     * the result to be retrievable by {@link #getAnalysis()}.
     *
     * @return This.
     */
    public MethodDependencyAnalyzer analyzeAndTransform() {
        final List<MethodDependency> methodDependencies = new ArrayList<>();
        final boolean[] allDependenciesImplemented = { true };

        /* set up context */
        // registers the dependencies of each method, not method calls
        Map<MethodInfo, List<MethodInfo>> dependenciesPerMethod = new HashMap<>();
        // registers the visited methods
        Set<MethodInfo> methodsVisited = new HashSet<>();
        // the known scoped block lambdas
        Set<MethodInfo> scopedBlockLambdas = new HashSet<>();

        classNode.accept(new TraceClassVisitor(new PrintWriter(System.out)));

        /* find dependencies */
        classNode.accept(new ClassVisitor(ASMV) {
            public void transformMethod(String name, String desc) {
                MethodNode m = ASMUtil.findMethod(classNode, name, desc);
                if (m == null)
                    return;

                MethodVisitor v = visitAndTransformMethod(name, desc);
                if (v == null)
                    return;

                m.accept(v);
            }

            public MethodVisitor visitAndTransformMethod(String name, String descriptor) {
                final MethodInfo currentMethodInfo = new MethodInfo(classReader.getClassName(), classReader.getClassName().replace('/', '.'), name, descriptor, Type.getMethodType(descriptor));
                if (methodsVisited.contains(currentMethodInfo)) {
                    return null;
                }

                methodsVisited.add(currentMethodInfo);

                // remove old method to be replaced
                MethodNode old = ASMUtil.findMethod(classNode, name, descriptor);
                if (old == null) {
                    return null;
                }

                // get direct dependency list
                List<MethodInfo> directDependencies = dependenciesPerMethod
                        .computeIfAbsent(currentMethodInfo, __ -> new ArrayList<>());

                // create method visitor
                MethodNode newMethod = new MethodNode(old.access, name, descriptor, old.signature, old.exceptions.toArray(new String[0]));
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
                            if (!lambda.direct()) {
                                scopedBlockLambdas.add(lambda.methodInfo);

                                // visit/transform lambda function
                                transformMethod(lambda.methodInfo.name(), lambda.methodInfo.desc());
                            }

                            List<MethodInfo> dependencies = lambda.direct() ?
                                    List.of(lambda.methodInfo) :
                                    dependenciesPerMethod.get(lambda.methodInfo());
                            if (dependencies != null) {
                                dependencies.forEach(dep ->
                                        methodDependencies.add(new MethodDependency(true, dep, currentMethodInfo)));
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
                                if (!lambda.direct()) {
                                    scopedBlockLambdas.add(lambda.methodInfo);

                                    // visit/transform lambda function
                                    transformMethod(lambda.methodInfo.name(), lambda.methodInfo.desc());
                                }

                                List<MethodInfo> dependencies = lambda.direct() ?
                                        List.of(lambda.methodInfo) :
                                        dependenciesPerMethod.get(lambda.methodInfo());
                                if (!abstractionManager.areAllImplemented(dependencies) || oneImplemented) {
                                    lambda.discard.value = true;
                                    continue;
                                }

                                oneImplemented = true;
                                implemented = lambda;
                                if (dependencies != null) {
                                    dependencies.forEach(dep ->
                                            methodDependencies.add(new MethodDependency(false, dep, currentMethodInfo)));
                                }
                            }

                            // replace method call
                            if (oneImplemented) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, NAME_InternalSubstituteMethods,
                                        "onePresent", "([Ljava/util/function/Supplier;)Ljava/lang/Object;",
                                        false);
                            } else {
                                allDependenciesImplemented[0] = false;
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, NAME_InternalSubstituteMethods,
                                        "nonePresent", "([Ljava/util/function/Supplier;)Ljava/lang/Object;",
                                        false);
                            }

                            return;
                        }

                        // analyze and copy dependencies
                        transformMethod(calledMethodInfo.name(), calledMethodInfo.desc());
                        List<MethodInfo> dependencies = dependenciesPerMethod.get(calledMethodInfo);
                        if (dependencies != null) {
                            directDependencies.addAll(dependencies);
                        }

                        /* Check for direct usage of dependencies */
                        if (isAbstractionClass(calledMethodInfo.ownerClassName()) && !specialMethods.contains(name)) {
                            if (!scopedBlockLambdas.contains(currentMethodInfo)) {
                                methodDependencies.add(new MethodDependency(false, calledMethodInfo, currentMethodInfo));

                                // insert runtime throw
                                if (!abstractionManager.isImplemented(calledMethodInfo)) {
                                    allDependenciesImplemented[0] = false;
                                    super.visitTypeInsn(Opcodes.NEW, NAME_NotImplementedException);
                                    super.visitInsn(Opcodes.DUP);
                                    makeMethodInfo(this, calledMethodInfo.ownerInternalName(), calledMethodInfo.name(), calledMethodInfo.desc());
                                    super.visitMethodInsn(Opcodes.INVOKESPECIAL, NAME_NotImplementedException, "<init>", "(L" + NAME_MethodInfo + ";)V", false);
                                    super.visitInsn(Opcodes.ATHROW);
                                }
                            }

                            directDependencies.add(calledMethodInfo);
                            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                            return;
                        }

                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                        Type mt = Type.getMethodType(descriptor);
                        System.out.println("CALLED  " + calledMethodInfo);
                        System.out.println("CURRENT STACK IVK " + computeStack);
                        if (opcode != Opcodes.INVOKESTATIC)
                            computeStack.pop(); // instance on the stack
                        for (int i = 0, n = mt.getArgumentTypes().length; i < n; i++) {
                            computeStack.pop();
                        }

                        if (!mt.getReturnType().equals(Type.VOID_TYPE)) {
                            computeStack.push(ReturnValue.of(calledMethodInfo));
                        }
                        System.out.println("NEW STACK IVK     " + computeStack);
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        super.visitTypeInsn(opcode, type);
                        if (opcode == Opcodes.ANEWARRAY) {
                            int size = (int) computeStack.pop();
                            computeStack.push(new Object[size]);
                        }

                        if (opcode == Opcodes.NEW) {
                            computeStack.push(new InstanceOf(Type.getType(type)));
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
                                System.out.println("D CURRENT STACK   " + computeStack);
                                Object val = computeStack.pop();
                                int idx = (int) computeStack.pop();
                                Object[] arr = (Object[]) computeStack.pop();
                                arr[idx] = val;
                            }
                        }

                        System.out.println("CURRENT STACK     " + computeStack);
                    }
                };

                classNode.methods.set(classNode.methods.indexOf(old), newMethod);
                return visitor;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return visitAndTransformMethod(name, descriptor);
            }
        });

        this.analysis = new ClassAnalysis(methodDependencies, allDependenciesImplemented[0]);
        return this;
    }

    public ClassAnalysis getAnalysis() {
        return analysis;
    }

    public ClassNode getClassNode() {
        return classNode;
    }
}
