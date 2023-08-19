package tools.redstone.abstracraft.util.functional;

public interface ThrowingSupplier<T, E extends Throwable> {
    T get() throws E;
}
