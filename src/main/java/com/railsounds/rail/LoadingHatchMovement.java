package com.railsounds.rail;

import com.railsounds.RailSounds;
import com.zurrtum.create.api.behaviour.movement.MovementBehaviour;
import com.zurrtum.create.content.contraptions.behaviour.MovementContext;
import com.zurrtum.create.content.trains.entity.Carriage;
import com.zurrtum.create.content.trains.entity.CarriageContraptionEntity;
import com.zurrtum.create.foundation.item.ItemHelper;
import com.zurrtum.create.infrastructure.items.CombinedInvWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Loading Hatch on a train (or any contraption). While the train is stopped it:
 *  - catches loose items that land on/in it (chutes or funnels pouring down, ejectors, players)
 *  - pulls items out of an inventory block sitting right above it (a hopper, chute, chest...)
 * and puts them into the train's own storage (vaults, chests, barrels on the same car).
 * Moving trains don't load - real hopper cars don't either.
 */
public class LoadingHatchMovement extends MovementBehaviour {
    private static final int PULL_EVERY = 2;   // ticks
    private static final int PULL_ITEMS = 16;  // items per pull, ~160 items/second
    private static final double STOPPED = 1e-3;

    @Override
    public void tick(MovementContext context) {
        // this runs inside Create's actor loop on the train entity: anything thrown here would
        // take the whole train tick (and a modded container above us is not our code)
        try {
            load(context);
        } catch (Throwable t) {
            long now = System.currentTimeMillis();
            if (now - lastErrorLog > 60_000) {
                lastErrorLog = now;
                RailSounds.LOG.error("[railsounds] loading hatch failed (further errors hidden for 60s)", t);
            }
        }
    }

    private static long lastErrorLog;

    private static void load(MovementContext context) {
        if (context.world == null || context.world.isClientSide() || context.position == null) return;
        if (!isStopped(context)) return;

        CombinedInvWrapper cargo;
        try {
            cargo = context.contraption.getStorage().getAllItems();
        } catch (RuntimeException e) {
            return; // storage not ready yet
        }
        if (cargo == null || cargo.getContainerSize() == 0) return;

        int timer = context.temporaryData instanceof Integer i ? i : 0;
        context.temporaryData = (timer + 1) % PULL_EVERY;

        Vec3 c = context.position;
        // the hatch block's space plus the block above it (items come to rest on top of it)
        AABB catchBox = new AABB(c.x - 0.5, c.y - 0.5, c.z - 0.5, c.x + 0.5, c.y + 1.5, c.z + 0.5);
        List<ItemEntity> items = context.world.getEntitiesOfClass(ItemEntity.class, catchBox, ItemEntity::isAlive);
        for (ItemEntity ie : items) {
            ItemStack stack = ie.getItem();
            if (stack.isEmpty()) continue;
            int moved = cargo.insert(stack.copy(), stack.getCount(), null);
            if (moved <= 0) continue;
            if (moved >= stack.getCount()) {
                ie.discard();
            } else {
                ItemStack rest = stack.copy();
                rest.shrink(moved);
                ie.setItem(rest);
            }
        }

        if (timer == 0) pullFromAbove(context, cargo, BlockPos.containing(c).above());
    }

    private static boolean isStopped(MovementContext context) {
        if (context.contraption.entity instanceof CarriageContraptionEntity cce) {
            Carriage car = cce.getCarriage();
            if (car != null && car.train != null) return Math.abs(car.train.speed) < STOPPED;
        }
        return context.motion == null || context.motion.lengthSqr() < STOPPED * STOPPED;
    }

    private static void pullFromAbove(MovementContext context, CombinedInvWrapper cargo, BlockPos above) {
        if (!context.world.isLoaded(above)) return;
        Container source = ItemHelper.getInventory(context.world, above, Direction.DOWN);
        if (source == null || source == cargo) return;
        int budget = PULL_ITEMS;
        int size = source.getContainerSize();
        int[] slots = null;
        if (source instanceof WorldlyContainer wc) slots = wc.getSlotsForFace(Direction.DOWN);
        int n = slots != null ? slots.length : size;
        for (int k = 0; k < n && budget > 0; k++) {
            int slot = slots != null ? slots[k] : k;
            if (slot < 0 || slot >= size) continue;
            ItemStack s = source.getItem(slot);
            if (s.isEmpty()) continue;
            if (source instanceof WorldlyContainer wc && !wc.canTakeItemThroughFace(slot, s, Direction.DOWN)) continue;
            int want = Math.min(budget, s.getCount());
            int moved = cargo.insert(s.copyWithCount(want), want, null);
            if (moved <= 0) continue;
            source.removeItem(slot, moved);
            budget -= moved;
        }
        if (budget != PULL_ITEMS) source.setChanged();
    }
}
