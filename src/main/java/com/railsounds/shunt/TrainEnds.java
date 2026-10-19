package com.railsounds.shunt;

import com.railsounds.RailConfig;
import com.zurrtum.create.content.contraptions.Contraption;
import com.zurrtum.create.content.trains.entity.Carriage;
import com.zurrtum.create.content.trains.entity.CarriageBogey;
import com.zurrtum.create.content.trains.entity.CarriageContraption;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.content.trains.entity.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where a train physically ends. Create only tracks wheel positions, but the car body
 * sticks out past the end bogey, so we measure that overhang from the contraption's blocks.
 *
 * Contraption local coords: the car's first (leading) bogey sits at the origin, and the car
 * was assembled running along assemblyDirection from front to back, so "forward" is
 * -assemblyDirection. Same convention Create uses in CarriageContraption.isHiddenInPortal.
 */
public final class TrainEnds {
    private TrainEnds() {}

    /** one end of a train */
    public record End(Train train, boolean front, Carriage car, Vec3 anchor, Vec3 out, double overhang) {
        /** point where the car body ends */
        public Vec3 face() {
            return anchor.add(out.scale(overhang));
        }
    }

    /** {front overhang from the leading bogey, rear overhang from the trailing bogey} */
    // client and server threads both use this in singleplayer
    private static final Map<Carriage, double[]> OVERHANG = java.util.Collections.synchronizedMap(new WeakHashMap<>());

    public static void clearCache() {
        OVERHANG.clear();
    }

    public static End end(Train train, boolean front) {
        try {
            if (train.carriages.isEmpty() || train.graph == null) return null;
            Carriage car = front ? train.carriages.get(0) : train.carriages.get(train.carriages.size() - 1);
            CarriageBogey bogey = front ? car.leadingBogey() : car.trailingBogey();
            Vec3 anchor = bogey.getAnchorPosition(false);
            if (anchor == null) return null;
            Vec3 dir;
            if (car.isOnTwoBogeys()) {
                Vec3 lead = car.leadingBogey().getAnchorPosition(false);
                Vec3 trail = car.trailingBogey().getAnchorPosition(false);
                if (lead == null || trail == null) return null;
                dir = front ? lead.subtract(trail) : trail.subtract(lead);
            } else {
                Vec3 a = bogey.leading().getPosition(train.graph);
                Vec3 b = bogey.trailing().getPosition(train.graph);
                dir = front ? a.subtract(b) : b.subtract(a);
            }
            if (dir.lengthSqr() < 1e-6) return null;
            double[] ov = overhang(car);
            return new End(train, front, car, anchor, dir.normalize(), front ? ov[0] : ov[1]);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static double[] overhang(Carriage car) {
        double[] cached = OVERHANG.get(car);
        if (cached != null) return cached;
        double d = RailConfig.V.defaultOverhang;
        CarriageContraptionEntity e = car.anyAvailableEntity();
        if (e == null || !(e.getContraption() instanceof CarriageContraption cc)) return new double[]{d, d};
        Direction facing = cc.getAssemblyDirection();
        if (facing == null || facing.getAxis() == Direction.Axis.Y) return new double[]{d, d};
        Map<BlockPos, ?> blocks = ((Contraption) cc).getBlocks();
        if (blocks == null || blocks.isEmpty()) return new double[]{d, d};

        int step = facing.getAxisDirection().getStep();
        boolean xAxis = facing.getAxis() == Direction.Axis.X;
        // sanity check the convention against the second bogey's block, flip if it's on the other side
        int sign = 1;
        if (car.isOnTwoBogeys()) {
            BlockPos expected = BlockPos.ZERO.relative(facing, car.bogeySpacing);
            BlockPos opposite = BlockPos.ZERO.relative(facing.getOpposite(), car.bogeySpacing);
            if (!blocks.containsKey(expected) && blocks.containsKey(opposite)) sign = -1;
        }
        int max = Integer.MIN_VALUE, min = Integer.MAX_VALUE;
        for (BlockPos p : blocks.keySet()) {
            int c = (xAxis ? p.getX() : p.getZ()) * -step * sign;
            if (c > max) max = c;
            if (c < min) min = c;
        }
        int rearBogey = car.isOnTwoBogeys() ? -car.bogeySpacing : 0;
        double front = clamp(max + 0.5);
        double rear = clamp(rearBogey - min + 0.5);
        double[] result = {front, rear};
        OVERHANG.put(car, result);
        return result;
    }

    private static double clamp(double v) {
        return Math.max(0.5, Math.min(24, v));
    }

    /** a touching pair: ours is the end that's moving / being worked, theirs the end it meets */
    public record Contact(End ours, End theirs, double gap) {}

    /**
     * Finds the end of another train that this end is facing and how far apart the car bodies are.
     * Has to be on the same line (not a parallel track) and facing back at us.
     */
    public static Contact findContact(End ours, double maxGap, Iterable<Train> trains) {
        Contact best = null;
        for (Train other : trains) {
            if (other == null || other == ours.train || other.invalid || other.derailed) continue;
            if (other.graph == null || other.graph != ours.train.graph || other.carriages.isEmpty()) continue;
            for (boolean f : new boolean[]{true, false}) {
                End theirs = end(other, f);
                if (theirs == null) continue;
                Vec3 v = theirs.anchor.subtract(ours.anchor);
                if (Math.abs(v.y) > 2.0) continue;
                double along = v.dot(ours.out);
                if (along < -1.0 || along > 64) continue;
                double lateral = v.subtract(ours.out.scale(along)).horizontalDistance();
                if (lateral > 1.3) continue;
                if (theirs.out.dot(ours.out) > -0.5) continue; // has to be pointing back at us
                double gap = along - ours.overhang - theirs.overhang;
                if (gap > maxGap) continue;
                if (best == null || gap < best.gap) best = new Contact(ours, theirs, gap);
            }
        }
        return best;
    }

    /** distance between the bogey centres across a would-be coupling, measured like Create does */
    public static double bogeyGap(End a, End b) {
        CarriageBogey ba = a.front ? a.car.leadingBogey() : a.car.trailingBogey();
        CarriageBogey bb = b.front ? b.car.leadingBogey() : b.car.trailingBogey();
        double d2 = a.anchor.distanceToSqr(b.anchor);
        if (ba.isUpsideDown() != bb.isUpsideDown()) d2 -= 4;
        return Math.sqrt(Math.max(0, d2));
    }
}
