package tools.redstone.abstracraft.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
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

    final Map<Class<?>, Class<?>> implByBaseClass = new HashMap<>();   // The registered implementation classes by base class
    final Map<MethodInfo, Boolean> implementedCache = new HashMap<>(); // A cache to store whether a specific method is implemented for fast access

    final Map<MethodInfo, ClassDependencyAnalyzer.MethodAnalysis> methodAnalysisMap = new HashMap<>(); // All analyzed methods by their descriptor
    final Map<String, ClassDependencyAnalyzer> analyzerMap = new HashMap<>();                          // All analyzers by class name
    final ClassLoader transformingClassLoader;

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
    public List<Class<?>> getApplicableAbstractionClasses(Class<?> startItf) {
        Class<?> current = startItf;
        outer: while (current != null) {
            if (ArrayUtil.anyMatch(current.getInterfaces(), i -> i == Abstraction.class))
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
        return List.of(current);
    }

    /**
     * Registers the given implementation class.
     *
     * @param implClass The implementation.
     */
    public void registerImpl(Class<?> implClass) {
        for (Class<?> kl : getApplicableAbstractionClasses(implClass)) {
            implByBaseClass.put(kl, implClass);
        }
    }

    // Check whether the given method is implemented
    // without referencing the cache
    private boolean isImplemented0(MethodInfo method) {
        // get abstraction class
        Class<?> abstractionClass = ReflectUtil.getClass(method.ownerClassName());
        if (abstractionClass == null)
            return false;

        // get implementation class for abstraction
        Class<?> implClass = implByBaseClass.get(abstractionClass);
        if (implClass == null)
            // object not implemented at all
            return false;

        try {
            // check method declaration
            Method m = implClass.getMethod(method.name(), ASMUtil.asClasses(method.asmType().getArgumentTypes()));

            if (m.isAnnotationPresent(Defaulted.class))
                return true;
            if (m.getDeclaringClass() == abstractionClass)
                return false;
            if (m.getDeclaringClass().isInterface() && !m.isDefault())
                return false;
            return !Modifier.isAbstract(m.getModifiers());
        } catch (Exception e) {
            throw new RuntimeException("Error while checking implementation status of " + method, e);
        }
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

        implementedCache.put(method, b = isImplemented0(method));
        return b;
    }

    /**
     * Check whether all given methods are implemented.
     *
     * @param methods The methods.
     * @return Whether they are all implemented.
     */
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
     * Check whether all the required given dependencies are implemented.
     *
     * @param dependencies The dependencies.
     * @return Whether they are all implemented.
     */
    public boolean areAllRequiredImplemented(List<MethodDependency> dependencies) {
        return areAllImplemented(dependencies.stream()
                .filter(d -> !d.optional())
                .map(MethodDependency::info)
                .toList());
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
