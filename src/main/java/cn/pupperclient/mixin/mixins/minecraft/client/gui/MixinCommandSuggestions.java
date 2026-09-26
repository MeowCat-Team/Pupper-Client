package cn.pupperclient.mixin.mixins.minecraft.client.gui;

import cn.pupperclient.management.command.PupperCommandSuggestions;
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

    @Shadow public abstract void hide();
    @Shadow public abstract void showSuggestions(boolean narrateFirstSuggestion);

    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void pupper$updateDotCommandSuggestions(CallbackInfo ci) {
        if (!(screen instanceof ChatScreen) || !input.getValue().startsWith(".")) {
            return;
        }

        ci.cancel();
        if (keepSuggestions) {
            return;
        }

        currentParse = null;
        currentParseIsCommand = false;
        currentParseIsMessage = false;
        commandUsage.clear();
        input.setSuggestion(null);
        hide();
        pendingSuggestions = CompletableFuture.completedFuture(
            PupperCommandSuggestions.suggest(input.getValue(), input.getCursorPosition())
        );
        showSuggestions(false);
    }
}
