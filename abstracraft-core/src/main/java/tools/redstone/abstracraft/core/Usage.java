package tools.redstone.abstracraft.core;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Methods for usage of methods.
 */
public class Usage {

    public static <T> Optional<T> optionally(Supplier<T> supplier) {
        return Optional.of(supplier.get());
    }

    public static boolean optionally(Runnable r) {
        r.run();
        return true;
    }

    public static <T> Optional<T> oneOf(Optional<T>... optionals) {
        throw new AssertionError(); // THIS WILL BE SUBSTITUTED BY THE BYTECODE TRANSFORMER
    }

    /**
     * Substitute methods that should only be called by code written
     * through the bytecode transformer.
     */
    public static class InternalSubstituteMethods {
        // Substitute for `optionally(Supplier<T>)` when it is not present
        public static Optional<?> notPresentOptional() {
            return Optional.empty();
        }

        // Substitute for `optionally(Runnable)` when it is not present
        public static boolean notPresentDirect() {
            return false;
        }

        // Substitute for `oneOf(Optional<T>...)` when at least one of the dependencies is present
        public static Optional<?> onePresentOptional(Optional<?> optional) {
            return optional;
        }

        // Substitute for `oneOf(Optional<T>...)` when none of the dependencies are present
        public static Optional<?> nonePresentOptional() {
            return Optional.empty();
        }
    }

}
