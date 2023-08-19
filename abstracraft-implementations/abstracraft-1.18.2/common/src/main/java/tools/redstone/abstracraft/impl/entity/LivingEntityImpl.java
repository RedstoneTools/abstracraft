package tools.redstone.abstracraft.impl.entity;

import net.minecraft.world.entity.LivingEntity;

public class LivingEntityImpl extends EntityImpl implements tools.redstone.abstracraft.entity.LivingEntity {
    public LivingEntityImpl(LivingEntity entity) {
        super(entity);
    }

    protected LivingEntity livingEntity() {
        return this.handle();
    }

    @Override
    public void setHealth(float health) {
        livingEntity().setHealth(health);
    }

    @Override
    public float getHealth() {
        return livingEntity().getHealth();
    }

    @Override
    public float getMaxHealth() {
        return livingEntity().getMaxHealth();
    }
}
