package cn.pupperclient.shader.patch;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.DynamicUniformStorage;

/** A bounded uniform store backed by Minecraft's current ring buffer implementation. */
public class FixedUniformStorage<T extends DynamicUniformStorage.DynamicUniform> {
    private final DynamicUniformStorage<T> storage;
    private final int capacity;
    private int size;

    public FixedUniformStorage(String name, int blockSize, int capacity) {
        this.storage = new DynamicUniformStorage<>(name, blockSize, capacity);
        this.capacity = capacity;
    }

    public GpuBufferSlice write(T value) {
        checkCapacity(1);
        size++;
        return storage.writeUniform(value);
    }

    public GpuBufferSlice[] writeAll(T[] values) {
        checkCapacity(values.length);
        size += values.length;
        return storage.writeUniforms(values);
    }

    private void checkCapacity(int count) {
        if (size + count > capacity) {
            throw new IndexOutOfBoundsException("Uniform storage capacity exceeded: " + (size + count) + " > " + capacity);
        }
    }

    public void clear() {
        size = 0;
        storage.endFrame();
    }

    public void close() {
        storage.close();
    }
}
