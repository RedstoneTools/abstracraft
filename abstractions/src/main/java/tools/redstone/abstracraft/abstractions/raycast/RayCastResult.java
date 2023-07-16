package tools.redstone.abstracraft.abstractions.raycast;

import tools.redstone.abstracraft.helpers.OptionalHelpers;
import tools.redstone.abstracraft.math.Vec3d;
import tools.redstone.abstracraft.math.Vec3i;

import java.util.Optional;

public abstract class RayCastResult {
    public final Vec3d position;

    public RayCastResult(Vec3d position) {
        this.position = position;
    }

    public Optional<MissResult> getMissResult() {
        return OptionalHelpers.getOptionalSubclass(this, MissResult.class);
    }

    public Optional<BlockHitResult> getBlockHitResult() {
        return OptionalHelpers.getOptionalSubclass(this, BlockHitResult.class);
    }

    public Optional<EntityHitResult> getEntityHitResult() {
        return OptionalHelpers.getOptionalSubclass(this, EntityHitResult.class);
    }

    public static class MissResult extends RayCastResult {
        public MissResult(Vec3d position) {
            super(position);
        }
    }

    public static class BlockHitResult extends RayCastResult {
        public final Vec3i blockPosition;

        public BlockHitResult(Vec3d position, Vec3i blockPosition) {
            super(position);

            this.blockPosition = blockPosition;
        }
    }

    public static class EntityHitResult extends RayCastResult {
        public EntityHitResult(Vec3d position) {
            super(position);
        }
    }
}
