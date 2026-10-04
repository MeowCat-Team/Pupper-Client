package cn.pupperclient.ui.component.handler.impl;

import cn.pupperclient.libraries.material3.hct.Hct;
import cn.pupperclient.ui.component.handler.ComponentHandler;

public abstract class HctColorPickerHandler extends ComponentHandler {
	public abstract void onPicking(Hct hct);
}
