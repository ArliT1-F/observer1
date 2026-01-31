package com.icy404.observer.snapshot;

import com.icy404.observer.TickManager;
import com.icy404.observer.profile.HomeProfiler;
import com.icy404.observer.state.ObserverState;
import com.icy404.observer.util.LogUtil;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class Snapshotter {
    private static final int SNAPSHOT_INTERVAL_TICKS = 20 * 120;
    private static final int MAX_SNAPSHOTS = 16;
    private static final int HOME_RADIUS = 20;
    private static final int SNAPSHOT_RADIUS = 16;
    private static final int SNAPSHOT_MIN_Y = -4;
    private static final int SNAPSHOT_MAX_Y = 14;

    private static final Map<UUID, Long> LAST_SNAPSHOT_TICK = new ConcurrentHashMap<>();

    private Snapshotter() {
    }

    public static void init() {
        TickManager.registerPlayerTick(Snapshotter::handlePlayerTick);
    }

    private static void handlePlayerTick(ServerPlayerEntity player) {
        if (player == null || player.getWorld().isClient()) {
            return;
        }

        if (!HomeProfiler.isActive(player)) {
            return;
        }

        ServerWorld world = player.getServerWorld();
        BlockPos homeAnchor = HomeProfiler.getHomeAnchor(player).orElse(null);
        if (homeAnchor == null) {
            return;
        }

        if (!isNearHome(player, homeAnchor)) {
            return;
        }

        long worldTime = world.getTime();
        UUID playerId = player.getUuid();
        long last = LAST_SNAPSHOT_TICK.getOrDefault(playerId, Long.MIN_VALUE);
        if (worldTime - last < SNAPSHOT_INTERVAL_TICKS) {
            return;
        }

        StructureSnapshot snapshot = StructureSnapshot.capture(world, homeAnchor, SNAPSHOT_RADIUS, SNAPSHOT_MIN_Y,
                SNAPSHOT_MAX_Y);
        ObserverState.get(world).addSnapshot(playerId, snapshot, MAX_SNAPSHOTS);
        LAST_SNAPSHOT_TICK.put(playerId, worldTime);
        LogUtil.info("Captured snapshot for " + player.getName().getString());
    }

    private static boolean isNearHome(ServerPlayerEntity player, BlockPos anchor) {
        double distanceSq = player.squaredDistanceTo(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
        return distanceSq <= HOME_RADIUS * HOME_RADIUS;
    }
}