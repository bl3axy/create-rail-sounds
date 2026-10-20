package com.railsounds.mixin;

import com.railsounds.RailConfig;
import com.railsounds.shunt.Shunting;
import com.railsounds.shunt.YardRules;
import com.zurrtum.create.content.trains.entity.Navigation;
import com.zurrtum.create.content.trains.entity.Train;
import com.zurrtum.create.content.trains.graph.DiscoveredPath;
import com.zurrtum.create.content.trains.station.GlobalStation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

/**
 * Lets single-cab trains drive backwards when that's wanted, the way Create already lets
 * trains with controls at both ends:
 *  - a player backing into a station with Space (see ControlsDockMixin), and
 *  - any train heading for a station marked /railsounds yard backin (must back in)
 *    or /railsounds yard reverse (may back in if that's the shorter way).
 * Create can't change direction part-way through a trip, so a back-in trip is driven in
 * reverse all the way from the train's previous stop.
 */
@Mixin(value = Navigation.class, remap = false)
public abstract class NavigationBackDockMixin {

    /** destinations of the findPathTo / startNavigation call in progress (server thread only) */
    private static final ThreadLocal<ArrayList<GlobalStation>> RAILSOUNDS_DESTS = new ThreadLocal<>();
    private static final ThreadLocal<GlobalStation> RAILSOUNDS_STARTING = new ThreadLocal<>();

    // ---------- choosing a route ----------

    @Inject(method = "findPathTo(Ljava/util/ArrayList;D)Lcom/zurrtum/create/content/trains/graph/DiscoveredPath;", at = @At("HEAD"))
    private void railsounds$rememberDests(ArrayList<GlobalStation> destinations, double maxCost, CallbackInfoReturnable<DiscoveredPath> cir) {
        RAILSOUNDS_DESTS.set(destinations);
    }

    @Inject(method = "findPathTo(Ljava/util/ArrayList;D)Lcom/zurrtum/create/content/trains/graph/DiscoveredPath;", at = @At("RETURN"))
    private void railsounds$forgetDests(ArrayList<GlobalStation> destinations, double maxCost, CallbackInfoReturnable<DiscoveredPath> cir) {
        RAILSOUNDS_DESTS.remove();
    }

    @Redirect(method = "findPathTo(Ljava/util/ArrayList;D)Lcom/zurrtum/create/content/trains/graph/DiscoveredPath;",
        at = @At(value = "INVOKE", target = "Lcom/zurrtum/create/content/trains/entity/Train;hasBackwardConductor()Z"))
    private boolean railsounds$backConductorPath(Train train) {
        return train.hasBackwardConductor() || Shunting.BACK_DOCK.contains(train.id) || anyReverse(RAILSOUNDS_DESTS.get());
    }

    @Redirect(method = "findPathTo(Ljava/util/ArrayList;D)Lcom/zurrtum/create/content/trains/graph/DiscoveredPath;",
        at = @At(value = "FIELD", target = "Lcom/zurrtum/create/content/trains/entity/Train;doubleEnded:Z"))
    private boolean railsounds$doubleEnded(Train train) {
        return train.doubleEnded || Shunting.BACK_DOCK.contains(train.id) || anyReverse(RAILSOUNDS_DESTS.get());
    }

    /** "must back in": don't offer the forward route at all */
    @Redirect(method = "findPathTo(Ljava/util/ArrayList;D)Lcom/zurrtum/create/content/trains/graph/DiscoveredPath;",
        at = @At(value = "INVOKE", target = "Lcom/zurrtum/create/content/trains/entity/Train;hasForwardConductor()Z"))
    private boolean railsounds$forwardConductorPath(Train train) {
        // only for trains on a schedule: a player Space-docking can still pull in forwards
        if (train.runtime != null && train.runtime.getSchedule() != null && allBack(RAILSOUNDS_DESTS.get())) return false;
        return train.hasForwardConductor();
    }

    // ---------- starting and driving the trip ----------

    @Inject(method = "startNavigation", at = @At("HEAD"))
    private void railsounds$rememberStart(DiscoveredPath pathTo, CallbackInfoReturnable<Double> cir) {
        RAILSOUNDS_STARTING.set(pathTo == null ? null : pathTo.destination);
    }

    @Inject(method = "startNavigation", at = @At("RETURN"))
    private void railsounds$forgetStart(DiscoveredPath pathTo, CallbackInfoReturnable<Double> cir) {
        RAILSOUNDS_STARTING.remove();
    }

    @Redirect(method = "startNavigation",
        at = @At(value = "INVOKE", target = "Lcom/zurrtum/create/content/trains/entity/Train;hasBackwardConductor()Z"))
    private boolean railsounds$backConductorStart(Train train) {
        return train.hasBackwardConductor() || Shunting.BACK_DOCK.contains(train.id) || reverse(RAILSOUNDS_STARTING.get());
    }

    @Redirect(method = "tick",
        at = @At(value = "INVOKE", target = "Lcom/zurrtum/create/content/trains/entity/Train;hasBackwardConductor()Z"))
    private boolean railsounds$backConductorTick(Train train) {
        return train.hasBackwardConductor() || Shunting.BACK_DOCK.contains(train.id) || reverse(train.navigation.destination);
    }

    // ---------- helpers ----------

    private static boolean reverse(GlobalStation st) {
        return st != null && RailConfig.V.shuntingEnabled && YardRules.approach(st.name) != null;
    }

    private static boolean anyReverse(ArrayList<GlobalStation> dests) {
        if (dests == null || !RailConfig.V.shuntingEnabled) return false;
        for (GlobalStation st : dests) if (st != null && YardRules.approach(st.name) != null) return true;
        return false;
    }

    private static boolean allBack(ArrayList<GlobalStation> dests) {
        if (dests == null || dests.isEmpty() || !RailConfig.V.shuntingEnabled) return false;
        for (GlobalStation st : dests)
            if (st == null || !YardRules.BACK.equals(YardRules.approach(st.name))) return false;
        return true;
    }
}
