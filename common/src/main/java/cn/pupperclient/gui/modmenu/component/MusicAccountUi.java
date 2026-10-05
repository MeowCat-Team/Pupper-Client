package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.music.MusicAccount;
import cn.pupperclient.management.music.MusicText;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.font.Fonts;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.ui.render.UiCanvas;
import cn.pupperclient.ui.theme.MaterialTheme;
import io.github.humbleui.skija.Image;
import io.github.humbleui.types.Rect;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Stateless MD3 account painters and shared modal geometry, usable by raster previews. */
public final class MusicAccountUi {
    public enum Tab { QR, PHONE }
    public enum Action { CLOSE, REGENERATE, SEND, SIGN_IN, CHECK, REFRESH, LOGOUT }
    public record State(Tab tab, MusicAccount account, String statusKey, String errorKey, boolean busy, int resendSeconds) {
        public State {
            account = account == null ? MusicAccount.GUEST : account;
            statusKey = statusKey == null ? "" : statusKey;
            errorKey = errorKey == null ? "" : errorKey;
        }
    }
    private static final String PNG_DATA_PREFIX = "data:image/png;base64,";
    private static final int MAX_QR_BYTES = 512 * 1024;
    private MusicAccountUi() { }

    public static MusicPlayerLayout.Box box() {
        return new MusicPlayerLayout.Box((MusicPlayerLayout.WIDTH - 560) / 2,
            (MusicPlayerLayout.HEIGHT - 620) / 2, 560, 620);
    }
    public static MusicPlayerLayout.Box tabBox(Tab tab) {
        var modal = box();
        return new MusicPlayerLayout.Box(modal.x() + 32 + tab.ordinal() * 256, modal.y() + 80, 240, 48);
    }
    public static MusicPlayerLayout.Box phoneBox() {
        var modal = box(); return new MusicPlayerLayout.Box(modal.x() + 32, modal.y() + 188, 496, 40);
    }
    public static MusicPlayerLayout.Box captchaBox() {
        var modal = box(); return new MusicPlayerLayout.Box(modal.x() + 32, modal.y() + 284, 304, 40);
    }
    public static MusicPlayerLayout.Box actionBox(Action action) {
        var modal = box(); float x = modal.x(), y = modal.y();
        return switch (action) {
            case CLOSE -> new MusicPlayerLayout.Box(x + 496, y + 16, 48, 48);
            case REGENERATE -> new MusicPlayerLayout.Box(x + 156, y + 520, 248, 48);
            case SEND -> new MusicPlayerLayout.Box(x + 352, y + 280, 176, 48);
            case SIGN_IN -> new MusicPlayerLayout.Box(x + 32, y + 352, 496, 48);
            case CHECK -> new MusicPlayerLayout.Box(x + 32, y + 400, 152, 48);
            case REFRESH -> new MusicPlayerLayout.Box(x + 204, y + 400, 152, 48);
            case LOGOUT -> new MusicPlayerLayout.Box(x + 376, y + 400, 152, 48);
        };
    }

    public static void launcher(MusicPlayerLayout.Box box, MusicAccount account, double mx, double my, ColorPalette palette) {
        boolean signedIn = account.authenticated();
        if (signedIn) Skia.drawRoundedRect(box.x(), box.y() + 4, box.width(), box.height() - 8, 12,
            MaterialTheme.surface(palette.getSecondaryContainer()));
        if (box.contains(mx, my)) Skia.drawRoundedRect(box.x(), box.y() + 4, box.width(), box.height() - 8, 12,
            MaterialTheme.alpha(palette.getOnSurface(), .08f));
        var color = signedIn ? palette.getOnSecondaryContainer() : palette.getOnSurfaceVariant();
        Skia.drawFullCenteredText(Icon.PERSON, box.x() + 26, box.y() + box.height() / 2, color, Fonts.getIcon(22));
        String label = signedIn ? account.nickname().isBlank() ? MusicText.get("music.account.signedin") : account.nickname()
            : MusicText.get("music.account.signin");
        Skia.drawHeightCenteredText(Skia.getLimitText(label, Fonts.getMedium(16), box.width() - 62),
            box.x() + 46, box.y() + box.height() / 2, color, Fonts.getMedium(16));
    }

