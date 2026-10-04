/**
 * @Author: oneachina
 * @link: github.com/oneachina
 */
package cn.pupperclient.utils.misc;

import net.minecraft.nbt.CompoundTag;

public interface ISerializable<T> {
    CompoundTag toTag();

    T fromTag(CompoundTag tag);
}
