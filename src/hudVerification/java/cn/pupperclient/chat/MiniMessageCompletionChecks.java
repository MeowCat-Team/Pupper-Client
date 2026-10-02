package cn.pupperclient.chat;

import cn.pupperclient.utils.chat.MiniMessageSuggestions;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.PUTFIELD;

/** Headless syntax/range and native chat completion contracts; no Minecraft instance or server. */
public final class MiniMessageCompletionChecks {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        require(has("<", "red>", "bold>", "gradient:", "hover:", "reset>"), "Opening bracket offers standard tags");
        require(has("你好 <gr", "green>", "gray>", "gradient>"), "Completion works after Unicode message text");
        require(has("/msg Player <re", "red>", "reset>"), "Slash command arguments can contain MiniMessage");
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            require(has("<ITAL", "italic>"), "Names are case-insensitive independently of system locale");
        } finally {
            Locale.setDefault(previous);
        }
        for (String text : new String[]{"hello", "<red>hello", "1 < 2", "\\<red", "<broken\\<re", "<hover:show_text:'<re", "<click:run_command:\"say <re"}) {
            require(MiniMessageSuggestions.suggest(text, text.length()).isEmpty(), "Vanilla passthrough: " + text);
        }
        require(has("\\\\<re", "red>"), "An escaped backslash does not escape the next tag");
        require(has("<hover:show_text:'<blue>x'>text <re", "red>"), "Quoted nested text cannot swallow following tags");
        require(has("<hover:show_text:'it\\'s <blue>'>x <re", "red>"), "Escaped quotes inside arguments");
        require(has("<!bo", "!bold>"), "Negated decoration");
        require(has("<color:r", "color:red>"), "Named color argument");
        require(has("<colour:dark_", "colour:dark_red>", "colour:dark_blue>"), "Color alias arguments");
        require(has("<bold:f", "bold:false>"), "Decoration boolean argument");
        require(has("<click:ru", "click:run_command:"), "Click action argument");
        require(has("<hover:show_", "hover:show_text:", "hover:show_item:", "hover:show_entity:"), "Hover actions");
        require(has("<gradient:red:bl", "gradient:red:blue>", "gradient:red:black>"), "Later gradient colors");
        require(has("<gradient:red:blue:-", "gradient:red:blue:-1>"), "Gradient phase");
        require(has("<shadow:red:0", "shadow:red:0>", "shadow:red:0.5>"), "Shadow alpha");
        require(has("<font:minecraft:u", "font:minecraft:uniform>"), "Namespaced font argument");
        require(has("<key:key.j", "key:key.jump>"), "Keybind argument");
        require(has("<pride:tr", "pride:trans>"), "Pride argument");
        require(has("<selector:@s", "selector:@s>"), "Selector argument");
        require(has("<nbt:en", "nbt:entity:"), "NBT source argument");
        require(has("<#Ab12CD", "#Ab12CD>"), "Custom hex color preserves spelling");
        require(has("<shadow:#Ab12CD80", "shadow:#Ab12CD80>"), "Shadow supports eight digit RGBA");
        require(suggestions("<color:red:bl").isEmpty(), "Simple colors do not accept a second color");
        require(suggestions("<not_a_standard_tag").isEmpty(), "Unknown tag is not completed to an unrelated name");
        require(has("<red><bold>Hello </", "/bold>", "/red>"), "Closing tags come from open scopes");
        require(suggestions("<red><bold>x</bold></").getList().stream().noneMatch(s -> s.getText().equals("/bold>")), "Closed scope is removed");
        require(suggestions("<red><bold>x</red></").isEmpty(), "Closing an outer scope also closes nested scopes");
        require(suggestions("<red><reset>x </").isEmpty(), "Reset clears scopes");
        require(suggestions("<red/><newline><key:key.jump></").isEmpty(), "Self-closing and inserting tags do not create closing candidates");
        require(suggestions("<hover:show_text:'<red>nested'>x </").getList().stream().noneMatch(s -> s.getText().equals("/red>")), "Quoted scopes stay isolated");
        require(has("<color:red>x </co", "/color>"), "Argument tags close by their name");
        require(has("<b>x</bold></", "/b>"), "Closing tags use the exact opening alias");
        require(has("<!bold>x </", "/!bold>"), "Negated decoration scopes can close");
        require(suggestions("<color:red><color:blue>x</color:red></").isEmpty(), "Closing arguments select the matching outer scope");
        require(has("<font:MyFont>x</font:myfont></", "/font>"), "Closing argument values are case-sensitive");
        applied("Hello <red> world", 9, "red>", "Hello <red> world");
        applied("Hello <re world", 9, "red>", "Hello <red> world");
        applied("<gradient:red:blue> tail", 17, "gradient:red:blue>", "<gradient:red:blue> tail");
        applied("<hover:show_text:'a > b'> tail", 3, "hover:show_text:'a > b'>", "<hover:show_text:'a > b'> tail");
        applied("<hover:show_text:'hello'> tail", 11, "hover:show_text:'hello'>", "<hover:show_text:'hello'> tail");
        applied("<gradient:red:blue> tail", 4, "gradient:red:blue>", "<gradient:red:blue> tail");
        require(MiniMessageSuggestions.suggest("<re", 0).isEmpty(), "Cursor before the tag leaves vanilla suggestions alone");
        require(hasAt("<red> text", 2, "red>"), "Cursor within tag name");
        require(hasAt("<re", Integer.MAX_VALUE, "red>"), "Out-of-range cursor is clamped");
        require(MiniMessageSuggestions.suggest("<hover:show_text:'unterminated", 3).isEmpty(), "An unfinished quoted suffix must not be damaged");
        checkMinecraftContracts();
        System.out.printf("MiniMessage completion checks passed: %d assertions (syntax, escaping, scopes, cursor replacement and Minecraft Tab contracts).%n", assertions);
    }

    private static boolean has(String input, String... expected) {
        return hasAt(input, input.length(), expected);
    }

    private static boolean hasAt(String input, int cursor, String... expected) {
        Set<String> values = new HashSet<>();
        MiniMessageSuggestions.suggest(input, cursor).orElseThrow().getList().forEach(s -> values.add(s.getText()));
        return values.containsAll(Set.of(expected));
    }

    private static Suggestions suggestions(String input) {
        return MiniMessageSuggestions.suggest(input, input.length()).orElseThrow();
    }

    private static void applied(String input, int cursor, String choice, String expected) {
        Suggestion suggestion = MiniMessageSuggestions.suggest(input, cursor).orElseThrow().getList().stream()
            .filter(s -> s.getText().equals(choice)).findFirst().orElseThrow();
        require(suggestion.apply(input).equals(expected), "Replacement must preserve suffix text: " + input);
    }

    private static void checkMinecraftContracts() throws Exception {
        String target = "net/minecraft/client/gui/components/CommandSuggestions";
        Map<String, String> fields = new HashMap<>();
        Set<String> methods = new HashSet<>();
        read(target, new ClassVisitor(ASM9) {
            @Override public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                fields.put(name, descriptor);
                return null;
            }
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                methods.add(name + descriptor);
                return null;
            }
        });
        Map<String, String> expected = Map.of(
            "screen", "Lnet/minecraft/client/gui/screens/Screen;",
            "input", "Lnet/minecraft/client/gui/components/EditBox;",
            "commandUsage", "Ljava/util/List;",
            "currentParse", "Lcom/mojang/brigadier/ParseResults;",
            "pendingSuggestions", "Ljava/util/concurrent/CompletableFuture;",
            "currentParseIsCommand", "Z", "currentParseIsMessage", "Z", "keepSuggestions", "Z"
        );
        expected.forEach((name, descriptor) -> require(descriptor.equals(fields.get(name)), "Mixin field contract: " + name));
        for (String method : new String[]{"updateCommandInfo()V", "hide()V", "showSuggestions(Z)V"}) {
            require(methods.contains(method), "Mixin method contract: " + method);
        }
        boolean[] keeping = {false};
        boolean[] guarded = {false};
        boolean[] released = {false};
        read(target + "$SuggestionsList", new ClassVisitor(ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!name.equals("useSuggestion") || !descriptor.equals("()V")) return null;
                return new MethodVisitor(ASM9) {
                    @Override public void visitFieldInsn(int opcode, String owner, String field, String type) {
                        if (opcode == PUTFIELD && owner.equals(target) && field.equals("keepSuggestions")) {
                            if (guarded[0]) released[0] = true;
                            else keeping[0] = true;
                        }
                    }
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String type, boolean isInterface) {
                        if (owner.equals("net/minecraft/client/gui/components/EditBox") && method.equals("setValue")) guarded[0] = keeping[0];
                    }
                };
            }
        });
        require(guarded[0] && released[0], "Native Tab protects and then releases input re-entry for cycling");
    }

    private static void read(String className, ClassVisitor visitor) throws Exception {
        try (InputStream stream = MiniMessageCompletionChecks.class.getClassLoader().getResourceAsStream(className + ".class")) {
            require(stream != null, "Minecraft class available: " + className);
            new ClassReader(stream).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
