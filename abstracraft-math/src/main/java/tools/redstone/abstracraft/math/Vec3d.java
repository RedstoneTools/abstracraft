package tools.redstone.abstracraft.math;

public record Vec3d(double x, double y, double z) {
    public Vec3d add(Vec3d other) {
        return new Vec3d(x + other.x, y + other.y, z + other.z);
    }
}
