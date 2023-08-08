package tools.redstone.abstracraft.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Manages all systems related to abstracting.
 * Notably contains the implementation class for each abstraction.
 *
 * Used to check whether a specific behavior is implemented by
 * any given class.
 *
 * @author orbyfied
 */
public class AbstractionManager {

    final Predicate<String> abstractionClassPredicate; // The predicate for abstraction class names.

    boolean implementedByDefault = false;                              // Whether it should assume unregistered methods are implemented by default
    final Map<Class<?>, Class<?>> implByBaseClass = new HashMap<>();   // The registered implementation classes by base class
    final Map<MethodInfo, Boolean> implementedCache = new HashMap<>(); // A cache to store whether a specific method is implemented for fast access

    final Map<MethodInfo, ClassDependencyAnalyzer.MethodAnalysis> methodAnalysisMap = new HashMap<>(); // All analyzed methods by their descriptor
    final Map<String, ClassDependencyAnalyzer> analyzerMap = new HashMap<>();                          // All analyzers by class name
    final ClassLoader transformingClassLoader;

    public AbstractionManager setImplementedByDefault(boolean implementedByDefault) {
        this.implementedByDefault = implementedByDefault;
        return this;
    }

    public AbstractionManager(Predicate<String> abstractionClassPredicate) {
        this.abstractionClassPredicate = abstractionClassPredicate;

        // create class loader
        this.transformingClassLoader = ReflectUtil.transformingClassLoader(
                // name predicate
                name -> !name.startsWith("java") && abstractionClassPredicate.test(name),
                // parent class loader
                getClass().getClassLoader(),
                // transformer
                ((name, reader, writer) -> {
                    var analyzer = analyzer(name, true);
                    if (analyzer.getClassAnalysis() == null || !analyzer.getClassAnalysis().completed)
                        analyzer.analyzeAndTransform();
                    analyzer.getClassNode().accept(writer);
                }), ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    }

    /**
     * Get the base abstraction class from the given interface.
     *
     * @param startItf The starting interface.
     * @return The base abstraction class/interface.
     */
    public Class<?> getBaseAbstractionClass(Class<?> startItf) {
        Class<?> current = startItf;
        outer: while (current != null) {
            if (current.isAnnotationPresent(BaseAbstraction.class))
                break;

            // find next interface by finding the
            // interface implementing Abstraction
            for (Class<?> itf : current.getInterfaces()) {
                if (Abstraction.class.isAssignableFrom(itf)) {
                    current = itf;
                    continue outer;
                }
            }

            throw new IllegalArgumentException(startItf + " is not an Abstraction class");
        }

        if (current == null)
            throw new IllegalArgumentException("Could not find base abstraction for " + startItf);
        return current;
    }

    /**
     * Registers the given implementation class.
     *
     * @param implClass The implementation.
     */
    public void registerImpl(Class<?> implClass) {
        implByBaseClass.put(
                getBaseAbstractionClass(implClass),
                implClass
        );
    }

    /**
     * Check whether the given method is implemented for it's
     * owning abstraction.
     *
     * @param method The method.
     * @return Whether it is implemented.
     */
    public boolean isImplemented(MethodInfo method) {
        Boolean b = implementedCache.get(method);
        if (b != null)
            return b;
        return implementedByDefault; // todo
    }

    public boolean areAllImplemented(List<MethodInfo> methods) {
        if (methods == null)
            return true;

        for (MethodInfo i : methods) {
            if (!isImplemented(i)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Manually set whether something is implemented.
     *
     * @param info The method.
     * @param b The status.
     */
    public void setImplemented(MethodInfo info, boolean b) {
        implementedCache.put(info, b);
    }

    /**
     * Get or create an analyzer for the given class name.
     *
     * @param className The class name.
     * @return The analyzer.
     */
    public ClassDependencyAnalyzer analyzer(String className, boolean ignoreLoadedClasses) {
        try {
            className = className.replace('.', '/');
            String publicName = className.replace('/', '.');

            // get cached/active
            ClassDependencyAnalyzer analyzer = analyzerMap.get(className);
            if (analyzer != null)
                return analyzer;

            if (ignoreLoadedClasses && ReflectUtil.findLoadedClass(transformingClassLoader, publicName) != null) {
                return null;
            }

            if (!abstractionClassPredicate.test(publicName)) {
                return null;
            }

            // open resource
            String classAsPath = className + ".class";
            try (InputStream stream = transformingClassLoader.getResourceAsStream(classAsPath)) {
                if (stream == null)
                    throw new IllegalArgumentException("Could not find resource stream for " + classAsPath);
                byte[] bytes = stream.readAllBytes();

                ClassReader reader = new ClassReader(bytes);
                analyzer = new ClassDependencyAnalyzer(this, reader);
                analyzerMap.put(className, analyzer);
                return analyzer;
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while creating MethodDependencyAnalyzer for class " + className, e);
        }
    }

    public ClassDependencyAnalyzer analyzer(Class<?> klass) {
        return analyzer(klass.getName(), false);
    }

    public ClassDependencyAnalyzer.MethodAnalysis getMethodAnalysis(MethodInfo info) {
        return methodAnalysisMap.get(info);
    }

    public ClassDependencyAnalyzer.MethodAnalysis registerAnalysis(ClassDependencyAnalyzer.MethodAnalysis analysis) {
        methodAnalysisMap.put(analysis.method, analysis);
        return analysis;
    }

    /**
     * Find/load a class using the transforming class loader
     * of this abstraction manager.
     *
     * @param name The name.
     * @return The class.
     */
    public Class<?> findClass(String name) {
        return ReflectUtil.getClass(name, this.transformingClassLoader);
    }

}
