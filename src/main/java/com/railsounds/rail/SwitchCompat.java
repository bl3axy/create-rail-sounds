package com.railsounds.rail;

import com.railsounds.RailSounds;
import com.zurrtum.create.Create;
import com.zurrtum.create.content.trains.entity.Carriage;
import com.zurrtum.create.content.trains.entity.CarriageBogey;
import com.zurrtum.create.content.trains.entity.Train;
import com.zurrtum.create.content.trains.graph.EdgePointType;
import com.zurrtum.create.content.trains.graph.TrackGraph;
import com.zurrtum.create.content.trains.signal.SingleBlockEntityEdgePoint;
import com.zurrtum.create.content.trains.signal.TrackEdgePoint;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Steam 'n' Rails track switches, by reflection so this mod still loads without S&R.
 * Only uses what S&R itself exposes: TrackSwitchBlockEntity.getSwitch(), and on the TrackSwitch
 * edge point getSwitchState(), trySetSwitchState() (refuses when the switch is locked by
 * redstone) and has{Straight,Left,Right}Exit().
 */
public final class SwitchCompat {
    private SwitchCompat() {}

    public static final String NORMAL = "NORMAL", LEFT = "REVERSE_LEFT", RIGHT = "REVERSE_RIGHT";

    private static boolean looked, ok;
    private static Class<?> beClass, switchClass;
    @SuppressWarnings("rawtypes")
    private static Class<? extends Enum> stateEnum;
    private static Method getSwitch, getState, trySet, hasStraight, hasLeft, hasRight, isLocked;

    private static synchronized boolean init() {
        if (looked) return ok;
        looked = true;
        try {
            beClass = Class.forName("com.railwayteam.railways.content.switches.TrackSwitchBlockEntity");
            switchClass = Class.forName("com.railwayteam.railways.content.switches.TrackSwitch");
            Class<?> st = Class.forName("com.railwayteam.railways.content.switches.TrackSwitchBlock$SwitchState");
            stateEnum = st.asSubclass(Enum.class);
            getSwitch = beClass.getMethod("getSwitch");
            getState = switchClass.getMethod("getSwitchState");
            trySet = switchClass.getMethod("trySetSwitchState", st);
            hasStraight = switchClass.getMethod("hasStraightExit");
            hasLeft = switchClass.getMethod("hasLeftExit");
            hasRight = switchClass.getMethod("hasRightExit");
            isLocked = switchClass.getMethod("isLocked");
            ok = true;
        } catch (Throwable t) {
            ok = false;
            RailSounds.LOG.warn("[railsounds] Steam 'n' Rails switches not found - switch stands and the interlocking panel won't throw switches ({})", t.toString());
        }
        return ok;
    }

    public static boolean available() {
        return init();
    }

    /** the S&R switch whose switch block is at pos, or null */
    public static TrackEdgePoint switchAt(Level level, BlockPos pos) {
        if (!init()) return null;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !beClass.isInstance(be)) return null;
        try {
            Object sw = getSwitch.invoke(be);
            return sw instanceof TrackEdgePoint p ? p : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isSwitchBlock(Level level, BlockPos pos) {
        if (!init()) return false;
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && beClass.isInstance(be);
    }

    /** look a switch up by its id on the rail network (works even if its chunk isn't loaded) */
    public static TrackEdgePoint switchById(UUID id) {
        if (id == null || !init() || Create.RAILWAYS == null) return null;
        EdgePointType<?> type = EdgePointType.TYPES.get(Identifier.fromNamespaceAndPath("railways", "switch"));
        if (type == null) return null;
        for (TrackGraph g : Create.RAILWAYS.trackNetworks.values()) {
            TrackEdgePoint p = g.getPoint(type, id);
            if (p != null) return p;
        }
        return null;
    }

    public static TrackGraph graphOf(TrackEdgePoint sw) {
        if (sw == null || Create.RAILWAYS == null) return null;
        for (TrackGraph g : Create.RAILWAYS.trackNetworks.values()) {
            if (g.getPoint(sw.getType(), sw.getId()) == sw) return g;
        }
        return null;
    }

    public static String state(TrackEdgePoint sw) {
        if (sw == null || !init()) return null;
        try {
            Object s = getState.invoke(sw);
            return s == null ? NORMAL : ((Enum<?>) s).name();
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean locked(TrackEdgePoint sw) {
        if (sw == null || !init()) return false;
        try {
            return (boolean) isLocked.invoke(sw);
        } catch (Throwable t) {
            return false;
        }
    }

    /** the routes this switch actually has, in the order NORMAL, REVERSE_LEFT, REVERSE_RIGHT */
    public static List<String> exits(TrackEdgePoint sw) {
        List<String> out = new ArrayList<>();
        if (sw == null || !init()) return out;
        try {
            if ((boolean) hasStraight.invoke(sw)) out.add(NORMAL);
            if ((boolean) hasLeft.invoke(sw)) out.add(LEFT);
            if ((boolean) hasRight.invoke(sw)) out.add(RIGHT);
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** next route after the current one */
    public static String next(TrackEdgePoint sw) {
        List<String> ex = exits(sw);
        if (ex.size() < 2) return null;
        int i = ex.indexOf(state(sw));
        return ex.get((i + 1) % ex.size());
    }

    /** throw the switch; false if it's locked, already there, or that route doesn't exist */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean set(TrackEdgePoint sw, String state) {
        if (sw == null || state == null || !init()) return false;
        try {
            Enum value = Enum.valueOf((Class) stateEnum, state);
            return (boolean) trySet.invoke(sw, value);
        } catch (Throwable t) {
            return false;
        }
    }

    /** true if any train has wheels within a few blocks of the switch - a real interlocking won't move points under a train */
    public static boolean occupied(TrackEdgePoint sw) {
        if (!(sw instanceof SingleBlockEntityEdgePoint single) || single.blockEntityPos == null) return false;
        TrackGraph graph = graphOf(sw);
        if (graph == null || Create.RAILWAYS == null) return false;
        Vec3 at = Vec3.atCenterOf(single.blockEntityPos);
        double r2 = 10 * 10;
        for (Train t : new ArrayList<>(Create.RAILWAYS.trains.values())) {
            if (t.graph != graph) continue;
            for (Carriage c : t.carriages) {
                for (CarriageBogey b : new CarriageBogey[]{c.bogeys.getFirst(), c.bogeys.getSecond()}) {
                    if (b == null) continue;
                    try {
                        Vec3 a = b.getAnchorPosition(false);
                        if (a != null && a.distanceToSqr(at) < r2) return true;
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        }
        return false;
    }

    public static String pretty(String state) {
        if (state == null) return "?";
        return switch (state) {
            case NORMAL -> "Normal (straight)";
            case LEFT -> "Reverse (left)";
            case RIGHT -> "Reverse (right)";
            default -> state;
        };
    }
}
