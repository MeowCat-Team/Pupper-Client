package cn.pupperclient.management.cape;

import cn.pupperclient.PupperClient;
import cn.pupperclient.PupperLogger;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

public class CapeManager implements Closeable {
    private static CapeManager instance;

    private final Map<String, Identifier> loadedCapes = Collections.synchronizedMap(new HashMap<>());
    private final Map<Identifier, DynamicTexture> loadedCapeTextures = Collections.synchronizedMap(new HashMap<>());

    private String selectedCapeId = null;

    private final String namespace = "pupper-capes";
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public CapeManager() {
        instance = this;
    }

    public static CapeManager getInstance() {
        return instance;
    }

    public void selectCape(String capeId) {
        this.selectedCapeId = capeId;
    }

    public String getSelectedCapeId() {
        return selectedCapeId;
    }

    public ClientAsset.Texture getSelectedCapeTexture() {
        if (selectedCapeId == null) return null;
        var capeTexture = getLoadedCape(selectedCapeId);
        return new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath(PupperClient.getModId(), "capes/selectedcape"), capeTexture);
    }

    public void clearSelectedCape() {
        this.selectedCapeId = null;
    }

    public void loadCape(String id, byte[] textureData) {
        if (id == null || textureData == null || loadedCapes.containsKey(id)) return;

        Minecraft minecraft = Minecraft.getInstance();
        executorService.submit(() -> {
            NativeImage image = readNativeImage(textureData);
            if (image == null) return;

            // Texture registration must happen on the client thread. Reading the PNG
            // stays on the worker so large capes do not stall a frame.
            minecraft.execute(() -> registerCape(id, image));
        });
    }

    private void registerCape(String id, NativeImage image) {
        DynamicTexture texture;
        try {
            texture = new DynamicTexture(() -> "Pupper Client cape texture", image);
        } catch (RuntimeException e) {
            image.close();
            PupperLogger.error("CapeManager", "Failed to create cape texture", e);
            return;
        }

        Identifier identifier = Identifier.fromNamespaceAndPath("pupper", texturePath(id));
        Minecraft.getInstance().getTextureManager().register(identifier, texture);
        loadedCapes.put(id, identifier);
        loadedCapeTextures.put(identifier, texture);
    }

    public void unloadCape(String id) {
        if (id == null) return;

        if (id.equals(selectedCapeId)) {
            selectedCapeId = null;
        }

        Identifier cape = loadedCapes.remove(id);
        if (cape != null) {
            DynamicTexture texture = loadedCapeTextures.remove(cape);
            if (texture != null) {
                texture.close();
            }
            Minecraft.getInstance().getTextureManager().release(cape);
        }
    }

    public Identifier getLoadedCape(String id) {
        return id != null ? loadedCapes.get(id) : null;
    }

    public Set<String> getLoadedCapeIds() {
        return new HashSet<>(loadedCapes.keySet());
    }

    /**
     * Cape names come from user-supplied file names, but {@link Identifier} paths only
     * accept {@code [a-z0-9/._-]}. Map arbitrary names to a deterministic, collision-safe
     * key instead of passing the original name through.
     */
    private String texturePath(String capeId) {
        return namespace + "/" + UUID.nameUUIDFromBytes(capeId.getBytes(StandardCharsets.UTF_8));
    }

    private static NativeImage readNativeImage(byte[] bytes) {
        if (bytes == null) return null;
        try {
            return NativeImage.read(bytes);
        } catch (IOException e) {
            PupperLogger.error("CapeManager", "Failed to read cape texture", e);
            return null;
        }
    }

    @Override
    public void close() {
        selectedCapeId = null;
        new HashMap<>(loadedCapes).keySet().forEach(this::unloadCape);
        executorService.shutdown();
    }
}
