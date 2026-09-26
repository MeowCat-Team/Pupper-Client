package cn.pupperclient.management.mod.impl.render;

import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.settings.impl.BooleanSetting;
import cn.pupperclient.management.mod.settings.impl.NumberSetting;
import cn.pupperclient.skia.font.Icon;

/** Reduces repeated render work while keeping every entity visible. */
public class EntityRenderOptimizerMod extends Mod {
    private static EntityRenderOptimizerMod instance;

    private final BooleanSetting optimizeItems = new BooleanSetting(
        "setting.entityoptimizer.items", "setting.entityoptimizer.items.description",
        Icon.INVENTORY, this, true);
    private final BooleanSetting optimizeChests = new BooleanSetting(
        "setting.entityoptimizer.chests", "setting.entityoptimizer.chests.description",
        Icon.INVENTORY_2, this, true);
    private final NumberSetting itemThreshold = new NumberSetting(
        "setting.entityoptimizer.threshold", "setting.entityoptimizer.threshold.description",
        Icon.PERFORMANCE_MAX, this, 64, 16, 256, 16);

    public EntityRenderOptimizerMod() {
        super("mod.entityoptimizer.name", "mod.entityoptimizer.description",
            Icon.PERFORMANCE_MAX, ModCategory.RENDER);
        instance = this;
    }

    public static EntityRenderOptimizerMod getInstance() {
        return instance;
    }

    public boolean optimizeItems() {
        return isEnabled() && optimizeItems.isEnabled();
    }

    public boolean optimizeChests() {
        return isEnabled() && optimizeChests.isEnabled();
    }

    public int getItemThreshold() {
        return (int) itemThreshold.getValue();
    }
}
