package cn.pupperclient.mixin.mixins.minecraft.client;

import java.io.File;
import java.io.IOException;

import cn.pupperclient.event.client.ResolutionChangedEvent;
import cn.pupperclient.gui.api.SimpleSoarGui;
import cn.pupperclient.gui.edithud.GuiEditHUD;
import cn.pupperclient.mixin.mixins.accessors.PlayerTabOverlayAccessor;
import cn.pupperclient.skia.Skia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.main.GameConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.gui.tooltip.ContainerPreview;
import com.mojang.blaze3d.platform.Window;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.pupperclient.PupperClient;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.ClientTickEvent;
import cn.pupperclient.management.config.ConfigType;
import cn.pupperclient.management.mod.impl.player.HitDelayFixMod;
import cn.pupperclient.management.mod.impl.player.OldAnimationsMod;
import cn.pupperclient.mixin.interfaces.IMixinLivingEntity;
import cn.pupperclient.mixin.interfaces.IMixinMinecraftClient;
import cn.pupperclient.skia.context.SkiaContext;

@Mixin(value = Minecraft.class, priority = 300)
public abstract class MixinMinecraftClient implements IMixinMinecraftClient {

	@Shadow
	@Final
	private Window window;

	@Shadow
	public int missTime;

	@Shadow
	public MultiPlayerGameMode gameMode;

	@Final
	@Shadow
	public ParticleEngine particleEngine;

	@Final
    @Shadow
	public Options options;

	@Shadow
	public HitResult hitResult;

	@Shadow
	public ClientLevel level;

	@Shadow
	public LocalPlayer player;

	@Shadow
    protected abstract String createTitle();

    @Unique
	private File assetDir;

	@Inject(method = "<init>(Lnet/minecraft/client/main/GameConfig;)V", at = @At("TAIL"))
	public void onInit(GameConfig args, CallbackInfo ci) {
		assetDir = args.location.assetDirectory;
	}

	@Inject(method = "close", at = @At("HEAD"))
	public void onStop(CallbackInfo ci) {
		PupperClient.getInstance().getConfigManager().save(ConfigType.MOD);
	}

	@Inject(method = "continueAttack", at = @At("HEAD"))
	private void handleBlockBreaking(boolean breaking, CallbackInfo ci) {

		if (OldAnimationsMod.getInstance().isEnabled() && OldAnimationsMod.getInstance().isOldBreaking()) {
			if (this.options.keyAttack.isDown() && this.options.keyUse.isDown()) {

				if (breaking && this.hitResult != null && this.hitResult.getType() == Type.BLOCK) {

					BlockHitResult blockHitResult = (BlockHitResult) this.hitResult;
					BlockPos blockPos = blockHitResult.getBlockPos();
                    BlockState blockState = level.getBlockState(blockPos);

					if (!blockState.isAir()) {
						Direction direction = blockHitResult.getDirection();
                        BlockParticleOption particleOption = new BlockParticleOption(ParticleTypes.BLOCK, blockState);

                        double x = blockPos.getX() + 0.5 + direction.getStepX() * 0.5;
                        double y = blockPos.getY() + 0.5 + direction.getStepY() * 0.5;
                        double z = blockPos.getZ() + 0.5 + direction.getStepZ() * 0.5;

                        double xSpeed = direction.getStepX() * 0.2;
                        double ySpeed = 0.1;
                        double zSpeed = direction.getStepZ() * 0.2;

                        level.addParticle(particleOption, x, y, z, xSpeed, ySpeed, zSpeed);
						((IMixinLivingEntity) player).soarClient_CN$fakeSwingHand(InteractionHand.MAIN_HAND);
					}
				}
			}
		}
	}

	@Inject(method = "startAttack", at = @At("HEAD"))
	private void onHitDelayFix(CallbackInfoReturnable<Boolean> cir) {
		if (HitDelayFixMod.getInstance().isEnabled()) {
			missTime = 0;
		}
	}

	/**
	 * @author EldoDebug
	 * @reason updateWindowTitle
	 */
	@Overwrite
	public void updateTitle() {
		this.window.setTitle(PupperClient.getInstance().getName() + " "
				+ PupperClient.getInstance().getClientVersion() + " | " + createTitle());
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	public void init(CallbackInfo ci) throws IOException {
		PupperClient.getInstance().start();
	}

    @Inject(method = "<init>", at = @At("RETURN"))
    public void skia(CallbackInfo ci) throws IOException {
        int[] width = new int[1];
        int[] height = new int[1];

        GLFW.glfwGetFramebufferSize(Minecraft.getInstance().getWindow().handle(), width, height);
        SkiaContext.createSurface(width[0] > 0 ? width[0] : 1, height[0] > 0 ? height[0] : 1, null);
    }

    @Inject(method = "close", at = @At("HEAD"))
    public void onShutdown(CallbackInfo ci) {
        PupperClient.getInstance().onShutdown();
    }

	@Inject(method = "tick", at = @At("HEAD"))
	public void onClientTick(CallbackInfo ci) {
		EventBus.getInstance().post(new ClientTickEvent());
	}

    @Inject(
        method = {"renderFrame"},
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/GpuSurface;present()V"
        )
    )
    private void onBeforeFlipFrame(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.gui.screen();
        boolean skiaScreen = screen instanceof SimpleSoarGui;
        boolean preview = screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>
            && ContainerPreview.hasActive();
        boolean tabVisible = level != null
            && ((PlayerTabOverlayAccessor) minecraft.gui.hud.getTabList()).pupper$isVisible();
        boolean drawHud = level != null
            && (screen instanceof GuiEditHUD || (!minecraft.gui.hud.isHidden() && !tabVisible))
            && EventBus.getInstance().hasListeners(RenderSkiaEvent.class);
        if (!drawHud && !skiaScreen && !preview) return;
        SkiaContext.draw((canvas) -> {
            Window currentWindow = minecraft.getWindow();
            if (drawHud) {
                Skia.save();
                try {
                    Skia.scale((float) currentWindow.getGuiScale());
                    EventBus.getInstance().post(new RenderSkiaEvent(canvas));
                } finally {
                    Skia.restore();
                }
            }

            if (screen instanceof SimpleSoarGui skiaGui) {
                double mouseX = minecraft.mouseHandler.getScaledXPos(currentWindow);
                double mouseY = minecraft.mouseHandler.getScaledYPos(currentWindow);
                Skia.save();
                if (skiaGui.usesMinecraftGuiScale()) {
                    Skia.scale((float) currentWindow.getGuiScale());
                }
                skiaGui.renderSkia(mouseX, mouseY);
                Skia.restore();
            }
            if (preview) {
                Skia.save();
                try {
                    Skia.scale((float) currentWindow.getGuiScale());
                    ContainerPreview.renderActive(screen);
                } finally {
                    Skia.restore();
                }
            }
        });
    }

	@Override
	public File pupper$getAssetDir() {
		return this.assetDir;
	}

    @Inject(method = "resizeGui", at = @At("TAIL"))
    private void onResizeGui(CallbackInfo ci) {
        EventBus.getInstance().post(new ResolutionChangedEvent());
    }
}
