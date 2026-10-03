package cn.pupperclient.management.command.impl;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.music.Music;
import cn.pupperclient.management.music.MusicError;
import cn.pupperclient.management.music.MusicRequest;
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
                String keyword = String.join(" ", Arrays.copyOfRange(args, 2, end));
                if (action.equals("quick")) {
                    ChatUtils.addChatMessage("§6" + MusicText.get("music.status.searching"));
                    service().quick(keyword, track -> ChatUtils.addChatMessage("§6" + MusicText.get(
                        MusicRequest.quickAction(track) == MusicRequest.Action.DOWNLOAD ? "music.status.downloadtrack"
                            : "music.status.loadingtrack", track.title())),
                        result -> completed(result.action(), result.music()), MusicCommand::error);
                } else search(keyword, limit);
            }
            case "download", "play" -> {
                if (args.length < 3) { usage(action); return; }
                MusicRequest.Action request = action.equals("play") ? MusicRequest.Action.PLAY : MusicRequest.Action.DOWNLOAD;
                ChatUtils.addChatMessage("§6" + MusicText.get(request == MusicRequest.Action.PLAY ? "music.status.loading" : "music.status.downloading"));
                service().request(request, args[2], args.length > 3 ? args[3] : null,
                    music -> completed(request, music), MusicCommand::error);
            }
            default -> search(String.join(" ", Arrays.copyOfRange(args, 1, args.length)), 10);
        }
    }

    private static void search(String keyword, int limit) {
        ChatUtils.addChatMessage("§6" + MusicText.get("music.status.searching"));
        service().search(keyword, limit, 0, result -> {
            if (result.tracks().isEmpty()) {
                ChatUtils.addChatMessage("§7" + MusicText.get("music.empty.results"));
                return;
            }
            ChatUtils.addChatMessage("§6" + MusicText.get(result.total() < 0 ? "music.results.loaded" : "music.results.count",
                result.total() < 0 ? result.tracks().size() : result.total()));
            for (int i = 0; i < result.tracks().size(); i++) {
                MusicTrack track = result.tracks().get(i);
                boolean download = MusicRequest.quickAction(track) == MusicRequest.Action.DOWNLOAD;
                String command = ".music " + (download ? "download " : "play ") + track.key();
                Component row = Component.literal((i + 1) + ". " + track.title() + " — " + track.artist()
                    + (MusicText.access(track).isEmpty() ? "" : " · " + MusicText.access(track)))
                    .withStyle(ChatFormatting.AQUA)
                    .withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand(command))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(
                            (download ? MusicText.downloadAction(track) : MusicText.get("music.action.play"))
                                + " · " + MusicText.get("music.provider." + track.provider())))));
                ChatUtils.addChatMessage(row);
            }
        }, MusicCommand::error);
    }

    private static void completed(MusicRequest.Action action, Music music) {
        ChatUtils.addChatMessage("§a" + (action == MusicRequest.Action.DOWNLOAD
            ? MusicText.downloaded(music.getTrack()) : MusicText.playing(music.getTrack())));
    }

    private static void list() {
        List<Music> tracks = service().libraryTracks();
        ChatUtils.addChatMessage("§6" + MusicText.get("music.library.count", tracks.size()));
        for (int i = 0; i < tracks.size(); i++)
            ChatUtils.addChatMessage("§b" + (i + 1) + ". §f" + tracks.get(i).getTitle() + " §7" + MusicText.artist(tracks.get(i).getTrack()));
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
