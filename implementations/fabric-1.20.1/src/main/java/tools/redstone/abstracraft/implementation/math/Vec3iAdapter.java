package tools.redstone.abstracraft.implementation.math;

import tools.redstone.abstracraft.math.Vec3i;

public class Vec3iAdapter {
    private Vec3iAdapter() {
    }

    public static Vec3i adapt(net.minecraft.util.math.Vec3i vec3i) {
        return new Vec3i(vec3i.getX(), vec3i.getY(), vec3i.getZ());
    }
}
