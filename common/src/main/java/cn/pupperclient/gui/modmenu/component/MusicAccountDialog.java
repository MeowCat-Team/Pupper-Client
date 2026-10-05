package cn.pupperclient.gui.modmenu.component;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.music.MusicAccount;
import cn.pupperclient.management.music.MusicError;
import cn.pupperclient.management.music.MusicLoginService;
import cn.pupperclient.ui.component.impl.text.TextField;
import io.github.humbleui.skija.Image;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.lwjgl.glfw.GLFW;

/** Owns one modal login attempt and its transient QR image, never the shared login service. */
public final class MusicAccountDialog {
    private final MusicLoginService login;
    private final Runnable accountChanged;
    private final TextField phone = new TextField(0, 0, 496, "");
    private final TextField captcha = new TextField(0, 0, 304, "");
    private MusicAccountUi.Tab tab = MusicAccountUi.Tab.QR;
    private MusicLoginService.Attempt attempt;
    private Image qr;
    private MusicAccountUi.Action pressed;
    private MusicAccountUi.Tab pressedTab;
    private String statusKey = "", errorKey = "";
    private long generation, resendAt;
    private boolean open, disposed, busy;

    public MusicAccountDialog(MusicLoginService login, Runnable accountChanged) {
        this.login = Objects.requireNonNull(login, "login");
        this.accountChanged = Objects.requireNonNull(accountChanged, "accountChanged");
    }

    public boolean isOpen() { return open; }
    public void open() {
        if (disposed || open) return;
        open = true; generation++; busy = false; statusKey = ""; errorKey = "";
        pressed = null; pressedTab = null; tab = MusicAccountUi.Tab.QR;
        phone.setText(""); captcha.setText(""); blur();
        if (!login.account().authenticated()) startQr();
    }
    public void close() {
        open = false; generation++; busy = false; pressed = null; pressedTab = null;
        cancelAttempt(); releaseQr(); phone.setText(""); captcha.setText(""); blur();
    }
    public void dispose() { close(); disposed = true; }

    public void draw(double mx, double my) {
        if (!open) return;
        layoutInputs();
        MusicAccountUi.dialog(new MusicAccountUi.State(tab, login.account(), statusKey, errorKey, busy, resendSeconds()),
            qr, mx, my, PupperClient.getInstance().getColorManager().getPalette());
        if (!login.account().authenticated() && tab == MusicAccountUi.Tab.PHONE) {
            phone.draw(mx, my); captcha.draw(mx, my);
        }
    }

    private void layoutInputs() {
        var phoneBox = MusicAccountUi.phoneBox(); phone.setX(phoneBox.x()); phone.setY(phoneBox.y());
        var codeBox = MusicAccountUi.captchaBox(); captcha.setX(codeBox.x()); captcha.setY(codeBox.y());
    }
    private int resendSeconds() {
        long remaining = resendAt - System.nanoTime();
        return remaining <= 0 ? 0 : (int) Math.min(60, (remaining + TimeUnit.SECONDS.toNanos(1) - 1) / TimeUnit.SECONDS.toNanos(1));
    }
    private boolean current(long token) { return open && !disposed && token == generation; }
    private long begin(String status) {
        cancelAttempt(); busy = true; statusKey = status; errorKey = ""; return ++generation;
    }
    private void cancelAttempt() {
        var previous = attempt; attempt = null;
        if (previous != null) previous.close();
    }
    private void releaseQr() {
        Image previous = qr; qr = null;
        if (previous != null) previous.close();
    }

    private void startQr() {
        releaseQr();
        long token = begin("music.account.qr.generating");
        var started = login.qrLogin(code -> {
            if (!current(token)) return;
            try {
                byte[] bytes = MusicAccountUi.qrBytes(code.image());
                Image next = Image.makeDeferredFromEncodedBytes(bytes);
                releaseQr(); qr = next; busy = false;
            } catch (IOException | RuntimeException invalid) {
                // Never log or persist QR data, account credentials or the server's private reply.
                generation++; busy = false; statusKey = ""; errorKey = "music.account.qr.invalid";
                cancelAttempt(); releaseQr();
            }
        }, state -> {
            if (current(token)) statusKey = state == MusicLoginService.QrState.SCANNED
                ? "music.account.qr.scanned" : "music.account.qr.waiting";
        }, account -> signedIn(token, account), error -> failed(token, error));
        if (current(token)) attempt = started; else started.close();
    }

