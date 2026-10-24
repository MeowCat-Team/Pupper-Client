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

package cn.pupperclient.smoke;

import cn.pupperclient.management.mod.impl.render.EntityRenderOptimizerMod;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.boat.BoatModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

/**
 * Render-cost benchmark for the entity renderer work. Lives in smokeVerification only and is
 * never part of the released mod; enable with {@code -Dpupper.smoke.bench=true}.
 *
 * <p>Part 1 — minecart render-state extraction: creates a superflat world, lays a rail under the
 * player and times {@code EntityRenderer#createRenderState} for a cart parked on it (the P0
 * rail-orientation cache).
 *
 * <p>Part 2 — boat model vertex generation: times {@code Model#renderToBuffer} for a boat model,
 * which is the path the P1 scratch-vector reuse touches.
 *
 * <p>Both parts interleave short batches between the two configurations and compare medians:
 * single long averages proved far too noisy on this machine.
 */
public final class BenchMinecartRender {
    private static final int GLOBAL_WARMUP = 120_000;
    private static final int BATCHES = 120;
    private static final int BATCH_ITERATIONS = 2_000;

    private static final int MODEL_WARMUP = 20_000;
    private static final int MODEL_BATCHES = 120;
    private static final int MODEL_BATCH_ITERATIONS = 200;

    private static final int LIGHT = 15_728_880;

    private static int phase;
    private static int waited;
    private static BlockPos base;

    private BenchMinecartRender() { }

    public static boolean tick(Minecraft minecraft) {
        try {
            switch (phase) {
                case 0 -> {
                    phase = 1;
                    requestWorld(minecraft);
                    return false;
                }
                case 1 -> {
                    if (minecraft.level == null || minecraft.player == null
                            || minecraft.getSingleplayerServer() == null) {
                        return timeout("world-creation");
                    }
                    if (++waited < 40) return false;
                    base = minecraft.player.blockPosition();
                    layRails(minecraft, base);
                    waited = 0;
                    phase = 2;
                    return false;
                }
                case 2 -> {
                    if (++waited < 20) return false;   // let the block changes reach the client
                    phase = 3;
                    run(minecraft);
                    return true;
                }
                default -> {
                    return true;
                }
            }
        } catch (Throwable failure) {
            System.err.println("PUPPER_BENCH_FAILURE " + failure);
            failure.printStackTrace(System.err);
            return true;
        }
    }

    private static boolean timeout(String reason) {
        if (++waited > 1_200) {
            System.err.println("PUPPER_BENCH_FAILURE reason=" + reason + "-timeout phase=" + phase);
            return true;
        }
        return false;
    }

