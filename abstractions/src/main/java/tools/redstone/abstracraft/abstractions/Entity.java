package tools.redstone.abstracraft.abstractions;

import tools.redstone.abstracraft.core.usage.Abstraction;
import tools.redstone.abstracraft.math.Vec3d;

public interface Entity extends Abstraction {
    default void kill() { unimplemented(); }
    default void damage(float amount) { unimplemented(); }
    default Vec3d getPosition() { return unimplemented(); }
}
