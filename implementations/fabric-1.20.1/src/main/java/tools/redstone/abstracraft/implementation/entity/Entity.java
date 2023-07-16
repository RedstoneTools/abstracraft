package tools.redstone.abstracraft.implementation.entity;

import tools.redstone.abstracraft.abstractions.entity.IEntity;
import tools.redstone.abstracraft.abstractions.raycast.RayCastResult;
import tools.redstone.abstracraft.implementation.raycast.RayCastResultAdapter;
import tools.redstone.abstracraft.math.Vec3d;
import tools.redstone.abstracraft.math.Vec3i;

public class Entity implements IEntity {
    private final net.minecraft.entity.Entity mcEntity;

    public Entity(net.minecraft.entity.Entity mcEntity) {
        this.mcEntity = mcEntity;
    }

    @Override
    public void teleportTo(Vec3d position) {
        mcEntity.teleport(position.x, position.y, position.z);
    }

    @Override
    public void teleportTo(Vec3i position) {
        teleportTo(position.toVec3D());
    }

    @Override
    public RayCastResult castRayFromEyes(double maxDistance, boolean includeFluids) {
        return RayCastResultAdapter.adapt(mcEntity.raycast(maxDistance, 1.0f, includeFluids));
    }
}
