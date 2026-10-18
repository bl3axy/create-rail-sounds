package com.railsounds.mixin;

import com.railsounds.TrainTypes;
import com.zurrtum.create.content.trains.GlobalRailwayManager;
import com.zurrtum.create.content.trains.entity.Train;
import com.zurrtum.create.content.trains.station.StationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Hands a /diesel or /steam mark from the station to the train it builds, and back on disassembly. */
@Mixin(value = StationBlockEntity.class, remap = false)
public abstract class StationAssemblyMixin {

    @Redirect(method = "assemble",
        at = @At(value = "INVOKE", target = "Lcom/zurrtum/create/content/trains/GlobalRailwayManager;addTrain(Lcom/zurrtum/create/content/trains/entity/Train;)V"),
        require = 0)
    private void railsounds$onAssemble(GlobalRailwayManager manager, Train train) {
        manager.addTrain(train);
        StationBlockEntity self = (StationBlockEntity) (Object) this;
        if (self.getLevel() != null)
            TrainTypes.onAssembled(TrainTypes.stationKey(self.getLevel(), self.getBlockPos()), train);
    }

    @Redirect(method = "tryDisassembleTrain",
        at = @At(value = "INVOKE", target = "Lcom/zurrtum/create/content/trains/entity/Train;disassemble(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;)Z"),
        require = 0)
    private boolean railsounds$onDisassemble(Train train, ServerPlayer sender, Direction dir, BlockPos pos) {
        String type = TrainTypes.get(train);
        boolean ok = train.disassemble(sender, dir, pos);
        StationBlockEntity self = (StationBlockEntity) (Object) this;
        if (ok && self.getLevel() != null)
            TrainTypes.onDisassembled(TrainTypes.stationKey(self.getLevel(), self.getBlockPos()), type);
        return ok;
    }
}
