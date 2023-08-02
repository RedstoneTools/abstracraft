package tools.redstone.abstracraft.core;

import org.objectweb.asm.*;

import java.util.*;

/**
 * Analyzes bytecode of a class to determine the methods required.
 *
 * @author orbyfied
 */
public class RequiredMethodFinder {

    /**
     * Analyzes and transforms the given class, registering
     * the required dependencies it detects.
     *
     * @param in The input bytes.
     * @return The result bytes.
     */
    public static byte[] transformClass(byte[] in) {
        ClassReader reader = new ClassReader(in);
        ClassWriter writer = new ClassWriter(reader, 0);
        var detector = new RequiredMethodFinder(reader);
        var list = detector.findRequiredDependenciesForClass();
        detector.auditClassWithDependencies(writer, list);
        return writer.toByteArray();
    }

    static Map<String, Boolean> isMethodTypeCache = new HashMap<>();

    static boolean isMethodType(String name) {
        Boolean b = isMethodTypeCache.get(name);
        if (b != null)
            return b;

        try {
            isMethodTypeCache.put(name, b = RawOptionalMethod.class.isAssignableFrom(Class.forName(name)));
            return b;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    final ClassReader classReader;

    public RequiredMethodFinder(ClassReader classReader) {
        this.classReader = classReader;
    }

    final List<Type> definedRequired = new ArrayList<>();

    static final Type TYPE_Optional = Type.getType(Optional.class);
    static final String NAME_Optional = TYPE_Optional.getInternalName();
    static final Type TYPE_Required = Type.getType(Required.class);
    static final String NAME_Required = TYPE_Required.getInternalName();

    record RequiredDependency(Type dependencyClass) { }
    record MethodInfo(int access, String name, String desc, String signature, String[] exceptions) { }
    record FieldGet(Type fieldType, String owner, String name) { }
    record InstanceOf(Type type, HashMap<String, Object> flags) {
        public InstanceOf set(String flag, Object val) {
            flags.put(flag, val);
            return this;
        }

        public InstanceOf set(String flag) {
            return set(flag, true);
        }

        @SuppressWarnings("unchecked")
        public <T> T get(String flag) {
            return (T) flags.get(flag);
        }

        public boolean has(String flag) {
            return flags.containsKey(flag);
        }
    }

    static InstanceOf instanceOf(Type type) {
        return new InstanceOf(type, new HashMap<>());
    }

    /**
     * Find all dependencies required by the given class.
     *
     * @return The list of dependencies.
     */
    public List<RequiredDependency> findRequiredDependenciesForClass() {
        List<RequiredDependency> list = new ArrayList<>();
        classReader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                return findRequiredDependenciesForMethod(list,
                        new MethodInfo(access, name, desc, signature, exceptions));
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!descriptor.equals("L" + NAME_Required + ";"))
                    return null;
                // register already defined dependencies
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        if (!name.equals("value"))
                            return null;
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String name, Object value) {
                                definedRequired.add((Type) value);
                            }
                        };
                    }
                };
            }
        }, 0);

        return list;
    }

    /**
     * Find all dependencies required by the given method.
     *
     * @param list The list of dependencies to output to.
     */
    public MethodVisitor findRequiredDependenciesForMethod(List<RequiredDependency> list, MethodInfo methodInfo) {
        return new MethodVisitor(Opcodes.ASM9) {
            /* Track the current compute stack */
            Stack<Object> stack = new Stack<>();

            @Override
            public void visitLdcInsn(Object cst) {
                stack.push(cst);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (isMethodType(owner) && "call".equals(name)) {
                    list.add(new RequiredDependency(Type.getObjectType(owner)));
                }
            }
        };
    }

    /**
     * Writes the given dependencies to the @Required annotation for the
     * given class.
     *
     * @param visitor The class visitor.
     * @param dependencies The dependencies to register.
     */
    public void auditClassWithDependencies(ClassVisitor visitor, List<RequiredDependency> dependencies) {
        // visit annotation and enter array
        AnnotationVisitor av = visitor.visitAnnotation("L" + NAME_Required + ";", true);
        av = av.visitArray("value");

        // restore previous elements
        for (Type defined : definedRequired) {
            av.visit(null, defined);
        }

        // register dependencies
        for (RequiredDependency dependency : dependencies) {
            av.visit(null, dependency.dependencyClass());
        }

        av.visitEnd();
    }

}
