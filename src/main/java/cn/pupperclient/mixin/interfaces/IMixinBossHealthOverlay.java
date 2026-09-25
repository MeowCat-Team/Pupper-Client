package cn.pupperclient.mixin.interfaces;

import java.util.Collection;
import net.minecraft.client.gui.components.LerpingBossEvent;

public interface IMixinBossHealthOverlay {
    Collection<LerpingBossEvent> pupper$getBossEvents();
}