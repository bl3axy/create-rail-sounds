package com.railsounds.client;

import com.railsounds.RailSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * A held horn/whistle that follows the lead carriage. The clip is a long seamless
 * hold; on release the volume ramps down from wherever it is right now.
 */
public class HornSound extends AbstractTickableSoundInstance {
    private final Entity entity;
    private final float baseVolume;
    private int fadeTotal;
    private int fadeLeft = -1;
    private final long created = System.currentTimeMillis();

    public long ageMillis() {
        return System.currentTimeMillis() - created;
    }

    public HornSound(String sound, Entity entity, float volume) {
        super(SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, sound)),
            SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.entity = entity;
        this.baseVolume = volume;
        this.volume = volume;
        this.looping = false;
        this.delay = 0;
        follow();
    }

    private void follow() {
        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();
    }

    public boolean isFading() {
        return fadeLeft >= 0;
    }

    public void fadeOut(int ticks) {
        if (isFading()) return;
        fadeTotal = Math.max(1, ticks);
        fadeLeft = fadeTotal;
    }

    /** stop right now (a new honk replacing one that was still fading) */
    public void cut() {
        Minecraft.getInstance().getSoundManager().stop(this);
    }

    @Override
    public void tick() {
        if (entity.isRemoved()) {
            stop();
            return;
        }
        follow();
        if (fadeLeft >= 0) {
            if (fadeLeft == 0) {
                stop();
                return;
            }
            // cosine ramp: drops smoothly without the "falls off a cliff" end of a straight line
            float t = 1f - (float) fadeLeft / fadeTotal;
            volume = baseVolume * (float) Math.cos(t * Math.PI / 2);
            fadeLeft--;
        }
    }
}
