package tools.redstone.abstracraft.core;

import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class MethodDependencyAnalyzer {

    static final int ASMV = Opcodes.ASM9;
    static final Set<String> specialMethods = Set.of("isImplemented");     // Special methods on abstractions
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
            isAbstractionClassCache.put(name, b = Abstraction.class.isAssignableFrom(
                    Class.forName(name.replace('/', '.'))));
            return b;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Represents the dependency of a piece of code on a specific method.
     */
    public record MethodDependency(
            MethodInfo info,
            // Details of the call of the method
            MethodInfo calledIn,
            int callInstructionIndex
    ) { }

    // The result of the dependency analysis on a class
    public record ClassAnalysis(
            List<MethodDependency> methodDependencies
    ) { }

    // ASM method information
    public record MethodInfo(String owner, String name, String desc, Type asmType) { }

    /* Compute Stack Tracking */
    record Lambda(MethodInfo methodInfo) { }

    static final Type TYPE_Usage = Type.getType(Usage.class);
    static final String NAME_Usage = TYPE_Usage.getInternalName();

    private final AbstractionManager abstractionManager; // The abstraction manager
    private final String internalName;                   // The internal name of this class
    private final ClassReader classReader;               // The class reader for the bytecode
    private final ClassWriter classWriter;               // The class writer for transformation of the bytecode

    private ClassAnalysis analysis;                      // The result of analysis

    public MethodDependencyAnalyzer(AbstractionManager manager,
                                    ClassReader classReader, ClassWriter classWriter) {
        this.abstractionManager = manager;
        this.internalName = classReader.getClassName();
        this.classReader = classReader;
        this.classWriter = classWriter;
    }

    /**
     * Analyze the class to locate and analyze it's dependencies, and set
     * the result to be retrievable by {@link #getAnalysis()}.
     *
     * @return This.
     */
    public MethodDependencyAnalyzer analyze() {
        final List<MethodDependency> methodDependencies = new ArrayList<>();

        /* set up context */
        // registers the dependencies of each method, not method calls
        Map<MethodInfo, List<MethodInfo>> dependenciesPerMethod = new HashMap<>();

        /* find dependencies */
        classReader.accept(new ClassVisitor(ASMV) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                final MethodInfo currentMethodInfo = new MethodInfo(classReader.getClassName(), name, descriptor, Type.getMethodType(descriptor));
                return new MethodVisitor(ASMV) {
                    // The compute stack of lambda's
                    Stack<Lambda> lambdaStack = new Stack<>();

                    @Override
                    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                        // check for lambda factory
                        if (
                                !bootstrapMethodHandle.getOwner().equals("java/lang/invoke/LambdaMetafactory") ||
                                !bootstrapMethodHandle.getName().equals("metafactory")
                        ) return;

                        Handle lambdaImpl = (Handle) bootstrapMethodArguments[1];
                        lambdaStack.push(new Lambda(new MethodInfo(
                                lambdaImpl.getOwner(), lambdaImpl.getName(),
                                lambdaImpl.getDesc(), Type.getMethodType(lambdaImpl.getDesc())
                        )));
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        final MethodInfo calledMethodInfo = new MethodInfo(owner, name, descriptor, Type.getMethodType(descriptor));

                        /* Check for usage of dependencies through proxy methods */

                        // check for Usage.optionally(Supplier<T>)
                        if (NAME_Usage.equals(owner) && "optionally".equals(name) && "(Ljava/util/functional/Supplier;)Ljava/util/Optional;".equals(descriptor)) {
                            var lambda = lambdaStack.pop();
                            List<MethodInfo> dependencies = dependenciesPerMethod.get(lambda.methodInfo());
                            if (dependencies != null) {
                                dependencies.forEach(dep ->
                                        methodDependencies.add(new MethodDependency(dep, currentMethodInfo, 0 /* todo */)));
                            }
                        }

                        // check for Usage.optionally(Runnable)
                        if (NAME_Usage.equals(owner) && "optionally".equals(name) && "(Ljava/lang/Runnable;)B".equals(descriptor)) {
                            var lambda = lambdaStack.pop();
                            List<MethodInfo> dependencies = dependenciesPerMethod.get(lambda.methodInfo());
                            if (dependencies != null) {
                                dependencies.forEach(dep ->
                                        methodDependencies.add(new MethodDependency(dep, currentMethodInfo, 0 /* todo */)));
                            }
                        }

                        // check for Usage.oneOf(Optional<T>...)
                        if (NAME_Usage.equals(owner) && "oneOf".equals(name) && "([Ljava/util/Optional;)Ljava/util/Optional;".equals(descriptor)) {
                            // todo
                        }

                        /* Check for direct usage of dependencies */
                        if ((opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE) &&
                                isAbstractionClass(owner) && !specialMethods.contains(name)) {
                            dependenciesPerMethod.computeIfAbsent(currentMethodInfo, __ -> new ArrayList<>())
                                    .add(calledMethodInfo);
                        }
                    }
                };
            }
        }, 0);

        this.analysis = new ClassAnalysis(methodDependencies);
        return this;
    }

    public ClassAnalysis getAnalysis() {
        return analysis;
    }

    /**
     * Transforms the current class using the class writer and the
     * last analysis.
     *
     * @return This.
     */
    public MethodDependencyAnalyzer transform() {
        if (this.analysis == null)
            throw new IllegalStateException("No analysis performed before attempted transformation");



        return this;
    }
}
