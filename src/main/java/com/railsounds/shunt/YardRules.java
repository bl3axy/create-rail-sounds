package com.railsounds.shunt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.railsounds.RailConfig;
import com.railsounds.RailSounds;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.trains.entity.Carriage;
import com.zurrtum.create.content.trains.entity.CarriageContraption;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.content.trains.entity.Train;
import com.zurrtum.create.content.trains.station.GlobalStation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Station automation, no extra blocks: give a station a rule and every train that stops there
 * runs it (scheduled or driven).
 *   couple      - couple to whatever cut is touching (or within couplerReach of) either end
 *   uncouple N  - drop N cars off the end away from the loco (0 = every car without controls)
 * Rules are by station name, saved in the world folder as railsounds_yards.json.
 */
public final class YardRules {
    private YardRules() {}

    public static class Rule {
        public String mode;     // "couple" or "uncouple" (or null: no coupling job here)
        public int count;       // uncouple only
        public String approach; // null = forward like Create, "any" = may back in, "back" = must back in
    }

    public static final String ANY = "any", BACK = "back";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, Rule> rules = new HashMap<>();
    private static Path file;

    /** train id -> station it's currently sitting at (so a rule runs once per stop) */
    private static final Map<UUID, UUID> AT_STATION = new HashMap<>();
    private record Due(UUID train, UUID station, long tick) {}
    private static final List<Due> DUE = new ArrayList<>();

    static String key(String stationName) {
        return stationName.trim().toLowerCase(Locale.ROOT);
    }

    public static Map<String, Rule> all() {
        return rules;
    }

    public static void set(String station, String mode, int count) {
        Rule r = rules.computeIfAbsent(key(station), k -> new Rule());
        r.mode = mode;
        r.count = count;
        save();
    }

    /** how trains may approach this station: null (forward), ANY or BACK */
    public static void setApproach(String station, String approach) {
        Rule r = rules.computeIfAbsent(key(station), k -> new Rule());
        r.approach = approach;
        if (r.mode == null && r.approach == null) rules.remove(key(station));
        save();
    }

    /** approach rule for a station by name, or null */
    public static String approach(String stationName) {
        if (stationName == null) return null;
        Rule r = rules.get(key(stationName));
        return r == null ? null : r.approach;
    }

    public static boolean remove(String station) {
        boolean had = rules.remove(key(station)) != null;
        if (had) save();
        return had;
    }

    /** a train that's already been handled at its current platform (merged/split by us) */
    static void markAt(Train t) {
        if (t != null && t.currentStation != null) AT_STATION.put(t.id, t.currentStation);
    }

    private static boolean seeded;

    static void resetArrivals() {
        AT_STATION.clear();
        DUE.clear();
        seeded = false;
    }

    static void tick(MinecraftServer server, long now) {
        if (!RailConfig.V.shuntingEnabled) return;
        // always track who's sitting where, so a train that was already parked when the server
        // started (or when a rule was added) doesn't fire the rule; only real arrivals do
        if (now % 5 == 0) {
            java.util.Set<UUID> seen = new java.util.HashSet<>();
            for (Train t : Shunting.allTrains()) {
                seen.add(t.id);
                UUID st = t.currentStation;
                if (st == null || t.derailed) {
                    AT_STATION.remove(t.id);
                    continue;
                }
                if (st.equals(AT_STATION.get(t.id))) continue;
                AT_STATION.put(t.id, st);
                if (!seeded || rules.isEmpty()) continue;
                GlobalStation gs = t.getCurrentStation();
                if (gs != null && rules.containsKey(key(gs.name)))
                    DUE.add(new Due(t.id, st, now + Math.max(0, RailConfig.V.yardRuleDelayTicks)));
            }
            AT_STATION.keySet().retainAll(seen);
            seeded = true;
        }
        if (DUE.isEmpty()) return;
        Iterator<Due> it = DUE.iterator();
        while (it.hasNext()) {
            Due d = it.next();
            if (d.tick > now) continue;
            it.remove();
            Train t = Create.RAILWAYS.trains.get(d.train);
            if (t == null || !d.station.equals(t.currentStation)) continue; // left already / got merged
            GlobalStation gs = t.getCurrentStation();
            if (gs == null) continue;
            Rule r = rules.get(key(gs.name));
            if (r == null) continue;
            Shunting.queue(() -> run(t, r));
        }
    }

    private static void run(Train t, Rule r) {
        if ("couple".equals(r.mode)) coupleAtStop(t);
        else if ("uncouple".equals(r.mode)) uncoupleAtStop(t, r.count);
    }

    /** couple a stopped train to the cars touching (or within couplerReach of) either end */
    public static boolean coupleAtStop(Train t) {
        RailConfig.Values c = RailConfig.V;
        TrainEnds.Contact best = null;
        for (boolean f : new boolean[]{true, false}) {
            TrainEnds.End e = TrainEnds.end(t, f);
            if (e == null) continue;
            TrainEnds.Contact ct = TrainEnds.findContact(e, c.contactGap + c.couplerReach, Shunting.allTrains());
            if (ct != null && (best == null || ct.gap() < best.gap())) best = ct;
        }
        if (best == null) {
            RailSounds.LOG.info("[railsounds] {} found nothing to couple to", t.name.getString());
            return false;
        }
        Train other = best.theirs().train();
        if (Math.abs(other.speed) > c.coupleMaxSpeed) return false;
        return Shunting.couple(t, best.ours().front(), other, best.theirs().front(), null, c.contactGap + c.couplerReach);
    }

    /** drop n cars off the end away from the loco (n <= 0: every car without controls) */
    public static boolean uncoupleAtStop(Train t, int count) {
        int size = t.carriages.size();
        if (size < 2) return false;
        boolean locoAtFront = controls(t.carriages.get(0)) || !controls(t.carriages.get(size - 1));
        int n = count;
        if (n <= 0) {
            // everything without controls, counted from the far end
            n = 0;
            for (int i = 0; i < size - 1; i++) {
                Carriage car = t.carriages.get(locoAtFront ? size - 1 - i : i);
                if (controls(car)) break;
                n++;
            }
        }
        n = Math.min(n, size - 1);
        if (n <= 0) return false;
        // S&R only splits off the back: dropping from the front means the loco end is what comes off
        return Shunting.uncouple(t, locoAtFront ? n : size - n, null);
    }

    private static boolean controls(Carriage car) {
        CarriageContraptionEntity e = car.anyAvailableEntity();
        return e != null && e.getContraption() instanceof CarriageContraption cc
            && (cc.hasForwardControls() || cc.hasBackwardControls());
    }

    public static void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve("railsounds_yards.json");
        rules = new HashMap<>();
        resetArrivals();
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    Map<String, Rule> loaded = GSON.fromJson(r, new com.google.gson.reflect.TypeToken<Map<String, Rule>>() {}.getType());
                    if (loaded != null) rules = loaded;
                }
            }
        } catch (Exception e) {
            RailSounds.LOG.error("Could not read railsounds_yards.json", e);
        }
    }

    public static void unload() {
        rules = new HashMap<>();
        file = null;
        resetArrivals();
    }

    private static void save() {
        if (file == null) return;
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(rules, w);
        } catch (Exception e) {
            RailSounds.LOG.error("Could not save railsounds_yards.json", e);
        }
    }
}
