package cn.pupperclient.music;

import cn.pupperclient.gui.modmenu.component.MusicAccountUi;
import cn.pupperclient.gui.modmenu.component.MusicPlayerLayout;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.language.Language;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Validates real PNG data and modal/i18n contracts without creating a client, sending SMS or writing QR files. */
public final class MusicAccountDialogChecks {
    private static int checks;
    private MusicAccountDialogChecks() { }

    public static void run() throws Exception {
        checks = 0;
        byte[] png = png(41, 41);
        require(Arrays.equals(MusicAccountUi.qrBytes(data(png)), png), "The bounded valid PNG QR image was rejected or altered");
        reject(null); reject("https://music.163.com/private-login-image");
        reject("data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(png));
        reject("data:image/png;base64,not-base64!");
        reject("data:image/png;base64," + "A".repeat(700_000));
        reject(data(new byte[]{'I', 'D', '3'})); reject(data(Arrays.copyOf(png, 20)));
        reject(data(png(41, 42)));
        byte[] huge = png.clone(); ByteBuffer.wrap(huge, 16, 4).putInt(1025); ByteBuffer.wrap(huge, 20, 4).putInt(1025);
        reject(data(huge));
        reject(data(Arrays.copyOf(png, 33)));
        var modal = MusicAccountUi.box();
        require(modal.x() >= 0 && modal.y() >= 0 && modal.x() + modal.width() <= MusicPlayerLayout.WIDTH
            && modal.y() + modal.height() <= MusicPlayerLayout.HEIGHT, "Account modal escaped the resized player coordinate system");
        require(Math.abs(modal.x() + modal.width() / 2 - MusicPlayerLayout.WIDTH / 2) < .1f
            && Math.abs(modal.y() + modal.height() / 2 - MusicPlayerLayout.HEIGHT / 2) < .1f,
            "Account modal is not centered in the current player layout");
        for (var action : MusicAccountUi.Action.values()) {
            var target = MusicAccountUi.actionBox(action);
            require(target.width() >= 48 && target.height() >= 48 && modal.contains(target.x(), target.y())
                && target.x() + target.width() <= modal.x() + modal.width() && target.y() + target.height() <= modal.y() + modal.height(),
                "Account action escaped its modal or lost its MD3 pointer target: " + action);
        }
        Language previous = I18n.getCurrentLanguage();
        try {
            for (Language language : List.of(Language.ENGLISH, Language.CHINESE)) {
                I18n.setLanguage(language);
                for (String key : List.of("music.account.login", "music.account.login.title", "music.account.tab.qr",
                        "music.account.tab.phone", "music.account.qr.waiting", "music.account.qr.scanned", "music.account.qr.expired",
                        "music.account.qr.invalid", "music.account.code.sent", "music.account.signin", "music.account.logout",
                        "music.lyrics.fullscreen", "music.lyrics.back"))
                    require(!MusicText.get(key).equals(key), "Missing account/fullscreen translation: " + key);
                require(MusicText.get("music.account.code.countdown", 42).contains("42"), "Resend countdown lost its translated seconds");
                require(MusicText.get("music.account.userid", "12345").contains("12345"), "Account ID translation lost its value");
            }
        } finally { if (previous != null) I18n.setLanguage(previous); }
        System.out.println("Music account dialog checks passed: " + checks + " assertions; bounded memory-only PNG decoding, "
            + "resized modal geometry and bilingual account/fullscreen controls.");
    }

    private static byte[] png(int width, int height) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setRGB(x, y, (x / 3 + y / 3) % 2 == 0 ? 0xffffff : 0);
        var bytes = new ByteArrayOutputStream();
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
            if (!ImageIO.write(image, "png", output)) throw new IOException("PNG writer unavailable");
            output.flush();
        }
        return bytes.toByteArray();
    }
    private static String data(byte[] bytes) { return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes); }
    private static void reject(String data) throws Exception {
        try { MusicAccountUi.qrBytes(data); throw new AssertionError("An unsupported, invalid or oversized QR image was accepted"); }
        catch (IOException expected) { checks++; }
    }
    private static void require(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
