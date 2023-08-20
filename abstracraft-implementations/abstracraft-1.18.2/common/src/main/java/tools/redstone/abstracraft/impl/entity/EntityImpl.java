package tools.redstone.abstracraft.impl.entity;

import net.minecraft.world.entity.Entity;
import tools.redstone.abstracraft.HandleAbstraction;

import java.util.UUID;

public class EntityImpl extends HandleAbstraction<Entity> implements tools.redstone.abstracraft.entity.Entity {
    public EntityImpl(Entity entity) {
        super(entity);
    }

    /* -------------------------------- */

    @Override
    public void discard() {
        handle().remove(Entity.RemovalReason.DISCARDED);
    }

    @Override
    public UUID getUniqueID() {
        return handle().getUUID();
    }
}
