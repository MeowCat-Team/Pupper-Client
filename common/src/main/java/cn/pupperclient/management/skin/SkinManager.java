package cn.pupperclient.management.skin;

import cn.pupperclient.PupperClient;
import cn.pupperclient.PupperLogger;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

/**
 * Owns the skin textures selected by this client's user. The textures are local-only:
 * the player renderer only uses them for {@code Minecraft.player}.
 */
public class SkinManager implements Closeable {
    private static SkinManager instance;

    private final Map<String, Identifier> loadedSkins = Collections.synchronizedMap(new HashMap<>());
    private final Map<Identifier, DynamicTexture> loadedSkinTextures = Collections.synchronizedMap(new HashMap<>());

    private String selectedSkinId = null;

    private final String namespace = "pupper-skins";
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public SkinManager() {
        instance = this;
    }

    public static SkinManager getInstance() {
        return instance;
    }

    public void selectSkin(String skinId) {
        this.selectedSkinId = skinId;
    }

    public String getSelectedSkinId() {
        return selectedSkinId;
    }

    public ClientAsset.Texture getSelectedSkinTexture() {
        if (selectedSkinId == null) return null;
        Identifier skinTexture = getLoadedSkin(selectedSkinId);
        if (skinTexture == null) return null;
        return new ClientAsset.ResourceTexture(
                Identifier.fromNamespaceAndPath(PupperClient.getModId(), "skins/selectedskin"),
                skinTexture);
    }

    public void clearSelectedSkin() {
        this.selectedSkinId = null;
    }

    public void loadSkin(String id, byte[] textureData) {
        if (id == null || textureData == null) return;

        executorService.submit(() -> RenderSystem.queueFencedTask(() -> {
            DynamicTexture nativeImage = createNativeTexture(textureData);
            if (nativeImage != null) {
                Identifier identifier = Identifier.fromNamespaceAndPath("pupper", texturePath(id));
                Minecraft.getInstance().getTextureManager().register(identifier, nativeImage);
                loadedSkins.put(id, identifier);
                loadedSkinTextures.put(identifier, nativeImage);
            }
        }));
    }

    public void unloadSkin(String id) {
        if (id == null) return;

        if (id.equals(selectedSkinId)) {
            selectedSkinId = null;
        }

        Identifier skin = loadedSkins.remove(id);
        if (skin != null) {
            DynamicTexture texture = loadedSkinTextures.remove(skin);
            if (texture != null) {
                texture.close();
            }
            Minecraft.getInstance().getTextureManager().release(skin);
        }
    }

    public Identifier getLoadedSkin(String id) {
        return id != null ? loadedSkins.get(id) : null;
    }

    public Set<String> getLoadedSkinIds() {
        return new HashSet<>(loadedSkins.keySet());
    }

    private String texturePath(String skinId) {
        // Skin names are user-supplied file names and may contain Unicode/spaces.
        // Identifier paths only allow [a-z0-9/._-], so use a deterministic safe key.
        return namespace + "/" + UUID.nameUUIDFromBytes(skinId.getBytes(StandardCharsets.UTF_8));
    }

    private static DynamicTexture createNativeTexture(byte[] bytes) {
        if (bytes == null) return null;
        try {
            return new DynamicTexture(() -> "Pupper Client skin texture", NativeImage.read(bytes));
        } catch (IOException e) {
            PupperLogger.error("SkinManager", "Failed to read skin texture", e);
            return null;
        }
    }

    @Override
    public void close() {
        selectedSkinId = null;
        new HashMap<>(loadedSkins).keySet().forEach(this::unloadSkin);
        executorService.shutdown();
    }
}
