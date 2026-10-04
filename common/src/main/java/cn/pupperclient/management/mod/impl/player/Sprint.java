package cn.pupperclient.management.mod.impl.player;

import cn.pupperclient.event.EventListener;
import cn.pupperclient.event.client.ClientTickEvent;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.skia.font.Icon;

public class Sprint extends Mod {
    public Sprint() {
        super("mod.sprint.name", "mod.sprint.description", Icon.DIRECTIONS_RUN, ModCategory.PLAYER);
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        client.options.keySprint.setDown(true);
    }
}
