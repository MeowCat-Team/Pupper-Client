package cn.pupperclient.management.command;

import cn.pupperclient.PupperClient;
import cn.pupperclient.management.command.impl.BindCommand;
import cn.pupperclient.management.command.impl.MusicCommand;
import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModManager;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PupperCommandSuggestions {
    private static final List<String> COMMANDS = List.of(
        "help", "list", "l", "toggle", "t", "bind", "b", "music", "m", "163", "login"
    );
    private static final List<String> BIND_SUBCOMMANDS = List.of("list", "clear", "reset");
    private static final List<String> MUSIC_SUBCOMMANDS = List.of("search", "download", "quick", "list", "help");
    private static final List<String> LOGIN_SUBCOMMANDS = List.of(
        "send", "phone", "qr", "status", "logout", "refresh", "help"
    );

    private PupperCommandSuggestions() {
    }

    public static Suggestions suggest(String input, int cursor) {
        String beforeCursor = input.substring(0, Math.clamp(cursor, 0, input.length()));
        if (!beforeCursor.startsWith(".")) {
            return new SuggestionsBuilder(beforeCursor, 0).build();
        }
        int firstSpace = beforeCursor.indexOf(' ');
        if (firstSpace < 0) {
            SuggestionsBuilder builder = new SuggestionsBuilder(beforeCursor, 1);
            suggestMatches(builder, COMMANDS);
            return builder.build();
        }

        String command = beforeCursor.substring(1, firstSpace).toLowerCase(Locale.ROOT);
        int argumentStart = skipSpaces(beforeCursor, firstSpace + 1);
        String arguments = beforeCursor.substring(argumentStart);

        return switch (command) {
            case "t", "toggle" -> suggestMods(beforeCursor, argumentStart);
            case "b", "bind" -> suggestBind(beforeCursor, argumentStart, arguments);
            case "m", "music", "163" -> suggestMusic(beforeCursor, argumentStart, arguments);
            case "login" -> suggestWords(beforeCursor, argumentStart, LOGIN_SUBCOMMANDS, arguments);
            default -> new SuggestionsBuilder(beforeCursor, argumentStart).build();
        };
    }

    private static Suggestions suggestBind(String input, int start, String arguments) {
        if (arguments.isEmpty()) {
            SuggestionsBuilder builder = new SuggestionsBuilder(input, start);
            suggestMatches(builder, BIND_SUBCOMMANDS);
            suggestMatches(builder, modNames());
            return builder.build();
        }

        int firstSpace = arguments.indexOf(' ');
        if (firstSpace >= 0) {
            String firstWord = arguments.substring(0, firstSpace).toLowerCase(Locale.ROOT);
            if (firstWord.equals("clear") || firstWord.equals("reset")) {
                return suggestMods(input, skipSpaces(input, start + firstSpace + 1));
            }
        }

        Set<String> names = modNames();
        if (startsWithAny(names, arguments)) {
            SuggestionsBuilder builder = new SuggestionsBuilder(input, start);
            suggestMatches(builder, names);
            if (firstSpace < 0) {
                suggestMatches(builder, BIND_SUBCOMMANDS);
            }
            return builder.build();
        }

        int lastSpace = arguments.lastIndexOf(' ');
        if (lastSpace >= 0 && findMod(arguments.substring(0, lastSpace)) != null) {
            SuggestionsBuilder builder = new SuggestionsBuilder(input, start + lastSpace + 1);
            suggestMatches(builder, BindCommand.getSupportedKeys());
            suggestMatches(builder, List.of("none", "clear"));
            return builder.build();
        }

        SuggestionsBuilder builder = new SuggestionsBuilder(input, start);
        suggestMatches(builder, names);
        if (firstSpace < 0) {
            suggestMatches(builder, BIND_SUBCOMMANDS);
        }
        return builder.build();
    }

    private static Suggestions suggestMusic(String input, int start, String arguments) {
        int firstSpace = arguments.indexOf(' ');
        if (firstSpace < 0) {
            return suggestWords(input, start, MUSIC_SUBCOMMANDS, arguments);
        }
        if (!arguments.substring(0, firstSpace).equalsIgnoreCase("download")) {
            return new SuggestionsBuilder(input, start).build();
        }

        int songIdStart = skipSpaces(input, start + firstSpace + 1);
        int qualitySpace = input.indexOf(' ', songIdStart);
        if (qualitySpace < 0 || songIdStart == qualitySpace) {
            return new SuggestionsBuilder(input, start).build();
        }
        return suggestWords(input, skipSpaces(input, qualitySpace + 1), MusicCommand.getQualityLevels(),
            input.substring(skipSpaces(input, qualitySpace + 1)));
    }

    private static Suggestions suggestMods(String input, int start) {
        SuggestionsBuilder builder = new SuggestionsBuilder(input, start);
        suggestMatches(builder, modNames());
        return builder.build();
    }

    private static Suggestions suggestWords(String input, int start, Iterable<String> choices, String remaining) {
        SuggestionsBuilder builder = new SuggestionsBuilder(input, start);
        if (!remaining.contains(" ")) {
            suggestMatches(builder, choices);
        }
        return builder.build();
    }

    private static void suggestMatches(SuggestionsBuilder builder, Iterable<String> choices) {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String choice : choices) {
            if (choice.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(choice);
            }
        }
    }

    private static boolean startsWithAny(Iterable<String> choices, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        for (String choice : choices) {
            if (choice.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> modNames() {
        Set<String> names = new LinkedHashSet<>();
        ModManager manager = PupperClient.getInstance().getModManager();
        if (manager == null) {
            return names;
        }

        for (Mod mod : manager.getMods()) {
            String rawName = mod.getRawName();
            String shortName = rawName.startsWith("mod.") && rawName.endsWith(".name")
                ? rawName.substring(4, rawName.length() - 5)
                : rawName;
            names.add(shortName);
            names.add(mod.getClass().getSimpleName());
            names.add(mod.getName());
        }
        return names;
    }

    private static Mod findMod(String name) {
        ModManager manager = PupperClient.getInstance().getModManager();
        return manager == null ? null : manager.getModByCommandName(name);
    }

    private static int skipSpaces(String input, int start) {
        while (start < input.length() && input.charAt(start) == ' ') {
            start++;
        }
        return start;
    }
}
