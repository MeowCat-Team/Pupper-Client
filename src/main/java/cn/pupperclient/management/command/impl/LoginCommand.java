package cn.pupperclient.management.command.impl;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.music.MusicAccount;
import cn.pupperclient.management.music.MusicError;
import cn.pupperclient.management.music.MusicLoginService;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.utils.chat.ChatUtils;
import java.util.List;
import java.util.Locale;

/** Chat arguments and feedback only; the music service owns authentication and persistence. */
public final class LoginCommand {
    private LoginCommand() { }
    private static MusicLoginService service() {
        return PupperClient.getInstance().getMusicManager().getService().login();
    }
    public static void handleCommand(String[] args) {
        if (args.length <= 1) { help(); return; }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "send" -> {
                if (args.length < 3) { usage(action); return; }
                message("§6", "music.login.sending");
                service().sendCaptcha(args[2], ignored -> {
                    message("§a", "music.login.sent");
                    message("§7", "music.login.command.phone");
                }, LoginCommand::error);
            }
            case "phone" -> {
                if (args.length < 4) { usage(action); return; }
                message("§6", "music.login.signing");
                service().phoneLogin(args[2], args[3], account -> signedIn(account, "music.login.success"), LoginCommand::error);
            }
            case "qr" -> {
                message("§6", "music.login.qr.generating");
                service().qrLogin(code -> {
                    message("§6", "music.login.qr.scan");
                    // The service supplies the same QR image to any future graphical login client.
                    ChatUtils.addChatMessage("§b" + code.image());
                }, state -> message("§6", state == MusicLoginService.QrState.SCANNED
                    ? "music.login.qr.scanned" : "music.login.qr.waiting"),
                    account -> signedIn(account, "music.login.success"), LoginCommand::error);
            }
            case "status" -> service().check(account -> {
                if (!account.authenticated()) message("§7", "music.login.guest");
                else signedIn(account, "music.login.status");
            }, LoginCommand::error);
            case "refresh" -> {
                message("§6", "music.login.refreshing");
                service().refresh(account -> message("§a", "music.login.refreshed"), LoginCommand::error);
            }
            case "logout" -> service().logout(ignored -> message("§a", "music.login.loggedout"), LoginCommand::error);
            case "help" -> help();
            default -> usage("help");
        }
    }
    private static void signedIn(MusicAccount account, String message) {
        message("§a", message);
        if (!account.nickname().isBlank()) message("§7", "music.login.user", account.nickname());
        message("§7", "music.login.userid", account.userId());
    }
    private static void usage(String action) {
        message("§c", "music.command.usage", MusicText.get("music.login.command." + action));
    }
    private static void help() {
        message("§6", "music.login.title");
        for (String action : List.of("send", "phone", "qr", "status", "refresh", "logout", "help"))
            message("§b", "music.login.command." + action);
    }
    private static void error(MusicError error) { message("§c", error.key()); }
    private static void message(String color, String key, Object... arguments) {
        ChatUtils.addChatMessage(color + MusicText.get(key, arguments));
    }
}
