package cn.pupperclient.management.cape;

import cn.pupperclient.PupperClient;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.Closeable;
import java.io.IOException;
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
        if (id == null || textureData == null) return;

        executorService.submit(() -> {
            RenderSystem.queueFencedTask(() -> {
                DynamicTexture nativeImage = createNativeTexture(textureData);
                if (nativeImage != null) {
                    Identifier identifier = Identifier.fromNamespaceAndPath("pupper", namespace + "/" + id);
                    Minecraft.getInstance().getTextureManager().register(identifier, nativeImage);
                    loadedCapes.put(id, identifier);
                    loadedCapeTextures.put(identifier, nativeImage);
                }
            });
        });
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

    private static DynamicTexture createNativeTexture(byte[] bytes) {
        if (bytes == null) return null;
        try {
            return new DynamicTexture(() -> "Pupper Client cape texture", NativeImage.read(bytes));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        selectedCapeId = null;
        new HashMap<>(loadedCapes).keySet().forEach(this::unloadCape);
        executorService.shutdown();
    }
}
