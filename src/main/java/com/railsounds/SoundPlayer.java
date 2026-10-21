package com.railsounds;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sends sounds straight to nearby players. The sound ids don't need to be registered on the
 * server: the client looks them up in the resource pack.
 */
public class SoundPlayer {
    public static final String CLACK = "clack";
    public static final String BRAKE = "brake_squeal";
    public static final String COUPLER = "coupler_clunk";
    public static final String AIR = "air_release";
    public static final String BELL = "crossing_bell";

    private static Holder<SoundEvent> holder(String name, int range) {
        Identifier id = Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, name);
        return Holder.direct(SoundEvent.createFixedRangeEvent(id, range));
    }

    /** play now to every player in this level within range */
    public static void play(ServerLevel level, Vec3 pos, String sound, float volume, float pitch, int range) {
        if (volume <= 0.01f) return;
        Holder<SoundEvent> h = holder(sound, range);
        double r2 = (double) range * range;
        long seed = ThreadLocalRandom.current().nextLong();
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (p.level() != level) continue;
            if (p.position().distanceToSqr(pos) > r2) continue;
            p.connection.send(new ClientboundSoundPacket(h, SoundSource.NEUTRAL,
                pos.x, pos.y, pos.z, volume, pitch, seed));
        }
    }

    /**
     * Like {@link #play}, but anyone riding the thing making the noise hears it centred on
     * themselves instead of at a point in the world.
     *
     * A world-positioned sound is panned and attenuated against the CAMERA, not the player, so
     * sitting in a cab and looking around swings the engine from ear to ear, and in third person
     * (F5) the camera sits several blocks away and the whole thing lurches. Neither is what you'd
     * hear with your hand on the throttle. Sending a rider the packet at their own position makes
     * it steady and central however they're looking; everyone outside still hears it from the
     * locomotive, which is the point for them.
     */
    public static void playRiderAware(ServerLevel level, Vec3 pos, String sound, float volume,
                                      float pitch, int range, java.util.Set<java.util.UUID> riders) {
        if (volume <= 0.01f) return;
        Holder<SoundEvent> h = holder(sound, range);
        double r2 = (double) range * range;
        long seed = ThreadLocalRandom.current().nextLong();
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (p.level() != level) continue;
            boolean riding = riders != null && riders.contains(p.getUUID());
            if (!riding && p.position().distanceToSqr(pos) > r2) continue;
            Vec3 at = riding ? p.position() : pos;
            p.connection.send(new ClientboundSoundPacket(h, SoundSource.NEUTRAL,
                at.x, at.y, at.z, volume, pitch, seed));
        }
    }

    /** stop a sound for players near a spot (vanilla can only stop by name, so it's every copy of it they hear) */
    public static void stop(ServerLevel level, Vec3 pos, String sound, int range) {
        ClientboundStopSoundPacket pkt = new ClientboundStopSoundPacket(
            Identifier.fromNamespaceAndPath(RailSounds.MOD_ID, sound), SoundSource.NEUTRAL);
        double r2 = (double) range * range;
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(pos) <= r2) p.connection.send(pkt);
        }
        // anything of this sound still queued for this spot shouldn't start after the cut
        PENDING.removeIf(q -> q.level == level && q.sound.equals(sound) && q.pos.distanceToSqr(pos) < 64 * 64);
    }

    // ---------- delayed sounds (coupler ripple, second bogey clack) ----------
    private record Pending(long tick, ServerLevel level, Vec3 pos, String sound, float vol, float pitch, int range) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    public static void playLater(MinecraftServer server, int delayTicks, ServerLevel level, Vec3 pos,
                                 String sound, float vol, float pitch, int range) {
        if (delayTicks <= 0) {
            play(level, pos, sound, vol, pitch, range);
            return;
        }
        PENDING.add(new Pending(server.getTickCount() + delayTicks, level, pos, sound, vol, pitch, range));
    }

    /** drop queued sounds when the server stops (singleplayer world switch) */
    public static void clear() {
        PENDING.clear();
    }

    public static void tickScheduled(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        long now = server.getTickCount();
        Iterator<Pending> it = PENDING.iterator();
        List<Pending> due = new ArrayList<>();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.tick <= now) {
                due.add(p);
                it.remove();
            }
        }
        for (Pending p : due) play(p.level, p.pos, p.sound, p.vol, p.pitch, p.range);
        if (PENDING.size() > 2000) PENDING.clear(); // safety valve
    }
}
