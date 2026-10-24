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

package cn.pupperclient.utils.file;

import java.io.File;
import java.nio.ByteBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.nfd.NFDFilterItem;
import org.lwjgl.util.nfd.NativeFileDialog;

import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;

public class FileDialog {

	public static ObjectObjectImmutablePair<Boolean, File> chooseFile(String name, String... extensions) {

		try (MemoryStack stack = MemoryStack.stackPush()) {

			NFDFilterItem.Buffer filters = NFDFilterItem.malloc(1);

			filters.get(0).name(stack.UTF8(name)).spec(stack.UTF8(String.join(",", extensions)));

			PointerBuffer path = stack.mallocPointer(1);

			return ObjectObjectImmutablePair.of(
					isSuccess(NativeFileDialog.NFD_OpenDialog(path, filters, (ByteBuffer) null)),
					new File(path.getStringUTF8(0)));
		}
	}

	public static ObjectObjectImmutablePair<Boolean, File> chooseFolder() {

		PointerBuffer path = MemoryUtil.memAllocPointer(1);

		try {
			return ObjectObjectImmutablePair.of(isSuccess(NativeFileDialog.NFD_PickFolder(path, (ByteBuffer) null)),
					new File(path.getStringUTF8(0)));
		} finally {
			MemoryUtil.memFree(path);
		}
	}

	private static boolean isSuccess(int result) {
		return result == NativeFileDialog.NFD_OKAY;
	}
}
