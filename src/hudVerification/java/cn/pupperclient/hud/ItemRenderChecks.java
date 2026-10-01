package cn.pupperclient.hud;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import cn.pupperclient.utils.render.ItemRenderBudget;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

/** Headless policy and bytecode checks. Does not initialize Minecraft or measure frame rate. */
public final class ItemRenderChecks {
    private static final String ITEM = "net/minecraft/client/renderer/item/";
    private static final String ENTITY_RENDERER = "net/minecraft/client/renderer/entity/EntityRenderer";
    private static final String STATE = "net/minecraft/client/renderer/entity/state/EntityRenderState";
    private static final String CLUSTER = "net/minecraft/client/renderer/entity/state/ItemClusterRenderState";
    private static final String STACK = "net/minecraft/world/item/ItemStack";
    private static final String ENTITY = "net/minecraft/world/entity/Entity";
    private static final String LAYER = ITEM + "ItemStackRenderState$LayerRenderState";
    private static final String SUBMIT = "(Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V";
    private static final AtomicInteger CHECKS = new AtomicInteger();

    private ItemRenderChecks() {}

    public static void main(String[] args) throws Exception {
        checkBudget();
        checkThreadIsolation();
        checkMinecraftContracts();
        checkItemSubmitContracts();
        System.out.printf("Item rendering checks passed: %d assertions covering shadow-budget boundaries, "
                + "world/disable reset, thread isolation, exact 26.2 mixin targets, static-model inputs and "
                + "read-only model submission and contiguous vertex buffers. Minecraft was not initialized; "
                + "these are not in-game FPS measurements.%n",
                CHECKS.get());
    }

    private static void checkBudget() {
        Object world = new Object();
        require(!ItemRenderBudget.skipDistantShadow(10000), "Inactive budget must preserve shadows");
        for (int visible : new int[]{0, 63, 64, 127, 128, 256}) {
            prime(world, 64, visible);
            ItemRenderBudget.beginFrame(world, true, 64);
            for (double distance : new double[]{-1, 0, 16, Math.nextUp(16.0), 64, Math.nextUp(64.0), 10000}) {
                boolean expected = visible >= 64 && distance > (visible >= 128 ? 16 : 64);
                require(ItemRenderBudget.skipDistantShadow(distance) == expected,
                        "Wrong shadow boundary: visible=" + visible + ", squaredDistance=" + distance);
            }
            for (double invalid : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
                require(!ItemRenderBudget.skipDistantShadow(invalid), "Non-finite distance must preserve shadows");
            }
            ItemRenderBudget.endFrame(visible);
            require(!ItemRenderBudget.skipDistantShadow(10000), "Budget must end with extraction");
        }

        prime(world, 64, 256);
        ItemRenderBudget.beginFrame(new Object(), true, 64);
        require(!ItemRenderBudget.skipDistantShadow(10000), "New world must not inherit item density");
        ItemRenderBudget.endFrame(256);
        ItemRenderBudget.beginFrame(null, true, 64);
        require(!ItemRenderBudget.skipDistantShadow(10000), "Null world must disable the budget");
        ItemRenderBudget.endFrame(256);

        prime(world, 64, 256);
        ItemRenderBudget.beginFrame(world, false, 64);
        require(!ItemRenderBudget.skipDistantShadow(10000), "Disabling optimization must restore shadows immediately");
        ItemRenderBudget.endFrame(256);
        ItemRenderBudget.beginFrame(world, true, 64);
        require(!ItemRenderBudget.skipDistantShadow(10000), "Re-enabling must start without stale density");
        ItemRenderBudget.endFrame(-100);
        ItemRenderBudget.beginFrame(world, true, 64);
        require(!ItemRenderBudget.skipDistantShadow(10000), "Negative visible counts must clamp to zero");
        ItemRenderBudget.endFrame(0);

        for (int invalidThreshold : new int[]{0, -1, Integer.MIN_VALUE}) {
            prime(world, invalidThreshold, 1);
            ItemRenderBudget.beginFrame(world, true, invalidThreshold);
            require(!ItemRenderBudget.skipDistantShadow(17), "Clamped threshold must not activate the dense tier early");
            require(ItemRenderBudget.skipDistantShadow(65), "Threshold must clamp to at least one item");
            ItemRenderBudget.endFrame(2);
            ItemRenderBudget.beginFrame(world, true, invalidThreshold);
            require(ItemRenderBudget.skipDistantShadow(17), "Two items must reach twice the clamped threshold");
            ItemRenderBudget.endFrame(0);
        }
        prime(world, Integer.MAX_VALUE, Integer.MAX_VALUE);
        ItemRenderBudget.beginFrame(world, true, Integer.MAX_VALUE);
        require(!ItemRenderBudget.skipDistantShadow(17), "Twice-threshold comparison must not overflow int");
        require(ItemRenderBudget.skipDistantShadow(65), "Maximum threshold must still activate its first tier");
        ItemRenderBudget.endFrame(0);
    }

