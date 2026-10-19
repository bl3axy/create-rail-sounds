package com.railsounds.shunt;

import com.railsounds.RailConfig;
import com.zurrtum.create.content.trains.entity.Carriage;
import com.zurrtum.create.content.trains.entity.CarriageBogey;
import com.zurrtum.create.content.trains.entity.Train;
import com.zurrtum.create.content.trains.entity.TravellingPoint;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Collision safety net. Each tick a moving train sends a scout along the track it's actually
 * about to take (its route if it has one, otherwise the way the switches are set for manual
 * steering) and looks for any other train's body on that track, ahead, around curves and
 * across junctions. If there is one, the train is held to a speed it can still stop from, so
 * it pulls up short instead of crashing. A train moving away is allowed to be followed.
 *
 * Parked cars (no controls) are approached at coupling speed so auto-couple still works.
 * Signals still do the real work (head-on on single track = both stop and wait forever).
 */
final class Avoidance {
    private Avoidance() {}

    private static final double STEP = 1.0;      // scout step along the track, blocks
    private static final double HIT = 1.1;       // a body point this close to the scout = in our path
    private static final double MARGIN = 0.4;    // stop this far short

    /** a point on another train's body, plus how fast that train moves (blocks/tick vector) */
    private record BodyPoint(Vec3 pos, Train train, Vec3 vel) {}

    static void limit(Train train, Iterable<Train> all) {
        RailConfig.Values c = RailConfig.V;
        double speed = Math.abs(train.speed);
        if (speed < 1e-3 || train.graph == null) return;
        boolean forward = train.speed > 0;
        Carriage leadCar = forward ? train.carriages.get(0) : train.carriages.get(train.carriages.size() - 1);
        TravellingPoint lead = forward ? leadCar.getLeadingPoint() : leadCar.getTrailingPoint();
        if (lead == null || lead.edge == null) return;
        Vec3 start = lead.getPosition(train.graph);

        double accel = Math.max(1e-4, train.acceleration());
        double look = Math.min(160, speed * speed / (2 * accel) + speed * 10 + 8);

        // body points of every other train that could be in reach
        List<BodyPoint> points = new ArrayList<>();
        for (Train other : all) {
            if (other == null || other == train || other.invalid || other.derailed || other.graph == null) continue;
            if (other.graph != train.graph || other.carriages.isEmpty()) continue;
            addBody(other, start, look + 2, points);
        }
        if (points.isEmpty()) return;

        // how far the car body sticks out past our leading wheel
        TrainEnds.End ours = TrainEnds.end(train, forward);
        double bodyAhead = 0.5;
        if (ours != null) bodyAhead = Math.max(0, ours.overhang() - ours.anchor().distanceTo(start));

        // heading for a station: Create stops our wheels at the marker. Anything past where our
        // body will end up when stopped there can't be hit, and the route beyond is unknown anyway
        double stopAt = Double.MAX_VALUE;
        if (train.navigation.destination != null) {
            stopAt = Math.abs(train.navigation.distanceToDestination);
            look = Math.min(look, stopAt + bodyAhead + HIT);
        }

        TravellingPoint scout = new TravellingPoint(lead.node1, lead.node2, lead.edge, lead.position, lead.upsideDown);
        TravellingPoint.ITrackSelector routed = train.navigation.destination != null ? train.navigation.controlSignalScout() : null;
        double sign = forward ? 1 : -1;
        Vec3 prev = start;
        double travelled = 0;
        while (travelled < look) {
            TravellingPoint.ITrackSelector sel = routed != null ? routed
                : scout.steer(train.manualSteer, new Vec3(0, 1, 0));
            double moved = Math.abs(scout.travel(train.graph, sign * STEP, sel));
            if (moved < 1e-3) return; // end of track
            travelled += moved;
            Vec3 pos = scout.getPosition(train.graph);
            Vec3 heading = pos.subtract(prev);
            prev = pos;
            Vec3 h = heading.lengthSqr() > 1e-6 ? heading.normalize() : Vec3.ZERO;
            // nearest thing on our track around this step
            BodyPoint hit = null;
            double at = Double.MAX_VALUE;
            for (BodyPoint bp : points) {
                double dx = bp.pos.x - pos.x, dz = bp.pos.z - pos.z;
                if (dx * dx + dz * dz > HIT * HIT || Math.abs(bp.pos.y - pos.y) > 2.5) continue;
                double a = travelled + bp.pos.subtract(pos).dot(h); // distance ahead of our front wheels
                if (a < at) {
                    at = a;
                    hit = bp;
                }
            }
            if (hit == null) continue;
            if (at - bodyAhead >= stopAt - 0.05) return; // beyond where we stop anyway
            double gap = at - bodyAhead - MARGIN;
            double away = Math.max(0, hit.vel.dot(h)); // how fast it's getting out of our way
            // parked cars end-on in front of us: roll up at coupling speed so they couple.
            // anything else (a loco, or cars across our path at a junction): stop short
            boolean coupleable = c.autoCouple && ours != null && !Shunting.hasControls(hit.train)
                && Shunting.mayAutoCouple(train)
                && TrainEnds.findContact(ours, look, List.of(hit.train)) != null;
            double floor = coupleable ? c.coupleMaxSpeed * 0.8 : 0;
            double cap = Math.sqrt(2 * accel * Math.max(0, gap)) + away + floor;
            if (speed > cap) train.speed = sign * cap;
            return;
        }
    }

    private static void addBody(Train t, Vec3 near, double range, List<BodyPoint> out) {
        Vec3 vel = velocity(t);
        for (Carriage car : t.carriages) {
            Vec3 lead = anchor(car.leadingBogey());
            Vec3 trail = anchor(car.trailingBogey());
            if (lead == null || trail == null) continue;
            if (lead.distanceTo(near) > range && trail.distanceTo(near) > range) continue;
            Vec3 dir = lead.subtract(trail);
            if (dir.lengthSqr() < 1e-4) {
                try {
                    dir = car.leadingBogey().leading().getPosition(t.graph)
                        .subtract(car.leadingBogey().trailing().getPosition(t.graph));
                } catch (RuntimeException e) {
                    dir = Vec3.ZERO;
                }
            }
            if (dir.lengthSqr() < 1e-4) {
                out.add(new BodyPoint(lead, t, vel));
                continue;
            }
            dir = dir.normalize();
            double[] ov = TrainEnds.overhang(car);
            Vec3 from = lead.add(dir.scale(ov[0]));
            Vec3 to = trail.subtract(dir.scale(ov[1]));
            double len = from.distanceTo(to);
            int n = Math.max(1, (int) Math.ceil(len / 0.9));
            for (int i = 0; i <= n; i++) out.add(new BodyPoint(from.lerp(to, i / (double) n), t, vel));
        }
    }

    private static Vec3 anchor(CarriageBogey b) {
        try {
            return b == null ? null : b.getAnchorPosition(false);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** horizontal velocity of a whole train (speed is along its own front direction) */
    private static Vec3 velocity(Train t) {
        if (t.speed == 0) return Vec3.ZERO;
        TrainEnds.End f = TrainEnds.end(t, true);
        if (f == null) return Vec3.ZERO;
        return new Vec3(f.out().x, 0, f.out().z).scale(t.speed);
    }
}
