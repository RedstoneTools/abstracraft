package tools.redstone.abstracraft.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages all systems related to abstracting.
 * Notably contains the implementation class for each abstraction.
 *
 * Used to check whether a specific behavior is implemented by
 * any given class.
 */
public class AbstractionManager {

    final Map<Class<?>, Boolean> implementedCache = new HashMap<>(); // Cache for storing which dependencies are supported
    final Map<Class<?>, Class<?>> implByBaseClass = new HashMap<>(); // The registered implementation classes by base class

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
     * Checks whether the given dependency class is implemented by
     * checking the implementations of the registered implementation.
     *
     * @param dependency The dependency.
     * @return Whether it is implemented.
     */
    public boolean isDependencyImplemented(Class<?> dependency) {
        // check cache
        Boolean b = implementedCache.get(dependency);
        if (b != null) return b;

        // get base abstraction class
        Class<?> baseAbstractionClass = getBaseAbstractionClass(dependency);

        // get implementation for the base abstraction
        Class<?> implClass = implByBaseClass.get(baseAbstractionClass);
        if (implClass == null) { // no implementation at all
            implementedCache.put(dependency, false);
            return false;
        }

        // finally check whether the implementation class
        // implements the depended on interface, and cache the result
        boolean isImplemented = dependency.isAssignableFrom(implClass);
        implementedCache.put(dependency, isImplemented);
        return isImplemented;
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
     * Checks whether all the given dependencies are implemented.
     *
     * @param dependencies The dependencies.
     * @return Whether they are all implemented.
     */
    public boolean areAllImplemented(Class<?>... dependencies) {
        for (Class<?> req : dependencies) {
            if (!isDependencyImplemented(req)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks whether the given class has all it's declared dependencies, uses
     * the dependencies declared by {@link Required}.
     *
     * @param klass The class.
     * @return Whether they are all implemented.
     */
    public boolean hasAllDependencies(Class<?> klass) {
        if (!klass.isAnnotationPresent(Required.class)) return true;
        return areAllImplemented(klass.getAnnotation(Required.class).value());
    }

}
