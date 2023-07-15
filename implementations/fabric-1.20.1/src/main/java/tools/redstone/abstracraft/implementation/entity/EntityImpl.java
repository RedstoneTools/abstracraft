package tools.redstone.abstracraft.implementation.entity;

import tools.redstone.abstracraft.abstractions.entity.Entity;
import tools.redstone.abstracraft.math.Vec3D;
import tools.redstone.abstracraft.math.Vec3L;

public class EntityImpl extends Entity {
    private final net.minecraft.entity.Entity mcEntity;

    public EntityImpl(net.minecraft.entity.Entity mcEntity) {
        this.mcEntity = mcEntity;
    }

    @Override
    protected void teleportTo(Vec3D position) {
        mcEntity.teleport(position.x, position.y, position.z);
    }

    @Override
    protected void teleportTo(Vec3L position) {
        teleportTo(position.toVec3D());
    }

    @Override
    protected void kill() {
        mcEntity.kill();
    }
}
