package tools.redstone.abstracraft.abstractions.entity;

import tools.redstone.abstracraft.abstractions.raycast.RayCastResult;
import tools.redstone.abstracraft.annotations.BehaviorClass;
import tools.redstone.abstracraft.math.Vec3d;
import tools.redstone.abstracraft.math.Vec3i;

@BehaviorClass
public interface IEntity {
    void teleportTo(Vec3d position);
    void teleportTo(Vec3i position);
    RayCastResult castRayFromEyes(double maxDistance, boolean includeFluids);
}
