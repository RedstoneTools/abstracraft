package tools.redstone.abstracraft.math;

abstract class Vec3<T> {
    public final T x;
    public final T y;
    public final T z;

    public Vec3(T x, T y, T z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
