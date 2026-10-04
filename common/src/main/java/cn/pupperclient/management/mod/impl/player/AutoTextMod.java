package cn.pupperclient.management.mod.impl.player;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.ClientTickEvent;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.KeybindSetting;
import cn.pupperclient.management.mod.settings.impl.StringSetting;
import cn.pupperclient.skia.font.Icon;
import com.mojang.blaze3d.platform.InputConstants;

public class AutoTextMod extends Mod {
    private KeybindSetting text1KeybindSetting = new KeybindSetting("setting.text1key", "setting.text1key.description", Icon.KEYBOARD, this, InputConstants.UNKNOWN);
    private StringSetting text1Setting = new StringSetting("setting.text1", "setting.text1.description", Icon.TEXT_FORMAT, this, "");
    private KeybindSetting text2KeybindSetting = new KeybindSetting("setting.text2key", "setting.text2key.description", Icon.KEYBOARD, this, InputConstants.UNKNOWN);
    private StringSetting text2Setting = new StringSetting("setting.text2", "setting.text2.description", Icon.TEXT_FORMAT, this, "");
    private KeybindSetting text3KeybindSetting = new KeybindSetting("setting.text3key", "setting.text3key.description", Icon.KEYBOARD, this, InputConstants.UNKNOWN);
    private StringSetting text3Setting = new StringSetting("setting.text3", "setting.text3.description", Icon.TEXT_FORMAT, this, "");

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    public AutoTextMod() {
        super("mod.autotext.name", "mod.autotext.description", Icon.TEXT_AD, ModCategory.PLAYER);
    }

    public final EventBus.EventListener<ClientTickEvent> onClientTick = event -> {
        if (client.player != null) {
            return;
        }

        if(text1KeybindSetting.isPressed()) {
            client.player.connection.sendChat(text1Setting.getValue());
        }

        if(text2KeybindSetting.isPressed()) {
            client.player.connection.sendChat(text2Setting.getValue());
        }

        if(text3KeybindSetting.isPressed()) {
            client.player.connection.sendChat(text3Setting.getValue());
        }
    };
}
