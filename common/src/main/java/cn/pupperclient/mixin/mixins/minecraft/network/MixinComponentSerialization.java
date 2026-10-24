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

package cn.pupperclient.mixin.mixins.minecraft.network;

import cn.pupperclient.utils.network.ServerTextPrivacy;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Function;

@Mixin(ComponentSerialization.class)
public abstract class MixinComponentSerialization {
    @Shadow @Final @Mutable public static StreamCodec<RegistryFriendlyByteBuf, Component> STREAM_CODEC;
    @Shadow @Final @Mutable public static StreamCodec<RegistryFriendlyByteBuf, Component> TRUSTED_STREAM_CODEC;
    @Shadow @Final @Mutable public static StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> OPTIONAL_STREAM_CODEC;
    @Shadow @Final @Mutable public static StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> TRUSTED_OPTIONAL_STREAM_CODEC;
    @Shadow @Final @Mutable public static StreamCodec<ByteBuf, Component> TRUSTED_CONTEXT_FREE_STREAM_CODEC;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void pupper$markDecodedServerText(CallbackInfo ci) {
        // Only decoding marks contents. Encoding and the local JSON/NBT codec stay unchanged.
        STREAM_CODEC = STREAM_CODEC.map(ServerTextPrivacy::markNetworkText, Function.identity());
        TRUSTED_STREAM_CODEC = TRUSTED_STREAM_CODEC.map(ServerTextPrivacy::markNetworkText, Function.identity());
        OPTIONAL_STREAM_CODEC = OPTIONAL_STREAM_CODEC.map(value -> value.map(ServerTextPrivacy::markNetworkText), Function.identity());
        TRUSTED_OPTIONAL_STREAM_CODEC = TRUSTED_OPTIONAL_STREAM_CODEC.map(value -> value.map(ServerTextPrivacy::markNetworkText), Function.identity());
        TRUSTED_CONTEXT_FREE_STREAM_CODEC = TRUSTED_CONTEXT_FREE_STREAM_CODEC.map(ServerTextPrivacy::markNetworkText, Function.identity());
    }
}
