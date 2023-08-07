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

}
