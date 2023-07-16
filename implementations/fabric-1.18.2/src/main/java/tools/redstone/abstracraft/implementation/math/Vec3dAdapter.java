package tools.redstone.abstracraft.implementation.math;

import tools.redstone.abstracraft.math.Vec3d;

public class Vec3dAdapter {
    private Vec3dAdapter() {
    }

    public static Vec3d adapt(net.minecraft.util.math.Vec3d vec3d) {
        return new Vec3d(vec3d.x, vec3d.y, vec3d.z);
    }
}
