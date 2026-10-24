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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class I18n {

	private static final Map<String, String> translateMap = new HashMap<>();
	private static final Map<String, String> fallbackMap = new HashMap<>();
	private static Language currentLanguage;

	private I18n() {
	}

	public static void setLanguage(Language language) {
		currentLanguage = language;
		load(language, translateMap);
		if (language != Language.ENGLISH) {
			load(Language.ENGLISH, fallbackMap);
		} else {
			fallbackMap.clear();
		}
	}

	private static void load(Language language, Map<String, String> map) {

		String resourcePath = String.format("assets/pupper/languages/%s.lang", language.getId());
		map.clear();

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
		return translateMap.getOrDefault(key, fallbackMap.getOrDefault(key, key));
	}

	public static Language getCurrentLanguage() {
		return currentLanguage;
	}
}
