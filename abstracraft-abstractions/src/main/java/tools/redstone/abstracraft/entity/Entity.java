package tools.redstone.abstracraft.entity;

import tools.redstone.abstracraft.math.Vec3d;
import tools.redstone.picasso.usage.Abstraction;

import java.util.UUID;

/**
 * Represents any type of entity in the world.
 */
public interface Entity extends Abstraction {
    /**
     * Get the unique ID for this entity.
     *
     * @return The UUID for this entity.
     */
    default UUID getUniqueID() { return unimplemented(); }

    /**
     * Discard this entity from the world immediately.
     */
    default void discard() { unimplemented(); }

    /**
     * Teleport the entity to the specified position.
     *
     * @param position The position to teleport the entity to.
     */
    default void teleportTo(Vec3d position) { unimplemented(); }

    /**
     * Gets the position of the entity.
     *
     * @return The position of the entity.
     */
    default Vec3d getPosition() { return unimplemented(); }
}