    private static void checkThreadIsolation() throws Exception {
        Object world = new Object();
        prime(world, 64, 64);
        ItemRenderBudget.beginFrame(world, true, 64);
        CountDownLatch denseReady = new CountDownLatch(1);
        CountDownLatch otherDone = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread dense = new Thread(() -> {
            try {
                require(!ItemRenderBudget.skipDistantShadow(10000), "New thread inherited the main thread's budget");
                prime(world, 64, 128);
                ItemRenderBudget.beginFrame(world, true, 64);
                denseReady.countDown();
                await(otherDone);
                require(ItemRenderBudget.skipDistantShadow(17), "Another thread reset the dense thread's budget");
                ItemRenderBudget.endFrame(0);
            } catch (Throwable exception) {
                failure.compareAndSet(null, exception);
            } finally {
                denseReady.countDown();
            }
        }, "item-budget-dense-check");
        Thread other = new Thread(() -> {
            try {
                await(denseReady);
                ItemRenderBudget.beginFrame(world, true, 64);
                require(!ItemRenderBudget.skipDistantShadow(10000), "Thread sharing a world inherited another budget");
                ItemRenderBudget.endFrame(256);
                ItemRenderBudget.beginFrame(new Object(), false, 64);
                ItemRenderBudget.endFrame(0);
            } catch (Throwable exception) {
                failure.compareAndSet(null, exception);
            } finally {
                otherDone.countDown();
            }
        }, "item-budget-reset-check");
        dense.start();
        other.start();
        dense.join(10000);
        other.join(10000);
        require(!dense.isAlive() && !other.isAlive(), "Thread-isolation check timed out");
        if (failure.get() != null) throw new AssertionError("Thread-isolation failure", failure.get());
        require(!ItemRenderBudget.skipDistantShadow(17), "Worker changed main thread's density tier");
        require(ItemRenderBudget.skipDistantShadow(65), "Worker reset main thread's active budget");
        ItemRenderBudget.endFrame(0);
    }

    private static void prime(Object world, int threshold, int visible) {
        ItemRenderBudget.beginFrame(world, true, threshold);
        ItemRenderBudget.endFrame(visible);
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        require(latch.await(10, TimeUnit.SECONDS), "Thread coordination timed out");
    }

