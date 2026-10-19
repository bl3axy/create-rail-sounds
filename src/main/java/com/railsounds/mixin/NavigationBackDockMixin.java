package com.railsounds.mixin;

import com.railsounds.shunt.Shunting;
import com.zurrtum.create.content.trains.entity.Navigation;
import com.zurrtum.create.content.trains.entity.Train;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * While a single-cab train is backing into a station (see ControlsDockMixin), let Create's
 * navigation treat the driver as able to drive backwards, the way it already does for trains
 * with controls at both ends.
 */
@Mixin(value = Navigation.class, remap = false)
public abstract class NavigationBackDockMixin {

    @Redirect(method = {"tick", "startNavigation"},
        at = @At(value = "INVOKE", target = "Lcom/zurrtum/create/content/trains/entity/Train;hasBackwardConductor()Z"))
    private boolean railsounds$backConductor(Train train) {
        return train.hasBackwardConductor() || Shunting.BACK_DOCK.contains(train.id);
    }

    @Redirect(method = "findPathTo(Ljava/util/ArrayList;D)Lcom/zurrtum/create/content/trains/graph/DiscoveredPath;",
        at = @At(value = "INVOKE", target = "Lcom/zurrtum/create/content/trains/entity/Train;hasBackwardConductor()Z"))
    private boolean railsounds$backConductorPath(Train train) {
        return train.hasBackwardConductor() || Shunting.BACK_DOCK.contains(train.id);
    }

    @Redirect(method = "findPathTo(Ljava/util/ArrayList;D)Lcom/zurrtum/create/content/trains/graph/DiscoveredPath;",
        at = @At(value = "FIELD", target = "Lcom/zurrtum/create/content/trains/entity/Train;doubleEnded:Z"))
    private boolean railsounds$doubleEnded(Train train) {
        return train.doubleEnded || Shunting.BACK_DOCK.contains(train.id);
    }
}