    private void chooseTab(MusicAccountUi.Tab next) {
        if (tab == next || login.account().authenticated()) return;
        cancelAttempt(); releaseQr(); generation++; busy = false; statusKey = ""; errorKey = ""; tab = next; blur();
        if (next == MusicAccountUi.Tab.QR) startQr(); else focusPhone();
    }
    private void sendCode() {
        if (busy || resendSeconds() > 0) return;
        String number = phone.getText().strip();
        if (!number.matches("[0-9]{5,20}")) { errorKey = "music.login.error.input"; return; }
        long token = begin("music.account.code.sending");
        // The only SMS request path is this explicit Send action.
        login.sendCaptcha(number, _ -> {
            if (!current(token)) return;
            busy = false; resendAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(60); statusKey = "music.account.code.sent";
        }, error -> failed(token, error));
    }
    private void signIn() {
        if (busy) return;
        String number = phone.getText().strip(), code = captcha.getText().strip();
        if (!number.matches("[0-9]{5,20}") || !code.matches("[0-9]{4,8}")) { errorKey = "music.login.error.input"; return; }
        long token = begin("music.account.signing");
        var started = login.phoneLogin(number, code, account -> signedIn(token, account), error -> failed(token, error));
        if (current(token)) attempt = started; else started.close();
    }
    private void signedIn(long token, MusicAccount account) {
        if (!current(token)) return;
        busy = false; statusKey = "music.account.success"; errorKey = "";
        releaseQr(); phone.setText(""); captcha.setText(""); blur(); accountChanged.run();
    }
    private void failed(long token, MusicError error) {
        if (!current(token)) return;
        busy = false; statusKey = "";
        errorKey = switch (error.key()) {
            case "music.login.error.qrexpired" -> "music.account.qr.expired";
            case "music.login.error.qrtimeout" -> "music.account.qr.timeout";
            default -> error.key();
        };
        if (tab == MusicAccountUi.Tab.QR) { cancelAttempt(); releaseQr(); }
    }
    private void checkAccount() {
        long token = begin("music.account.checking");
        login.check(account -> {
            if (!current(token)) return;
            busy = false; statusKey = account.authenticated() ? "music.account.status.valid" : "music.account.status.expired";
            accountChanged.run();
            if (!account.authenticated()) startQr();
        }, error -> failed(token, error));
    }
    private void refreshAccount() {
        long token = begin("music.account.refreshing");
        login.refresh(account -> {
            if (!current(token)) return;
            busy = false; statusKey = "music.account.refreshed"; accountChanged.run();
        }, error -> failed(token, error));
    }
    private void logout() {
        long token = begin("music.account.signingout");
        login.logout(_ -> {
            if (!current(token)) return;
            busy = false; statusKey = "music.account.loggedout"; accountChanged.run();
            tab = MusicAccountUi.Tab.QR; startQr();
        }, error -> failed(token, error));
    }

    private boolean enabled(MusicAccountUi.Action action) {
        if (action == MusicAccountUi.Action.CLOSE) return true;
        if (busy) return false;
        boolean signedIn = login.account().authenticated();
        return switch (action) {
            case CHECK, REFRESH, LOGOUT -> signedIn;
            case REGENERATE -> !signedIn && tab == MusicAccountUi.Tab.QR;
            case SEND -> !signedIn && tab == MusicAccountUi.Tab.PHONE && resendSeconds() == 0;
            case SIGN_IN -> !signedIn && tab == MusicAccountUi.Tab.PHONE;
            default -> true;
        };
    }
    public void mousePressed(double mx, double my, int button) {
        if (!open) return;
        pressed = null; pressedTab = null;
        layoutInputs();
        if (!login.account().authenticated() && tab == MusicAccountUi.Tab.PHONE && !busy) {
            phone.mousePressed(mx, my, button); captcha.mousePressed(mx, my, button);
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        if (!login.account().authenticated()) for (var choice : MusicAccountUi.Tab.values())
            if (MusicAccountUi.tabBox(choice).contains(mx, my)) { pressedTab = choice; return; }
        for (var action : MusicAccountUi.Action.values()) if (enabled(action) && MusicAccountUi.actionBox(action).contains(mx, my)) {
            pressed = action; return;
        }
    }
    public void mouseReleased(double mx, double my, int button) {
        var action = pressed; var choice = pressedTab; pressed = null; pressedTab = null;
        if (!open || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        if (choice != null && MusicAccountUi.tabBox(choice).contains(mx, my)) { chooseTab(choice); return; }
        if (action == null || !enabled(action) || !MusicAccountUi.actionBox(action).contains(mx, my)) return;
        switch (action) {
            case CLOSE -> close();
            case REGENERATE -> startQr();
            case SEND -> sendCode();
            case SIGN_IN -> signIn();
            case CHECK -> checkAccount();
            case REFRESH -> refreshAccount();
            case LOGOUT -> logout();
        }
    }
    public void mouseScrolled(double mx, double my, double horizontal, double vertical) { }
    public void charTyped(int chr) {
        if (!open || busy || login.account().authenticated() || tab != MusicAccountUi.Tab.PHONE || chr < '0' || chr > '9') return;
        if (phone.isFocused()) phone.charTyped(chr); else if (captcha.isFocused()) captcha.charTyped(chr);
    }
    public void keyPressed(int key, int scancode, int modifiers) {
        if (!open) return;
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return; }
        if (login.account().authenticated() || tab != MusicAccountUi.Tab.PHONE || busy) return;
        if (key == GLFW.GLFW_KEY_TAB) {
            boolean phoneFocused = phone.isFocused(); blur();
            if (phoneFocused) captcha.mousePressed(captcha.getX() + 1, captcha.getY() + 1, GLFW.GLFW_MOUSE_BUTTON_LEFT); else focusPhone();
        } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) signIn();
        else if (phone.isFocused()) phone.keyPressed(key, scancode, modifiers);
        else if (captcha.isFocused()) captcha.keyPressed(key, scancode, modifiers);
    }
    private void focusPhone() {
        layoutInputs(); phone.mousePressed(phone.getX() + 1, phone.getY() + 1, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
    private void blur() {
        phone.mousePressed(-1_000_000, -1_000_000, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        captcha.mousePressed(-1_000_000, -1_000_000, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
}
