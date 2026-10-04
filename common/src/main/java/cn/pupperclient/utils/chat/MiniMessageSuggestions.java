package cn.pupperclient.utils.chat;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Local syntax completion; the server decides how the submitted MiniMessage text is rendered. */
public final class MiniMessageSuggestions {
    private static final List<String> COLORS = List.of(
        "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
        "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white", "grey", "dark_grey"
    );
    private static final List<String> DECORATIONS = List.of(
        "bold", "b", "italic", "em", "i", "underlined", "u", "strikethrough", "st", "obfuscated", "obf"
    );
    private static final List<String> HEX_COLORS = List.of("#ffffff", "#ff5555", "#55ff55", "#5555ff", "#ffaa00");
    private static final List<String> SCOPED_TAGS = List.of(
        "color", "colour", "c", "shadow", "click", "hover", "insert", "rainbow", "gradient", "transition",
        "font", "pride"
    );
    private static final List<String> VALUE_TAGS = List.of(
        "key", "lang", "tr", "translate", "lang_or", "tr_or", "translate_or", "selector", "sel", "score",
        "nbt", "data", "sprite", "head"
    );
    private static final List<String> OPTIONAL_ARGUMENTS = List.of("rainbow", "gradient", "transition", "pride");

    private MiniMessageSuggestions() {}

    /** Empty Optional leaves vanilla completion alone; an empty Suggestions hides an unmatched tag. */
    public static Optional<Suggestions> suggest(String input, int cursor) {
        int position = Math.clamp(cursor, 0, input.length());
        Context context = scan(input, position);
        if (context.start < 0 || context.quote != 0) {
            return Optional.empty();
        }
        String body = input.substring(context.start + 1, position);
        if (body.chars().anyMatch(Character::isWhitespace)) {
            return Optional.empty();
        }
        String prefix = body.toLowerCase(Locale.ROOT);
        Set<String> choices = new LinkedHashSet<>();
        if (prefix.startsWith("/")) {
            for (int index = context.openTags.size() - 1; index >= 0; index--) {
                choices.add("/" + tagName(context.openTags.get(index)) + ">");
            }
        } else if (prefix.indexOf(':') >= 0) {
            argumentChoices(body, choices);
        } else if (prefix.startsWith("!")) {
            for (String decoration : DECORATIONS) choices.add("!" + decoration + ">");
            choices.add("!shadow>");
        } else {
            add(choices, "", COLORS, ">");
            add(choices, "", HEX_COLORS, ">");
            add(choices, "", DECORATIONS, ">");
            for (String name : SCOPED_TAGS) {
                choices.add(name + (OPTIONAL_ARGUMENTS.contains(name) ? ">" : ":"));
                if (OPTIONAL_ARGUMENTS.contains(name)) choices.add(name + ":");
            }
            add(choices, "", VALUE_TAGS, ":");
            choices.add("reset>");
            choices.add("newline>");
            choices.add("br>");
            completeHex(body, choices, "", false);
        }
        int end = replacementEnd(input, position);
        if (end < 0) return Optional.empty();
        StringRange range = StringRange.between(context.start + 1, end);
        String existing = range.get(input);
        List<Suggestion> matches = new ArrayList<>();
        for (String choice : choices) {
            if (choice.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(new Suggestion(range, preserveArguments(choice, existing)));
            }
        }
        return Optional.of(Suggestions.create(input, matches));
    }

    private static void argumentChoices(String body, Set<String> choices) {
        int firstColon = body.indexOf(':');
        String name = body.substring(0, firstColon).toLowerCase(Locale.ROOT);
        String stem = body.substring(0, firstColon + 1);
        if (DECORATIONS.contains(name) || (name.startsWith("!") && DECORATIONS.contains(name.substring(1)))) {
            add(choices, stem, List.of("true", "false"), ">");
            return;
        }
        switch (name) {
            case "color", "colour", "c", "shadow", "gradient", "transition" -> {
                int lastColon = body.lastIndexOf(':');
                if (List.of("color", "colour", "c").contains(name) && firstColon != lastColon) return;
                String colorStem = body.substring(0, lastColon + 1);
                String color = body.substring(lastColon + 1);
                if (!name.equals("shadow") || firstColon == lastColon) {
                    add(choices, colorStem, COLORS, ">");
                    add(choices, colorStem, HEX_COLORS, ">");
                    completeHex(color, choices, colorStem, name.equals("shadow"));
                }
                if (firstColon != lastColon) {
                    add(choices, colorStem, name.equals("shadow") ? List.of("0", "0.5", "1") : List.of("-1", "0", "0.5", "1"), ">");
                }
            }
            case "click" -> add(choices, stem, List.of(
                "open_url", "run_command", "suggest_command", "change_page", "copy_to_clipboard"
            ), ":");
            case "hover" -> add(choices, stem, List.of("show_text", "show_item", "show_entity"), ":");
            case "font" -> add(choices, stem, List.of("minecraft:default", "minecraft:uniform", "minecraft:alt"), ">");
            case "key" -> add(choices, stem, List.of(
                "key.jump", "key.sneak", "key.sprint", "key.inventory", "key.attack", "key.use", "key.chat",
                "key.drop", "key.swapOffhand", "key.forward", "key.back", "key.left", "key.right"
            ), ">");
            case "rainbow" -> add(choices, stem, List.of("!", "0", "1", "!1"), ">");
            case "pride" -> add(choices, stem, List.of(
                "pride", "progress", "trans", "bi", "pan", "nb", "lesbian", "ace", "agender", "demisexual",
                "genderqueer", "genderfluid", "intersex", "aro", "baker", "philly", "queer", "gay", "bigender",
                "demigender", "femboy"
            ), ">");
            case "selector", "sel" -> add(choices, stem, List.of("@s", "@p", "@a", "@e", "@r"), ">");
            case "nbt", "data" -> add(choices, stem, List.of("block", "entity", "storage"), ":");
            default -> { /* Free-form values are deliberately left to the user. */ }
        }
    }

