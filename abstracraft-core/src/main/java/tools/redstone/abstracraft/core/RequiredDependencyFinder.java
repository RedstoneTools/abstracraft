package tools.redstone.abstracraft.core;

import org.objectweb.asm.*;

import java.util.*;

/**
 * Utilizes ASM bytecode analysis to detect required dependencies
 * and automatically register them.
 */
public class RequiredDependencyFinder {

    public static void transformClass(String name, ClassReader reader, ClassWriter writer) {
        var detector = new RequiredDependencyFinder(reader);
        var list = detector.findRequiredDependenciesForClass();
        detector.auditClassWithDependencies(writer, list);
    }

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
        transformClass(reader.getClassName(), reader, writer);
        return writer.toByteArray();
    }

    //////////////////////////

    final ClassReader classReader;

    public RequiredDependencyFinder(ClassReader classReader) {
        this.classReader = classReader;
    }

    final List<Type> definedRequired = new ArrayList<>();

    static final Type TYPE_DynamicExtensible = Type.getType(DynamicExtensible.class);
    static final String NAME_DynamicExtensible = TYPE_DynamicExtensible.getInternalName();
    static final Type TYPE_Optional = Type.getType(Optional.class);
    static final String NAME_Optional = TYPE_Optional.getInternalName();
    static final Type TYPE_Required = Type.getType(Required.class);
    static final String NAME_Required = TYPE_Required.getInternalName();

    record RequiredDependency(Type dependencyClass) { }
    record MethodInfo(int access, String name, String desc, String signature, String[] exceptions) { }
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
                // check for DynamicExtensible.require() calls
                if (NAME_DynamicExtensible.equals(owner) && "require".equals(name) && "(Ljava/lang/Class;)Ljava/lang/Object;".equals(desc)) {
                    Type dependency = (Type) stack.pop();
                    list.add(new RequiredDependency(dependency));
                    stack.push(instanceOf(dependency).set("behavior"));
                }

                // check for DynamicExtensible.optionally() calls
                if (NAME_DynamicExtensible.equals(owner) && "optionally".equals(name) && "(Ljava/lang/Class;)Ljava/util/Optional;".equals(desc)) {
                    Type dependency = (Type) stack.pop();
                    stack.push(instanceOf(TYPE_Optional)
                            .set("dependency", dependency));
                }

                // check for Optional.isPresent() calls
                if (NAME_Optional.equals(owner) && "isPresent".equals(name)) {
                    ((InstanceOf)stack.peek()).set("checked", true);
                }

                // check for Optional.get() calls
                if (NAME_Optional.equals(owner) && "get".equals(name)) {
                    InstanceOf instance = (InstanceOf) stack.peek();
                    if (instance.has("dependency") && instance.get("checked") != Boolean.TRUE) {
                        list.add(new RequiredDependency(instance.get("dependency")));
                    }
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

