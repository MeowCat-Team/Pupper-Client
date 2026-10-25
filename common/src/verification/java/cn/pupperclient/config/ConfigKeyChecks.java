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

package cn.pupperclient.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonObject;

import cn.pupperclient.management.mod.Mod;
import cn.pupperclient.management.mod.ModCategory;
import cn.pupperclient.management.mod.ModNames;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.language.Language;

/**
 * Run with gradlew verifyConfigKeys; no game window or third-party test runner needed.
 *
 * <p>Covers the regression where saved settings were keyed by the translated mod name, so
 * switching language made every toggle, position, setting and keybind unreachable.
 */
public final class ConfigKeyChecks {

    private static final List<String> RAW_NAMES = Arrays.asList(
        "mod.fpsdisplay.name",
        "mod.arraylist.name",
        "mod.coords.name",
        "mod.nohurtfov.name",
        "mod.nohurtfov.nohurtFov.name");

    public static void main(String[] args) {
        I18n.setLanguage(Language.ENGLISH);

        stableKeysAreLanguageIndependent();
        translatedNamesAreNotStableKeys();
        legacyChineseKeysResolveInEnglish();
        legacyEnglishKeysResolveInChinese();
        ambiguousTranslatedNamesAreRejected();
        stableEntryWinsOverLegacyEntry();
        legacyEntryIsFoundWhenNoStableEntryExists();
        unknownKeyResolvesToNothing();
        rewrittenConfigSurvivesASecondLanguageSwitch();

        System.out.println("Config key checks passed");
    }

    /** The key written to disk is the translation key, identical in every language. */
    private static void stableKeysAreLanguageIndependent() {
        for (String rawName : RAW_NAMES) {
            String english = ModNames.configKey(rawName);
            I18n.setLanguage(Language.CHINESE);
            String chinese = ModNames.configKey(rawName);
            I18n.setLanguage(Language.ENGLISH);
            require(english.equals(rawName), "Stable key must be the translation key: " + english);
            require(chinese.equals(english), "Stable key changed with the language: " + english + " vs " + chinese);
        }
    }

    /** Guards the actual defect: a translated name must never be used as the stored key. */
    private static void translatedNamesAreNotStableKeys() {
        for (String rawName : RAW_NAMES) {
            for (Language language : Language.values()) {
                String translated = I18n.get(language, rawName);
                if (translated.equals(rawName)) {
                    continue;
                }
                require(!ModNames.configKey(rawName).equals(translated),
                    "Stable key must not equal the " + language + " translation: " + translated);
                require(!ModNames.configKey(rawName).equals(translated),
                    "Translated name must not be a stable key: " + translated);
            }
        }
    }

    /** A config saved while the game was in Chinese is readable while running in English. */
    private static void legacyChineseKeysResolveInEnglish() {
        I18n.setLanguage(Language.CHINESE);
        String chineseName = I18n.get("mod.fpsdisplay.name");
        require(!chineseName.equals("mod.fpsdisplay.name"), "Chinese translation must exist");

        I18n.setLanguage(Language.ENGLISH);
        require("mod.fpsdisplay.name".equals(ModNames.resolveRawName(RAW_NAMES, chineseName)),
            "Legacy Chinese key must resolve back to its mod while running in English");
        require("mod.fpsdisplay.name".equals(ModNames.resolveRawName(RAW_NAMES, "mod.fpsdisplay.name")),
            "Stable key must resolve to itself");
    }

    /** The reverse direction: an English config survives switching to Chinese. */
    private static void legacyEnglishKeysResolveInChinese() {
        I18n.setLanguage(Language.ENGLISH);
        String englishName = I18n.get("mod.coords.name");
        require(!englishName.equals("mod.coords.name"), "English translation must exist");

        I18n.setLanguage(Language.CHINESE);
        require("mod.coords.name".equals(ModNames.resolveRawName(RAW_NAMES, englishName)),
            "Legacy English key must resolve back to its mod while running in Chinese");
        I18n.setLanguage(Language.ENGLISH);
    }

    /**
     * Two mods share a translated name in the shipped language files. Such a name must not be
     * accepted as a migration key, or one mod would inherit the other's settings.
     */
    private static void ambiguousTranslatedNamesAreRejected() {
        I18n.setLanguage(Language.ENGLISH);
        String shared = I18n.get("mod.nohurtfov.name");
        String alias = I18n.get("mod.nohurtfov.nohurtFov.name");
        require(shared.equals(alias), "Fixture expects these two keys to share a display name");

        require(ModNames.resolveRawName(RAW_NAMES, shared) == null,
            "Ambiguous translated name must not resolve to a mod");
        require(!ModNames.legacyKeys("mod.nohurtfov.name", RAW_NAMES).contains(shared),
            "Ambiguous translated name must not be offered as a migration key");
    }

