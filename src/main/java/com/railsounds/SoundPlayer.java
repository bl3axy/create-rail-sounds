package com.railsounds;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
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