    private static void completeHex(String value, Set<String> choices, String stem, boolean alpha) {
        if (value.matches("#[0-9a-fA-F]{6}") || (alpha && value.matches("#[0-9a-fA-F]{8}"))) {
            choices.add(stem + value + ">");
        }
    }

    private static void add(Set<String> choices, String stem, Iterable<String> values, String suffix) {
        for (String value : values) choices.add(stem + value + suffix);
    }

    private static String preserveArguments(String choice, String existing) {
        String lower = existing.toLowerCase(Locale.ROOT);
        if (choice.endsWith(":")) {
            // Editing a tag/action name must not discard its already typed command or hover text.
            if (lower.startsWith(choice.toLowerCase(Locale.ROOT)) && existing.length() > choice.length()) {
                return choice + existing.substring(choice.length()) + (existing.endsWith(">") ? "" : ">");
            }
        } else if (choice.endsWith(">") && choice.indexOf(':') < 0) {
            String name = choice.substring(0, choice.length() - 1);
            if (lower.startsWith(name.toLowerCase(Locale.ROOT) + ":")) {
                return name + existing.substring(name.length()) + (existing.endsWith(">") ? "" : ">");
            }
        }
        return choice;
    }

    private static Context scan(String input, int cursor) {
        int start = -1;
        char quote = 0;
        List<String> openTags = new ArrayList<>();
        for (int index = 0; index < cursor; index++) {
            char ch = input.charAt(index);
            if (quote != 0) {
                if (ch == '\\' && index + 1 < cursor && (input.charAt(index + 1) == quote || input.charAt(index + 1) == '\\')) {
                    index++;
                } else if (ch == quote) {
                    quote = 0;
                }
            } else if (start < 0) {
                if (ch == '\\' && index + 1 < cursor && (input.charAt(index + 1) == '<' || input.charAt(index + 1) == '\\')) {
                    index++;
                } else if (ch == '<') {
                    start = index;
                }
            } else if (ch == '\\' && index + 1 < cursor && input.charAt(index + 1) == '<') {
                start = -1;
                index++;
            } else if (ch == '\'' || ch == '"') {
                quote = ch;
            } else if (ch == '<') {
                start = index;
            } else if (ch == '>') {
                rememberTag(input.substring(start + 1, index), openTags);
                start = -1;
            }
        }
        return new Context(start, quote, openTags);
    }

    private static void rememberTag(String body, List<String> openTags) {
        String name = tagName(body).toLowerCase(Locale.ROOT);
        String normalized = name + body.substring(name.length());
        if (name.equals("reset")) {
            openTags.clear();
        } else if (name.startsWith("/")) {
            String closing = normalized.substring(1);
            for (int index = openTags.size() - 1; index >= 0; index--) {
                String opening = openTags.get(index);
                // MiniMessage compares actual tag names, even for aliases. Optional closing arguments
                // distinguish nested scopes of the same tag; argument values remain case-sensitive.
                if (opening.equals(closing) || opening.startsWith(closing + ":")) {
                    openTags.subList(index, openTags.size()).clear();
                    break;
                }
            }
        } else if (!body.endsWith("/") && (COLORS.contains(name) || DECORATIONS.contains(name)
            || SCOPED_TAGS.contains(name) || name.matches("#[0-9a-f]{6}") || name.equals("!shadow")
            || (name.startsWith("!") && DECORATIONS.contains(name.substring(1))))) {
            openTags.add(normalized);
        }
    }

    private static String tagName(String body) {
        int colon = body.indexOf(':');
        return colon < 0 ? body : body.substring(0, colon);
    }

    /** Replace the whole existing token when editing in its middle; never consume following message text. */
    private static int replacementEnd(String input, int cursor) {
        char quote = 0;
        for (int index = cursor; index < input.length(); index++) {
            char ch = input.charAt(index);
            if (quote != 0) {
                if (ch == '\\' && index + 1 < input.length() && (input.charAt(index + 1) == quote || input.charAt(index + 1) == '\\')) {
                    index++;
                } else if (ch == quote) {
                    quote = 0;
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                continue;
            }
            if (ch == '>') return index + 1;
            if (ch == '<' || Character.isWhitespace(ch)) return index;
        }
        return quote == 0 ? input.length() : -1;
    }

    private record Context(int start, char quote, List<String> openTags) {}
}
