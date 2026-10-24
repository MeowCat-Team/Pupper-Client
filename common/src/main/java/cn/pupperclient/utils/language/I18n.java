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

package cn.pupperclient.utils.language;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class I18n {

	private static final Map<Language, Map<String, String>> bundles = new EnumMap<>(Language.class);
	private static Language currentLanguage;

	private I18n() {
	}

	public static void setLanguage(Language language) {
		currentLanguage = language;
		bundle(language);
		if (language != Language.ENGLISH) {
			bundle(Language.ENGLISH);
		}
	}

	private static Map<String, String> bundle(Language language) {
		return bundles.computeIfAbsent(language, key -> {
			Map<String, String> map = new HashMap<>();
			load(key, map);
			return map;
		});
	}

	private static void load(Language language, Map<String, String> map) {

		String resourcePath = String.format("assets/pupper/languages/%s.lang", language.getId());

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            Objects.requireNonNull(I18n.class.getClassLoader().getResourceAsStream(resourcePath), "Language file not found: " + resourcePath), StandardCharsets.UTF_8))) {

			reader.lines().filter(line -> !line.isEmpty() && !line.startsWith("#")).map(line -> line.split("=", 2))
					.filter(parts -> parts.length == 2)
					.forEach(parts -> map.put(parts[0].trim(), parts[1].trim()));
		} catch (Exception e) {
            cn.pupperclient.PupperLogger.error("I18n", "Failed to load language: " + language.getId(), e);
		}
	}

	public static String get(String key) {
		if (currentLanguage == null) {
			return key;
		}
		String translated = bundle(currentLanguage).get(key);
		if (translated != null) {
			return translated;
		}
		return currentLanguage == Language.ENGLISH ? key : bundle(Language.ENGLISH).getOrDefault(key, key);
	}

	/** Reads a translation without changing the active language. */
	public static String get(Language language, String key) {
		return bundle(language).getOrDefault(key, key);
	}

	public static Language getCurrentLanguage() {
		return currentLanguage;
	}
}
