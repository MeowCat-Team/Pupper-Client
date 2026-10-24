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

package cn.pupperclient.mixin.mixins.minecraft.client.gui;

import cn.pupperclient.management.command.PupperCommandSuggestions;
import cn.pupperclient.management.mod.impl.misc.MiniMessageCompletionMod;
import cn.pupperclient.utils.chat.MiniMessageSuggestions;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public abstract class MixinCommandSuggestions {
    @Shadow @Final private Screen screen;
    @Shadow @Final private EditBox input;
    @Shadow @Final private List<FormattedCharSequence> commandUsage;
    @Shadow private ParseResults<ClientSuggestionProvider> currentParse;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow private boolean currentParseIsCommand;
    @Shadow private boolean currentParseIsMessage;
    @Shadow private boolean keepSuggestions;
    @Unique private boolean pupper$miniMessageSuggestions;

    @Shadow public abstract void hide();
    @Shadow public abstract void showSuggestions(boolean narrateFirstSuggestion);

    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void pupper$updateLocalSuggestions(CallbackInfo ci) {
        if (!(screen instanceof ChatScreen)) {
            return;
        }

        String value = input.getValue();
        Suggestions localSuggestions;
        if (value.startsWith(".")) {
            pupper$miniMessageSuggestions = false;
            ci.cancel();
            if (keepSuggestions) return;
            localSuggestions = PupperCommandSuggestions.suggest(value, input.getCursorPosition());
        } else if (MiniMessageCompletionMod.isCompletionEnabled()) {
            // Tab mutates the input while this flag is set, even after inserting the final '>'.
            // Preserve the original candidates so subsequent Tab/Shift+Tab can still cycle them.
            if (keepSuggestions) {
                if (pupper$miniMessageSuggestions) ci.cancel();
                return;
            }
            var miniMessage = MiniMessageSuggestions.suggest(value, input.getCursorPosition());
            pupper$miniMessageSuggestions = miniMessage.isPresent();
            if (miniMessage.isEmpty()) return;
            ci.cancel();
            localSuggestions = miniMessage.get();
        } else {
            pupper$miniMessageSuggestions = false;
            return;
        }

        currentParse = null;
        currentParseIsCommand = false;
        currentParseIsMessage = false;
        commandUsage.clear();
        input.setSuggestion(null);
        hide();
        pendingSuggestions = CompletableFuture.completedFuture(localSuggestions);
        showSuggestions(false);
    }
}
