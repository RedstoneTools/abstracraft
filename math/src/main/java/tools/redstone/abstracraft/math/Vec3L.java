package tools.redstone.abstracraft.math;

public class Vec3L extends Vec3<Long> {
    public Vec3L(Long x, Long y, Long z) {
        super(x, y, z);
    }

    public Vec3D toVec3D() {
        return new Vec3D(x.doubleValue(), y.doubleValue(), z.doubleValue());
    }
}
