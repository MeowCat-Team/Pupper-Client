package cn.pupperclient.mixin.interfaces;

public interface IMixinCameraEntity {
	float pupperClient$getCameraPitch();
	float pupperClient$getCameraYaw();

	void pupperClient$setCameraPitch(float pitch);
	void pupperClient$setCameraYaw(float yaw);
}
