package tools.redstone.abstracraft.core.usage;

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

    @SafeVarargs
    public static <T> T requireAtLeastOne(Supplier<T>... suppliers) {
        throw new AssertionError(); // THIS WILL BE SUBSTITUTED BY THE BYTECODE TRANSFORMER
    }

    /**
     * Substitute methods that should only be called by code written
     * through the bytecode transformer.
     */
    public static class InternalSubstituteMethods {
        // Substitute for `optionally(Supplier<T>)` when it is not present
        public static Optional<?> notPresentOptional(Supplier<?> supplier) {
            return Optional.empty();
        }

        // Substitute for `optionally(Runnable)` when it is not present
        public static boolean notPresentBoolean(Runnable r) {
            return false;
        }

        // Substitute for `oneOf(Supplier<T>...)` when at least one is present
        public static Object onePresent(Supplier<?>... suppliers) {
            for (Supplier<?> supplier : suppliers) {
                if (supplier != null) {
                    return supplier.get();
                }
            }

            throw new NoneImplementedException("");
        }

        // Substitute for `oneOf(Supplier<T>...)` when none are present
        public static Object nonePresent(Supplier<?>... suppliers) {
            throw new NoneImplementedException("");
        }
    }

}
