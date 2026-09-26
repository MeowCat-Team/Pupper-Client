package cn.pupperclient.management.mod.impl.settings;

import java.util.Arrays;
import java.util.Objects;
import net.minecraft.client.gui.screens.Screen;
import cn.pupperclient.PupperClient;
import cn.pupperclient.gui.MusicPlayGui;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.ClientTickEvent;
import cn.pupperclient.gui.modmenu.GuiModMenu;
import cn.pupperclient.libraries.material3.hct.Hct;
import cn.pupperclient.management.config.ConfigType;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.management.mod.settings.impl.ComboSetting;
import cn.pupperclient.management.mod.settings.impl.HctColorSetting;
import cn.pupperclient.management.mod.settings.impl.KeybindSetting;
import cn.pupperclient.management.mod.settings.impl.NumberSetting;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.language.Language;
import com.mojang.blaze3d.platform.InputConstants;

public class ModMenuSettings extends Mod {

	private static ModMenuSettings instance;
	private String previousLanguageOption = "language.english";
	private boolean languageInitialized = false;

	private final KeybindSetting keybindSetting = new KeybindSetting("setting.keybind", "setting.keybind.description",
			Icon.KEYBOARD, this, InputConstants.getKey(new KeyEvent(GLFW.GLFW_KEY_RIGHT_SHIFT, 0, 0)));
    private final KeybindSetting keybindSetting_music = new KeybindSetting("setting.keybind_music", "setting.keybind_music.description",
        Icon.KEYBOARD, this, InputConstants.getKey(new KeyEvent(GLFW.GLFW_KEY_M, 0, 0)));
	private final BooleanSetting darkModeSetting = new BooleanSetting("setting.darkmode", "setting.darkmode.description",
			Icon.DARK_MODE, this, false);
	private final HctColorSetting hctColorSetting = new HctColorSetting("setting.color", "setting.color.description",
			Icon.PALETTE, this, Hct.from(220, 26, 6));
	private final BooleanSetting blurSetting = new BooleanSetting("setting.blur", "setting.blur.description", Icon.LENS_BLUR,
			this, true);
	private final NumberSetting blurIntensitySetting = new NumberSetting("setting.blurintensity",
			"setting.blurintensity.description", Icon.BLUR_LINEAR, this, 5, 1, 20, 1);

	private final ComboSetting languageSetting = new ComboSetting("setting.language", "setting.language.description",
			Icon.LANGUAGE, this, Arrays.asList("language.english", "language.chinese"), "language.english");

	private Screen modMenu;

	public ModMenuSettings() {
		super("mod.modmenu.name", "mod.modmenu.description", Icon.MENU, ModCategory.MISC);

		instance = this;
		this.setHidden(true);
		this.setEnabled(true);
	}

	private void initializeLanguageSetting() {
		String configLanguage = languageSetting.getOption();
		Language targetLanguage = null;

		if (configLanguage != null && !configLanguage.isEmpty() && languageSetting.has(configLanguage)) {
			targetLanguage = getLanguageEnumFromOption(configLanguage);
		} else {
			targetLanguage = Language.ENGLISH;
			configLanguage = "language.english";
			languageSetting.setOption(configLanguage);
		}

		I18n.setLanguage(targetLanguage);
		previousLanguageOption = configLanguage;
	}

	private String getLanguageOptionFromEnum(Language language) {
        if (Objects.requireNonNull(language) == Language.CHINESE) {
            return "language.chinese";
        }
        return "language.english";
    }

	private Language getLanguageEnumFromOption(String option) {
		if (option.contains("chinese")) {
			return Language.CHINESE;
		} else {
			return Language.ENGLISH;
		}
	}

	public final EventBus.EventListener<ClientTickEvent> onClientTick = event -> {

		if (!languageInitialized) {
			initializeLanguageSetting();
			languageInitialized = true;
		}

		if (keybindSetting.isPressed()) {
			client.gui.setScreen(new GuiModMenu());
		}

        if (keybindSetting_music.isPressed()) {
            client.gui.setScreen(new MusicPlayGui());
        }

		handleLanguageChange();
	};

	private void handleLanguageChange() {
		String currentLanguageOption = languageSetting.getOption();

		if (currentLanguageOption == null || currentLanguageOption.isEmpty()) {
			return;
		}

		if (!currentLanguageOption.equals(previousLanguageOption)) {
			Language targetLanguage = getLanguageEnumFromOption(currentLanguageOption);

			if (I18n.getCurrentLanguage() != targetLanguage) {
				I18n.setLanguage(targetLanguage);

				if (modMenu != null) {
					modMenu = null;
				}
			}

			previousLanguageOption = currentLanguageOption;

			// save config
			PupperClient.getInstance().getConfigManager().save(ConfigType.MOD);
		}
	}

	@Override
	public void onDisable() {
		this.setEnabled(true);
	}

	public static ModMenuSettings getInstance() {
		return instance;
	}

	public BooleanSetting getDarkModeSetting() {
		return darkModeSetting;
	}

	public HctColorSetting getHctColorSetting() {
		return hctColorSetting;
	}

	public BooleanSetting getBlurSetting() {
		return blurSetting;
	}

	public NumberSetting getBlurIntensitySetting() {
		return blurIntensitySetting;
	}

	public Screen getModMenu() {
		return modMenu;
	}
}
