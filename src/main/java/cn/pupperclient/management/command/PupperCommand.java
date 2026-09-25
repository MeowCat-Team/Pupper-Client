package cn.pupperclient.management.command;

import cn.pupperclient.PupperClient;
import cn.pupperclient.PupperLogger;
import cn.pupperclient.management.command.impl.*;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModManager;
import cn.pupperclient.utils.chat.ChatUtils;
import cn.pupperclient.utils.minecraft.interfaces.IMinecraft;
import cn.pupperclient.utils.language.I18n;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import java.io.IOException;
import java.util.Objects;

public class PupperCommand implements IMinecraft {
    private static final String PREFIX = ".";
    private static final ModManager modManager = PupperClient.getInstance().getModManager();

    public static void register() {
        ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {
            if (message.startsWith(PREFIX)) {
                String command = message.substring(PREFIX.length()).trim();
                try {
                    runCommand(command);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return false;
            }
            return true;
        });
    }

    public static void runCommand(String command) throws IOException {
        String[] args = command.split(" ");

        if (args.length == 0) {
            showHelp();
        }

        String mainCommand = args[0].toLowerCase();

        switch (mainCommand) {
            case "t":
            case "toggle":
                if (args.length >= 2) {
                    String modName = args[1];
                    toggleMod(modName);
                } else {
                    ChatUtils.addChatMessage(I18n.get("command.help.toggle.usage"));
                }
                break;

            case "help":
                showHelp();
                break;

            case "l":
            case "list":
                listMods();
                break;

            case "b":
            case "bind":
                BindCommand.handleCommand(args);
                break;

            case "music", "m", "163":
                if (args.length >= 2) {
                    MusicCommand.handleCommand(args);
                } else {
                    MusicCommand.handleCommand(new String[]{"music", "help"});
                }
                break;

            case "login":
                if (args.length >= 2) {
                    LoginCommand.handleCommand(args);
                } else {
                    LoginCommand.handleCommand(new String[]{"login", "help"});
                }
                break;

            default:
                ChatUtils.addChatMessage(I18n.get("command.help.unknown"));
                break;
        }
    }

    private static void toggleMod(String modName) {
        Mod targetMod = modManager.getModByName("mod." + modName + ".name");
        if (targetMod == null) {
            ChatUtils.addChatMessage("§c" + I18n.get("mod.notFound") + ": " + modName);
            ChatUtils.addChatMessage("§6" + ".list " + " §7- " + I18n.get("command.help.modlist.description"));
            return;
        }

        boolean newState = !targetMod.isEnabled();
        modManager.setModEnabledByName(newState, targetMod.getName());

        String status = newState ? "§a" + I18n.get("mod.enabled") : "§c" + I18n.get("mod.disabled");
        ChatUtils.addChatMessage("Mod " + I18n.get(targetMod.getName()) + " " + status);
    }

    private static void showHelp() {
        ChatUtils.addChatMessage("§6=== " + I18n.get("command.help.title") + " ===");
        ChatUtils.addChatMessage("§b.t <modName> §7- " + I18n.get("command.help.toggle"));
        ChatUtils.addChatMessage("§b.toggle <modName> §7- " + I18n.get("command.help.toggle"));
        ChatUtils.addChatMessage("§b.list §7- " + I18n.get("command.help.list"));
        ChatUtils.addChatMessage("§b.help §7- " + I18n.get("command.help.help"));
        ChatUtils.addChatMessage("§6" + I18n.get("command.help.example") + " §b.t FPSDisplayMod");
    }

    private static void listMods() {
        if (modManager == null) {
            ChatUtils.addChatMessage(Component.literal("§c" + I18n.get("modManager.notInitialized")));
            return;
        }

        // 创建标题和刷新按钮
        MutableComponent title = Component.literal("=== " + I18n.get("command.help.modlist.title") + " ===")
            .withStyle(ChatFormatting.GOLD);

        MutableComponent refreshButton = createClickableText(" [" + I18n.get("command.help.modlist.refresh") + "]", ".list",
            I18n.get("command.help.modlist.refresh.tip"), ChatFormatting.GREEN);

        title.append(refreshButton);
        ChatUtils.addChatMessage(title);

        int enabledCount = 0;
        int totalCount = 0;

        for (Mod mod : modManager.getMods()) {
            totalCount++;
            if (mod.isEnabled()) {
                enabledCount++;
            }

            String modDisplayName = I18n.get(mod.getName());
            boolean isEnabled = mod.isEnabled();
            String shortModName = getShortModName(mod.getName());

            MutableComponent modNameText = Component.literal("• " + modDisplayName)
                .withStyle(ChatFormatting.AQUA);

            MutableComponent statusText;
            if (isEnabled) {
                statusText = createClickableText(I18n.get("mod.enabled"),
                    ".toggle " + shortModName,
                    I18n.get("modNameText.c") + " " + modDisplayName, ChatFormatting.GREEN);
            } else {
                statusText = createClickableText(I18n.get("mod.disabled"),
                    ".toggle " + shortModName,
                    I18n.get("modNameText.d") + " " + modDisplayName, ChatFormatting.RED);
            }

            MutableComponent modLine = Component.empty()
                .append(modNameText)
                .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                .append(statusText);

            ChatUtils.addChatMessage(modLine);
        }

        // 统计信息行
        MutableComponent stats = Component.literal(I18n.get("command.help.modlist.stats") + ": ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(enabledCount + "/" + totalCount + " " + I18n.get("mod.enabled"))
                .withStyle(enabledCount > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));

        ChatUtils.addChatMessage(stats);
    }

    private static MutableComponent createClickableText(String displayText, String command, String hoverText, ChatFormatting color) {
        ClickEvent clickEvent = new ClickEvent.SuggestCommand("." + command);
        HoverEvent hoverEvent = new HoverEvent.ShowText(Component.literal(hoverText));

        return Component.literal(displayText)
            .withStyle(color)
            .withStyle(style -> style
                .withClickEvent(clickEvent)
                .withHoverEvent(hoverEvent));
    }

    private static String getShortModName(String fullName) {
        if (fullName.startsWith("mod.") && fullName.endsWith(".name")) {
            return fullName.substring(4, fullName.length() - 5);
        }
        return fullName;
    }
}
