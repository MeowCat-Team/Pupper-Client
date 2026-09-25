package cn.pupperclient;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.server.PacketHandler;
import cn.pupperclient.management.cape.CapeManager;
import cn.pupperclient.management.color.ColorManager;
import cn.pupperclient.management.command.PupperCommand;
import cn.pupperclient.management.config.ConfigManager;
import cn.pupperclient.management.hypixel.HypixelManager;
import cn.pupperclient.management.keybind.KeybindManager;
import cn.pupperclient.management.mod.ModManager;
import cn.pupperclient.management.music.MusicManager;
import cn.pupperclient.management.profile.ProfileManager;
import cn.pupperclient.management.websocket.WebSocketManager;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.utils.minecraft.interfaces.IMinecraft;
import cn.pupperclient.utils.thread.Multithreading;
import cn.pupperclient.utils.file.FileLocation;
import cn.pupperclient.utils.language.*;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PupperClient implements IMinecraft {

    private static final String CONFIG_FILE_NAME = "pupper.ok";
    private static final String ICON_PATH = "assets/pupper/logo.png";
    private static final String CLIENT_NAME = "Pupper Client";
    private static final String CLIENT_VERSION = "mc26.1.2-9.0.0a";
    private static final String MOD_ID = "pupper";

    private static final PupperClient INSTANCE = new PupperClient();

    public static boolean hasAcceptedTerms = false;
    public static boolean firstLaunch = false;
    public static final Logger LOGGER = PupperLogger.getLogger();

    private long launchTime;

    private ModManager modManager;
    private ColorManager colorManager;
    private MusicManager musicManager;
    private ConfigManager configManager;
    private ProfileManager profileManager;
    private WebSocketManager webSocketManager;
    private HypixelManager hypixelManager;
    private KeybindManager keybindManager;
    private CapeManager capeManager;

    private PupperClient() {}

    public void start() throws IOException {
        initializeResources();
        initializeManagers();
        registerEventHandlers();
        handleFirstLaunch();
        checkResources();
    }

    public void onShutdown() {
        if (keybindManager != null) {
            keybindManager.cleanup();
        }
        cn.pupperclient.skia.Skia.getImageHelper().clear();
        cn.pupperclient.skia.font.FontHelper.clearCache();
        Multithreading.shutdown();
    }

    private void initializeResources() {
        Fonts.loadAll();
        FileLocation.init();
        I18n.setLanguage(Language.ENGLISH);
        launchTime = System.currentTimeMillis();
    }

    private void initializeManagers() {
        modManager = new ModManager();
        modManager.init();
        colorManager = new ColorManager();
        musicManager = new MusicManager();
        configManager = new ConfigManager();
        profileManager = new ProfileManager();
        webSocketManager = new WebSocketManager();
        hypixelManager = new HypixelManager();
        keybindManager = KeybindManager.getInstance();
        keybindManager.initialize();
        PupperCommand.register();
        capeManager = new CapeManager();
    }

    private void registerEventHandlers() {
        EventBus eventBus = EventBus.getInstance();
        eventBus.register(new PupperEventHandle());
        eventBus.register(new PacketHandler());
    }

    private void handleFirstLaunch() throws IOException {
        Path configDir = FileLocation.MAIN_DIR.toPath();
        Path configFile = configDir.resolve(CONFIG_FILE_NAME);

        if (!Files.exists(configFile)) {
            firstLaunch = true;
            createConfigFile(configFile);
        }
    }

    private void createConfigFile(Path configFile) throws IOException {
        try {
            Files.createFile(configFile);
            var configContent = String.format(
                "First launch: %d%nPupperClient Version: %s%n",
                launchTime, CLIENT_VERSION
            );
            Files.writeString(configFile, configContent);
        } catch (IOException e) {
            LOGGER.error("Failed to create first launch detection file: {}", e.getMessage());
            throw e;
        }
    }

    private void checkResources() {
        if (getClass().getClassLoader().getResource(ICON_PATH) == null) {
            LOGGER.error("PupperClient icon not found in resources!");
        } else {
            LOGGER.info("PupperClient icon found in resources");
        }
    }

    public static PupperClient getInstance() {
        return INSTANCE;
    }

    public String getName() {
        return CLIENT_NAME;
    }

    public String getVersion() {
        return CLIENT_VERSION;
    }

    public long getLaunchTime() {
        return launchTime;
    }

    public ModManager getModManager() {
        return modManager;
    }

    public ColorManager getColorManager() {
        return colorManager;
    }

    public KeybindManager getKeybindManager() {
        return keybindManager;
    }

    public MusicManager getMusicManager() {
        return musicManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
    }

    public WebSocketManager getWebSocketManager() {
        return webSocketManager;
    }

    public HypixelManager getHypixelManager() {
        return hypixelManager;
    }

    public CapeManager getCapeManager() {
        return capeManager;
    }

    public static String getModId() {
        return MOD_ID;
    }
}
