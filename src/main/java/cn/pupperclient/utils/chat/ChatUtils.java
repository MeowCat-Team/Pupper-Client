package cn.pupperclient.utils.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class ChatUtils {
    private static final String PREFIX = "§7[§bPupper§7] ";
    private static final String PREFIX_FORMATTED = ChatFormatting.GRAY + "[" + ChatFormatting.AQUA + "Pupper" + ChatFormatting.GRAY + "] ";
    private static final Component PREFIX_TEXT = Component.literal("[")
        .withStyle(ChatFormatting.GRAY)
        .append(Component.literal("PupperClient").withStyle(ChatFormatting.AQUA))
        .append(Component.literal("] ").withStyle(ChatFormatting.GRAY));


    public static void component(Component component) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        client.execute(() -> {
            ChatComponent chat = client.gui.hud.getChat();
            chat.addClientSystemMessage(component);
        });
    }

    public static void addChatMessage(String message) {
        addChatMessage(true, message);
    }

    public static void addChatMessage(Component message) {
        if (message == null || message.getString().isEmpty()) return;

        MutableComponent fullMessage = Component.empty()
            .append(PREFIX_TEXT)
            .append(message);

        component(fullMessage);
    }

    public static void addChatMessage(boolean prefix, String message) {
        if (message == null || message.isEmpty()) return;

        String formattedMessage = (prefix ? PREFIX : "") + message;
        component(Component.literal(formattedMessage));
    }

    public static void addChatMessage(boolean prefix, Component message) {
        if (message == null) return;

        String formattedMessage = (prefix ? PREFIX : "") + message.getString();
        component(Component.literal(formattedMessage));
    }

    public static void addFormattedMessage(String message, ChatFormatting... formattings) {
        MutableComponent text = Component.literal(PREFIX_FORMATTED).withStyle(ChatFormatting.GRAY);
        text.append(Component.literal(message).withStyle(formattings));
        component(text);
    }

    public static void error(String message) {
        addFormattedMessage(message, ChatFormatting.RED);
    }

    public static void success(String message) {
        addFormattedMessage(message, ChatFormatting.GREEN);
    }

    public static void warning(String message) {
        addFormattedMessage(message, ChatFormatting.YELLOW);
    }

    public static void info(String message) {
        addFormattedMessage(message, ChatFormatting.BLUE);
    }

}
