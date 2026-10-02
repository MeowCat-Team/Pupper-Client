package cn.pupperclient.management.command.impl;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.music.Music;
import cn.pupperclient.management.music.MusicError;
import cn.pupperclient.management.music.MusicService;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.management.music.MusicTrack;
import cn.pupperclient.utils.chat.ChatUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/** Commands use the same metadata, download directory and authenticated API as the player. */
public class MusicCommand {
    public static List<String> getQualityLevels() { return MusicService.QUALITIES; }
    private static MusicService service() { return PupperClient.getInstance().getMusicManager().getService(); }

    public static void handleCommand(String[] args) {
        if (args.length <= 1) { help(); return; }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "help" -> help();
            case "list" -> list();
            case "search", "quick" -> {
                if (args.length < 3) { usage(action); return; }
                int end = args.length;
                int limit = action.equals("quick") ? 1 : 10;
                if (action.equals("search") && args.length > 3 && args[end - 1].matches("\\d+")) {
                    try { limit = Math.clamp(Integer.parseInt(args[--end]), 1, 50); }
                    catch (NumberFormatException invalid) { usage(action); return; }
                }
                search(String.join(" ", Arrays.copyOfRange(args, 2, end)), limit, action.equals("quick"));
            }
            case "download" -> {
                if (args.length < 3 || !args[2].matches("\\d+")) { usage(action); return; }
                String quality = args.length > 3 ? quality(args[3]) : "exhigh";
                if (quality == null) { usage(action); return; }
                try {
                    long id = Long.parseLong(args[2]);
                    if (id <= 0) { usage(action); return; }
                    ChatUtils.addChatMessage("§6" + MusicText.get("music.status.downloading"));
                    service().download(id, quality, false, MusicCommand::downloaded, MusicCommand::error);
                } catch (NumberFormatException invalid) { usage(action); }
            }
            default -> search(String.join(" ", Arrays.copyOfRange(args, 1, args.length)), 10, false);
        }
    }

    private static String quality(String input) {
        for (String quality : MusicService.QUALITIES)
            if (quality.equalsIgnoreCase(input) || MusicText.get("music.quality." + quality).equals(input)) return quality;
        return null;
    }

    private static void search(String keyword, int limit, boolean quick) {
        ChatUtils.addChatMessage("§6" + MusicText.get("music.status.searching"));
        service().search(keyword, limit, 0, result -> {
            if (result.tracks().isEmpty()) {
                ChatUtils.addChatMessage("§7" + MusicText.get("music.empty.results"));
                return;
            }
            if (quick) {
                // Preserve the already known title even if /song/detail is temporarily unavailable.
                MusicTrack first = result.tracks().getFirst();
                ChatUtils.addChatMessage("§6" + MusicText.get("music.status.downloadtrack", first.title()));
                service().download(first, "exhigh", false, MusicCommand::downloaded, MusicCommand::error);
                return;
            }
            ChatUtils.addChatMessage("§6" + MusicText.get("music.results.count", result.total()));
            for (int i = 0; i < result.tracks().size(); i++) {
                MusicTrack track = result.tracks().get(i);
                String command = ".music download " + track.id();
                Component row = Component.literal((i + 1) + ". " + track.title() + " — " + track.artist())
                    .withStyle(ChatFormatting.AQUA)
                    .withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(
                            MusicText.get("music.action.download") + " · " + track.album()))));
                ChatUtils.addChatMessage(row);
            }
        }, MusicCommand::error);
    }

    private static void downloaded(Music music) {
        ChatUtils.addChatMessage("§a" + MusicText.get("music.status.downloaded", music.getTitle()));
    }

    private static void list() {
        List<Music> tracks = PupperClient.getInstance().getMusicManager().getMusics();
        ChatUtils.addChatMessage("§6" + MusicText.get("music.library.count", tracks.size()));
        for (int i = 0; i < tracks.size(); i++)
            ChatUtils.addChatMessage("§b" + (i + 1) + ". §f" + tracks.get(i).getTitle() + " §7" + tracks.get(i).getArtist());
    }

    private static void usage(String action) {
        ChatUtils.addChatMessage("§c" + MusicText.get("music.command.usage", MusicText.get("music.command." + action)));
    }

    private static void help() {
        ChatUtils.addChatMessage("§6" + MusicText.get("music.command.title"));
        for (String action : List.of("search", "download", "quick", "list", "help"))
            ChatUtils.addChatMessage("§b" + MusicText.get("music.command." + action));
    }

    private static void error(MusicError error) {
        ChatUtils.addChatMessage("§c" + MusicText.get(error.key()));
    }
}
