package com.icy404.observer.ghost;

import com.icy404.observer.snapshot.StructureSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.util.math.BlockPos;

public final class GhostHousePlanner {
    private static final List<Block> LIGHT_SOURCES = List.of(
        Blocks.TORCH,
        Blocks.WALL_TORCH,
        Blocks.LANTERN,
        Blocks.SOUL_LANTERN,
        Blocks.GLOWSTONE,
        Blocks.SEA_LANTERN,
        Blocks.REDSTONE_LAMP,
        Blocks.CAMPFIRE,
        Blocks.SOUL_CAMPFIRE
    );

    private GhostHousePlanner() {
    }

    public static List<Placement> plan(RegistryEntryLookup<Block> blockLookup, StructureSnapshot snapshot, BlockPos newAnchor, int attemptId, double fidelity) {
        List<Placement> placements = new ArrayList<>();
        BlockPos snapshotAnchor = snapshot.anchor();
        BlockPos min = snapshot.min();
        BlockPos max = snapshot.max();
        BlockPos offset = newAnchor.subtract(snapshotAnchor);

        for (StructureSnapshot.BlockEntry entry : snapshot.blocks()) {
            BlockPos pos = BlockPos.fromLong(entry.pos()).add(offset);
            BlockState state = NbtHelper.toBlockState(blockLookup, entry.state());
            if (fidelity < 0.9 && isLightSource(state)) {
                continue;
            }

            double includeProb = fidelity + (isSilhouette(pos, min.add(offset), max.add(offset)) ? 0.15 : 0.0);
            includeProb = Math.min(0.99, Math.max(0.0, includeProb));
            if (sample(pos, attemptId, 31L) > includeProb) {
                continue;
            }

            if (state.getBlock() instanceof BedBlock) {
                state = invertBed(state);
            }

            if (fidelity < 0.6) {
                state = applyDecay(state, pos, attemptId);
            }

            placements.add(new Placement(pos, state));
        }

        addCobwebs(placements, min.add(offset), max.add(offset), attemptId, fidelity);
        return placements;
    }

    private static boolean isLightSource(BlockState state) {
        return LIGHT_SOURCES.contains(state.getBlock());
    }

    private static boolean isSilhouette(BlockPos pos, BlockPos min, BlockPos max) {
        return pos.getX() == min.getX()
            || pos.getX() == max.getX()
            || pos.getZ() == min.getZ()
            || pos.getZ() == max.getZ()
            || pos.getY() == min.getY()
            || pos.getY() == max.getY();
    }

    private static BlockState applyDecay(BlockState state, BlockPos pos, int attemptId) {
        if (state.isOf(Blocks.OAK_PLANKS) && sample(pos, attemptId, 47L) < 0.2) {
            return Blocks.CRACKED_STONE_BRICKS.getDefaultState();
        }
        if (state.isOf(Blocks.COBBLESTONE) && sample(pos, attemptId, 53L) < 0.25) {
            return Blocks.MOSSY_COBBLESTONE.getDefaultState();
        }
        return state;
    }

    private static void addCobwebs(List<Placement> placements, BlockPos min, BlockPos max, int attemptId, double fidelity) {
        if (fidelity > 0.85) {
            return;
        }
        List<BlockPos> candidates = List.of(
            new BlockPos(min.getX(), max.getY(), min.getZ()),
            new BlockPos(max.getX(), max.getY(), min.getZ()),
            new BlockPos(min.getX(), max.getY(), max.getZ()),
            new BlockPos(max.getX(), max.getY(), max.getZ())
        );
        for (BlockPos pos : candidates) {
            if (sample(pos, attemptId, 97L) < 0.6) {
                placements.add(new Placement(pos, Blocks.COBWEB.getDefaultState()));
            }
        }
    }

    private static BlockState invertBed(BlockState state) {
        Block block = state.getBlock();
        Block inverted = block;
        if (block == Blocks.WHITE_BED) {
            inverted = Blocks.BLACK_BED;
        } else if (block == Blocks.BLACK_BED) {
            inverted = Blocks.WHITE_BED;
        } else if (block == Blocks.ORANGE_BED) {
            inverted = Blocks.BLUE_BED;
        } else if (block == Blocks.BLUE_BED) {
            inverted = Blocks.ORANGE_BED;
        } else if (block == Blocks.MAGENTA_BED) {
            inverted = Blocks.LIME_BED;
        } else if (block == Blocks.LIME_BED) {
            inverted = Blocks.MAGENTA_BED;
        } else if (block == Blocks.LIGHT_BLUE_BED) {
            inverted = Blocks.RED_BED;
        } else if (block == Blocks.RED_BED) {
            inverted = Blocks.LIGHT_BLUE_BED;
        } else if (block == Blocks.YELLOW_BED) {
            inverted = Blocks.PURPLE_BED;
        } else if (block == Blocks.PURPLE_BED) {
            inverted = Blocks.YELLOW_BED;
        } else if (block == Blocks.PINK_BED) {
            inverted = Blocks.GREEN_BED;
        } else if (block == Blocks.GREEN_BED) {
            inverted = Blocks.PINK_BED;
        } else if (block == Blocks.CYAN_BED) {
            inverted = Blocks.BROWN_BED;
        } else if (block == Blocks.BROWN_BED) {
            inverted = Blocks.CYAN_BED;
        } else if (block == Blocks.LIGHT_GRAY_BED) {
            inverted = Blocks.GRAY_BED;
        } else if (block == Blocks.GRAY_BED) {
            inverted = Blocks.LIGHT_GRAY_BED;
        }
        if (inverted == block) {
            return state;
        }
        BlockState newState = inverted.getDefaultState();
        if (state.contains(BedBlock.PART)) {
            newState = newState.with(BedBlock.PART, state.get(BedBlock.PART));
        }
        if (state.contains(BedBlock.FACING)) {
            newState = newState.with(BedBlock.FACING, state.get(BedBlock.FACING));
        }
        if (state.contains(BedBlock.OCCUPIED)) {
            newState = newState.with(BedBlock.OCCUPIED, state.get(BedBlock.OCCUPIED));
        }
        return newState;
    }

    private static double sample(BlockPos pos, int attemptId, long salt) {
        long hash = pos.asLong() ^ (attemptId * 0x9E3779B97F4A7C15L) ^ salt;
        hash ^= (hash >>> 33);
        hash *= 0xff51afd7ed558ccdL;
        hash ^= (hash >>> 33);
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= (hash >>> 33);
        return ((hash >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
    }

    public record Placement(BlockPos pos, BlockState state) {
        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putLong("pos", pos.asLong());
            nbt.put("state", NbtHelper.fromBlockState(state));
            return nbt;
        }
    }
}