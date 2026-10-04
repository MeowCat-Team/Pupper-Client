package cn.pupperclient.mixin.mixins.minecraft.network;

import io.netty.channel.ChannelFutureListener;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.ReceivePacketEvent;
import cn.pupperclient.event.client.SendPacketEvent;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.RunningOnDifferentThreadException;

@Mixin(Connection.class)
public abstract class MixinClientConnection {

	@Shadow
    private static <T extends PacketListener> void genericsFtw(Packet<T> packet, PacketListener listener) {
	}

    @Shadow
    protected abstract void sendPacket(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush);

    @Redirect(
        method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;sendPacket(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V")
    )
    private void redirectSendPacket(Connection instance, Packet<?> packet, ChannelFutureListener listener, boolean flush) {

		SendPacketEvent event = new SendPacketEvent(packet);
		EventBus.getInstance().post(event);

        if (!event.isCancelled()) {
            sendPacket(packet, listener, flush);
        }
	}

	@Inject(method = "genericsFtw", at = @At("HEAD"), cancellable = true, require = 1)
	private static void onRecievePacket(Packet<?> packet, PacketListener listener, CallbackInfo ci) {

		if (packet instanceof ClientboundBundlePacket bundleS2CPacket) {
			ci.cancel();

			for (Packet<?> packetInBundle : bundleS2CPacket.subPackets()) {
				try {
					genericsFtw(packetInBundle, listener);
				} catch (RunningOnDifferentThreadException ignored) {
				}
			}
			return;
		}

		ReceivePacketEvent event = new ReceivePacketEvent(packet);
		EventBus.getInstance().post(event);

		if (event.isCancelled()) {
			ci.cancel();
		}
	}
}
