package tools.redstone.abstracraft.entity;

/**
 * Represents an entity which has health.
 */
public interface LivingEntity extends Entity {

    /**
     * Get the current health of this entity.
     *
     * Note that relative to player health, each health point (1.0)
     * represents half a heart.
     *
     * @return The health as a floating point value.
     */
    default float getHealth() { return unimplemented(); }

    /**
     * Set the current health on this entity.
     *
     * Note that relative to player health, each health point (1.0)
     * represents half a heart.
     *
     * @param health The health to set as a floating point value.
     */
    default void setHealth(float health) { unimplemented(); }

    /**
     * Get the maximum health of this entity, as calculated
     * by the set attributes.
     *
     * Note that relative to player health, each health point (1.0)
     * represents half a heart.
     *
     * @return The max health as a floating point value.
     */
    default float getMaxHealth() { return unimplemented(); }

}
