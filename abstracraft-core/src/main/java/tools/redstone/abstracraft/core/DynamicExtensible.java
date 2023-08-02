package tools.redstone.abstracraft.core;

import java.util.Optional;

public interface DynamicExtensible {
    /**
     * Get the given behavior/extension on this class if present.
     *
     * @param rClass The behavior class which this instance could implement.
     * @return The optional with the behavior if present, otherwise an empty optional.
     */
    @SuppressWarnings("unchecked")
    default <R> Optional<R> optionally(Class<? super R> rClass) {
        return rClass.isInstance(this) ? Optional.of((R) this) : Optional.empty();
    }

    /**
     * Get the given behavior/extension on this class, or throw an exception
     * if it is absent.
     *
     * @param rClass The behavior class which this instance could implement.
     * @return The behavior instance.
     * @throws UnsupportedOperationException If the behavior is not implemented.
     */
    @SuppressWarnings("unchecked")
    default <R> R require(Class<? super R> rClass) {
        if (!rClass.isInstance(this))
            throw new UnsupportedOperationException("Required dependency " + rClass + " is not implemented by " +
                    "implementation " + this.getClass());
        return (R) this;
    }
}
