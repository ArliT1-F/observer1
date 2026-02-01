package com.icy404.observer.util;

import java.util.List;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class VisibilityUtil {
    public static final double OBSERVATION_RADIUS = 64.0;
    private static final double HIT_EPSILON = 0.01;

    private VisibilityUtil() {
    }

    public static boolean isAreaObserved(ServerWorld world, Box target) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (isPlayerNear(player, target) || hasLineOfSight(world, player, target)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAnyPlayerInside(ServerWorld world, Box target) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (target.contains(player.getPos())) {
                return true;
            }
        }
        return false;
    }

    public static Box boxFrom(BlockPos min, BlockPos max) {
        return new Box(
            min.getX(),
            min.getY(),
            min.getZ(),
            max.getX() + 1,
            max.getY() + 1,
            max.getZ() + 1
        );
    }

    private static boolean isPlayerNear(ServerPlayerEntity player, Box target) {
        Vec3d position = player.getPos();
        double distanceSquared = distanceSquaredToBox(position, target);
        return distanceSquared <= OBSERVATION_RADIUS * OBSERVATION_RADIUS;
    }

    private static double distanceSquaredToBox(Vec3d position, Box box) {
        double dx = 0.0;
        if (position.x < box.minX) {
            dx = box.minX - position.x;
        } else if (position.x > box.maxX) {
            dx = position.x - box.maxX;
        }
        double dy = 0.0;
        if (position.y < box.minY) {
            dy = box.minY - position.y;
        } else if (position.y > box.maxY) {
            dy = position.y - box.maxY;
        }
        double dz = 0.0;
        if (position.z < box.minZ) {
            dz = box.minZ - position.z;
        } else if (position.z > box.maxZ) {
            dz = position.z - box.maxZ;
        }
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean hasLineOfSight(ServerWorld world, ServerPlayerEntity player, Box target) {
        Vec3d eye = player.getEyePos();
        for (Vec3d sample : samplePoints(target)) {
            if (isLineClear(world, player, eye, sample)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLineClear(ServerWorld world, ServerPlayerEntity player, Vec3d start, Vec3d end) {
        HitResult hit = world.raycast(new RaycastContext(
            start,
            end,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            player
        ));
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        double hitDistance = hit.getPos().squaredDistanceTo(start);
        double targetDistance = end.squaredDistanceTo(start);
        return hitDistance >= targetDistance - HIT_EPSILON;
    }

    private static List<Vec3d> samplePoints(Box box) {
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;
        double centerX = (minX + maxX) * 0.5;
        double centerY = (minY + maxY) * 0.5;
        double centerZ = (minZ + maxZ) * 0.5;
        return List.of(
            new Vec3d(centerX, centerY, centerZ),
            new Vec3d(minX, minY, minZ),
            new Vec3d(minX, minY, maxZ),
            new Vec3d(minX, maxY, minZ),
            new Vec3d(minX, maxY, maxZ),
            new Vec3d(maxX, minY, minZ),
            new Vec3d(maxX, minY, maxZ),
            new Vec3d(maxX, maxY, minZ),
            new Vec3d(maxX, maxY, maxZ)
        );
    }
}