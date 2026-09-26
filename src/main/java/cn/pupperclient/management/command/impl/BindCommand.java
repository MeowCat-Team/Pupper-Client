package cn.pupperclient.management.command.impl;

import cn.pupperclient.PupperClient;
import cn.pupperclient.event.EventBus;
import cn.pupperclient.event.client.KeyEvent;
import cn.pupperclient.management.config.ConfigType;
import cn.pupperclient.management.keybind.KeybindManager;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModManager;
import cn.pupperclient.utils.chat.ChatUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public class BindCommand {
    private static final ModManager modManager = PupperClient.getInstance().getModManager();
    private static final Map<String, Object> keyListeners = new HashMap<>();
    private static final Map<String, Integer> KEY_CODES = createKeyCodes();

    public static void handleCommand(String[] args) {
        if (args.length == 1) {
            // 显示绑定帮助
            showBindHelp();
            return;
        }

        String subCommand = args[1].toLowerCase();

        switch (subCommand) {
            case "list":
                listKeybinds();
                break;

            case "clear":
            case "reset":
                if (args.length >= 3) {
                    String modName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    clearKeybind(modName);
                } else {
                    // 清除所有按键绑定
                    clearAllKeybinds();
                }
                break;

            default:
                // 绑定特定模组
                String fullName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                if (args.length == 2 || modManager.getModByCommandName(fullName) != null) {
                    // 进入按键监听模式
                    startKeyListening(fullName);
                } else {
                    String modName = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
                    String keyName = args[args.length - 1];
                    setKeybind(modName, keyName);
                }
                break;
        }
    }

    private static void startKeyListening(String modName) {
        Mod mod = modManager.getModByCommandName(modName);

        if (mod == null) {
            ChatUtils.addChatMessage("§cMod not found: " + modName);
            ChatUtils.addChatMessage("§6Use .list to see available mods");
            return;
        }

        // 取消之前的监听器（如果有）
        if (keyListeners.containsKey(mod.getRawName())) {
            EventBus.getInstance().unregister(keyListeners.get(mod.getRawName()));
            keyListeners.remove(mod.getRawName());
        }

        ChatUtils.addChatMessage("§ePress a key to bind §6" + mod.getName() + "§e to (Press ESC to cancel)");

        Object listener = getListener(mod);

        keyListeners.put(mod.getRawName(), listener);
        EventBus.getInstance().register(listener);
    }

    private static void setKeybind(String modName, String keyName) {
        Mod mod = modManager.getModByCommandName(modName);

        if (mod == null) {
            ChatUtils.addChatMessage("§cMod not found: " + modName);
            return;
        }

        if (keyName.equalsIgnoreCase("none") || keyName.equalsIgnoreCase("clear")) {
            mod.setKeybind(0);
            ChatUtils.addChatMessage("§aCleared keybind for §6" + mod.getName());
            PupperClient.getInstance().getConfigManager().save(ConfigType.KEY);
            return;
        }

        try {
            int keyCode = parseKeyCode(keyName);
            if (keyCode == -1) {
                ChatUtils.addChatMessage("§cInvalid key: " + keyName);
                ChatUtils.addChatMessage("§6Use .bind list to see key names");
                return;
            }

            mod.setKeybind(keyCode);
            ChatUtils.addChatMessage("§aBound §6" + mod.getName() + "§a to §b" + getKeyName(keyCode));
            PupperClient.getInstance().getConfigManager().save(ConfigType.KEY);

        } catch (Exception e) {
            ChatUtils.addChatMessage("§cError setting keybind: " + e.getMessage());
        }
    }

    private static void listKeybinds() {
        ChatUtils.addChatMessage("§6=== Current Keybinds ===");

        KeybindManager keybindManager = PupperClient.getInstance().getKeybindManager();
        int boundCount = 0;

        // 遍历所有按键
        for (Map.Entry<Integer, List<Mod>> entry : keybindManager.keybindMap.entrySet()) {
            int keyCode = entry.getKey();
            List<Mod> mods = entry.getValue();
            String keyName = getKeyName(keyCode);

            for (Mod mod : mods) {
                MutableComponent message = Component.literal("§b• " + mod.getName() + " §7→ §a" + keyName + " §7(keycode: " + keyCode + ")")
                    .withStyle(style -> style
                        .withClickEvent(new ClickEvent.SuggestCommand(".bind " + mod.getRawName() + " none"))
                        .withHoverEvent(new HoverEvent.ShowText(
                            Component.literal("Click to clear this keybind").withStyle(ChatFormatting.GRAY)
                        ))
                    );

                ChatUtils.addChatMessage(message);
                boundCount++;
            }
        }

        if (boundCount == 0) {
            ChatUtils.addChatMessage("§7No keybinds configured");
        } else {
            ChatUtils.addChatMessage("§7Total: " + boundCount + " keybind(s)");
        }

        ChatUtils.addChatMessage("§6Usage: §b.bind <mod> [key] §7- Set keybind");
        ChatUtils.addChatMessage("§6       §b.bind clear [mod] §7- Clear keybind(s)");
    }

    private static void clearKeybind(String modName) {
        Mod mod = modManager.getModByCommandName(modName);

        if (mod == null) {
            ChatUtils.addChatMessage("§cMod not found: " + modName);
            return;
        }

        if (mod.getKeybind() == 0) {
            ChatUtils.addChatMessage("§7No keybind set for " + mod.getName());
            return;
        }

        String oldKeyName = getKeyName(mod.getKeybind());
        mod.setKeybind(0);
        PupperClient.getInstance().getConfigManager().save(ConfigType.KEY);
        ChatUtils.addChatMessage("§aCleared keybind for §6" + mod.getName() + "§a (was: " + oldKeyName + ")");
    }

    private static @NotNull Object getListener(Mod mod) {
        return new Object() {
            public final EventBus.EventListener<KeyEvent> onKey = e -> {
                if (e.isState()) {
                    if (e.getKeybind() == 256) {
                        ChatUtils.addChatMessage("§cKey binding cancelled");
                        cleanupListener(mod.getRawName());
                        return;
                    }

                    // 设置按键绑定
                    mod.setKeybind(e.getKeybind());
                    String keyName = getKeyName(e.getKeybind());

                    ChatUtils.addChatMessage("§aBound §6" + mod.getName() + "§a to §b" + keyName + "§a (keycode: " + e.getKeybind() + ")");

                    // 保存配置
                    PupperClient.getInstance().getConfigManager().save(ConfigType.KEY);

                    // 清理监听器
                    cleanupListener(mod.getRawName());
                }
            };
        };
    }


    private static void clearAllKeybinds() {
        int clearedCount = 0;
        for (Mod mod : modManager.getMods()) {
            if (mod.getKeybind() != 0) {
                mod.setKeybind(0);
                clearedCount++;
            }
        }

        PupperClient.getInstance().getConfigManager().save(ConfigType.KEY);
        ChatUtils.addChatMessage("§aCleared all keybinds (" + clearedCount + " mods)");
    }


    private static void showBindHelp() {
        ChatUtils.addChatMessage("§6=== Keybind Commands ===");
        ChatUtils.addChatMessage("§b.bind <mod> §7- Enter key listening mode for a mod");
        ChatUtils.addChatMessage("§b.bind <mod> <key> §7- Set keybind directly");
        ChatUtils.addChatMessage("§b.bind list §7- Show all current keybinds");
        ChatUtils.addChatMessage("§b.bind clear §7- Clear all keybinds");
        ChatUtils.addChatMessage("§b.bind clear <mod> §7- Clear keybind for specific mod");
        ChatUtils.addChatMessage("§6Examples:");
        ChatUtils.addChatMessage("§7  .bind FPSDisplayMod §8- Listen for key press");
        ChatUtils.addChatMessage("§7  .bind FPSDisplayMod R §8- Bind to R key");
        ChatUtils.addChatMessage("§7  .bind FPSDisplayMod none §8- Clear binding");
    }

    public static Set<String> getSupportedKeys() {
        return KEY_CODES.keySet();
    }

    private static int parseKeyCode(String keyName) {
        return KEY_CODES.getOrDefault(keyName.toLowerCase(Locale.ROOT), -1);
    }

    private static Map<String, Integer> createKeyCodes() {
        // 常见按键映射
        Map<String, Integer> keyMap = new HashMap<>();
        keyMap.put("esc", 256); keyMap.put("escape", 256);
        keyMap.put("space", 32); keyMap.put("tab", 258);
        keyMap.put("enter", 257); keyMap.put("return", 257);
        keyMap.put("backspace", 259); keyMap.put("delete", 261);
        keyMap.put("insert", 260); keyMap.put("home", 268);
        keyMap.put("end", 269); keyMap.put("pageup", 266);
        keyMap.put("pagedown", 267);
        keyMap.put("up", 265); keyMap.put("down", 264);
        keyMap.put("left", 263); keyMap.put("right", 262);
        keyMap.put("lshift", 340); keyMap.put("rshift", 344);
        keyMap.put("lctrl", 341); keyMap.put("rctrl", 345);
        keyMap.put("lalt", 342); keyMap.put("ralt", 346);

        // 功能键
        for (int i = 1; i <= 12; i++) {
            keyMap.put("f" + i, 289 + i);
        }

        // 字母键
        for (char c = 'A'; c <= 'Z'; c++) {
            keyMap.put(String.valueOf(c).toLowerCase(), (int) c);
        }

        // 数字键
        for (int i = 0; i <= 9; i++) {
            keyMap.put(String.valueOf(i), 48 + i);
        }

        return Map.copyOf(keyMap);
    }

    private static String getKeyName(int keyCode) {
        if (keyCode == 0) return "NONE";

        return switch (keyCode) {
            case 32 -> "SPACE";
            case 256 -> "ESC";
            case 257 -> "ENTER";
            case 258 -> "TAB";
            case 259 -> "BACKSPACE";
            case 260 -> "INSERT";
            case 261 -> "DELETE";
            case 262 -> "RIGHT";
            case 263 -> "LEFT";
            case 264 -> "DOWN";
            case 265 -> "UP";
            case 340 -> "LSHIFT";
            case 341 -> "LCTRL";
            case 342 -> "LALT";
            case 344 -> "RSHIFT";
            case 345 -> "RCTRL";
            case 346 -> "RALT";
            default -> {
                if (keyCode >= 65 && keyCode <= 90) {
                    yield Character.toString((char) keyCode);
                } else if (keyCode >= 48 && keyCode <= 57) {
                    yield "NUM_" + (keyCode - 48);
                } else if (keyCode >= 290 && keyCode <= 301) {
                    yield "F" + (keyCode - 289);
                }
                yield "KEY_" + keyCode;
            }
        };
    }

    private static void cleanupListener(String modName) {
        if (keyListeners.containsKey(modName)) {
            EventBus.getInstance().unregister(keyListeners.get(modName));
            keyListeners.remove(modName);
        }
    }
}
