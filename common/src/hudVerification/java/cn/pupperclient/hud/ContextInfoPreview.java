package cn.pupperclient.hud;

import cn.pupperclient.libraries.material3.hct.Hct;
import cn.pupperclient.management.color.api.ColorPalette;
import cn.pupperclient.management.mod.api.hud.design.ContextInfoHudUi;
import cn.pupperclient.management.mod.api.hud.design.HUDColors;
import cn.pupperclient.management.mod.api.hud.design.HUDDesign;
import cn.pupperclient.management.mod.api.hud.design.HUDText;
import cn.pupperclient.skia.Skia;
import cn.pupperclient.skia.context.SkiaContext;
import cn.pupperclient.skia.font.Icon;
import cn.pupperclient.utils.language.I18n;
import cn.pupperclient.utils.language.Language;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.EncodedImageFormat;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.Surface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Offscreen production-painter specimens; Minecraft supplies item sprites in the game. */
public final class ContextInfoPreview {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]); Files.createDirectories(output.getParent());
        var field = SkiaContext.class.getDeclaredField("surface"); field.setAccessible(true);
        var previous = field.get(null);
        try (Surface surface = Surface.makeRasterN32Premul(1120, 720)) {
            field.set(null, surface);
            var canvas = surface.getCanvas(); canvas.clear(0xff59646b); canvas.scale(2, 2);
            for (boolean dark : new boolean[] { false, true }) {
                I18n.setLanguage(dark ? Language.CHINESE : Language.ENGLISH);
                var palette = new ColorPalette(Hct.from(220, 26, 60), dark);
                var colors = HUDColors.from(palette, palette.getSurfaceContainer());
                var design = new HUDDesign("specimen") {
                    @Override public void drawBackground(float x, float y, float w, float h, float radius) {
                        Skia.drawRoundedRect(x, y, w, h, radius, colors.surface());
                    }
                };
                float x = dark ? 292 : 16, y = 16;
                var names = dark ? List.of("钻石剑", "圆石", "小麦", "村民")
                    : List.of("Diamond Sword", "Cobblestone", "Wheat", "Villager");
                var details = List.of(List.of("sharpness", "unbreaking", "mending", "knockback", "looting", "fire_aspect").stream()
                        .map(key -> I18n.get("hud.enchantment." + key) + " III").collect(java.util.stream.Collectors.joining(" · ")),
                    I18n.get("hud.iteminfo.total") + ": 2304",
                    "Minecraft · " + I18n.get("hud.lookinfo.growth") + ": 71%",
                    "Minecraft · " + I18n.get("hud.lookinfo.health") + ": 18.0 / 20.0");
                for (int i = 0; i < names.size(); i++) {
                    var layout = ContextInfoHudUi.layout(names.get(i), List.of(details.get(i)), 252);
                    int before = canvas.getSaveCount();
                    ContextInfoHudUi.draw(x, y, layout, i < 2 ? Icon.INVENTORY_2 : i == 2 ? Icon.BOX : Icon.PERSON,
                        false, design, colors);
                    if (canvas.getSaveCount() != before) throw new AssertionError("Painter leaked its transform/clip stack");
                    y += layout.height() + 20;
                }
            }
            try (Image image = surface.makeImageSnapshot(); Data png = image.encodeToData(EncodedImageFormat.PNG)) {
                if (png == null) throw new IllegalStateException("PNG encoding failed");
                Files.write(output, png.getBytes());
            }
        } finally { field.set(null, previous); HUDText.releaseResources(); }
        System.out.println("Context HUD specimens: " + output);
    }
}
