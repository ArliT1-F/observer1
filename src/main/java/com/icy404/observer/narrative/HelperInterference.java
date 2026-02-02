package com.icy404.observer.narrative;

import com.icy404.observer.convergence.ConvergenceManager;
import com.icy404.observer.ghost.GhostHousePlanner;
import com.icy404.observer.observer.ObserverLifecycle;
import com.icy404.observer.snapshot.StructureSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class HelperInterference {
    private static final List<Text> BOOK_HINTS = List.of(
        Text.literal("margin: the west wall quiets."),
        Text.literal("smudge: north hall feels thinner."),
        Text.literal("note: left turns repeat less."),
        Text.literal("pencil: safer light near the edge."),
        Text.literal("scribble: follow the colder stones.")
    );
    private static final List<Text> CONVERGENCE_HINTS = List.of(
        Text.literal("this is as far as it went"),
        Text.literal("it does not stop"),
        Text.literal("it learned you well enough")
    );

    private HelperInterference() {
    }

    public static void applyGhostHouseInterference(ServerWorld world, RegistryEntryLookup<Block> blockLookup,
            StructureSnapshot snapshot, BlockPos anchor, List<GhostHousePlanner.Placement> placements, int attemptId,
            long day) {
        if (ObserverLifecycle.isObserverDisabled(world) || ConvergenceManager.isConvergenceActive(world)) {
            return;
        }
        if (attemptId < 2 || placements.isEmpty()) {
            return;
        }
        Random random = new Random(seed(world, attemptId, day, anchor));
        double chance = 0.06 + 0.02 * Math.min(6, attemptId - 1);
        if (random.nextDouble() > chance) {
            return;
        }

        BlockPos offset = anchor.subtract(snapshot.anchor());
        Map<Long, BlockState> snapshotStates = new HashMap<>();
        for (StructureSnapshot.BlockEntry entry : snapshot.blocks()) {
            BlockPos pos = BlockPos.fromLong(entry.pos()).add(offset);
            snapshotStates.put(pos.asLong(), NbtHelper.toBlockState(blockLookup, entry.state()));
        }

        List<Integer> revertCandidates = new ArrayList<>();
        List<Integer> cobwebCandidates = new ArrayList<>();
        for (int i = 0; i < placements.size(); i++) {
            GhostHousePlanner.Placement placement = placements.get(i);
            if (placement.state().isOf(Blocks.COBWEB)) {
                cobwebCandidates.add(i);
                continue;
            }
            BlockState original = snapshotStates.get(placement.pos().asLong());
            if (original != null && !original.isAir() && original.getBlock() != placement.state().getBlock()) {
                revertCandidates.add(i);
            }
        }

        boolean tryRevert = random.nextBoolean();
        if (tryRevert && !revertCandidates.isEmpty()) {
            int index = revertCandidates.get(random.nextInt(revertCandidates.size()));
            GhostHousePlanner.Placement placement = placements.get(index);
            BlockState original = snapshotStates.get(placement.pos().asLong());
            if (original != null) {
                placements.set(index, new GhostHousePlanner.Placement(placement.pos(), original));
            }
            return;
        }
        if (!cobwebCandidates.isEmpty()) {
            int index = cobwebCandidates.get(random.nextInt(cobwebCandidates.size()));
            placements.remove(index);
            return;
        }
        if (!revertCandidates.isEmpty()) {
            int index = revertCandidates.get(random.nextInt(revertCandidates.size()));
            GhostHousePlanner.Placement placement = placements.get(index);
            BlockState original = snapshotStates.get(placement.pos().asLong());
            if (original != null) {
                placements.set(index, new GhostHousePlanner.Placement(placement.pos(), original));
            }
        }
    }

    public static void maybeAlterBook(ServerWorld world, NbtList pages, int attemptId, long day) {
        if (ObserverLifecycle.isObserverDisabled(world)) {
            return;
        }
        if (attemptId < 3 || pages.isEmpty()) {
            return;
        }
        Random random = new Random(seed(null, attemptId, day, pages.size()));
        double chance = 0.1 + 0.03 * Math.min(4, attemptId - 2);
        if (random.nextDouble() > chance) {
            return;
        }
        List<Text> hints = ConvergenceManager.isConvergenceActive(world) ? CONVERGENCE_HINTS : BOOK_HINTS;
        Text hint = hints.get(random.nextInt(hints.size()));
        int targetPage = Math.min(pages.size() - 1, 2);
        String existing = pages.getString(targetPage);
        Text base = Text.Serializer.fromJson(existing);
        String baseText = base != null ? base.getString() : "";
        Text combined = Text.literal(baseText).append("\n\n").append(hint);
        pages.set(targetPage, NbtString.of(Text.Serializer.toJson(combined)));
    }

    public static void maybePlaceArchiveMarker(ServerWorld world, BlockPos archiveOrigin,
            BlockPos cellMin, BlockPos cellMax, int padding, int attemptId, long day) {
        if (ObserverLifecycle.isObserverDisabled(world) || ConvergenceManager.isConvergenceActive(world)) {
            return;
        }
        if (attemptId < 2) {
            return;
        }
        Random random = new Random(seed(world, attemptId, day, cellMin));
        double chance = 0.08 + 0.02 * Math.min(5, attemptId - 1);
        if (random.nextDouble() > chance) {
            return;
        }

        int padHalf = padding / 2;
        BlockPos structureMin = cellMin.add(padHalf, padHalf, padHalf);
        BlockPos structureMax = cellMax.add(-padHalf, -padHalf, -padHalf);

        boolean towardWest = archiveOrigin.getX() < cellMin.getX();
        boolean towardNorth = archiveOrigin.getZ() < cellMin.getZ();

        int markerX = towardWest ? structureMin.getX() - 1 : structureMax.getX() + 1;
        int markerZ = towardNorth ? structureMin.getZ() - 1 : structureMax.getZ() + 1;
        markerX = Math.max(cellMin.getX(), Math.min(cellMax.getX(), markerX));
        markerZ = Math.max(cellMin.getZ(), Math.min(cellMax.getZ(), markerZ));

        BlockPos markerPos = new BlockPos(markerX, structureMin.getY(), markerZ);
        world.setBlockState(markerPos, Blocks.CHISELED_STONE_BRICKS.getDefaultState(), 3);
    }

    private static long seed(ServerWorld world, int attemptId, long day, Object saltSource) {
        long seed = attemptId * 0x9E3779B97F4A7C15L;
        seed ^= day * 0xBF58476D1CE4E5B9L;
        seed ^= saltSource.hashCode() * 0x94D049BB133111EBL;
        if (world != null) {
            seed ^= world.getSeed();
        }
        return seed;
    }
}
