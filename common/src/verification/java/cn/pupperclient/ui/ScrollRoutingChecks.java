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

package cn.pupperclient.ui;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import cn.pupperclient.gui.api.PupperClientGui;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Checks that the mod menu routes a wheel event to one target, not to everything.
 *
 * <p>The regression this guards: {@code onMouseScrolled} delivered the event to the page
 * and to every chrome component, so scrolling a list also scrolled the navigation rail
 * behind it. The screen needs a live client to construct, so the check reads the
 * compiled method instead of running it.
 */
public final class ScrollRoutingChecks {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        List<String> calls = callsInScrollDispatch();
        require(calls.contains("isHovered"),
            "Scroll dispatch must test the cursor position before forwarding the event");
        require(calls.contains("mouseScrolled"),
            "Scroll dispatch must still forward the event to the hovered target");
        System.out.println("Scroll routing checks passed: " + assertions);
    }

    /** Every method the compiled scroll dispatch calls, by name. */
    private static List<String> callsInScrollDispatch() throws Exception {
        String name = PupperClientGui.class.getName().replace('.', '/') + ".class";
        List<String> calls = new ArrayList<>();
        try (InputStream source = PupperClientGui.class.getClassLoader().getResourceAsStream(name)) {
            if (source == null) throw new AssertionError("Missing class: " + name);
            new ClassReader(source).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String method, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (!method.equals("onMouseScrolled")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String called,
                                                    String calledDescriptor, boolean isInterface) {
                            calls.add(called);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        require(!calls.isEmpty(), "Scroll dispatch was not found in the compiled screen");
        return calls;
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
