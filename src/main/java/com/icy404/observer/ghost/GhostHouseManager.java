package com.icy404.observer.ghost;

import com.icy404.observer.archive.ArchiveManager;
import com.icy404.observer.convergence.ConvergenceManager;
import com.icy404.observer.observer.ObserverLifecycle;
import com.icy404.observer.revelation.RevelationBookGenerator;
import com.icy404.observer.profile.HomeProfiler;
import com.icy404.observer.snapshot.StructureSnapshot;
import com.icy404.observer.state.ObserverState;
import com.icy404.observer.util.LogUtil;
import com.icy404.observer.util.VisibilityUtil;
import com.icy404.observer.narrative.HelperInterference;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;

public final class GhostHouseManager {
    private static final int MIN_DISTANCE = 160;
    private static final int MAX_DISTANCE = 500;
    private static final int MAX_ATTEMPTS = 8;

    private GhostHouseManager() {
    }

    public static void onServerTick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            handlePlayer(player);
        }
    }

    private static void handlePlayer(ServerPlayerEntity player) {
        if (!HomeProfiler.isActive(player)) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        if (ObserverLifecycle.isObserverDisabled(world)) {
            return;
        }
        long day = world.getTimeOfDay() / 24000L;
        ObserverState state = ObserverState.get(world);
        boolean convergenceActive = ConvergenceManager.isConvergenceActive(world);
        if (!state.shouldAttemptGhostHouse(player.getUuid(), day, world.getTime(), convergenceActive)) {
            return;
        }

        Optional<BlockPos> homeAnchor = HomeProfiler.getHomeAnchor(player);
        if (homeAnchor.isEmpty()) {
            return;
        }
        List<StructureSnapshot> snapshots = state.getSnapshots(player.getUuid());
        if (snapshots.isEmpty()) {
            return;
        }

        StructureSnapshot latestSnapshot = snapshots.get(snapshots.size() - 1);
        int attemptId = state.getAttemptCount(player.getUuid()) + 1;
        double fidelity = Math.min(0.99, 0.30 + (attemptId - 1) * 0.05);
        if (convergenceActive) {
            fidelity = Math.max(0.25, fidelity - 0.04);
        }
        Random random = new Random(seed(player.getUuid(), day, attemptId));
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
            int targetX = MathHelper.floor(homeAnchor.get().getX() + Math.cos(angle) * distance);
            int targetZ = MathHelper.floor(homeAnchor.get().getZ() + Math.sin(angle) * distance);
            int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, targetX, targetZ);
            BlockPos anchor = new BlockPos(targetX, surfaceY, targetZ);

            if (!isAreaClear(world, latestSnapshot, anchor)) {
                continue;
            }
            if (isAreaObserved(world, latestSnapshot, anchor)) {
                return;
            }

            List<GhostHousePlanner.Placement> placements = GhostHousePlanner.plan(
                world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),
                latestSnapshot,
                anchor,
                attemptId,
                fidelity
            );
            HelperInterference.applyGhostHouseInterference(
                world,
                world.getRegistryManager().getWrapperOrThrow(RegistryKeys.BLOCK),
                latestSnapshot,
                anchor,
                placements,
                attemptId,
                day
            );
            for (GhostHousePlanner.Placement placement : placements) {
                world.setBlockState(placement.pos(), placement.state(), 3);
            }
            boolean newPerfect = state.updateMostPerfectAttempt(player.getUuid(), attemptId, fidelity);
            placeMarker(world, player, state, placements, fidelity, attemptId, day, newPerfect);
            state.incrementAttemptCount(player.getUuid());
            AttemptRecord record = new AttemptRecord(attemptId, anchor, fidelity, snapshots.size() - 1, day);
            state.addAttemptRecord(player.getUuid(), record);
            state.markAttempted(player.getUuid(), day, world.getTime());
            ConvergenceManager.onGhostHouseSpawned(world, player, record);
            ArchiveManager.onGhostHouseSpawned(world, player, record, latestSnapshot);
            LogUtil.info("Spawned ghost house " + attemptId + " for " + player.getName().getString());
            return;
        }

        state.markAttemptedToday(player.getUuid(), day);
    }

    private static boolean isAreaObserved(ServerWorld world, StructureSnapshot snapshot, BlockPos anchor) {
        BlockPos offset = anchor.subtract(snapshot.anchor());
        BlockPos min = snapshot.min().add(offset);
        BlockPos max = snapshot.max().add(offset);
        Box target = VisibilityUtil.boxFrom(min, max);
        return VisibilityUtil.isAreaObserved(world, target);
    }
    private static boolean isAreaClear(ServerWorld world, StructureSnapshot snapshot, BlockPos anchor) {
        BlockPos offset = anchor.subtract(snapshot.anchor());
        BlockPos min = snapshot.min().add(offset);
        BlockPos max = snapshot.max().add(offset);
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
                for (int y = surfaceY; y <= max.getY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!world.getBlockState(pos).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void placeMarker(ServerWorld world, ServerPlayerEntity player, ObserverState state, List<GhstHousePlanner.Placement>placements, double fidelity, int attemptId, long day, boolean newPerfect) {
        if (placements.isEmpty()) {
            return;
        }
        BlockPos entrance = placements.get(0).pos();
        world.setBlockState(entrance, Blocks.LECTERN.getDefaultState().with(net.minecraft.block.LecternBlock.FACING, Deirection.NORTH));
        if (world.getBlockEntity(entrance) instanceof net.minecraft.block.entity.LecternBlockEntity lectern) {
            NbtCompound tag = new NbtCompound();
            tag.putString("title", "ITERATION " + String.format("%02d", attemptId));
            tag.putString("author", "observer");
            NbtList pages = new NbtList();
            pages.add(NbtString.of(Text.Serializer.toJson(Text.literal("ITERATION " + String.format("%02d", attemptId)))));
            pages.add(NbtString.of(Text.Serializer.toJson(Text.literal(String.format("FIT: %.2f", fidelity)))));
            pages.add(NbtString.of(Text.Serializer.toJson(Text.literal(String.format("LOSS: %.2f", 1.0 - fidelity)))));
            HelperInterference.maybeAlterBook(world, pages, attemptId, day);
            tag.put("pages", pages);
            ItemStack book;
            boolean shouldReveal = newPerfect && (ConvergenceManager.isConvergenceActive(world) || fidelity >= 0.98);
            if (shouldReveal) {
                book = RevelationBookGenerator.createFinalBook(world, player, fidelity);
            } else {
                book = new ItemStack(Items.WRITTEN_BOOK);
                book.setNbt(tag);
            }
            lectern.setBook(book);
            if (newPerfect) {
                state.setMostPerfectLectern(player.getUuid(), entrance);
                if (shouldReveal) {
                    state.markMostPerfectRevelationPlaced(player.getUuid());
                }
            }
        }
    }

        private static long seed(UUID playerId, long day, int attemptId) {
        long seed = playerId.getMostSignificantBits() ^ playerId.getLeastSignificantBits();
        seed ^= day * 0x9E3779B97F4A7C15L;
        seed ^= (long) attemptId * 0xBF58476D1CE4E5B9L;
        return seed;
    }

    public record AttemptRecord(int id, BlockPos origin, double fidelity, int snapshotIndex, long day) {
        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putInt("id", id);
            nbt.putLong("origin", origin.asLong());
            nbt.putDouble("fidelity", fidelity);
            nbt.putInt("snapshotIndex", snapshotIndex);
            nbt.putLong("day", day);
            return nbt;
        }

        public static AttemptRecord fromNbt(NbtCompound nbt) {
            return new AttemptRecord(
                nbt.getInt("id"),
                BlockPos.fromLong(nbt.getLong("origin")),
                nbt.getDouble("fidelity"),
                nbt.getInt("snapshotIndex"),
                nbt.getLong("day")
            );
        }
    }
}