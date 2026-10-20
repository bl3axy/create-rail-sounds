package com.railsounds.rail;

import com.zurrtum.create.api.contraption.storage.fluid.MountedFluidStorage;
import com.zurrtum.create.content.contraptions.minecart.TrainCargoManager;
import com.zurrtum.create.content.trains.entity.Carriage;
import com.zurrtum.create.content.trains.entity.Train;
import com.zurrtum.create.content.trains.station.GlobalStation;
import com.zurrtum.create.content.trains.station.StationBlockEntity;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Shared bits for the blocks that sit next to a Create Train Station and read the train
 * that's stopped there (Cargo Sensor, Car Counter).
 */
public final class StationSensors {
    private StationSensors() {}

    /** a station block touching this position (any of the 6 sides), or null */
    public static GlobalStation adjacentStation(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockEntity be = level.getBlockEntity(pos.relative(d));
            if (be instanceof StationBlockEntity sbe) {
                GlobalStation st = sbe.getStation();
                if (st != null) return st;
            }
        }
        return null;
    }

    /** the train stopped at that station right now, or null */
    public static Train dockedTrain(GlobalStation station) {
        if (station == null) return null;
        try {
            Train t = station.getPresentTrain();
            if (t == null || t.invalid || t.derailed || t.carriages.isEmpty()) return null;
            return t;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** how full a train's cargo is, 0..1. Returns -1 if it has no storage of that kind at all. */
    public static double fill(Train train, boolean fluids) {
        double total = 0;
        int slots = 0;
        for (Carriage car : train.carriages) {
            TrainCargoManager storage = car.storage;
            if (storage == null) continue;
            try {
                if (fluids) {
                    for (MountedFluidStorage tank : storage.getFluids().storages.values()) {
                        int max = tank.getMaxAmountPerStack();
                        if (max <= 0 || max == Integer.MAX_VALUE) continue;
                        for (int i = 0; i < tank.size(); i++) {
                            FluidStack fs = tank.getStack(i);
                            total += fs == null || fs.isEmpty() ? 0 : Math.min(1.0, fs.getAmount() / (double) max);
                            slots++;
                        }
                    }
                } else {
                    Container inv = storage.getAllItems();
                    int limit = inv.getMaxStackSize();
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        ItemStack s = inv.getItem(i);
                        slots++;
                        if (s.isEmpty()) continue;
                        int max = Math.max(1, Math.min(limit, s.getMaxStackSize()));
                        total += Math.min(1.0, s.getCount() / (double) max);
                    }
                }
            } catch (RuntimeException e) {
                // storage not initialised yet (train still assembling) - treat as nothing
            }
        }
        if (slots == 0) return -1;
        return total / slots;
    }
}
