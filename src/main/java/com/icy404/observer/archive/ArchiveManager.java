package com.icy404.observer.archive;

import com.icy404.observer.TickManager;
import com.icy404.observer.ghost.GhostHouseManager;
import com.icy404.observer.ghost.GhostHousePlanner;
import com.icy404.observer.profile.HomeProfiler;
import com.icy404.observer.snapshot.StructureSnapshot;
import com.icy404.observer.state.ObserverState;
import com.icy404.observer.util.LogUtil;
import com.icy404.observer.world.BuildQueue;
import java.util.List;
import java.util.Optional;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class ArchiveManager {
    private static final int MAX_ATTEMPTS = 32;
    private static final int COLUMNS = 4;
    private static final int PADDING = 6;
    private static final int LIGHT_SPACING = 12;

    private ArchiveManager() {
    }

    public static void onServerTick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            handlePlayer(player);
        }
    }

    public static void onGhostHouseSpawned(ServerWorld world, ServerPlayerEntity player, GhostHouseManager.AttemptRecord record, StructureSnapshot snapshot) {
        ObserverState state = ObserverState.get(world);
        Optional<BlockPos> archiveOrigin = state.getArchiveOrigin(player.getUuid());
        if (archiveOrigin.isEmpty()) {
            Optional<BlockPos> homeAnchor = HomeProfiler.getHomeAnchor(player);
            if (homeAnchor.isEmpty()) {
                return;
            }
            archiveOrigin = Optional.of(createArchive(world, player, snapshot, homeAnchor.get()));
        }
        if (state.isArchiveAttemptPasted(player.getUuid(), record.id())) {
            return;
        }
        placeAttemptInArchive(world, player, snapshot, record, archiveOrigin.get());
        state.markArchiveAttemptPasted(player.getUuid(), record.id());
    }

    private static void handlePlayer(ServerPlayerEntity player) {
        if (!HomeProfiler.isActive(player)) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        ObserverState state = ObserverState.get(world);
        Optional<BlockPos> homeAnchor = HomeProfiler.getHomeAnchor(player);
        if (homeAnchor.isEmpty()) {
            return;
        }
        List<StructureSnapshot> snapshots = state.getSnapshots(player.getUuid());
        if (snapshots.isEmpty()) {
            return;
        }
        StructureSnapshot latestSnapshot = snapshots.get(snapshots.size() - 1);
        Optional<BlockPos> archiveOrigin = state.getArchiveOrigin(player.getUuid());
        if (archiveOrigin.isEmpty()) {
            archiveOrigin = Optional.of(createArchive(world, player, latestSnapshot, homeAnchor.get()));
        }
        for (GhostHouseManager.AttemptRecord record : state.getAttemptRecords(player.getUuid())) {
            if (record.id() > MAX_ATTEMPTS) {
                continue;
            }
            if (state.isArchiveAttemptPasted(player.getUuid(), record.id())) {
                continue;
            }
            StructureSnapshot snapshot = record.snapshotIndex() >= 0 && record.snapshotIndex() < snapshots.size()
                ? snapshots.get(record.snapshotIndex())
                : latestSnapshot;
            placeAttemptInArchive(world, player, snapshot, record, archiveOrigin.get());
            state.markArchiveAttemptPasted(player.getUuid(), record.id());
        }
    }

    private static BlockPos createArchive(ServerWorld world, ServerPlayerEntity player, StructureSnapshot snapshot, BlockPos homeAnchor) {
        int width = snapshot.max().getX() - snapshot.min().getX() + 1;
        int height = snapshot.max().getY() - snapshot.min().getY() + 1;
        int depth = snapshot.max().getZ() - snapshot.min().getZ() + 1;

        int cellWidth = width + PADDING;
        int cellHeight = height + PADDING;
        int cellDepth = depth + PADDING;

        int rows = (int) Math.ceil(MAX_ATTEMPTS / (double) COLUMNS);
        int totalWidth = cellWidth * COLUMNS;
        int totalDepth = cellDepth * rows;
        int totalHeight = cellHeight;

        int baseY = Math.max(homeAnchor.getY() - 80, world.getBottomY() + 20);
        BlockPos origin = new BlockPos(
            homeAnchor.getX() - totalWidth / 2,
            baseY,
            homeAnchor.getZ() - totalDepth / 2
        );

        BuildQueue queue = TickManager.getQueue(world);
        carveVolume(queue, origin, origin.add(totalWidth - 1, totalHeight - 1, totalDepth - 1));
        enqueueLighting(queue, origin, totalWidth, totalDepth, baseY);

        ObserverState.get(world).setArchiveOrigin(player.getUuid(), origin);
        LogUtil.info("Archive cavern created for " + player.getName().getString() + " at " + origin);
        return origin;
    }

    private static void placeAttemptInArchive(ServerWorld world, ServerPlayerEntity player, StructureSnapshot snapshot, GhostHouseManager.AttemptRecord record, BlockPos archiveOrigin) {
        int width = snapshot.max().getX() - snapshot.min().getX() + 1;
        int height = snapshot.max().getY() - snapshot.min().getY() + 1;
        int depth = snapshot.max().getZ() - snapshot.min().getZ() + 1;

        int cellWidth = width + PADDING;
        int cellHeight = height + PADDING;
        int cellDepth = depth + PADDING;

        int index = record.id() - 1;
        if (index < 0 || index >= MAX_ATTEMPTS) {
            return;
        }
        int col = index % COLUMNS;
        int row = index / COLUMNS;
        BlockPos cellMin = archiveOrigin.add(col * cellWidth, 0, row * cellDepth);
        BlockPos cellMax = cellMin.add(cellWidth - 1, cellHeight - 1, cellDepth - 1);

        BuildQueue queue = TickManager.getQueue(world);
        carveVolume(queue, cellMin, cellMax);

        int padHalf = PADDING / 2;
        BlockPos localMin = snapshot.min().subtract(snapshot.anchor());
        BlockPos structureMin = cellMin.add(padHalf, padHalf, padHalf);
        BlockPos newAnchor = structureMin.subtract(localMin);

        List<GhostHousePlanner.Placement> placements = GhostHousePlanner.plan(
            world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),
            snapshot,
            newAnchor,
            record.id(),
            record.fidelity()
        );
        for (GhostHousePlanner.Placement placement : placements) {
            queue.enqueue(placement.pos(), placement.state(), 3);
        }

        LogUtil.info("Archived ghost house " + record.id() + " for " + player.getName().getString());
    }

    private static void carveVolume(BuildQueue queue, BlockPos min, BlockPos max) {
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    queue.enqueue(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), 3);
                }
            }
        }
    }

    private static void enqueueLighting(BuildQueue queue, BlockPos origin, int totalWidth, int totalDepth, int baseY) {
        int x = origin.getX() + 1;
        int zStart = origin.getZ() + 2;
        int zEnd = origin.getZ() + totalDepth - 2;
        int y = baseY + 1;
        for (int z = zStart; z <= zEnd; z += LIGHT_SPACING) {
            queue.enqueue(new BlockPos(x, y, z), Blocks.TORCH.getDefaultState(), 3);
            queue.enqueue(new BlockPos(origin.getX() + totalWidth - 2, y, z), Blocks.TORCH.getDefaultState(), 3);
        }
    }