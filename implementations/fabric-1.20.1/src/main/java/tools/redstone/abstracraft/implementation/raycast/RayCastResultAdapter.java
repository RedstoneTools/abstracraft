package tools.redstone.abstracraft.implementation.raycast;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import tools.redstone.abstracraft.abstractions.raycast.RayCastResult;
import tools.redstone.abstracraft.implementation.math.Vec3dAdapter;
import tools.redstone.abstracraft.implementation.math.Vec3iAdapter;

public class RayCastResultAdapter {
    private RayCastResultAdapter() {
    }

    public static RayCastResult adapt(HitResult hitResult) {
        if (hitResult instanceof BlockHitResult blockHitResult) {
            return adapt(blockHitResult);
        } else if (hitResult instanceof EntityHitResult entityHitResult) {
            return adapt(entityHitResult);
        } else {
            return new RayCastResult.MissResult(
                    Vec3dAdapter.adapt(hitResult.getPos())
            );
        }
    }

    public static RayCastResult.BlockHitResult adapt(BlockHitResult blockHitResult) {
        return new RayCastResult.BlockHitResult(
                Vec3dAdapter.adapt(blockHitResult.getPos()),
                Vec3iAdapter.adapt(blockHitResult.getBlockPos())
        );
    }

    public static RayCastResult.EntityHitResult adapt(EntityHitResult entityHitResult) {
        return new RayCastResult.EntityHitResult(
                Vec3dAdapter.adapt(entityHitResult.getPos())
        );
    }
}
