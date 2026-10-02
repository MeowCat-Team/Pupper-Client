package cn.pupperclient.management.command.impl;

import cn.pupperclient.PupperClient;
import cn.pupperclient.PupperLogger;
import cn.pupperclient.utils.chat.ChatUtils;
import cn.pupperclient.utils.thread.Multithreading;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.NonNull;

public class LoginCommand {

    private static final String BASE_API = "https://zm.wwoyun.cn/";
    private static volatile String currentCookie = null;
    private static volatile String currentUserId = null;
    private static boolean loginStatusLoaded;
    private static String currentNickname = null;
    private static String currentPhone = null; // 保存当前登录的手机号

    // 保存登录状态的文件
    private static final File LOGIN_FILE = new File(cn.pupperclient.utils.file.FileLocation.MAIN_DIR, "login_status.json");

    public static void handleCommand(String[] args) {
        if (args.length == 1) {
            showHelp();
            return;
        }

        String subCommand = args[1].toLowerCase();

        switch (subCommand) {
            case "send":
                if (args.length >= 3) {
                    String phone = args[2];
                    sendCaptcha(phone);
                } else {
                    ChatUtils.addChatMessage("§c用法: .login send <手机号>");
                }
                break;

            case "phone":
                if (args.length >= 4) {
                    String phone = args[2];
                    String captcha = args[3];
                    phoneLoginWithCaptcha(phone, captcha);
                } else {
                    ChatUtils.addChatMessage("§c用法: .login phone <手机号> <验证码>");
                }
                break;

            case "qr":
                startQRLogin();
                break;

            case "status":
                checkLoginStatus();
                break;

            case "logout":
                logout();
                break;

            case "refresh":
                refreshLogin();
                break;

            case "help":
                showHelp();
                break;

            default:
                ChatUtils.addChatMessage("§c未知的子命令，使用 .login help 查看帮助");
                break;
        }
    }

