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

package cn.pupperclient.mixin.mixins.minecraft.client.util;

import cn.pupperclient.PupperClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
    @Shadow
    @Final
    private long handle;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onWindowInit(CallbackInfo ci) {
        try {
            try (InputStream is = PupperClient.class.getResourceAsStream("/assets/pupper/logo.png")) {
                if (is == null) {
                    System.err.println("Pupper Client icon not found!");
                    return;
                }

                byte[] imageBytes = is.readAllBytes();
                ByteBuffer imageBuffer = ByteBuffer.allocateDirect(imageBytes.length);
                imageBuffer.put(imageBytes);
                imageBuffer.flip();

                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer width = stack.mallocInt(10);
                    IntBuffer height = stack.mallocInt(10);
                    IntBuffer channels = stack.mallocInt(2);

                    ByteBuffer iconBuffer = STBImage.stbi_load_from_memory(
                        imageBuffer, width, height, channels, 4
                    );

                    if (iconBuffer == null) {
                        System.err.println("Failed to load Pupper Client icon: " + STBImage.stbi_failure_reason());
                        return;
                    }

                    GLFWImage image = GLFWImage.malloc(stack);
                    image.set(width.get(0), height.get(0), iconBuffer);

                    GLFWImage.Buffer images = GLFWImage.malloc(1, stack);
                    images.put(0, image);

                    GLFW.glfwSetWindowIcon(handle, images);

                    STBImage.stbi_image_free(iconBuffer);
                    PupperClient.LOGGER.info("Pupper Client icon loaded successfully!");
                }
            }
        } catch (IOException e) {
            PupperClient.LOGGER.error("Error loading Pupper Client icon: {}", e.getMessage());
        }
    }

    @Inject(method = "setIcon", at = @At("HEAD"), cancellable = true)
    private void onSetIcon(CallbackInfo ci) {
        ci.cancel();
    }
}
