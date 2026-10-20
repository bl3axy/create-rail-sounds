package com.railsounds.rail;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Hitbox helper. Shapes are written in pixels (0-16) for a block whose front faces NORTH,
 * the same way the JSON models are drawn, then turned for the other three facings to match
 * the blockstate y-rotations (north 0, east 90, south 180, west 270).
 */
public final class RailShapes {
    private RailShapes() {}

    /** one box, pixels, front facing north */
    public record Box(double x1, double y1, double z1, double x2, double y2, double z2) {}

    public static Box box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new Box(x1, y1, z1, x2, y2, z2);
    }

    public static Map<Direction, VoxelShape> horizontal(Box... boxes) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (Box b : boxes) shape = Shapes.or(shape, turn(b, d));
            map.put(d, shape.optimize());
        }
        return map;
    }

    public static VoxelShape single(Box... boxes) {
        VoxelShape shape = Shapes.empty();
        for (Box b : boxes) shape = Shapes.or(shape, turn(b, Direction.NORTH));
        return shape.optimize();
    }

    private static VoxelShape turn(Box b, Direction d) {
        return switch (d) {
            case EAST -> Block.box(16 - b.z2, b.y1, b.x1, 16 - b.z1, b.y2, b.x2);
            case SOUTH -> Block.box(16 - b.x2, b.y1, 16 - b.z2, 16 - b.x1, b.y2, 16 - b.z1);
            case WEST -> Block.box(b.z1, b.y1, 16 - b.x2, b.z2, b.y2, 16 - b.x1);
            default -> Block.box(b.x1, b.y1, b.z1, b.x2, b.y2, b.z2);
        };
    }
}
