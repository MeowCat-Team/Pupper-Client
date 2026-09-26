package cn.pupperclient.management.command.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cn.pupperclient.PupperClient;
import cn.pupperclient.utils.chat.ChatUtils;
import cn.pupperclient.utils.thread.Multithreading;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public class MusicCommand {

    private static final String SEARCH_API = "https://zm.wwoyun.cn/search";
    private static final String CHECK_API = "https://zm.wwoyun.cn/check/music";
    private static final String URL_API = "https://zm.wwoyun.cn/song/url/v1";

    private static final File MUSIC_DIR = new File("pupper/music");

    // 音质等级映射
    private static final String[] QUALITY_LEVELS = {
        "standard", "higher", "exhigh", "lossless", "hires",
        "jyeffect", "sky", "dolby", "jymaster"
    };

    public static List<String> getQualityLevels() {
        return List.of(QUALITY_LEVELS);
    }

    // 音质显示名称
    private static final String[] QUALITY_NAMES = {
        "标准", "较高", "极高", "无损", "Hi-Res",
        "高清环绕声", "沉浸环绕声", "杜比全景声", "超清母带"
    };

    public static void handleCommand(String[] args) {
        if (args.length == 1) {
            showHelp();
            return;
        }

        String subCommand = args[1].toLowerCase();

        switch (subCommand) {
            case "search":
                if (args.length >= 3) {
                    String keyword = buildKeyword(args, 2);
                    int limit = 10;
                    if (args.length >= 4) {
                        try {
                            limit = Integer.parseInt(args[3]);
                            limit = Math.min(Math.max(limit, 1), 50); // 限制1-50条
                        } catch (NumberFormatException e) {
                            ChatUtils.addChatMessage("§c无效的数量: " + args[3]);
                            return;
                        }
                    }
                    searchMusic(keyword, limit);
                } else {
                    ChatUtils.addChatMessage("§c用法: .music search <关键词> [数量]");
                }
                break;

            case "download":
                if (args.length >= 3) {
                    String songId = args[2];
                    String quality = "exhigh"; // 默认极高音质
                    if (args.length >= 4) {
                        quality = getQualityLevel(args[3]);
                        if (quality == null) {
                            ChatUtils.addChatMessage("§c无效的音质: " + args[3]);
                            ChatUtils.addChatMessage("§6可用音质: standard, higher, exhigh, lossless, hires, jyeffect, sky, dolby, jymaster");
                            return;
                        }
                    }
                    downloadMusic(songId, quality);
                } else {
                    ChatUtils.addChatMessage("§c用法: .music download <歌曲ID> [音质]");
                }
                break;

            case "quick":
                if (args.length >= 3) {
                    String keyword = buildKeyword(args, 2);
                    quickDownload(keyword);
                } else {
                    ChatUtils.addChatMessage("§c用法: .music quick <关键词>");
                }
                break;

            case "list":
                listDownloadedMusic();
                break;

            case "help":
                showHelp();
                break;

            default:
                // 如果没有子命令，默认搜索
                String keyword = buildKeyword(args, 1);
                searchMusic(keyword, 10);
                break;
        }
    }

    private static void searchMusic(String keyword, int limit) {
        ChatUtils.addChatMessage("§6搜索音乐: §b" + keyword);

        Multithreading.runAsync(() -> {
            try {
                String url = SEARCH_API + "?keywords=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8) + "&limit=" + limit + "&type=1";
                JsonObject result = sendGetRequest(url);

                if (result == null || !result.has("result") || !result.getAsJsonObject("result").has("songs")) {
                    ChatUtils.addChatMessage("§c搜索失败，请稍后重试");
                    return;
                }

                JsonArray songs = result.getAsJsonObject("result").getAsJsonArray("songs");
                if (songs.isEmpty()) {
                    ChatUtils.addChatMessage("§c未找到相关音乐");
                    return;
                }

                // 在主线程显示结果
                Multithreading.runMainThread(() -> {
                    ChatUtils.addChatMessage("§6=== 搜索结果 (" + songs.size() + "首) ===");

                    for (int i = 0; i < songs.size(); i++) {
                        JsonObject song = songs.get(i).getAsJsonObject();
                        String songId = song.get("id").getAsString();
                        String songName = song.get("name").getAsString();

                        // 获取歌手信息
                        JsonArray artists = song.getAsJsonArray("artists");
                        List<String> artistNames = new ArrayList<>();
                        for (JsonElement artist : artists) {
                            artistNames.add(artist.getAsJsonObject().get("name").getAsString());
                        }
                        String artistsStr = String.join(", ", artistNames);

                        // 获取专辑信息
                        String albumName;
                        if (song.has("album")) {
                            albumName = song.getAsJsonObject("album").get("name").getAsString();
                        } else {
                            albumName = "未知专辑";
                        }

                        // 格式化显示
                        MutableComponent songText = Component.literal("§b" + (i + 1) + ". §f" + songName)
                            .withStyle(style -> style
                                .withClickEvent(new ClickEvent.SuggestCommand(".music download " + songId))
                                .withHoverEvent(new HoverEvent.ShowText(
                                    Component.literal("§6点击快速下载\n§7歌手: " + artistsStr + "\n§7专辑: " + albumName))));

                        MutableComponent artistText = Component.literal(" §7- " + artistsStr);
                        MutableComponent downloadBtn = createClickableText(
                            ".music download " + songId,
                            "下载 " + songName);

                        MutableComponent fullLine = Component.empty()
                            .append(songText)
                            .append(artistText)
                            .append(downloadBtn);

                        ChatUtils.addChatMessage(fullLine);
                    }

                    ChatUtils.addChatMessage("§6提示: §7点击歌曲名或使用 §b.music download <ID> §7下载");
                    ChatUtils.addChatMessage("§7快速下载: §b.music quick <关键词>");
                });

            } catch (Exception e) {
                Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c搜索失败: " + e.getMessage()));
                PupperClient.LOGGER.error("音乐搜索失败: {}", e.getMessage(), e);
            }
        });
    }

    private static void downloadMusic(String songId, String quality) {
        Multithreading.runAsync(() -> {
            try {
                String checkUrl = CHECK_API + "?id=" + songId;
                JsonObject checkResult = sendGetRequest(checkUrl);

                if (checkResult == null || !checkResult.get("success").getAsBoolean()) {
                    String message = checkResult != null ? checkResult.get("message").getAsString() : "检查失败";
                    Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c音乐不可用: " + message));
                    return;
                }

                String urlApi = URL_API + "?id=" + songId + "&level=" + quality + "&cookie=os=pc";
                JsonObject urlResult = sendGetRequest(urlApi);

                if (urlResult == null || !urlResult.has("data")) {
                    Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c获取音乐URL失败"));
                    return;
                }

                JsonArray data = urlResult.getAsJsonArray("data");
                if (data.isEmpty() || data.get(0).getAsJsonObject().get("url") == null) {
                    Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c该音质暂不可用，请尝试其他音质"));
                    return;
                }

                String musicUrl = data.get(0).getAsJsonObject().get("url").getAsString();
                if (musicUrl == null || musicUrl.isEmpty()) {
                    Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c获取音乐链接失败"));
                    return;
                }

                String infoUrl = "https://ncm.zhenxin.me/song/detail?ids=" + songId;
                JsonObject infoResult = sendGetRequest(infoUrl);
                String fileName = "music_" + songId + ".mp3";

                if (infoResult != null && infoResult.has("songs") && !infoResult.getAsJsonArray("songs").isEmpty()) {
                    JsonObject song = infoResult.getAsJsonArray("songs").get(0).getAsJsonObject();
                    String songName = song.get("name").getAsString();

                    List<String> artistNames = new ArrayList<>();
                    if (song.has("ar")) {
                        JsonArray artists = song.getAsJsonArray("ar");
                        for (JsonElement artist : artists) {
                            JsonObject artistObj = artist.getAsJsonObject();
                            if (artistObj.has("name")) {
                                artistNames.add(artistObj.get("name").getAsString());
                            }
                        }
                    }

                    String artistsStr = artistNames.isEmpty() ? "未知歌手" : String.join(", ", artistNames);

                    fileName = cleanFileName(songName + " - " + artistsStr) + ".mp3";

                    // 在主线程显示下载信息
                    Multithreading.runMainThread(() -> {
                        ChatUtils.addChatMessage("§6开始下载: §b" + songName);
                        ChatUtils.addChatMessage("§7歌手: " + artistsStr);
                        ChatUtils.addChatMessage("§7音质: " + getQualityDisplayName(quality));
                    });
                } else {
                    Multithreading.runMainThread(() -> {
                        ChatUtils.addChatMessage("§6开始下载音乐 ID: §b" + songId);
                        ChatUtils.addChatMessage("§7音质: " + getQualityDisplayName(quality));
                    });
                }

                File outputFile = new File(MUSIC_DIR, fileName);
                if (!MUSIC_DIR.exists()) {
                    MUSIC_DIR.mkdirs();
                }

                boolean downloadSuccess = downloadFile(musicUrl, outputFile);

                String finalFileName = fileName;
                Multithreading.runMainThread(() -> {
                    if (downloadSuccess) {
                        ChatUtils.addChatMessage("§a下载完成: §b" + finalFileName);
                        ChatUtils.addChatMessage("§7保存位置: pupper/music/" + finalFileName);

                        // 显示文件大小
                        if (outputFile.exists()) {
                            String fileSize = formatFileSize(outputFile.length());
                            ChatUtils.addChatMessage("§7文件大小: " + fileSize);
                        }
                    } else {
                        ChatUtils.addChatMessage("§c下载失败: " + finalFileName);
                    }
                });

            } catch (Exception e) {
                Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c下载失败: " + e.getMessage()));
                PupperClient.LOGGER.error("音乐下载失败: {}", e.getMessage(), e);
            }
        });
    }

    private static void quickDownload(String keyword) {
        ChatUtils.addChatMessage("§6快速下载: §b" + keyword);

        Multithreading.runAsync(() -> {
            try {
                // 搜索获取第一首歌曲
                String searchUrl = SEARCH_API + "?keywords=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8) + "&limit=1&type=1";
                JsonObject result = sendGetRequest(searchUrl);

                if (result == null || !result.has("result") || !result.getAsJsonObject("result").has("songs")) {
                    Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c搜索失败，无法快速下载"));
                    return;
                }

                JsonArray songs = result.getAsJsonObject("result").getAsJsonArray("songs");
                if (songs.isEmpty()) {
                    Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c未找到相关音乐"));
                    return;
                }

                String songId = songs.get(0).getAsJsonObject().get("id").getAsString();

                // 使用默认音质下载
                downloadMusic(songId, "exhigh");

            } catch (Exception e) {
                Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c快速下载失败: " + e.getMessage()));
                PupperClient.LOGGER.error("快速下载失败: {}", e.getMessage(), e);
            }
        });
    }

    private static void listDownloadedMusic() {
        if (!MUSIC_DIR.exists() || !MUSIC_DIR.isDirectory()) {
            ChatUtils.addChatMessage("§c音乐目录不存在或为空");
            return;
        }

        File[] musicFiles = MUSIC_DIR.listFiles((_, name) ->
            name.toLowerCase().endsWith(".mp3") || name.toLowerCase().endsWith(".flac"));

        if (musicFiles == null || musicFiles.length == 0) {
            ChatUtils.addChatMessage("§7暂无已下载的音乐");
            return;
        }

        ChatUtils.addChatMessage("§6=== 已下载音乐 (" + musicFiles.length + "首) ===");

        for (int i = 0; i < musicFiles.length; i++) {
            File musicFile = musicFiles[i];
            String fileName = musicFile.getName();
            String displayName = fileName.substring(0, fileName.lastIndexOf('.'));

            MutableComponent fileText = Component.literal("§b" + (i + 1) + ". §f" + displayName)
                .withStyle(style -> style
                    .withHoverEvent(new HoverEvent.ShowText(
                        Component.literal("§6文件: " + fileName + "\n§7大小: " + formatFileSize(musicFile.length())))));

            ChatUtils.addChatMessage(fileText);
        }
    }

    private static void showHelp() {
        ChatUtils.addChatMessage("§6=== 音乐下载命令 ===");
        ChatUtils.addChatMessage("§b.music search <关键词> [数量] §7- 搜索音乐");
        ChatUtils.addChatMessage("§b.music download <歌曲ID> [音质] §7- 下载指定歌曲");
        ChatUtils.addChatMessage("§b.music quick <关键词> §7- 快速下载（搜索并下载第一首）");
        ChatUtils.addChatMessage("§b.music list §7- 查看已下载音乐");
        ChatUtils.addChatMessage("§b.music help §7- 显示帮助");
        ChatUtils.addChatMessage("§6音质选项: §7standard(标准), higher(较高), exhigh(极高), lossless(无损)");
        ChatUtils.addChatMessage("§7         hires(Hi-Res), jyeffect(高清环绕), sky(沉浸环绕), dolby(杜比)");
        ChatUtils.addChatMessage("§6示例:");
        ChatUtils.addChatMessage("§7  .music search 周杰伦 §8- 搜索周杰伦的歌");
        ChatUtils.addChatMessage("§7  .music download 33894312 lossless §8- 下载指定ID的无损音乐");
        ChatUtils.addChatMessage("§7  .music quick 海阔天空 §8- 快速下载海阔天空");
    }

    // ========== 工具方法 ==========

    private static String buildKeyword(String[] args, int startIndex) {
        StringBuilder keyword = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) keyword.append(" ");
            keyword.append(args[i]);
        }
        return keyword.toString();
    }

    private static String getQualityLevel(String input) {
        for (int i = 0; i < QUALITY_LEVELS.length; i++) {
            if (QUALITY_LEVELS[i].equalsIgnoreCase(input) ||
                QUALITY_NAMES[i].equals(input)) {
                return QUALITY_LEVELS[i];
            }
        }
        return null;
    }

    private static String getQualityDisplayName(String level) {
        for (int i = 0; i < QUALITY_LEVELS.length; i++) {
            if (QUALITY_LEVELS[i].equals(level)) {
                return QUALITY_NAMES[i];
            }
        }
        return level;
    }

    private static JsonObject sendGetRequest(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            return null;
        }

        try (InputStream inputStream = connection.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            return JsonParser.parseString(response.toString()).getAsJsonObject();
        }
    }

    private static boolean downloadFile(String fileUrl, File outputFile) {
        try {
            URL url = new URL(fileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            try (InputStream inputStream = connection.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(outputFile)) {

                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalRead = 0;
                int fileSize = connection.getContentLength();

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    if (fileSize > 0 && totalRead % (1024 * 1024) == 0) {
                        int progress = (int) ((totalRead * 100) / fileSize);
                        ChatUtils.addChatMessage("§7下载进度: " + progress + "%");
                    }
                }
            }

            return true;
        } catch (Exception e) {
            PupperClient.LOGGER.error("文件下载失败: {}", e.getMessage(), e);
            return false;
        }
    }

    private static String cleanFileName(String fileName) {
        // 移除文件名中的非法字符
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    private static MutableComponent createClickableText(String command, String hoverText) {
        return Component.literal(" [下载]")
            .withStyle(ChatFormatting.GREEN)
            .withStyle(style -> style
                .withClickEvent(new ClickEvent.SuggestCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(
                    Component.literal(hoverText).withStyle(ChatFormatting.GRAY))));
    }
}