    /** A stable entry present on disk always wins over a stale translated one. */
    private static void stableEntryWinsOverLegacyEntry() {
        I18n.setLanguage(Language.ENGLISH);
        List<Mod> mods = fixtureMods();
        String legacy = I18n.get("mod.fpsdisplay.name");
        require(!legacy.equals("mod.fpsdisplay.name"), "English translation must exist");

        JsonObject modsJson = new JsonObject();
        JsonObject stableEntry = new JsonObject();
        stableEntry.addProperty("enabled", true);
        modsJson.add("mod.fpsdisplay.name", stableEntry);
        JsonObject legacyEntry = new JsonObject();
        legacyEntry.addProperty("enabled", false);
        modsJson.add(legacy, legacyEntry);

        List<String> migrated = new ArrayList<>();
        JsonObject found = ModNames.find(modsJson, mods.getFirst(), mods, migrated);
        require(found != null && found.get("enabled").getAsBoolean(), "Stable entry must win");
        require(migrated.isEmpty(), "A stale legacy entry must not be reported as migrated");
    }

    /** With only a translated entry on disk, it is found and reported for rewriting. */
    private static void legacyEntryIsFoundWhenNoStableEntryExists() {
        I18n.setLanguage(Language.ENGLISH);
        List<Mod> mods = fixtureMods();
        String legacy = I18n.get("mod.fpsdisplay.name");

        JsonObject modsJson = new JsonObject();
        JsonObject legacyEntry = new JsonObject();
        legacyEntry.addProperty("enabled", true);
        modsJson.add(legacy, legacyEntry);

        List<String> migrated = new ArrayList<>();
        JsonObject found = ModNames.find(modsJson, mods.getFirst(), mods, migrated);
        require(found != null && found.get("enabled").getAsBoolean(), "Legacy entry must be found");
        require(migrated.equals(List.of(legacy)), "Legacy key must be reported for migration");
    }

    /** A key that belongs to no mod must not silently match one. */
    private static void unknownKeyResolvesToNothing() {
        require(ModNames.resolveRawName(RAW_NAMES, "mod.doesnotexist.name") == null,
            "Unknown key must not resolve");
        require(ModNames.resolveRawName(RAW_NAMES, "FPS Display 2") == null,
            "Unknown display name must not resolve");
    }

    /**
     * End to end: a config written in Chinese is migrated while running in English, rewritten
     * under stable keys, and still readable after switching back to Chinese.
     */
    private static void rewrittenConfigSurvivesASecondLanguageSwitch() {
        I18n.setLanguage(Language.CHINESE);
        List<Mod> mods = fixtureMods();

        JsonObject savedInChinese = new JsonObject();
        for (Mod mod : mods) {
            String translated = I18n.get(mod.getRawName());
            if (translated.equals(mod.getRawName())) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", true);
            savedInChinese.add(translated, entry);
        }
        require(savedInChinese.size() > 0, "Fixture must contain translated entries");

        // Next start: the language is English until the player's choice is applied.
        I18n.setLanguage(Language.ENGLISH);
        int migratedCount = 0;
        for (Mod mod : mods) {
            List<String> migrated = new ArrayList<>();
            if (ModNames.find(savedInChinese, mod, mods, migrated) != null) {
                migratedCount++;
            }
        }
        require(migratedCount > 0, "Legacy entries must be discoverable for migration");

        // Saving rewrites every entry under the stable key.
        JsonObject rewritten = new JsonObject();
        for (Mod mod : mods) {
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", true);
            rewritten.add(ModNames.configKey(mod), entry);
        }

        I18n.setLanguage(Language.CHINESE);
        for (Mod mod : mods) {
            require(rewritten.has(ModNames.configKey(mod)),
                "Rewritten config must be readable in Chinese: " + mod.getRawName());
        }
        I18n.setLanguage(Language.ENGLISH);
    }

    private static List<Mod> fixtureMods() {
        List<Mod> mods = new ArrayList<>();
        for (String rawName : RAW_NAMES) {
            mods.add(new Mod(rawName, rawName + ".description", "icon", ModCategory.HUD));
        }
        return mods;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
