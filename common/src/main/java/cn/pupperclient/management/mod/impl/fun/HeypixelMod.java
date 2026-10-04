package cn.pupperclient.management.mod.impl.fun;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.EventListener;
import cn.pupperclient.event.mod.AutoAgainEvent;
import cn.pupperclient.event.server.ChatEvent;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.chat.ChatUtils;
import net.minecraft.client.Minecraft;

public class HeypixelMod extends Mod {
    private static HeypixelMod instance;
    public static final String HEYPIXEL_END_MESSAGE = "可以用 /hub 退出观察者模式并返回大厅";

    public final BooleanSetting AutoAgain = new BooleanSetting(
        "mod.heypixel.autoAgain.name",
        "mod.heypixel.autoAgain.description",
        Icon.PLAY_ARROW,
        this,
        true
    );

    public final BooleanSetting Logging = new BooleanSetting(
        "mod.heypixel.logging.name",
        "mod.heypixel.logging.description",
        Icon.PLAY_ARROW,
        this,
        false
    );

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    public HeypixelMod() {
        super("mod.heypixel.name", "mod.heypixel.desc", Icon.PLAY_ARROW, ModCategory.FUN);
    }

    @EventListener
    public void onChatMessage(ChatEvent event) {
        if (Logging.isEnabled()) {
            ChatUtils.addChatMessage("check the message: " + event.getMessage());
        }
        if (event.getMessage().contains(HEYPIXEL_END_MESSAGE) && AutoAgain.isEnabled()) {
            if (Logging.isEnabled()) {
                ChatUtils.addChatMessage("checked! try to send /again command");
            }
            EventBus.getInstance().post(new AutoAgainEvent());
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                client.player.connection.sendCommand("again");
                if (Logging.isEnabled()) {
                    ChatUtils.addChatMessage("Done");
                }
            }
        }
    }

    public static HeypixelMod getInstance() {
        return instance;
    }
}
