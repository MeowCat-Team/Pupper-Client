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

package cn.pupperclient.management.mod.impl.hud;

import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.skia.RenderSkiaEvent;
import cn.pupperclient.management.mod.api.hud.SimpleHUDMod;
import cn.pupperclient.skia.font.Icon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FallDamageHelp extends SimpleHUDMod {
    public FallDamageHelp() {
        super("mod.fallDamageHelp.name", "mod.fallDamageHelp.description", Icon.FALLING);
    }

    public final EventBus.EventListener<RenderSkiaEvent> onRenderSkia = e -> {
        this.draw();
    };

    public static boolean willFallToDeath() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) {
            return false;
        }

        Level world = player.level();
        double currentY = player.getY();
        BlockPos playerBlockPos = player.blockPosition();

        for (int offset = 1; offset < 256; offset++) {
            int y = playerBlockPos.getY() - offset;
            if (y < world.getMinY()) {
                break;
            }

            BlockPos checkPos = new BlockPos(playerBlockPos.getX(), y, playerBlockPos.getZ());
            BlockState state = world.getBlockState(checkPos);
            VoxelShape collisionShape = state.getCollisionShape(world, checkPos);

            if (!collisionShape.isEmpty() && collisionShape != Shapes.empty()) {
                double blockTopY = y + collisionShape.max(Direction.Axis.Y);
                double fallDistance = currentY - blockTopY;
                return fallDistance >= 23;
            }
        }

        double fallDistance = currentY - world.getMinY();
        return fallDistance >= 23;
    }

    @Override
    public String getText() {
        return willFallToDeath() ? "is falling to death" : "is not falling to death";
    }

    @Override
    public String getIcon() {
        return Icon.FALLING;
    }
}
