package com.imjustseiyin.sit;

import com.imjustseiyin.block.custom.ChairBlock;
import com.imjustseiyin.block.custom.DoubleChairBlock;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

public class SitUtil {
    private static final Map<Identifier, Map<BlockPos, Pair<SitEntity, BlockPos>>> OCCUPIED = new HashMap<>();

    private static Identifier dim(Level level) {
        return level.dimension().identifier();
    }

    public static boolean addSitEntity(Level level, BlockPos blockPos, SitEntity entity, BlockPos playerPos) {
        if (level.isClientSide()) return false;
        OCCUPIED.computeIfAbsent(dim(level), k -> new HashMap<>()).put(blockPos.immutable(), Pair.of(entity, playerPos.immutable()));
        return true;
    }

    public static boolean removeSitEntity(Level level, BlockPos pos) {
        if (level.isClientSide()) return false;
        Map<BlockPos, Pair<SitEntity, BlockPos>> map = OCCUPIED.get(dim(level));
        if (map == null) return false;
        map.remove(pos);
        return true;
    }

    public static SitEntity getSitEntity(Level level, BlockPos pos) {
        if (level.isClientSide()) return null;
        Map<BlockPos, Pair<SitEntity, BlockPos>> map = OCCUPIED.get(dim(level));
        if (map == null || !map.containsKey(pos)) return null;
        return map.get(pos).getFirst();
    }

    public static BlockPos getPreviousPlayerPosition(Player player, SitEntity sitEntity) {
        Level level = player.level();
        if (level.isClientSide()) return null;
        Map<BlockPos, Pair<SitEntity, BlockPos>> map = OCCUPIED.get(dim(level));
        if (map == null) return null;
        for (Pair<SitEntity, BlockPos> pair : map.values()) {
            if (pair.getFirst() == sitEntity) {
                BlockPos pos = pair.getSecond();
                Block block = level.getBlockState(pos).getBlock();
                Block blockAbove = level.getBlockState(pos.above()).getBlock();
                if (block instanceof ChairBlock || block instanceof DoubleChairBlock) return pos.above();
                if (blockAbove instanceof ChairBlock || blockAbove instanceof DoubleChairBlock) return pos.above(2);
                return pos;
            }
        }
        return null;
    }

    public static boolean isOccupied(Level level, BlockPos pos) {
        Map<BlockPos, Pair<SitEntity, BlockPos>> map = OCCUPIED.get(dim(level));
        return map != null && map.containsKey(pos);
    }

    /** Shared sit logic for chairs and benches. */
    public static boolean trySit(Level level, BlockPos pos, Player player, double sitHeight) {
        if (isOccupied(level, pos) || !player.getMainHandItem().isEmpty()) return false;
        if (level.isClientSide()) return true;
        SitEntity sit = com.imjustseiyin.JustOutdoorStuffsMod.SIT_ENTITY_TYPE.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        if (sit == null) return false;
        sit.setPos(pos.getX() + 0.5D, pos.getY() + sitHeight, pos.getZ() + 0.5D);
        if (addSitEntity(level, pos, sit, player.blockPosition())) {
            level.addFreshEntity(sit);
            player.startRiding(sit);
            return true;
        }
        return false;
    }
}
