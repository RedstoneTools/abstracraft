package tools.redstone.abstracraft.abstractions;

public interface Player extends Entity {
    default float getHealth() { return unimplemented(); }
}