    private static void checkMinecraftContracts() throws IOException {
        ClassInfo resolver = read(ITEM + "ItemModelResolver");
        resolver.method("getItemModel", "(Lnet/minecraft/resources/Identifier;)L" + ITEM + "ItemModel;", ACC_PRIVATE);
        ClassInfo cuboid = read(ITEM + "CuboidItemModelWrapper");
        cuboid.field("tints", "Ljava/util/List;", ACC_PRIVATE | ACC_FINAL);
        ClassInfo cluster = read(CLUSTER);
        cluster.field("item", "L" + ITEM + "ItemStackRenderState;", ACC_PUBLIC | ACC_FINAL);
        cluster.field("count", "I", ACC_PUBLIC);
        cluster.field("seed", "I", ACC_PUBLIC);
        MethodInfo group = cluster.method("extractItemGroupRenderState", "(L" + ENTITY + ";L" + STACK
                + ";L" + ITEM + "ItemModelResolver;)V", ACC_PUBLIC);
        group.requireCall(ITEM + "ItemModelResolver", "updateForNonLiving", "(L" + ITEM + "ItemStackRenderState;L"
                + STACK + ";Lnet/minecraft/world/item/ItemDisplayContext;L" + ENTITY + ";)V");
        cluster.method("getRenderedAmount", "(I)I", ACC_PUBLIC | ACC_STATIC);
        cluster.method("getSeedForItemStack", "(L" + STACK + ";)I", ACC_PUBLIC | ACC_STATIC);
        require(group.writes(CLUSTER, "count") && group.writes(CLUSTER, "seed"), "Cluster extraction layout changed");

        String manager = "net/minecraft/client/resources/model/ModelManager";
        MethodInfo apply = read(manager).method("apply", "(L" + manager + "$ReloadState;)V", ACC_PRIVATE);
        require(apply.writes(manager, "bakedItemStackModels"), "ModelManager.apply no longer replaces baked item models");
        String extractor = "net/minecraft/client/renderer/extract/LevelExtractor";
        ClassInfo level = read(extractor);
        level.field("level", "Lnet/minecraft/client/multiplayer/ClientLevel;", ACC_PRIVATE);
        level.method("extractVisibleEntities", "(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;"
                + "Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/client/renderer/state/level/LevelRenderState;)V", ACC_PRIVATE);

        ClassInfo entity = read(ENTITY_RENDERER);
        String shadowDescriptor = "(L" + STATE + ";Lnet/minecraft/client/Minecraft;Lnet/minecraft/world/level/Level;)V";
        entity.method("extractShadow", shadowDescriptor, ACC_PRIVATE);
        String finalizeDescriptor = "(L" + ENTITY + ";L" + STATE + ";)V";
        entity.method("finalizeRenderState", finalizeDescriptor, ACC_PROTECTED)
                .requireCall(ENTITY_RENDERER, "extractShadow", shadowDescriptor);
        MethodInfo create = entity.method("createRenderState", "(L" + ENTITY + ";F)L" + STATE + ";", ACC_PUBLIC | ACC_FINAL);
        int allocate = create.requireCall(ENTITY_RENDERER, "createRenderState", "()L" + STATE + ";");
        int extract = create.requireCall(ENTITY_RENDERER, "extractRenderState", "(L" + ENTITY + ";L" + STATE + ";F)V");
        int finalize = create.requireCall(ENTITY_RENDERER, "finalizeRenderState", finalizeDescriptor);
        require(allocate < extract && extract < finalize, "Per-frame state allocation/extraction/finalization order changed");
        ClassInfo entityState = read(STATE);
        entityState.field("distanceToCameraSq", "D", ACC_PUBLIC);
        entityState.field("shadowPieces", "Ljava/util/List;", ACC_PUBLIC | ACC_FINAL);
        entityState.field("shadowRadius", "F", ACC_PUBLIC);

        checkStaticCuboidInputs(cuboid);
        checkReadOnlySubmit(read(ITEM + "ItemStackRenderState").method("submit", SUBMIT, ACC_PUBLIC));
        ClassInfo layer = read(LAYER);
        checkReadOnlySubmit(layer.method("submit", SUBMIT, ACC_PRIVATE));
        checkReadOnlySubmit(layer.method("applyTransform", "(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)V", ACC_PRIVATE));
    }

    private static void checkStaticCuboidInputs(ClassInfo cuboid) {
        MethodInfo update = cuboid.method("update", "(L" + ITEM + "ItemStackRenderState;L" + STACK + ";L" + ITEM
                + "ItemModelResolver;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/multiplayer/ClientLevel;"
                + "Lnet/minecraft/world/entity/ItemOwner;I)V", ACC_PUBLIC);
        int foilCall = update.requireCall(STACK, "hasFoil", "()Z");
        int tintCall = update.requireCall("java/util/List", "isEmpty", "()Z");
        int foilEnd = update.branchTargetAfter(foilCall, IFEQ);
        int tintEnd = update.branchTargetAfter(tintCall, IFNE);
        for (int index = 0; index < update.instructions.size(); index++) {
            Instruction instruction = update.instructions.get(index);
            if (instruction.opcode == ALOAD && (instruction.variable == 5 || instruction.variable == 6)) {
                require(index > tintCall && index < tintEnd, "Cuboid now depends on world/owner without a tint");
            }
            require(!(instruction.opcode == ALOAD && instruction.variable == 3), "Cuboid now depends on the resolver");
            require(!(instruction.opcode == ILOAD && instruction.variable == 7), "Cuboid now depends on entity seed");
            if (instruction.opcode == ALOAD && instruction.variable == 2) {
                require(index == foilCall - 1 || (index > foilCall && index < foilEnd)
                        || (index > tintCall && index < tintEnd), "Cuboid now reads stack data outside foil/tint branches");
            }
        }
        // Unlike model selection, this helper only applies the fixed GROUND transform/material.
        MethodInfo properties = uncheckedRead(ITEM + "ModelRenderProperties").method("applyToLayer", "(L" + LAYER
                + ";Lnet/minecraft/world/item/ItemDisplayContext;)V", ACC_PUBLIC);
        checkReadOnlySubmit(properties);
        MethodInfo special = cuboid.method("hasSpecialAnimatedTexture", "(L" + STACK + ";)Z", ACC_PRIVATE | ACC_STATIC);
        special.requireCall(STACK, "is", "(Lnet/minecraft/tags/TagKey;)Z");
        special.requireCall(STACK, "is", "(Ljava/lang/Object;)Z");
        for (Instruction instruction : special.instructions) {
            if (instruction.opcode == GETSTATIC) {
                require(("net/minecraft/tags/ItemTags".equals(instruction.owner) && "COMPASSES".equals(instruction.name))
                        || ("net/minecraft/world/item/Items".equals(instruction.owner) && "CLOCK".equals(instruction.name)),
                        "Cuboid special foil selection now depends on another item/tag");
            }
            if (STACK.equals(instruction.owner)) require("is".equals(instruction.name),
                    "Cuboid special foil selection now reads additional stack components");
        }
    }

