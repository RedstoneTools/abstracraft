package tools.redstone.abstracraft.implementation;

import tools.redstone.abstracraft.abstractions.Player;

public class PlayerImpl extends EntityImpl implements Player {
    private final net.minecraft.world.entity.player.Player mcPlayer;

    public PlayerImpl(net.minecraft.world.entity.player.Player mcPlayer) {
        super(mcPlayer);

        this.mcPlayer = mcPlayer;
    }

    @Override
    public float getHealth() {
        return mcPlayer.getHealth();
    }
}
