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
    private final BooleanSetting optimizeItemShadows = new BooleanSetting(
        "setting.entityoptimizer.shadows", "setting.entityoptimizer.shadows.description",
        Icon.PERFORMANCE_MAX, this, true) {
        @Override public boolean isVisible() { return optimizeItems.isEnabled(); }
    };
    private final NumberSetting itemThreshold = new NumberSetting(
        "setting.entityoptimizer.threshold", "setting.entityoptimizer.threshold.description",
        Icon.PERFORMANCE_MAX, this, 64, 16, 256, 16) {
        @Override public boolean isVisible() { return optimizeItems.isEnabled() && optimizeItemShadows.isEnabled(); }
    };
    private final BooleanSetting optimizeMinecarts = new BooleanSetting(
        "setting.entityoptimizer.minecarts", "setting.entityoptimizer.minecarts.description",
        Icon.TRAIN, this, true);
    private final BooleanSetting optimizeEntities = new BooleanSetting(
        "setting.entityoptimizer.extraction", "setting.entityoptimizer.extraction.description",
        Icon.TRANSPORTATION, this, true);
    private final BooleanSetting fixBoatWaterPatch = new BooleanSetting(
        "setting.entityoptimizer.boatwaterpatch", "setting.entityoptimizer.boatwaterpatch.description",
        Icon.DIRECTIONS_BOAT, this, true);

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

    public boolean optimizeItemShadows() {
        return optimizeItems() && optimizeItemShadows.isEnabled();
    }

    public int getItemThreshold() {
        return (int) itemThreshold.getValue();
    }

    /** 静止矿车复用上一帧的铁路姿态，跳过每帧 6 次方块查询与 10 次坐标分配。 */
    public boolean optimizeMinecarts() {
        return isEnabled() && optimizeMinecarts.isEnabled();
    }

    /** 应用 26.2 声明但从未生效的水膜模型姿势（属于修正，而非性能优化）。 */
    public boolean fixBoatWaterPatch() {
        return isEnabled() && fixBoatWaterPatch.isEnabled();
    }

    /** 实体阴影扫描记忆化 + 矿车展示方块模型脏标记。 */
    public boolean optimizeEntities() {
        return isEnabled() && optimizeEntities.isEnabled();
    }
}
