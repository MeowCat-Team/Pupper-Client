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

package cn.pupperclient.skia.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import io.github.humbleui.skija.Data;

public class SkiaUtils {

	public static Optional<byte[]> convertToBytes(String path) {
		try (InputStream inputStream = getResourceAsStream(path)) {
			return Optional.of(inputStream.readAllBytes());
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	public static Optional<Data> convertToData(String path) {
		return convertToBytes(path).map(Data::makeFromBytes);
	}

	public static InputStream getResourceAsStream(String path) {
		InputStream inputStream = SkiaUtils.class.getResourceAsStream(path);
		if (inputStream == null) {
			throw new IllegalArgumentException("Resource not found: " + path);
		}
		return inputStream;
	}
}
