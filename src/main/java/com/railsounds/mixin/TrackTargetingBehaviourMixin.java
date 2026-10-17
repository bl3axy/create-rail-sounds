package com.railsounds.mixin;

import com.zurrtum.create.content.trains.graph.TrackGraphLocation;
import com.zurrtum.create.content.trains.track.ITrackBlock;
import com.zurrtum.create.content.trains.track.TrackTargetingBehaviour;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

/**
 * Crash guard: Create's determineGraphLocation() blindly casts the block at the
 * target position to ITrackBlock. Steam 'n' Rails' track coupler points its second
 * edge point back at itself when that point isn't on matching straight track, so
 * powering the coupler threw a ClassCastException and took the whole server down.
 * Every caller already handles a null location, so return null instead.
 */
@Mixin(value = TrackTargetingBehaviour.class, remap = false)
public abstract class TrackTargetingBehaviourMixin {

    private static final Logger RAILSOUNDS_LOG = LoggerFactory.getLogger("railsounds");
    private static final Set<BlockPos> RAILSOUNDS_WARNED = new HashSet<>();

    @Inject(method = "determineGraphLocation", at = @At("HEAD"), cancellable = true)
    private void railsounds$guardNonTrack(CallbackInfoReturnable<TrackGraphLocation> cir) {
        TrackTargetingBehaviour<?> self = (TrackTargetingBehaviour<?>) (Object) this;
        if (self.getTrackBlockState().getBlock() instanceof ITrackBlock)
            return;
        BlockPos owner = self.blockEntity.getBlockPos().immutable();
        if (RAILSOUNDS_WARNED.size() < 256 && RAILSOUNDS_WARNED.add(owner))
            RAILSOUNDS_LOG.warn("[railsounds] {} at {} targets a non-track block at {}; skipped instead of crashing. "
                    + "For a coupler, both of its points must sit on the same straight track.",
                self.blockEntity.getBlockState().getBlock(), owner, self.getGlobalPosition());
        cir.setReturnValue(null);
    }
}
