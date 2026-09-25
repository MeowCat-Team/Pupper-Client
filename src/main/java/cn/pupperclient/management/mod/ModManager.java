package cn.pupperclient.management.mod;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import cn.pupperclient.management.mod.api.hud.*;
import cn.pupperclient.management.mod.api.hud.design.HUDDesign;
import cn.pupperclient.management.mod.api.hud.design.impl.*;
import cn.pupperclient.management.mod.impl.fun.*;
import cn.pupperclient.management.mod.impl.hud.*;
import cn.pupperclient.management.mod.impl.misc.*;
import cn.pupperclient.management.mod.impl.player.*;
import cn.pupperclient.management.mod.impl.render.*;
import cn.pupperclient.management.mod.impl.settings.*;
import cn.pupperclient.management.mod.settings.Setting;
import cn.pupperclient.management.mod.settings.impl.KeybindSetting;

public class ModManager {
	private final List<Mod> mods = new CopyOnWriteArrayList<>();
	private final List<Setting> settings = new CopyOnWriteArrayList<>();
	private final List<HUDDesign> designs = new CopyOnWriteArrayList<>();

    private HUDDesign currentDesign;

	public void init() {
		initMods();
		initDesigns();
	}

	private void initMods() {
        initHudMods();
		initPlayerMods();
        initRenderMods();
		initMiscMods();
        initSettings();
        initFunMods();

		sortMods();
	}

    private void initHudMods()
    {
        mods.add(new Scoreboard());
        mods.add(new BedwarsStatsOverlayMod());
        mods.add(new BossBarMod());
        mods.add(new ClockMod());
        mods.add(new ComboCounterMod());
        mods.add(new CoordsMod());
        mods.add(new DayCounterMod());
        mods.add(new FPSDisplayMod());
        mods.add(new GameModeDisplayMod());
        mods.add(new HealthDisplayMod());
        mods.add(new JumpResetIndicatorMod());
        mods.add(new KeystrokesMod());
        mods.add(new MemoryUsageMod());
        mods.add(new MouseStrokesMod());
        mods.add(new MusicInfoMod());
        mods.add(new NameDisplayMod());
        mods.add(new PingDisplayMod());
        mods.add(new PitchDisplayMod());
        mods.add(new PlayerCounterMod());
        mods.add(new PlayTimeDisplayMod());
        mods.add(new ProtocolVersionMod());
        mods.add(new ReachDisplayMod());
        mods.add(new ServerIPDisplayMod());
        mods.add(new SpeedometerMod());
        mods.add(new StopwatchMod());
        mods.add(new WeatherDisplayMod());
        mods.add(new YawDisplayMod());
        mods.add(new WatermarkMod());
        mods.add(new CPSDisplayMod());
        mods.add(new TargetHUDMod());
        mods.add(new ArrayListMod());
        mods.add(new DynamicIsland());
        mods.add(new FallDamageHelp());
        mods.add(new CloudMusicHudMod());
        mods.add(new CooldownHudMod());
        mods.add(new PotionHudMod());
    }

    private void initPlayerMods(){
        mods.add(new AutoGGMod());
        mods.add(new ForceMainHandMod());
        mods.add(new FreelookMod());
        mods.add(new HitDelayFixMod());
        mods.add(new NoJumpDelayMod());
        mods.add(new OldAnimationsMod());
        mods.add(new SnapTapMod());
        mods.add(new TaplookMod());
        mods.add(new ZoomMod());
        mods.add(new AutoTextMod());
        mods.add(new Sprint());
    }

    private void initRenderMods(){
        mods.add(new BloodParticleMod());
        mods.add(new CustomHandMod());
        mods.add(new FullbrightMod());
        mods.add(new MusicWaveformMod());
        mods.add(new OverlayEditorMod());
        mods.add(new ParticlesMod());
        mods.add(new ProjectileTrailMod());
        mods.add(new ClickEffectMod());
        mods.add(new NoHurtFov());
    }

    private void initMiscMods() {
        mods.add(new DiscordRPCMod());
        mods.add(new HypixelMod());
        mods.add(new TimeChangerMod());
        mods.add(new WeatherChangerMod());
    }

    private void initFunMods() {
        mods.add(new FakeFpsMod());
        mods.add(new HeypixelMod());
        mods.add(new TotemTracker());
    }

    private void initSettings() {
        mods.add(new HUDModSettings());
        mods.add(new ModMenuSettings());
        mods.add(new SystemSettings());
    }

	private void initDesigns() {
		designs.add(new ClassicDesign());
		designs.add(new ClearDesign());
		designs.add(new MaterialYouDesign());
		designs.add(new SimpleDesign());
		setCurrentDesign("design.simple");
	}

	public List<Mod> getMods() {
		return mods;
	}

	public List<Setting> getSettings() {
		return settings;
	}

	public List<HUDMod> getHUDMods() {
		return mods.stream().filter(m -> m instanceof HUDMod).map(m -> (HUDMod) m).collect(Collectors.toList());
	}

	public List<KeybindSetting> getKeybindSettings() {
		return settings.stream().filter(s -> s instanceof KeybindSetting).map(s -> (KeybindSetting) s)
				.collect(Collectors.toList());
	}

	public List<Setting> getSettingsByMod(Mod m) {
		return settings.stream().filter(s -> s.getParent().equals(m)).collect(Collectors.toList());
	}

	public void addSetting(Setting setting) {
		settings.add(setting);
	}

	public HUDDesign getCurrentDesign() {
		return currentDesign;
	}

	public void setCurrentDesign(String name) {
		this.currentDesign = getDesignByName(name);
	}

	public HUDDesign getDesignByName(String name) {
		return designs.stream().filter(design -> design.getName().equals(name)).findFirst()
				.orElseGet(() -> getDesignByName("design.simple"));
	}

	private void sortMods() {
		mods.sort(Comparator.comparing(Mod::getRawName));
	}

    public void setModEnabledByName(boolean enabled, String modName) {
        Mod targetMod = getModByName(modName);
        if (targetMod != null) {
            targetMod.setEnabled(enabled);
        }
    }

    public Mod getModByName(String modName) {
        return mods.stream()
            .filter(mod -> mod.getName().equalsIgnoreCase(modName))
            .findFirst()
            .orElse(null);
    }

    public Mod getModByRawName(String modName) {
        return mods.stream()
            .filter(mod -> mod.getRawName().equalsIgnoreCase(modName))
            .findFirst()
            .orElse(null);
    }
}
