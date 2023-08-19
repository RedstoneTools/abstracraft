package tools.redstone.abstracraft.entity;

import tools.redstone.abstracraft.math.Vec3d;

public class EntityImpl implements Entity {
    private final net.minecraft.world.entity.Entity mcEntity;

    public EntityImpl(net.minecraft.world.entity.Entity mcEntity) {
        this.mcEntity = mcEntity;
    }

    @Override
    public void kill() {
        mcEntity.kill();
    }

    @Override
    public Vec3d getPosition() {
        var position = mcEntity.position();

        return new Vec3d(position.x, position.y, position.z);
    }
}
