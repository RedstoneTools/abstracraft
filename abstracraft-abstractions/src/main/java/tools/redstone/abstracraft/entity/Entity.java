package tools.redstone.abstracraft.entity;

import tools.redstone.abstracraft.usage.Abstraction;
import tools.redstone.abstracraft.math.Vec3d;

public interface Entity extends Abstraction {
    void kill();
    void damage(double amount);
    double getHealth();
    Vec3d getPosition();
    Vec3d getSpawnPosition();
}
