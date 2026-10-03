package cn.pupperclient.network;

import cn.pupperclient.utils.network.ServerTextContents;
import cn.pupperclient.utils.network.ServerTextPrivacy;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.objectweb.asm.Opcodes.ASM9;

/** Headless remote-text policy, nested probes and Minecraft hook contracts. */
public final class ServerTextPrivacyChecks {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Language moddedLanguage = new Language() {
            private final Map<String, String> translations = Map.of(
                "screen.title.viafabricplus", "ViaFabricPlus Settings",
                "key.viafabricplus.settings", "V",
                "options.thirdparty", "A mod translation with a vanilla-looking prefix",
                "gui.done", "完成"
            );
            @Override public String getOrDefault(String key, String fallback) { return translations.getOrDefault(key, fallback); }
            @Override public boolean has(String key) { return translations.containsKey(key); }
            @Override public boolean isDefaultRightToLeft() { return false; }
            @Override public FormattedCharSequence getVisualOrder(net.minecraft.network.chat.FormattedText text) { return FormattedCharSequence.EMPTY; }
        };

        ServerTextPrivacy.setEnabled(true);
        require(resolve(moddedLanguage, "screen.title.viafabricplus", "fallb", true).equals("fallb"), "Remote translation probe uses the supplied fallback");
        require(resolve(moddedLanguage, "screen.title.viafabricplus", "screen.title.viafabricplus", true).equals("screen.title.viafabricplus"), "Missing fallback returns the raw key");
        require(resolve(moddedLanguage, "screen.title.viafabricplus", "", true).isEmpty(), "Explicit empty fallback is preserved");
        require(resolve(moddedLanguage, "options.thirdparty", "fallback", true).equals("fallback"), "A vanilla-looking prefix does not bypass protection");
        require(resolve(moddedLanguage, "gui.done", "fallback", true).equals("完成"), "Vanilla translations keep the current client language");
        require(resolve(moddedLanguage, "screen.title.viafabricplus", "fallb", false).equals("ViaFabricPlus Settings"), "Local mod UI retains its translations");
        require(ServerTextPrivacy.protectKeybind("key.viafabricplus.settings", true), "Remote mod keybind is hidden");
        require(!ServerTextPrivacy.protectKeybind("key.jump", true), "Vanilla keybind is preserved");
        require(!ServerTextPrivacy.protectKeybind("key.viafabricplus.settings", false), "Local mod keybind is preserved");
        String template = resolve(moddedLanguage, "screen.title.viafabricplus", "%2$s / %1$s / %%", true);
        require(Component.translatableWithFallback("pupper.test.absent", template, "one", "two").getString().equals("two / one / %"), "Native fallback still supports indexed arguments and escaped percent signs");

        checkNestedMarking();
        checkMinecraftContracts();

        ServerTextPrivacy.setEnabled(false);
        require(resolve(moddedLanguage, "screen.title.viafabricplus", "fallb", true).equals("ViaFabricPlus Settings"), "Disabling restores ordinary translation resolution");
        require(!ServerTextPrivacy.protectKeybind("key.viafabricplus.settings", true), "Disabling restores ordinary keybind resolution");
        System.out.printf("Server text privacy checks passed: %d assertions.%n", assertions);
    }

    private static String resolve(Language language, String key, String fallback, boolean remote) {
        return ServerTextPrivacy.resolveTranslation(language, key, fallback, remote);
    }

    private static void checkNestedMarking() {
        ProbeTranslation argument = new ProbeTranslation("probe.argument");
        ProbeTranslation hover = new ProbeTranslation("probe.hover");
        ProbeKeybind sibling = new ProbeKeybind("key.probe.sibling");
        ProbeTranslation rootContents = new ProbeTranslation("probe.root", MutableComponent.create(argument));
        Style style = Style.EMPTY.withBold(true).withHoverEvent(new HoverEvent.ShowText(MutableComponent.create(hover)));
        MutableComponent root = MutableComponent.create(rootContents).setStyle(style).append(MutableComponent.create(sibling));
        require(ServerTextPrivacy.markServerText(root) == root, "Marking retains the packet component object");
        require(rootContents.marked && argument.marked && hover.marked && sibling.marked, "Arguments, siblings and hover text cannot hide probes");
        require(root.getStyle() == style && rootContents.getKey().equals("probe.root"), "Marking preserves style and serialized keys");
        ProbeTranslation local = new ProbeTranslation("probe.local");
        require(!local.marked, "Unrelated local UI content is never marked");
        // Shared nodes and cycles must not recurse indefinitely or call equals/hashCode on components.
        root.append(root);
        int[] visits = {0};
        ServerTextPrivacy.visitContents(root, ignored -> visits[0]++);
        require(visits[0] == 4, "Shared/cyclic component graphs are visited once per object");
    }

    private static void checkMinecraftContracts() throws Exception {
        Set<String> calls = new HashSet<>();
        read("net/minecraft/network/chat/contents/TranslatableContents", new ClassVisitor(ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!name.equals("decompose") || !descriptor.equals("()V")) return null;
                return new MethodVisitor(ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String type, boolean isInterface) {
                        if (owner.equals("net/minecraft/locale/Language")) calls.add(method + type);
                    }
                };
            }
        });
        require(calls.contains("getOrDefault(Ljava/lang/String;)Ljava/lang/String;"), "Native no-fallback translation hook exists");
        require(calls.contains("getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"), "Native explicit-fallback translation hook exists");

        Set<String> fields = new HashSet<>();
        read("net/minecraft/network/chat/ComponentSerialization", new ClassVisitor(ASM9) {
            @Override public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if (descriptor.equals("Lnet/minecraft/network/codec/StreamCodec;")) fields.add(name);
                return null;
            }
        });
        require(fields.containsAll(Set.of("STREAM_CODEC", "TRUSTED_STREAM_CODEC", "OPTIONAL_STREAM_CODEC", "TRUSTED_OPTIONAL_STREAM_CODEC", "TRUSTED_CONTEXT_FREE_STREAM_CODEC")), "All component network codec hooks exist");
        checkMethod("net/minecraft/network/chat/contents/KeybindContents", "getNestedComponent()Lnet/minecraft/network/chat/Component;");
        checkMethod("net/minecraft/world/level/block/entity/SignBlockEntity", "loadLine(Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/Component;");
    }

    private static void checkMethod(String owner, String expected) throws Exception {
        Set<String> methods = new HashSet<>();
        read(owner, new ClassVisitor(ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                methods.add(name + descriptor);
                return null;
            }
        });
        require(methods.contains(expected), "Native hook exists: " + expected);
    }

    private static void read(String owner, ClassVisitor visitor) throws Exception {
        try (InputStream stream = ServerTextPrivacyChecks.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            require(stream != null, "Minecraft class available: " + owner);
            new ClassReader(stream).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }

    private static final class ProbeTranslation extends TranslatableContents implements ServerTextContents {
        private boolean marked;
        private ProbeTranslation(String key, Object... arguments) { super(key, "fallback", arguments); }
        @Override public void pupper$markServerSupplied() { marked = true; }
    }

    private static final class ProbeKeybind extends KeybindContents implements ServerTextContents {
        private boolean marked;
        private ProbeKeybind(String name) { super(name); }
        @Override public void pupper$markServerSupplied() { marked = true; }
    }
}
