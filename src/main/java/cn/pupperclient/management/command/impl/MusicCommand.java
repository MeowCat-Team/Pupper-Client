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
    public static List<String> getQualityLevels() { return service().qualities(); }
    public static List<String> getQualityLevels(String reference) { return service().qualities(reference); }
    public static List<String> getProviders() { return service().providers().stream().map(p -> p.id()).toList(); }
    private static MusicService service() { return PupperClient.getInstance().getMusicManager().getService(); }

    public static void handleCommand(String[] args) {
        if (args.length <= 1) { help(); return; }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "help" -> help();
            case "list" -> list();
            case "provider" -> {
                if (args.length < 3) {
                    for (var provider : service().providers()) ChatUtils.addChatMessage("§b" + provider.id() + " §7· "
                        + MusicText.get(provider.nameKey()) + (provider == service().provider() ? " ✓" : ""));
                    return;
                }
                try {
                    service().selectProvider(args[2]);
                    ChatUtils.addChatMessage("§a" + MusicText.get("music.provider.selected", MusicText.get(service().provider().nameKey())));
                } catch (MusicError failure) { error(failure); }
                catch (java.io.IOException failure) { error(new MusicError("music.error.file")); }
            }
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
            case "download", "play" -> {
                if (args.length < 3 || !args[2].matches("(?:[a-zA-Z]+:)?[a-zA-Z0-9_-]{1,80}")) { usage(action); return; }
                String requestedQuality = args.length > 3 ? quality(args[3]) : null;
                if (args.length > 3 && (requestedQuality == null || !getQualityLevels(args[2]).contains(requestedQuality))) {
                    usage(action); return;
                }
                boolean play = action.equals("play");
                ChatUtils.addChatMessage("§6" + MusicText.get(play ? "music.status.loading" : "music.status.downloading"));
                service().resolve(args[2], track -> {
                    String quality = requestedQuality == null ? service().defaultQuality(track) : requestedQuality;
                    if (play) service().play(track, quality, music -> ChatUtils.addChatMessage("§a"
                        + MusicText.get("music.status.playing", music.getTitle())), MusicCommand::error);
                    else service().download(track, quality, false, MusicCommand::downloaded, MusicCommand::error);
                }, MusicCommand::error);
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
                ChatUtils.addChatMessage("§6" + MusicText.get(first.provider().equals("netease")
                    ? "music.status.downloadtrack" : "music.status.loadingtrack", first.title()));
                if (first.provider().equals("netease")) service().download(first, "exhigh", false,
                    MusicCommand::downloaded, MusicCommand::error);
                else service().play(first, "standard", music -> ChatUtils.addChatMessage("§a"
                    + MusicText.get("music.status.playing", music.getTitle())), MusicCommand::error);
                return;
            }
            ChatUtils.addChatMessage("§6" + MusicText.get(result.total() < 0 ? "music.results.loaded" : "music.results.count",
                result.total() < 0 ? result.tracks().size() : result.total()));
            for (int i = 0; i < result.tracks().size(); i++) {
                MusicTrack track = result.tracks().get(i);
                String command = ".music " + (track.provider().equals("netease") ? "download " : "play ") + track.key();
                Component row = Component.literal((i + 1) + ". " + track.title() + " — " + track.artist())
                    .withStyle(ChatFormatting.AQUA)
                    .withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(
                            MusicText.get(track.provider().equals("netease") ? "music.action.download" : "music.action.play")
                                + " · " + MusicText.get("music.provider." + track.provider())))));
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
        for (String action : List.of("provider", "search", "play", "download", "quick", "list", "help"))
            ChatUtils.addChatMessage("§b" + MusicText.get("music.command." + action));
    }

    private static void error(MusicError error) {
        ChatUtils.addChatMessage("§c" + MusicText.get(error.key()));
    }
}
