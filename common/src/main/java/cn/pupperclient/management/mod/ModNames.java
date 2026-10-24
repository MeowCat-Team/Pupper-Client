/*
 * Copyright (C) 2024-2026 Pupper Client contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package cn.pupperclient.management.mod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.language.Language;

/**
 * Maps mods to the keys used in saved configuration.
 *
 * <p>Configuration must never be keyed by a translated string: the display name changes with the
 * selected language, so a key written in one language cannot be found after the player switches to
 * another. The stable key is the mod's translation key ({@link Mod#getRawName()}), which is
 * identical in every language.
 *
 * <p>Configs written before that rule still hold translated names. {@link #find} and
 * {@link #resolve} accept those legacy keys, so an existing installation migrates on its next save
 * instead of losing every setting and keybind.
 */
public final class ModNames {

    private ModNames() {
    }

    /** The stable, language-independent configuration key for a mod. */
    public static String configKey(Mod mod) {
        return configKey(mod.getRawName());
    }

    /** The stable configuration key for a mod's translation key. */
    public static String configKey(String rawName) {
        return rawName;
    }

    /**
     * Translated names this mod may have been saved under by older builds.
     *
     * <p>Only names that resolve back to this single mod are returned: two mods can share a
     * translated name (the language files contain such duplicates), and migrating on an ambiguous
     * name would apply one mod's settings to another.
     */
    public static List<String> legacyKeys(Mod mod, List<Mod> allMods) {
        List<String> rawNames = new ArrayList<>(allMods.size());
        for (Mod candidate : allMods) {
            rawNames.add(candidate.getRawName());
        }
        return legacyKeys(mod.getRawName(), rawNames);
    }

    /** Translated names {@code rawName} may have been saved under, in any supported language. */
    public static List<String> legacyKeys(String rawName, Collection<String> allRawNames) {
        List<String> keys = new ArrayList<>();
        for (Language language : Language.values()) {
            String translated = I18n.get(language, rawName);
            if (translated.equals(rawName) || keys.contains(translated)) {
                continue;
            }
            if (countByDisplayName(allRawNames, translated) == 1) {
                keys.add(translated);
            }
        }
        return keys;
    }

    /**
     * Returns the JSON object holding this mod's settings, or {@code null} when it has none.
     *
     * <p>The stable key wins. A legacy translated key is only consulted when no entry exists for
     * the stable key; a match is appended to {@code migratedKeys} so the caller can report it.
     */
    public static JsonObject find(JsonObject modsJson, Mod mod, List<Mod> allMods, List<String> migratedKeys) {
        JsonObject stable = object(modsJson, configKey(mod));
        if (stable != null) {
            return stable;
        }
        for (String legacy : legacyKeys(mod, allMods)) {
            JsonObject migrated = object(modsJson, legacy);
            if (migrated != null) {
                migratedKeys.add(legacy);
                return migrated;
            }
        }
        return null;
    }

    /** Resolves a saved key back to a mod: stable key first, then a legacy translated name. */
    public static Mod resolve(ModManager modManager, String key) {
        List<String> rawNames = new ArrayList<>(modManager.getMods().size());
        for (Mod mod : modManager.getMods()) {
            rawNames.add(mod.getRawName());
        }
        String rawName = resolveRawName(rawNames, key);
        return rawName == null ? null : modManager.getModByRawName(rawName);
    }

    /** Resolves a saved key back to a mod's translation key, or {@code null} when ambiguous. */
    public static String resolveRawName(Collection<String> allRawNames, String key) {
        if (allRawNames.contains(key)) {
            return key;
        }
        String match = null;
        for (String rawName : allRawNames) {
            if (!isTranslatedName(rawName, key)) {
                continue;
            }
            if (match != null && !match.equals(rawName)) {
                return null;
            }
            match = rawName;
        }
        return match;
    }

    private static boolean isTranslatedName(String rawName, String candidate) {
        for (Language language : Language.values()) {
            if (I18n.get(language, rawName).equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject object(JsonObject parent, String key) {
        if (parent == null || !parent.has(key)) {
            return null;
        }
        JsonElement element = parent.get(key);
        return element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static int countByDisplayName(Collection<String> allRawNames, String displayName) {
        int count = 0;
        for (String rawName : allRawNames) {
            if (isTranslatedName(rawName, displayName)) {
                count++;
            }
        }
        return count;
    }
}