    public static void dialog(State state, Image qr, double mx, double my, ColorPalette palette) {
        var box = box(); float x = box.x(), y = box.y();
        Skia.drawRect(0, 0, MusicPlayerLayout.WIDTH, MusicPlayerLayout.HEIGHT, MaterialTheme.alpha(Color.BLACK, .45f));
        Skia.drawRoundedRect(x, y + 4, box.width(), box.height(), 28, MaterialTheme.alpha(Color.BLACK, .2f));
        Skia.drawRoundedRect(x, y, box.width(), box.height(), 28, palette.getSurfaceContainerHigh());
        Skia.drawText(MusicText.get(state.account().authenticated() ? "music.account.title" : "music.account.login.title"),
            x + 32, y + 30, palette.getOnSurface(), Fonts.getMedium(24));
        var close = actionBox(Action.CLOSE);
        MusicUi.iconButton(close.x(), close.y(), Icon.CLOSE, false, true, close.contains(mx, my), palette);
        if (state.account().authenticated()) accountCard(state, mx, my, palette);
        else {
            for (Tab tab : Tab.values()) {
                var segment = tabBox(tab); boolean selected = state.tab() == tab;
                Skia.drawRoundedRect(segment.x(), segment.y(), segment.width(), segment.height(), 24,
                    selected ? palette.getSecondaryContainer() : palette.getSurfaceContainerLow());
                if (segment.contains(mx, my)) Skia.drawRoundedRect(segment.x(), segment.y(), segment.width(), segment.height(), 24,
                    MaterialTheme.alpha(palette.getOnSurface(), .08f));
                Skia.drawFullCenteredText(MusicText.get(tab == Tab.QR ? "music.account.tab.qr" : "music.account.tab.phone"),
                    segment.x() + segment.width() / 2, segment.y() + 24,
                    selected ? palette.getOnSecondaryContainer() : palette.getOnSurfaceVariant(), Fonts.getMedium(18));
            }
            if (state.tab() == Tab.QR) {
                Skia.drawRoundedRect(x + 148, y + 146, 264, 264, 16, Color.WHITE);
                if (qr != null) Skia.getCanvas().drawImageRect(qr, Rect.makeWH(qr.getWidth(), qr.getHeight()),
                    Rect.makeXYWH(x + 160, y + 158, 240, 240), null, true, UiCanvas.ImageSampling.PIXEL);
                else Skia.drawFullCenteredText(Icon.QR_CODE_2, x + 280, y + 278, palette.getOutline(), Fonts.getIcon(72));
                centered(MusicText.get("music.account.qr.help"), x + 280, y + 430, 496, palette.getOnSurfaceVariant(), 16);
                status(state, x + 280, y + 470, palette);
                button(Action.REGENERATE, MusicText.get("music.account.qr.regenerate"), true, !state.busy(), mx, my, palette);
            } else {
                Skia.drawText(MusicText.get("music.account.phone.help"), x + 32, y + 142, palette.getOnSurfaceVariant(), Fonts.getRegular(16));
                Skia.drawText(MusicText.get("music.account.phone.label"), x + 32, y + 162, palette.getOnSurfaceVariant(), Fonts.getRegular(16));
                Skia.drawText(MusicText.get("music.account.code.label"), x + 32, y + 258, palette.getOnSurfaceVariant(), Fonts.getRegular(16));
                button(Action.SEND, MusicText.get(state.resendSeconds() > 0 ? "music.account.code.countdown" : "music.account.code.send",
                    state.resendSeconds() > 0 ? new Object[]{state.resendSeconds()} : new Object[0]),
                    false, !state.busy() && state.resendSeconds() == 0, mx, my, palette);
                button(Action.SIGN_IN, MusicText.get("music.account.signin"), true, !state.busy(), mx, my, palette);
                status(state, x + 280, y + 438, palette);
            }
        }
    }

