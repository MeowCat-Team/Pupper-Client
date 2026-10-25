/*
 * Copyright (C) 2024-2026 Pupper Client contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cn.pupperclient.mixin.core.minecraft.client.util;

import cn.pupperclient.PupperClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

@Mixin(Window.class)
public class MixinWindow {
    private static final String ICON_PATH = "/assets/pupper/logo.png";

    @Shadow
    @Final
    private long handle;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onWindowInit(CallbackInfo ci) {
        pupper$applyIcon();
    }

    /**
     * Keeps the client icon when Minecraft applies a resource-pack icon.
     *
     * <p>The vanilla call is replaced rather than dropped: the window still receives an
     * icon, it is just ours.
     */
    @Inject(method = "setIcon", at = @At("HEAD"), cancellable = true)
    private void onSetIcon(CallbackInfo ci) {
        pupper$applyIcon();
        ci.cancel();
    }

    @Unique
    private void pupper$applyIcon() {
        byte[] encoded;
        try (InputStream stream = PupperClient.class.getResourceAsStream(ICON_PATH)) {
            if (stream == null) {
                PupperClient.LOGGER.error("Pupper Client icon is missing from the jar");
                return;
            }
            encoded = stream.readAllBytes();
        } catch (IOException failure) {
            PupperClient.LOGGER.error("Could not read the Pupper Client icon", failure);
            return;
        }

        ByteBuffer source = org.lwjgl.system.MemoryUtil.memAlloc(encoded.length);
        try {
            source.put(encoded).flip();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                IntBuffer channels = stack.mallocInt(1);
                ByteBuffer pixels = STBImage.stbi_load_from_memory(source, width, height, channels, 4);
                if (pixels == null) {
                    PupperClient.LOGGER.error("Could not decode the Pupper Client icon: {}", STBImage.stbi_failure_reason());
                    return;
                }
                try {
                    GLFWImage.Buffer images = GLFWImage.malloc(1, stack);
                    images.get(0).set(width.get(0), height.get(0), pixels);
                    GLFW.glfwSetWindowIcon(handle, images);
                } finally {
                    STBImage.stbi_image_free(pixels);
                }
            }
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(source);
        }
    }
}
