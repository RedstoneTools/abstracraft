package tools.redstone.abstracraft.entity;

public class PlayerImpl extends EntityImpl implements Player {
    private final net.minecraft.world.entity.player.Player mcPlayer;

    public PlayerImpl(net.minecraft.world.entity.player.Player mcPlayer) {
        super(mcPlayer);

        this.mcPlayer = mcPlayer;
    }
}
