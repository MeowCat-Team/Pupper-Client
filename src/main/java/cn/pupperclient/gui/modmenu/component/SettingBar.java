package cn.pupperclient.gui.modmenu.component;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import cn.pupperclient.PupperClient;
import cn.pupperclient.animation.SimpleAnimation;
import cn.pupperclient.libraries.material3.hct.Hct;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.mod.settings.Setting;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.management.mod.settings.impl.ComboSetting;
import cn.pupperclient.management.mod.settings.impl.FileSetting;
import cn.pupperclient.management.mod.settings.impl.HctColorSetting;
import cn.pupperclient.management.mod.settings.impl.KeybindSetting;
import cn.pupperclient.management.mod.settings.impl.NumberSetting;
import cn.pupperclient.management.mod.settings.impl.StringSetting;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.ui.component.Component;
import cn.pupperclient.ui.component.handler.impl.ComboButtonHandler;
import cn.pupperclient.ui.component.handler.impl.FileSelectorHandler;
import cn.pupperclient.ui.component.handler.impl.HctColorPickerHandler;
import cn.pupperclient.ui.component.handler.impl.KeybindHandler;
import cn.pupperclient.ui.component.handler.impl.SliderHandler;
import cn.pupperclient.ui.component.handler.impl.SwitchHandler;
import cn.pupperclient.ui.component.handler.impl.TextHandler;
import cn.pupperclient.ui.component.impl.ComboButton;
import cn.pupperclient.ui.component.impl.FileSelector;
import cn.pupperclient.ui.component.impl.HctColorPicker;
import cn.pupperclient.ui.component.impl.Keybind;
import cn.pupperclient.ui.component.impl.Slider;
import cn.pupperclient.ui.component.impl.Switch;
import cn.pupperclient.ui.component.impl.text.TextField;
import cn.pupperclient.utils.color.ColorUtils;
import cn.pupperclient.utils.language.I18n;
import com.mojang.blaze3d.platform.InputConstants;

public class SettingBar extends Component {

	private SimpleAnimation yAnimation = new SimpleAnimation();
	private String title, description, icon;
	private Component component;

    private static final Map<Class<? extends Setting>, Function<Setting, Component>> COMPONENT_FACTORIES = new LinkedHashMap<>();

    static {
        register(BooleanSetting.class, setting -> {
            BooleanSetting bSetting = (BooleanSetting) setting;
            Switch switchComp = new Switch(0, 0, bSetting.isEnabled());
            switchComp.setHandler(new SwitchHandler() {
                @Override
                public void onEnabled() {
                    bSetting.setEnabled(true);
                }

                @Override
                public void onDisabled() {
                    bSetting.setEnabled(false);
                }
            });
            return switchComp;
        });

        register(NumberSetting.class, setting -> {
            NumberSetting nSetting = (NumberSetting) setting;
            Slider slider = new Slider(0, 0, 200, nSetting.getValue(), nSetting.getMinValue(), nSetting.getMaxValue(), nSetting.getStep());
            slider.setHandler(new SliderHandler() {
                @Override
                public void onValueChanged(Object value) {
                    nSetting.setValue((Float) value);
                }
            });
            return slider;
        });

        register(ComboSetting.class, setting -> {
            ComboSetting cSetting = (ComboSetting) setting;
            ComboButton button = new ComboButton(0, 0, cSetting.getOptions(), cSetting.getOption());
            button.setHandler(new ComboButtonHandler() {
                @Override
                public void onChanged(String option) {
                    cSetting.setOption(option);
                }
            });
            return button;
        });

        register(KeybindSetting.class, setting -> {
            KeybindSetting kSetting = (KeybindSetting) setting;
            Keybind bind = new Keybind(0, 0, kSetting.getKey());
            bind.setHandler(new KeybindHandler() {
                @Override
                public void onBinded(InputConstants.Key key) {
                    kSetting.setKey(key);
                }
            });
            return bind;
        });

        register(HctColorSetting.class, setting -> {
            HctColorSetting hSetting = (HctColorSetting) setting;
            HctColorPicker picker = new HctColorPicker(0, 0, hSetting.getHct());
            picker.setHandler(new HctColorPickerHandler() {
                @Override
                public void onPicking(Hct hct) {
                    hSetting.setHct(hct);
                }
            });
            return picker;
        });

        register(StringSetting.class, setting -> {
            StringSetting sSetting = (StringSetting) setting;
            TextField textField = new TextField(0, 0, 150, sSetting.getValue());
            textField.setHandler(new TextHandler() {
                @Override
                public void onTyped(String value) {
                    sSetting.setValue(value);
                }
            });
            return textField;
        });

        register(FileSetting.class, setting -> {
            FileSetting fSetting = (FileSetting) setting;
            FileSelector fileSelector = new FileSelector(0, 0, fSetting.getFile(), fSetting.getExtensions());
            fileSelector.setHandler(new FileSelectorHandler() {
                @Override
                public void onSelect(File file) {
                    fSetting.setFile(file);
                }
            });
            return fileSelector;
        });
    }

    private static void register(Class<? extends Setting> settingClass, Function<Setting, Component> factory) {
        COMPONENT_FACTORIES.put(settingClass, factory);
    }

    private static Component createComponent(Setting setting) {
        for (Map.Entry<Class<? extends Setting>, Function<Setting, Component>> entry : COMPONENT_FACTORIES.entrySet()) {
            if (entry.getKey().isInstance(setting)) {
                return entry.getValue().apply(setting);
            }
        }
        return null;
    }

	public SettingBar(Setting setting, float x, float y, float width) {
		super(x, y);
		this.title = setting.getName();
		this.description = setting.getDescription();
		this.icon = setting.getIcon();
		this.width = width;
		this.height = 68;
        component = createComponent(setting);
	}

	@Override
	public void draw(double mouseX, double mouseY) {

		ColorPalette palette = PupperClient.getInstance().getColorManager().getPalette();

		float itemY = y;

		yAnimation.onTick(itemY, 14);
		itemY = yAnimation.getValue();

		if (component != null) {
			component.setX(x + width - component.getWidth() - 22);
			component.setY(itemY + (height - component.getHeight()) / 2);
		}

		Skia.drawRoundedRect(x, itemY, width, height, 18, palette.getSurfaceContainerLow());
		Skia.drawOutline(x, itemY, width, height, 18, 1,
			ColorUtils.applyAlpha(palette.getOutlineVariant(), 0.45F));
		Skia.drawFullCenteredText(icon, x + 30, itemY + (height / 2), palette.getOnSurface(), Fonts.getIcon(32));
		Skia.drawText(I18n.get(title), x + 52, itemY + 20, palette.getOnSurface(), Fonts.getRegular(17));
		Skia.drawText(I18n.get(description), x + 52, itemY + 37, palette.getOnSurfaceVariant(), Fonts.getRegular(14));

		if (component != null) {
			component.draw(mouseX, mouseY);
		}
	}

	@Override
	public void mousePressed(double mouseX, double mouseY, int button) {

		if (component != null) {
			component.mousePressed(mouseX, mouseY, button);
		}
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {

		if (component != null) {
			component.mouseReleased(mouseX, mouseY, button);
		}
	}

	@Override
	public void charTyped(int chr) {

		if (component != null) {
			component.charTyped(chr);
		}
	}

	@Override
	public void keyPressed(int keyCode, int scanCode, int modifiers) {

		if (component != null) {
			component.keyPressed(keyCode, scanCode, modifiers);
		}
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getIcon() {
		return icon;
	}
}
