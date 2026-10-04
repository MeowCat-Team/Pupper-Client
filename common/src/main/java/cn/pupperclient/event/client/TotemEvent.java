package cn.pupperclient.event.client;

import cn.pupperclient.event.Event;
import net.minecraft.world.entity.player.Player;

public class TotemEvent extends Event {
    private final Player player;

    public TotemEvent(Player entity) {
        player = entity;
    }

    public Player getPlayer() {
        return player;
    }
}