    /**
     * 发送验证码
     */
    private static void sendCaptcha(String phone) {
        ChatUtils.addChatMessage("§6正在发送验证码到: §b" + phone);

        Multithreading.runAsync(() -> {
            try {
                String url = BASE_API + "/captcha/sent?phone=" + phone;
                PupperLogger.info("发送验证码请求URL: {}", url);

                JsonObject result = sendGetRequest(url);

                if (result == null || result.has("code") && result.get("code").getAsInt() != 200) {
                    String errorMsg = result != null && result.has("message") ?
                        result.get("message").getAsString() : "发送验证码失败";
                    Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c发送验证码失败: " + errorMsg));
                    return;
                }

                Multithreading.runMainThread(() -> {
                    ChatUtils.addChatMessage("§a验证码已发送到您的手机！");
                    ChatUtils.addChatMessage("§7请使用 §b.login phone " + phone + " <验证码> §7登录");
                });

            } catch (Exception e) {
                Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c发送验证码失败: " + e.getMessage()));
                PupperClient.LOGGER.error("发送验证码失败: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 手机号+验证码登录
     */
    private static void phoneLoginWithCaptcha(String phone, String captcha) {
        ChatUtils.addChatMessage("§6正在使用验证码登录...");

        Multithreading.runAsync(() -> {
            try {
                String url = BASE_API + "/login/cellphone?phone=" + phone +
                    "&captcha=" + captcha;

                PupperLogger.info("登录请求URL: {}", url);

                JsonObject result = sendPostRequest(url);

                if (result == null || result.has("code") && result.get("code").getAsInt() != 200) {
                    String errorMsg = result != null && result.has("message") ?
                        result.get("message").getAsString() : "登录失败";
                    Multithreading.runMainThread(() -> {
                        ChatUtils.addChatMessage("§c验证码登录失败: " + errorMsg);

                        // 提供重新发送验证码的快捷方式
                        MutableComponent retryText = Component.literal("§7[重新发送验证码]")
                            .withStyle(style -> style
                                .withClickEvent(new ClickEvent.SuggestCommand(".login send " + phone))
                                .withHoverEvent(new HoverEvent.ShowText(
                                    Component.literal("点击重新发送验证码"))));
                        ChatUtils.addChatMessage(retryText);
                    });
                    return;
                }

                // 登录成功，保存cookie和用户信息
                if (result.has("cookie")) {
                    currentCookie = result.get("cookie").getAsString();
                    currentPhone = phone;

                    if (result.has("account")) {
                        JsonObject account = result.getAsJsonObject("account");
                        currentUserId = account.get("id").getAsString();
                    }
                    if (result.has("profile")) {
                        JsonObject profile = result.getAsJsonObject("profile");
                        currentNickname = profile.get("nickname").getAsString();
                    }

                    saveLoginStatus();

                    Multithreading.runMainThread(() -> {
                        ChatUtils.addChatMessage("§a验证码登录成功！");
                        if (currentNickname != null) {
                            ChatUtils.addChatMessage("§7欢迎: §b" + currentNickname);
                        }
                        ChatUtils.addChatMessage("§7用户ID: §b" + currentUserId);
                    });
                }

            } catch (Exception e) {
                Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c登录过程出错: " + e.getMessage()));
                PupperClient.LOGGER.error("验证码登录失败: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 二维码登录
     */
    private static void startQRLogin() {
        ChatUtils.addChatMessage("§6正在生成二维码...");

        Multithreading.runAsync(() -> {
            try {
                // 1. 获取二维码key
                String keyUrl = BASE_API + "/login/qr/key";
                JsonObject keyResult = sendGetRequest(keyUrl);

                if (keyResult == null || !keyResult.has("data") || keyResult.getAsJsonObject("data").get("code").getAsInt() != 200) {
                    Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c获取二维码key失败"));
                    return;
                }

                String qrKey = keyResult.getAsJsonObject("data").get("unikey").getAsString();

                // 2. 生成二维码
                String qrUrl = BASE_API + "/login/qr/create?key=" + qrKey + "&qrimg=1&timestamp=" + System.currentTimeMillis();
                JsonObject qrResult = sendGetRequest(qrUrl);

                if (qrResult == null || !qrResult.has("data")) {
                    Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c生成二维码失败"));
                    return;
                }

                String qrImageUrl = qrResult.getAsJsonObject("data").get("qrimg").getAsString();

                Multithreading.runMainThread(() -> {
                    ChatUtils.addChatMessage("§6请使用网易云音乐APP扫描以下二维码:");
                    ChatUtils.addChatMessage("§b" + qrImageUrl);
                    ChatUtils.addChatMessage("§7正在等待扫描...");
                });

                // 3. 轮询检查扫码状态
                checkQRStatus(qrKey);

            } catch (Exception e) {
                Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c二维码登录失败: " + e.getMessage()));
                PupperClient.LOGGER.error("二维码登录失败: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 轮询检查二维码状态
     */
    private static void checkQRStatus(String qrKey) {
        Multithreading.runAsync(() -> {
            try {
                int attempts = 0;
                while (attempts < 60) { // 最多尝试60次，每次间隔3秒
                    Thread.sleep(3000);

                    String checkUrl = BASE_API + "/login/qr/check?key=" + qrKey + "&timestamp=" + System.currentTimeMillis();
                    JsonObject checkResult = sendGetRequest(checkUrl);

                    if (checkResult == null || !checkResult.has("code")) {
                        attempts++;
                        continue;
                    }

                    int code = checkResult.get("code").getAsInt();

                    if (code == 800) {
                        Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c二维码已过期，请重新生成"));
                        return;
                    } else if (code == 801) {
                        // 等待扫码，继续轮询
                        attempts++;
                        continue;
                    } else if (code == 802) {
                        Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§6已扫描，请在手机上确认登录"));
                        attempts++;
                        continue;
                    } else if (code == 803) {
                        // 登录成功
                        if (checkResult.has("cookie")) {
                            currentCookie = checkResult.get("cookie").getAsString();

                            // 获取用户信息
                            getUserProfile();
                            saveLoginStatus();

                            Multithreading.runMainThread(() -> {
                                ChatUtils.addChatMessage("§a二维码登录成功！");
                                if (currentNickname != null) {
                                    ChatUtils.addChatMessage("§7欢迎: §b" + currentNickname);
                                }
                            });
                        }
                        return;
                    }

                    attempts++;
                }

                Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c二维码登录超时，请重试"));

            } catch (Exception e) {
                Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c检查二维码状态失败: " + e.getMessage()));
                PupperClient.LOGGER.error("检查二维码状态失败: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 获取用户信息
     */
    private static void getUserProfile() {
        try {
            String profileUrl = BASE_API + "/user/account";
            JsonObject profileResult = sendGetRequestWithCookie(profileUrl);

            if (profileResult != null && profileResult.has("account")) {
                JsonObject account = profileResult.getAsJsonObject("account");
                currentUserId = account.get("id").getAsString();

                // 获取昵称
                String detailUrl = BASE_API + "/user/detail?uid=" + currentUserId;
                JsonObject detailResult = sendGetRequestWithCookie(detailUrl);

                if (detailResult != null && detailResult.has("profile")) {
                    JsonObject profile = detailResult.getAsJsonObject("profile");
                    currentNickname = profile.get("nickname").getAsString();
                }
            }
        } catch (Exception e) {
            PupperClient.LOGGER.error("获取用户信息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 检查登录状态
     */
    private static void checkLoginStatus() {
        loadLoginStatus(); // 先尝试加载保存的登录状态

        if (currentCookie == null) {
            ChatUtils.addChatMessage("§c未登录");
            return;
        }

        Multithreading.runAsync(() -> {
            try {
                String statusUrl = BASE_API + "/login/status";
                JsonObject result = sendGetRequestWithCookie(statusUrl);

                if (result != null && result.has("data")) {
                    JsonObject data = result.getAsJsonObject("data");
                    if (data.get("code").getAsInt() == 200) {
                        Multithreading.runMainThread(() -> {
                            ChatUtils.addChatMessage("§a登录状态: 已登录");
                            if (currentNickname != null) {
                                ChatUtils.addChatMessage("§7用户: §b" + currentNickname);
                            }
                            if (currentUserId != null) {
                                ChatUtils.addChatMessage("§7用户ID: §b" + currentUserId);
                            }
                            if (currentPhone != null) {
                                ChatUtils.addChatMessage("§7手机号: §b" + currentPhone);
                            }
                        });
                    } else {
                        Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c登录状态: 已过期"));
                        currentCookie = null;
                        currentUserId = null;
                        currentNickname = null;
                        currentPhone = null;
                        saveLoginStatus();
                    }
                } else {
                    Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c登录状态检查失败"));
                }

            } catch (Exception e) {
                Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c检查登录状态失败: " + e.getMessage()));
                PupperClient.LOGGER.error("检查登录状态失败: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 刷新登录状态
     */
    private static void refreshLogin() {
        if (currentCookie == null) {
            ChatUtils.addChatMessage("§c未登录，无法刷新");
            return;
        }

        ChatUtils.addChatMessage("§6正在刷新登录状态...");

        Multithreading.runAsync(() -> {
            try {
                String refreshUrl = BASE_API + "/login/refresh";
                JsonObject result = sendGetRequestWithCookie(refreshUrl);

                if (result != null && result.get("code").getAsInt() == 200) {
                    Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§a登录状态刷新成功"));
                } else {
                    Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c登录状态刷新失败"));
                }

            } catch (Exception e) {
                Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c刷新登录状态失败: " + e.getMessage()));
                PupperClient.LOGGER.error("刷新登录状态失败: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 退出登录
     */
    private static void logout() {
        if (currentCookie == null) {
            ChatUtils.addChatMessage("§c未登录");
            return;
        }

        Multithreading.runAsync(() -> {
            try {
                String logoutUrl = BASE_API + "/logout";
                sendGetRequestWithCookie(logoutUrl);

                // 清除本地登录状态
                currentCookie = null;
                currentUserId = null;
                currentNickname = null;
                currentPhone = null;
                saveLoginStatus();

                Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§a已退出登录"));

            } catch (Exception e) {
                Multithreading.runMainThread(() -> ChatUtils.addChatMessage("§c退出登录失败: " + e.getMessage()));
                PupperClient.LOGGER.error("退出登录失败: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 保存登录状态到文件
     */
    private static void saveLoginStatus() {
        try {
            if (!LOGIN_FILE.getParentFile().exists()) {
                LOGIN_FILE.getParentFile().mkdirs();
            }

            JsonObject status = new JsonObject();
            if (currentCookie != null) status.addProperty("cookie", currentCookie);
            if (currentUserId != null) status.addProperty("userId", currentUserId);
            if (currentNickname != null) status.addProperty("nickname", currentNickname);
            if (currentPhone != null) status.addProperty("phone", currentPhone);
            status.addProperty("savedTime", System.currentTimeMillis());

            try (FileWriter writer = new FileWriter(LOGIN_FILE)) {
                writer.write(status.toString());
            }

        } catch (Exception e) {
            PupperClient.LOGGER.error("保存登录状态失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 从文件加载登录状态
     */
    private static void loadLoginStatus() {
        try {
            if (!LOGIN_FILE.exists()) {
                return;
            }

            try (FileReader reader = new FileReader(LOGIN_FILE)) {
                JsonObject status = JsonParser.parseReader(reader).getAsJsonObject();

                // 检查是否过期（7天内有效）
                long savedTime = status.get("savedTime").getAsLong();
                if (System.currentTimeMillis() - savedTime > 7 * 24 * 60 * 60 * 1000L) {
                    // 过期，删除文件
                    LOGIN_FILE.delete();
                    return;
                }

                if (status.has("cookie")) currentCookie = status.get("cookie").getAsString();
                if (status.has("userId")) currentUserId = status.get("userId").getAsString();
                if (status.has("nickname")) currentNickname = status.get("nickname").getAsString();
                if (status.has("phone")) currentPhone = status.get("phone").getAsString();
            }

        } catch (Exception e) {
            PupperClient.LOGGER.error("加载登录状态失败: {}", e.getMessage(), e);
        }
    }

    private static void showHelp() {
        ChatUtils.addChatMessage("§6=== 网易云登录命令 ===");
        ChatUtils.addChatMessage("§b.login send <手机号> §7- 发送验证码");
        ChatUtils.addChatMessage("§b.login phone <手机号> <验证码> §7- 手机号验证码登录");
        ChatUtils.addChatMessage("§b.login qr §7- 二维码登录");
        ChatUtils.addChatMessage("§b.login status §7- 检查登录状态");
        ChatUtils.addChatMessage("§b.login refresh §7- 刷新登录状态");
        ChatUtils.addChatMessage("§b.login logout §7- 退出登录");
        ChatUtils.addChatMessage("§b.login help §7- 显示帮助");
        ChatUtils.addChatMessage("§6登录流程:");
        ChatUtils.addChatMessage("§7  1. 使用 §b.login send 13800138000 §7发送验证码");
        ChatUtils.addChatMessage("§7  2. 使用 §b.login phone 13800138000 123456 §7登录");
        ChatUtils.addChatMessage("§7  或使用 §b.login qr §7二维码登录");
    }

    // ========== 工具方法 ==========

    /**
     * 构建关键词（修复空格问题）
     */
    private static String buildKeyword(String[] args, int startIndex) {
        StringBuilder keyword = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) keyword.append(" ");
            keyword.append(args[i]);
        }
        return keyword.toString();
    }

    private static JsonObject sendGetRequest(String urlString) throws IOException {
        return sendRequest(urlString, "GET", false);
    }

    private static JsonObject sendGetRequestWithCookie(String urlString) throws IOException {
        return sendRequest(urlString, "GET", true);
    }

    private static JsonObject sendPostRequest(String urlString) throws IOException {
        return sendRequest(urlString, "POST", false);
    }

    private static JsonObject sendRequest(String urlString, String method, boolean useCookie) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = getHttpURLConnection(method, useCookie, url);

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

    private static @NonNull HttpURLConnection getHttpURLConnection(String method, boolean useCookie, URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        if (useCookie && currentCookie != null) {
            connection.setRequestProperty("Cookie", currentCookie);
        }

        return connection;
    }

    public static synchronized String getCurrentCookie() {
        if (currentCookie == null && !loginStatusLoaded) {
            loadLoginStatus();
            loginStatusLoaded = true;
        }
        return currentCookie;
    }

    public static String getCurrentUserId() {
        getCurrentCookie();
        return currentUserId;
    }

    public static String getApiBase() {
        return BASE_API;
    }

    public static String getCurrentNickname() {
        return currentNickname;
    }

    public static String getCurrentPhone() {
        return currentPhone;
    }
}
