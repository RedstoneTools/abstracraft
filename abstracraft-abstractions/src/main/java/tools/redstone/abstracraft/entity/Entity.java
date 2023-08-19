package tools.redstone.abstracraft.entity;

import tools.redstone.abstracraft.usage.Abstraction;

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
}