    private static void accountCard(State state, double mx, double my, ColorPalette palette) {
        var box = box(); float x = box.x(), y = box.y();
        Skia.drawCircle(x + 280, y + 176, 56, palette.getPrimaryContainer());
        Skia.drawFullCenteredText(Icon.PERSON, x + 280, y + 176, palette.getOnPrimaryContainer(), Fonts.getIcon(64));
        centered(state.account().nickname().isBlank() ? MusicText.get("music.account.signedin") : state.account().nickname(),
            x + 280, y + 262, 496, palette.getOnSurface(), 24);
        centered(MusicText.get("music.account.userid", state.account().userId()), x + 280, y + 304, 496, palette.getOnSurfaceVariant(), 16);
        status(state, x + 280, y + 350, palette);
        button(Action.CHECK, MusicText.get("music.account.check"), false, !state.busy(), mx, my, palette);
        button(Action.REFRESH, MusicText.get("music.account.refresh"), false, !state.busy(), mx, my, palette);
        button(Action.LOGOUT, MusicText.get("music.account.logout"), true, !state.busy(), mx, my, palette);
    }

    private static void status(State state, float x, float y, ColorPalette palette) {
        if (!state.statusKey().isBlank()) centered(MusicText.get(state.statusKey()), x, y, 496, palette.getOnSurfaceVariant(), 16);
        if (!state.errorKey().isBlank()) centered(MusicText.get(state.errorKey()), x, y + 28, 496, palette.getError(), 16);
    }
    private static void centered(String text, float x, float y, float width, Color color, float size) {
        Skia.drawFullCenteredText(Skia.getLimitText(text, Fonts.getRegular(size), width), x, y, color, Fonts.getRegular(size));
    }
    private static void button(Action action, String label, boolean primary, boolean enabled,
            double mx, double my, ColorPalette palette) {
        var box = actionBox(action);
        var fill = enabled ? primary ? palette.getPrimary() : palette.getSecondaryContainer() : MaterialTheme.alpha(palette.getOnSurface(), .12f);
        var content = enabled ? primary ? palette.getOnPrimary() : palette.getOnSecondaryContainer() : MaterialTheme.alpha(palette.getOnSurface(), .38f);
        Skia.drawRoundedRect(box.x(), box.y(), box.width(), box.height(), 24, fill);
        if (enabled && box.contains(mx, my)) Skia.drawRoundedRect(box.x(), box.y(), box.width(), box.height(), 24, MaterialTheme.alpha(content, .08f));
        centered(label, box.x() + box.width() / 2, box.y() + 24, box.width() - 24, content, 16);
    }

    /** Accepts only a small PNG data URI; no remote URLs, files, credentials or persisted QR images. */
    public static byte[] qrBytes(String dataUri) throws IOException {
        if (dataUri == null || !dataUri.startsWith(PNG_DATA_PREFIX) || dataUri.length() > PNG_DATA_PREFIX.length() + (MAX_QR_BYTES + 2) / 3 * 4)
            throw new IOException("Invalid login QR image");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(dataUri.substring(PNG_DATA_PREFIX.length())); }
        catch (IllegalArgumentException invalid) { throw new IOException("Invalid login QR image"); }
        if (bytes.length < 33 || bytes.length > MAX_QR_BYTES || bytes[0] != (byte) 137 || bytes[1] != 'P'
                || bytes[2] != 'N' || bytes[3] != 'G' || bytes[4] != 13 || bytes[5] != 10 || bytes[6] != 26 || bytes[7] != 10
                || ByteBuffer.wrap(bytes, 8, 4).getInt() != 13 || bytes[12] != 'I' || bytes[13] != 'H' || bytes[14] != 'D' || bytes[15] != 'R')
            throw new IOException("Invalid login QR image");
        int width = ByteBuffer.wrap(bytes, 16, 4).getInt(), height = ByteBuffer.wrap(bytes, 20, 4).getInt();
        if (width <= 0 || width > 1024 || height != width) throw new IOException("Invalid login QR image dimensions");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Invalid login QR image");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                var image = reader.read(0);
                if (image == null || image.getWidth() != width || image.getHeight() != height) throw new IOException("Invalid login QR image");
            } finally { reader.dispose(); }
        }
        return bytes;
    }
}
