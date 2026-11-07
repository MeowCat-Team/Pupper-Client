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

package cn.pupperclient.hud;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import cn.pupperclient.skia.context.SkiaContext;

/**
 * Checks that a HUD module declares at most one frame listener.
 *
 * <p>The bus walks the superclass chain, so a subclass that re-declares
 * {@code onRenderSkia} registers a second listener on top of the base class's one and
 * draws its panel twice. That is invisible in a single frame and easy to reintroduce.
 */
public final class HudListenerChecks {
    private static final String BASE = "cn/pupperclient/management/mod/api/hud/";
    private static final String LISTENER = "onRenderSkia";
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path classes = Path.of(SkiaContext.class.getProtectionDomain().getCodeSource()
            .getLocation().toURI()).resolve("cn/pupperclient/management/mod");
        List<String> offenders = new ArrayList<>();
        int scanned = 0;

        try (var files = Files.walk(classes)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                String name = file.getFileName().toString();
                if (name.contains("$")) continue;
                scanned++;
                check(file, offenders);
            }
        }

        require(scanned > 50, "HUD class scan was incomplete");
        require(offenders.isEmpty(), "Subclass declares its own frame listener: " + offenders);
        System.out.println("HUD listener checks passed: " + scanned + " classes, one listener each.");
    }

    /** Records a subclass that re-declares the listener while inheriting the base one. */
    private static void check(Path file, List<String> offenders) throws Exception {
        String[] own = {null};
        String[] parent = {null};
        boolean[] declares = {false};

        try (InputStream source = Files.newInputStream(file)) {
            new ClassReader(source).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature,
                                  String superName, String[] interfaces) {
                    own[0] = name;
                    parent[0] = superName;
                }

                @Override
                public FieldVisitor visitField(int access, String name, String descriptor,
                                               String signature, Object value) {
                    if (name.equals(LISTENER)) declares[0] = true;
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        if (!declares[0] || own[0] == null) return;
        // The base class itself is allowed to declare it.
        if ((BASE + "SimpleHUDMod").equals(own[0])) return;
        if (inheritsSimpleHudMod(parent[0])) offenders.add(own[0]);
    }

    /** Walks the compiled superclass chain looking for SimpleHUDMod. */
    private static boolean inheritsSimpleHudMod(String start) throws Exception {
        String current = start;
        while (current != null && !current.equals("java/lang/Object")) {
            if ((BASE + "SimpleHUDMod").equals(current)) return true;
            String resource = current + ".class";
            try (InputStream source = HudListenerChecks.class.getClassLoader()
                .getResourceAsStream(resource)) {
                if (source == null) return false;
                String[] next = {null};
                new ClassReader(source).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(int version, int access, String name, String signature,
                                      String superName, String[] interfaces) {
                        next[0] = superName;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                current = next[0];
            }
        }
        return false;
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