    private static void requestWorld(Minecraft minecraft) {
        LevelSettings settings = new LevelSettings("SmokeBench", GameType.CREATIVE,
                LevelSettings.DifficultySettings.DEFAULT, false, WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(4242L, false, false);
        Function<HolderLookup.Provider, WorldDimensions> dimensions = provider -> {
            WorldPreset flat = provider.lookupOrThrow(Registries.WORLD_PRESET)
                    .get(WorldPresets.FLAT)
                    .orElseThrow(() -> new IllegalStateException("missing minecraft:flat preset"))
                    .value();
            return flat.createWorldDimensions();
        };
        System.out.println("PUPPER_BENCH_WORLD_REQUEST");
        minecraft.createWorldOpenFlows().createFreshLevel("SmokeBench", settings, options, dimensions, null);
    }

    private static void layRails(Minecraft minecraft, BlockPos base) {
        ServerLevel server = minecraft.getSingleplayerServer().overworld();
        for (int dx = -4; dx <= 4; dx++) {
            server.setBlock(base.offset(dx, 0, 0), Blocks.RAIL.defaultBlockState(), 3);
        }
    }

    private static void run(Minecraft minecraft) throws Exception {
        EntityRenderOptimizerMod mod = EntityRenderOptimizerMod.getInstance();
        if (mod == null) throw new IllegalStateException("optimizer mod not initialised");
        boolean originalEnabled = mod.isEnabled();

        JsonObject report = new JsonObject();
        measureExtraction(minecraft, mod, report);
        measureModelVertices(minecraft, mod, report);

        mod.setEnabled(originalEnabled);

        String json = report.toString();
        Path game = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        Files.writeString(game.resolve("pupper-smoke-bench.json"), json, StandardCharsets.UTF_8);
        System.out.println("PUPPER_BENCH_RESULT " + json);
        System.out.flush();
    }

    private static void measureExtraction(Minecraft minecraft, EntityRenderOptimizerMod mod, JsonObject report) {
        double x = base.getX() + 0.5;
        double y = base.getY() + 0.0625;
        double z = base.getZ() + 0.5;

        Minecart cart = new Minecart(EntityTypes.MINECART, minecraft.level);
        // A detached entity has no id yet, and extractRenderState derives its render offset seed from it.
        cart.setId(1);
        cart.setPos(x, y, z);
        // extractRenderState samples Mth.lerp(partialTicks, xOld, x); without this the sample
        // lands half-way to the origin and never sits on a rail.
        cart.xOld = x;
        cart.yOld = y;
        cart.zOld = z;

        MinecartBehavior behavior = cart.getBehavior();
        if (!(behavior instanceof OldMinecartBehavior oldBehavior)) {
            report.addProperty("extractionSkipped", "not-old-behavior");
            return;
        }
        Vec3 rail = oldBehavior.getPos(x, y, z);
        if (rail == null) {
            report.addProperty("extractionSkipped", "no-rail-under-sample");
            return;
        }

        EntityRenderer renderer = minecraft.getEntityRenderDispatcher().getRenderer(cart);
        try {
            MinecartRenderState probe = (MinecartRenderState) ((EntityRenderer) renderer).createRenderState(cart, 0.5F);
            if (probe.posOnRail == null) {
                report.addProperty("extractionSkipped", "sample-not-on-rail");
                return;
            }
        } catch (RuntimeException unsupported) {
            report.addProperty("extractionSkipped", unsupported.toString());
            return;
        }

        mod.setEnabled(false);
        runExtractBatch(renderer, cart, GLOBAL_WARMUP);
        mod.setEnabled(true);
        runExtractBatch(renderer, cart, GLOBAL_WARMUP);

        long[] vanilla = new long[BATCHES];
        long[] optimized = new long[BATCHES];
        for (int i = 0; i < BATCHES; i++) {
            mod.setEnabled(false);
            vanilla[i] = runExtractBatch(renderer, cart, BATCH_ITERATIONS) / BATCH_ITERATIONS;
            mod.setEnabled(true);
            optimized[i] = runExtractBatch(renderer, cart, BATCH_ITERATIONS) / BATCH_ITERATIONS;
        }

        long vanillaMedian = percentile(vanilla, 50);
        long optimizedMedian = percentile(optimized, 50);
        report.addProperty("extractVanillaNs", vanillaMedian);
        report.addProperty("extractOptimizedNs", optimizedMedian);
        report.addProperty("extractVanillaP10Ns", percentile(vanilla, 10));
        report.addProperty("extractOptimizedP10Ns", percentile(optimized, 10));
        report.addProperty("extractSavingPercent", savingPercent(vanillaMedian, optimizedMedian));

        // How much of the remaining extraction cost is the per-entity shadow block scan?
        boolean shadowsWereOn = minecraft.options.entityShadows().get();
        long[] noShadow = new long[BATCHES];
        try {
            minecraft.options.entityShadows().set(false);
            mod.setEnabled(false);
            runExtractBatch(renderer, cart, GLOBAL_WARMUP);
            for (int i = 0; i < BATCHES; i++) {
                noShadow[i] = runExtractBatch(renderer, cart, BATCH_ITERATIONS) / BATCH_ITERATIONS;
            }
        } finally {
            minecraft.options.entityShadows().set(shadowsWereOn);
            mod.setEnabled(true);
        }
        long noShadowMedian = percentile(noShadow, 50);
        report.addProperty("extractNoShadowNs", noShadowMedian);
        report.addProperty("shadowScanNs", Math.max(0L, vanillaMedian - noShadowMedian));
        report.addProperty("shadowScanPercent", savingPercent(vanillaMedian, noShadowMedian));
    }

    private static void measureModelVertices(Minecraft minecraft, EntityRenderOptimizerMod mod, JsonObject report) {
        EntityModelSet models = minecraft.getEntityModels();
        BoatModel model = new BoatModel(models.bakeLayer(ModelLayers.OAK_BOAT));
        BoatRenderState state = new BoatRenderState();
        PoseStack pose = new PoseStack();
        pose.translate(0.25, 1.5, -0.75);
        pose.mulPose(Axis.YP.rotationDegrees(37.0F));
        CountingVertexConsumer sink = new CountingVertexConsumer();

        mod.setEnabled(false);
        runModelBatch(model, state, pose, sink, MODEL_WARMUP);
        mod.setEnabled(true);
        runModelBatch(model, state, pose, sink, MODEL_WARMUP);

        long[] vanilla = new long[MODEL_BATCHES];
        long[] optimized = new long[MODEL_BATCHES];
        for (int i = 0; i < MODEL_BATCHES; i++) {
            mod.setEnabled(false);
            vanilla[i] = runModelBatch(model, state, pose, sink, MODEL_BATCH_ITERATIONS) / MODEL_BATCH_ITERATIONS;
            mod.setEnabled(true);
            optimized[i] = runModelBatch(model, state, pose, sink, MODEL_BATCH_ITERATIONS) / MODEL_BATCH_ITERATIONS;
        }

        long vanillaMedian = percentile(vanilla, 50);
        long optimizedMedian = percentile(optimized, 50);
        report.addProperty("modelVanillaNs", vanillaMedian);
        report.addProperty("modelOptimizedNs", optimizedMedian);
        report.addProperty("modelVanillaP10Ns", percentile(vanilla, 10));
        report.addProperty("modelOptimizedP10Ns", percentile(optimized, 10));
        report.addProperty("modelSavingPercent", savingPercent(vanillaMedian, optimizedMedian));
        report.addProperty("modelVertices", sink.vertices);
        report.addProperty("modelChecksum", sink.checksum);
    }

    private static long runExtractBatch(EntityRenderer renderer, Minecart cart, int iterations) {
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            MinecartRenderState state = (MinecartRenderState) ((EntityRenderer) renderer).createRenderState(cart, 0.5F);
            if (state == null) throw new IllegalStateException("no render state");
        }
        return System.nanoTime() - start;
    }

    private static long runModelBatch(BoatModel model, BoatRenderState state, PoseStack pose,
                                      CountingVertexConsumer sink, int iterations) {
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            model.setupAnim(state);
            model.renderToBuffer(pose, sink, LIGHT, OverlayTexture.NO_OVERLAY, -1);
        }
        return System.nanoTime() - start;
    }

    private static long percentile(long[] values, int percent) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        int index = Math.min(sorted.length - 1, Math.max(0, sorted.length * percent / 100));
        return sorted[index];
    }

    private static double savingPercent(long vanilla, long optimized) {
        return Math.round(1000.0 * (vanilla - optimized) / Math.max(1L, vanilla)) / 10.0;
    }

    /** Absorbs every vertex into a checksum so the JIT cannot delete the transform maths. */
    private static final class CountingVertexConsumer implements VertexConsumer {
        private long vertices;
        private float checksum;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.vertices++;
            this.checksum += x + y + z;
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.checksum += x + y + z;
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }
}
