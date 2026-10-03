package cn.pupperclient.hud;

import cn.pupperclient.ui.render.BlazeUiRenderer;
import cn.pupperclient.skia.font.Fonts;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;
import java.util.Arrays;
import static org.lwjgl.opengl.GL33C.*;

/** Optional UI benchmark. Native timer queries belong only to this verification source set. */
public final class GlassGpuBenchmark {
    private static final float[][] PANELS = {
        {16,16,150,700}, {1110,24,340,64}, {2336,28,192,76}, {2300,136,54,54},
        {2238,198,54,54}, {2300,198,54,54}, {2362,198,54,54}, {2238,260,178,38},
        {2250,1340,250,92}, {24,1450,280,56}, {950,1430,280,78}, {2000,380,210,92}
    };
    public static void main(String[] args) {
        try (var fixture = new UiGpuFixture(); var target = new UiGpuFixture.Target(2560,1540); var renderer = new BlazeUiRenderer();
             var text = new Paint().setColor(0xFFF0F2F8); var tint = new Paint().setColor(0x731C1F26)) {
            var font = Fonts.getRegular(14);
            for (boolean menu : new boolean[]{false,true}) for (boolean glass : new boolean[]{false,true}) {
                long[] cpu = new long[120], gpu = new long[120]; int query = glGenQueries();
                try {
                    for (int frame = -30; frame < 120; frame++) {
                        target.clear(.2f,.4f,.6f,0);
                        glBeginQuery(GL_TIME_ELAPSED,query); long start = System.nanoTime();
                        renderer.draw(target.view, canvas -> {
                            for (int i=0;i<PANELS.length+(menu?1:0);i++) {
                                float[] p = i<PANELS.length?PANELS[i]:new float[]{980,570,600,400};
                                if(glass) canvas.drawGlass(p[0],p[1],p[2],p[3],16,1.5f,1.75f);
                                canvas.drawRRect(RRect.makeXYWH(p[0],p[1],p[2],p[3],16),tint);
                                for(int row=0;row<(i==0?24:2);row++) canvas.drawString("Pupper Client 144 FPS",p[0]+10,p[1]+24+row*24,font,text);
                            }
                        }, glass);
                        long elapsed = System.nanoTime()-start;
                        glEndQuery(GL_TIME_ELAPSED); UiGpuFixture.finish();
                        long measured = glGetQueryObjectui64(query,GL_QUERY_RESULT);
                        if(frame>=0) {cpu[frame]=elapsed;gpu[frame]=measured;}
                    }
                } finally {glDeleteQueries(query);}
                Arrays.sort(cpu);Arrays.sort(gpu);
                System.out.printf("%s %s: CPU median/p95 %.3f/%.3f ms; GPU median/p95 %.3f/%.3f ms; asset rasterizations %d%n",
                        menu?"HUD+menu":"HUD",glass?"glass":"tint",cpu[60]/1e6,cpu[114]/1e6,gpu[60]/1e6,gpu[114]/1e6,renderer.assetRasterizations());
            }
        }
    }
}
