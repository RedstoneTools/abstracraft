package tools.redstone.abstracraft.abstractions.entity;

import tools.redstone.abstracraft.abstractions.Method;
import tools.redstone.abstracraft.math.Vec3D;
import tools.redstone.abstracraft.math.Vec3L;

// Method implementations should be generated
public final class Entity_teleportTo extends Method<Entity, Entity_teleportTo> {
    public Entity_teleportTo() {
        super();
    }

    private Entity_teleportTo(Entity self) {
        super(self);
    }

    @Override
    protected Entity_teleportTo withSelf(Entity self) {
        return new Entity_teleportTo(self);
    }

    public void call(Vec3D position) {
        getSelf().teleportTo(position);
    }

    public void call(Vec3L position) {
        getSelf().teleportTo(position);
    }
}
