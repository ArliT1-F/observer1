package com.icy404.observer.convergence;

import com.icy404.observer.ghost.GhostHouseManager;
import com.icy404.observer.observer.ObserverLifecycle;
import com.icy404.observer.revelation.RevelationBookGenerator;
import com.icy404.observer.snapshot.StructureSnapshot;
import com.icy404.observer.state.ObserverState;
import com.icy404.observer.util.LogUtil;
import java.util.List;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class ConvergenceManager {
    private ConvergenceManager() {
    }

    public static boolean isConvergenceActive(ServerWorld world) {
        return ObserverState.get(world).isObserverConvergence();
    }

    public static void onGhostHouseSpawned(ServerWorld world, ServerPlayerEntity player, GhostHouseManager.AttemptRecord record) {
        if (ObserverLifecycle.isObserverDisabled(world)) {
            return;
        }
        if (record.fidelity() >= 0.98) {
            triggerConvergence(world, player, "fidelity");
        }
    }

    public static void onArchiveFinalSlot(ServerWorld world, ServerPlayerEntity player) {
        if (ObserverLifecycle.isObserverDisabled(world)) {
            return;
        }
        triggerConvergence(world, player, "archive");
    }

    public static void onFinalLoreDecoded(ServerWorld world, ServerPlayerEntity player) {
        if (ObserverLifecycle.isObserverDisabled(world)) {
            return;
        }
        triggerConvergence(world, player, "lore");
    }

    public static void onBlockInteracted(ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (player == null || player.getWorld().isClient()) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        if (ObserverLifecycle.isObserverDisabled(world)) {
            return;
        }
        if (isConvergenceActive(world)) {
            return;
        }
        if (isWithinMostPerfectHouse(player, pos)) {
            triggerConvergence(world, player, "perfect-house");
            return;
        }
        if (state.hasBlockEntity() && world.getBlockEntity(pos) instanceof LecternBlockEntity lectern) {
            if (RevelationBookGenerator.isRevelationBook(lectern.getBook())) {
                triggerConvergence(world, player, "revelation");
            }
        }
    }

    public static boolean isWithinMostPerfectHouse(ServerPlayerEntity player, BlockPos pos) {
        ServerWorld world = player.getServerWorld();
        ObserverState state = ObserverState.get(world);
        Optional<Integer> attemptId = state.getMostPerfectAttempt(player.getUuid());
        if (attemptId.isEmpty()) {
            return false;
        }
        GhostHouseManager.AttemptRecord record = findAttempt(state.getAttemptRecords(player.getUuid()), attemptId.get());
        if (record == null) {
            return false;
        }
        List<StructureSnapshot> snapshots = state.getSnapshots(player.getUuid());
        if (record.snapshotIndex() < 0 || record.snapshotIndex() >= snapshots.size()) {
            return false;
        }
        StructureSnapshot snapshot = snapshots.get(record.snapshotIndex());
        BlockPos offset = record.origin().subtract(snapshot.anchor());
        BlockPos min = snapshot.min().add(offset);
        BlockPos max = snapshot.max().add(offset);
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
            && pos.getY() >= min.getY() && pos.getY() <= max.getY()
            && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    private static GhostHouseManager.AttemptRecord findAttempt(List<GhostHouseManager.AttemptRecord> records, int id) {
        for (GhostHouseManager.AttemptRecord record : records) {
            if (record.id() == id) {
                return record;
            }
        }
        return null;
    }

    private static void triggerConvergence(ServerWorld world, ServerPlayerEntity player, String source) {
        ObserverState state = ObserverState.get(world);
        if (state.isObserverConvergence()) {
            return;
        }
        state.markObserverConvergence(world.getTime());
        ensureMostPerfectRevelation(world, player, state);
        LogUtil.info("Convergence triggered for " + player.getName().getString() + " via " + source);
    }

    private static void ensureMostPerfectRevelation(ServerWorld world, ServerPlayerEntity player, ObserverState state) {
        if (state.isMostPerfectRevelationPlaced(player.getUuid())) {
            return;
        }
        Optional<BlockPos> lecternPos = state.getMostPerfectLectern(player.getUuid());
        if (lecternPos.isEmpty()) {
            return;
        }
        BlockPos pos = lecternPos.get();
        if (world.getBlockEntity(pos) instanceof LecternBlockEntity lectern) {
            if (!RevelationBookGenerator.isRevelationBook(lectern.getBook())) {
                double fidelity = state.getMostPerfectFidelity(player.getUuid());
                lectern.setBook(RevelationBookGenerator.createFinalBook(world, player, fidelity));
            }
            state.markMostPerfectRevelationPlaced(player.getUuid());
        }
    }
}