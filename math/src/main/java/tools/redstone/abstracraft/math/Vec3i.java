package tools.redstone.abstracraft.math;

public class Vec3i extends Vec3<Integer> {
    public Vec3i(Integer x, Integer y, Integer z) {
        super(x, y, z);
    }

    public Vec3d toVec3D() {
        return new Vec3d(x.doubleValue(), y.doubleValue(), z.doubleValue());
    }
}
