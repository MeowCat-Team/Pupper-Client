package cn.pupperclient.utils.misc;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;

public class SoundEventHelper {
    public static Entity lastSoundSource;
    public static SoundEvent lastSoundEvent;

    public static Entity getLastSoundSource() {
        return lastSoundSource;
    }

    public static SoundEvent getLastSoundEvent() {
        return lastSoundEvent;
    }

    public static void clearLastSound() {
        lastSoundSource = null;
        lastSoundEvent = null;
    }
}
