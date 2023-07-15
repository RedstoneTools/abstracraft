package tools.redstone.abstracraft.abstractions.entity;

import tools.redstone.abstracraft.math.Vec3D;
import tools.redstone.abstracraft.math.Vec3L;

public abstract class Entity {
    protected abstract void teleportTo(Vec3D position);
    protected abstract void teleportTo(Vec3L position);
    protected abstract void kill();
}
