package cn.pupperclient.mixin.interfaces;

import net.minecraft.world.InteractionHand;

public interface IMixinLivingEntity {
	void pupperClient$fakeSwingHand(InteractionHand hand);
}
