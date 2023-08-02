package tools.redstone.abstracraft.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.util.function.BiConsumer;

/**
 * A utility class for transforming and loading classes.
 */
public class ClassUtil {
    private ClassUtil() { }

    /**
     * Get the bytes of the class file of the given loaded class.
     *
     * @param klass The class.
     * @return The bytes.
     */
    public static byte[] getBytes(Class<?> klass) {
        try {
            // get resource path
            String className = klass.getName();
            String classAsPath = className.replace('.', '/') + ".class";

            // open resource
            try (InputStream stream = klass.getClassLoader().getResourceAsStream(classAsPath)) {
                if (stream == null)
                    throw new IllegalArgumentException("Could not find resource stream for " + klass);
                return stream.readAllBytes();
            }
        } catch (Exception e) {
            throw new RuntimeException("Error occurred", e);
        }
    }

    /**
     * Gets the bytes from the given source class, transforms
     * them using the given method and returns the result.
     *
     * @param src The source class.
     * @param name The name of the result class.
     * @param transformer The transformer function.
     * @return The class
     */
    public static byte[] transform(Class<?> src,
                                   String name,
                                   BiConsumer<ClassReader, ClassWriter> transformer) {
        byte[] bytes = getBytes(src);
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        writer.visit(Opcodes.V16, Opcodes.ACC_PUBLIC, name, "L" + name + ";", "java/lang/Object", new String[] { });
        transformer.accept(reader, writer);

        return writer.toByteArray();
    }

    /**
     * Gets the bytes from the given source class, transforms
     * them using the given method and finally loads the class and returns it
     * to do tests on.
     *
     * @param loader The loader to load the class with.
     * @param src The source class.
     * @param transformer The transformer function.
     * @return The loaded class.
     */
    public static Class<?> transformAndLoad(DirectClassLoader loader,
                                            Class<?> src,
                                            BiConsumer<ClassReader, ClassWriter> transformer) {
        String name = "temp/TestClass$" + Integer.toHexString(src.getName().hashCode());
        byte[] resultBytes = transform(src, name, transformer);
        return loader.define(name.replace("/", "."), resultBytes);
    }

    public static DirectClassLoader directClassLoader() {
        return new DirectClassLoader();
    }

    public static DirectClassLoader directClassLoader(ClassLoader loader) {
        return new DirectClassLoader(loader);
    }

    /** Defines a class transformer. */
    public interface ClassTransformer {
        void transform(String name, ClassReader reader, ClassWriter writer);
    }

    public static ClassLoader transformingClassLoader(ClassTransformer transformer) {
        return transformingClassLoader(ClassLoader.getSystemClassLoader(), transformer);
    }

    public static ClassLoader transformingClassLoader(ClassLoader loader,
                                                      ClassTransformer transformer) {
        return new ClassLoader(loader) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                Class<?> klass = super.findClass(name);
                String internalName = klass.getName().replace('.', '/');
                byte[] bytes = transform(klass, internalName,
                        (classReader, writer) -> transformer.transform(internalName, classReader, writer));
                return this.defineClass(klass.getName(), bytes, 0, bytes.length);
            }
        };
    }

}