    private static void checkItemSubmitContracts() throws IOException {
        String feature = "net/minecraft/client/renderer/feature/ItemFeatureRenderer";
        String base = "net/minecraft/client/renderer/feature/RenderTypeFeatureRenderer";
        String type = "net/minecraft/client/renderer/rendertype/RenderType";
        String vertex = "com/mojang/blaze3d/vertex/VertexConsumer";
        String instance = "com/mojang/blaze3d/vertex/QuadInstance";
        String getter = "(L" + type + ";)L" + vertex + ";";
        String descriptor = "(L" + feature + "$Submit;)V";
        ClassInfo renderer = read(feature);
        renderer.field("quadInstance", "L" + instance + ";", ACC_PRIVATE | ACC_FINAL);
        MethodInfo main = renderer.method("prepareMainSubmit", descriptor, ACC_PRIVATE);
        main.requireCall(feature, "getVertexBuilder", getter);
        main.requireCall(instance, "setLightCoords", "(I)V");
        main.requireCall(instance, "setOverlayCoords", "(I)V");
        main.requireCall(instance, "setColor", "(I)V");
        main.requireCall(vertex, "putBakedQuad", "(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;"
                + "Lnet/minecraft/client/resources/model/geometry/BakedQuad;L" + instance + ";)V");
        MethodInfo dispatch = renderer.method("prepareSubmit", "(L" + feature + "$Submit;Z)V", ACC_PRIVATE);
        dispatch.requireCall(feature, "prepareMainSubmit", descriptor);
        dispatch.requireCall(feature, "prepareFoilSubmit", descriptor);
        dispatch.requireCall(feature, "prepareOutlineSubmit", descriptor);
        read(base).method("getVertexBuilder", getter, ACC_PROTECTED | ACC_FINAL);
        ClassInfo group = read(base + "$Group");
        group.field("lastRenderType", "L" + type + ";", ACC_PRIVATE);
        MethodInfo buffer = group.method("getVertexBuilder", getter, ACC_PUBLIC);
        buffer.requireCall(type, "canConsolidateConsecutiveGeometry", "()Z");
        buffer.requireCall("net/minecraft/client/renderer/StagedVertexBuffer", "getVertexBuilder",
                "(Lnet/minecraft/client/renderer/StagedVertexBuffer$Draw;)L" + vertex + ";");
        // Sodium and Pupper Client must keep using the same pose-normal contract and vertex format.
        MethodInfo sodium = read("net/caffeinemc/mods/sodium/client/render/immediate/model/BakedModelEncoder")
                .method("writeQuadVertices", "(Lnet/caffeinemc/mods/sodium/api/vertex/buffer/VertexBufferWriter;"
                        + "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/caffeinemc/mods/sodium/client/model/quad/BakedQuadView;"
                        + "L" + instance + ";)V", ACC_PUBLIC | ACC_STATIC);
        sodium.requireCall("net/caffeinemc/mods/sodium/api/math/MatrixHelper", "transformNormal", "(Lorg/joml/Matrix3f;ZI)I");
        sodium.requireCall("net/caffeinemc/mods/sodium/api/vertex/buffer/VertexBufferWriter", "push",
                "(Lorg/lwjgl/system/MemoryStack;JILcom/mojang/blaze3d/vertex/VertexFormat;)V");
    }

