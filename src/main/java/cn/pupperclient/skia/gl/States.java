/*
 * This file is part of https://github.com/Lyzev/Skija.
 *
 * Copyright (c) 2024-2025. Lyzev
 *
 * Skija is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License, or
 * (at your option) any later version.
 *
 * Skija is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Skija. If not, see <https://www.gnu.org/licenses/>.
 */

package cn.pupperclient.skia.gl;

import org.lwjgl.opengl.GL30.*;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.lwjgl.opengl.GL11.glGetIntegerv;
import static org.lwjgl.opengl.GL30C.GL_MAJOR_VERSION;
import static org.lwjgl.opengl.GL30C.GL_MINOR_VERSION;

/**
 * Stores and restores OpenGL states.
 */
public final class States {

    /**
     * The current OpenGL version.
     */
    private static final int GL_VERSION;

    /**
     * The stack of OpenGL states.
     */
    private static final Deque<State> STATES = new ArrayDeque<>();
    private static final Deque<State> AVAILABLE = new ArrayDeque<>();

    private States() {
        // private constructor to prevent instantiation
    }

    /**
     * Pushes the current OpenGL state onto the stack.
     */
    public static void push() {
        State state = AVAILABLE.pollFirst();
        STATES.push((state == null ? new State(GL_VERSION) : state).push());
    }

    /**
     * Pops the last OpenGL state from the stack and restores it.
     */
    public static void pop() {
        if (STATES.isEmpty()) {
            throw new IllegalStateException("No state to restore.");
        }
        State state = STATES.pop();
        try {
            state.pop();
        } finally {
            AVAILABLE.push(state);
        }
    }

    static {
        int[] major = new int[1];
        int[] minor = new int[1];
        glGetIntegerv(GL_MAJOR_VERSION, major);
        glGetIntegerv(GL_MINOR_VERSION, minor);
        GL_VERSION = major[0] * 100 + minor[0] * 10;
    }
}
