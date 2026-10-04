package cn.pupperclient.ui.component.handler.impl;

import cn.pupperclient.ui.component.handler.ComponentHandler;
import com.mojang.blaze3d.platform.InputConstants.Key;

public abstract class KeybindHandler extends ComponentHandler {
	public abstract void onBinded(Key key);
}