    private static void checkReadOnlySubmit(MethodInfo method) {
        for (Instruction instruction : method.instructions) {
            require(instruction.opcode != PUTFIELD && instruction.opcode != PUTSTATIC,
                    "Cached model method now writes state: " + method.name);
            if ("java/util/List".equals(instruction.owner) || "it/unimi/dsi/fastutil/ints/IntList".equals(instruction.owner)) {
                require(!List.of("add", "addAll", "clear", "remove", "removeIf", "replaceAll", "set", "sort").contains(instruction.name),
                        "Cached model submission now mutates a list: " + method.name);
            }
        }
    }

    private static ClassInfo uncheckedRead(String name) {
        try {
            return read(name);
        } catch (IOException exception) {
            throw new AssertionError("Cannot inspect " + name, exception);
        }
    }

    private static ClassInfo read(String name) throws IOException {
        try (InputStream stream = ItemRenderChecks.class.getClassLoader().getResourceAsStream(name + ".class")) {
            require(stream != null, "Missing Minecraft class on verification classpath: " + name);
            ClassInfo info = new ClassInfo(name);
            new ClassReader(stream).accept(new ClassVisitor(ASM9) {
                @Override
                public FieldVisitor visitField(int access, String field, String descriptor, String signature, Object value) {
                    info.fields.put(field + descriptor, access);
                    return null;
                }

                @Override
                public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                    MethodInfo data = new MethodInfo(method, access);
                    info.methods.put(method + descriptor, data);
                    return new MethodVisitor(ASM9) {
                        @Override public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                            data.instructions.add(new Instruction(opcode, owner, field, desc, -1, null));
                        }
                        @Override public void visitMethodInsn(int opcode, String owner, String called, String desc, boolean isInterface) {
                            data.instructions.add(new Instruction(opcode, owner, called, desc, -1, null));
                        }
                        @Override public void visitVarInsn(int opcode, int variable) {
                            data.instructions.add(new Instruction(opcode, null, null, null, variable, null));
                        }
                        @Override public void visitJumpInsn(int opcode, Label label) {
                            data.instructions.add(new Instruction(opcode, null, null, null, -1, label));
                        }
                        @Override public void visitLabel(Label label) {
                            data.labels.put(label, data.instructions.size());
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return info;
        }
    }

    private static final class ClassInfo {
        private final String name;
        private final Map<String, Integer> fields = new HashMap<>();
        private final Map<String, MethodInfo> methods = new HashMap<>();
        private ClassInfo(String name) { this.name = name; }
        private void field(String field, String descriptor, int access) {
            Integer actual = fields.get(field + descriptor);
            require(actual != null && (actual & access) == access, "Missing/changed field: " + name + "." + field + descriptor);
        }
        private MethodInfo method(String method, String descriptor, int access) {
            MethodInfo result = methods.get(method + descriptor);
            require(result != null && (result.access & access) == access, "Missing/changed method: " + name + "." + method + descriptor);
            return result;
        }
    }

    private static final class MethodInfo {
        private final String name;
        private final int access;
        private final List<Instruction> instructions = new ArrayList<>();
        private final Map<Label, Integer> labels = new IdentityHashMap<>();
        private MethodInfo(String name, int access) { this.name = name; this.access = access; }
        private int requireCall(String owner, String method, String descriptor) {
            for (int index = 0; index < instructions.size(); index++) {
                Instruction instruction = instructions.get(index);
                if (owner.equals(instruction.owner) && method.equals(instruction.name) && descriptor.equals(instruction.descriptor)) {
                    require(instruction.opcode >= INVOKEVIRTUAL && instruction.opcode <= INVOKEINTERFACE, "Expected a method call");
                    return index;
                }
            }
            throw new AssertionError(name + " no longer calls " + owner + "." + method + descriptor);
        }
        private int branchTargetAfter(int call, int opcode) {
            Instruction branch = instructions.get(call + 1);
            require(branch.opcode == opcode && labels.containsKey(branch.target), "Static-model branch guard changed in " + name);
            return labels.get(branch.target);
        }
        private boolean writes(String owner, String field) {
            return instructions.stream().anyMatch(instruction -> instruction.opcode == PUTFIELD
                    && owner.equals(instruction.owner) && field.equals(instruction.name));
        }
    }

    private record Instruction(int opcode, String owner, String name, String descriptor, int variable, Label target) {}

    private static void require(boolean condition, String message) {
        CHECKS.incrementAndGet();
        if (!condition) throw new AssertionError(message);
    }
}
