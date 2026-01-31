package com.icy404.observer.snapshot;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class StructureSnapshot {
    private static final String KEY_ANCHOR = "anchor";
    private static final String KEY_MIN = "min";
    private static final String KEY_MAX = "max";
    private static final String KEY_BLOCKS = "blocks";
    private static final String KEY_MASK = "mask";
    private static final String KEY_STATE = "state";
    private static final String KEY_POS = "pos";

    private final BlockPos anchor;
    private final BlockPos min;
    private final BlockPos max;
    private final List<BlockEntry> blocks;
    private final long[] mask;

    public StructureSnapshot(BlockPos anchor, BlockPos min, BlockPos max, List<BlockEntry> blocks, long[] mask) {
        this.anchor = anchor;
        this.min = min;
        this.max = max;
        this.blocks = List.copyOf(blocks);
        this.mask = mask.clone();
    }

    public BlockPos anchor() {
        return anchor;
    }

    public BlockPos min() {
        return min;
    }

    public BlockPos max() {
        return max;
    }

    public List<BlockEntry> blocks() {
        return blocks;
    }

    public long[] mask() {
        return mask.clone();
    }

    public static StructureSnapshot capture(ServerWorld world, BlockPos anchor, int radius, int minYOffset,
            int maxYOffset) {
        BlockPos min = anchor.add(-radius, minYOffset, -radius);
        BlockPos max = anchor.add(radius, maxYOffset, radius);

        List<BlockEntry> blocks = new ArrayList<>();
        List<Long> mask = new ArrayList<>();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    mask.add(pos.asLong());
                    BlockState state = world.getBlockState(pos);
                    if (!state.isAir()) {
                        blocks.add(new BlockEntry(pos.asLong(), NbtHelper.fromBlockState(state)));
                    }
                }
            }
        }

        long[] maskArray = new long[mask.size()];
        for (int i = 0; i < mask.size(); i++) {
            maskArray[i] = mask.get(i);
        }

        return new StructureSnapshot(anchor.toImmutable(), min.toImmutable(), max.toImmutable(), blocks, maskArray);
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putLong(KEY_ANCHOR, anchor.asLong());
        nbt.putLong(KEY_MIN, min.asLong());
        nbt.putLong(KEY_MAX, max.asLong());

        NbtList blockList = new NbtList();
        for (BlockEntry entry : blocks) {
            NbtCompound block = new NbtCompound();
            block.putLong(KEY_POS, entry.pos());
            block.put(KEY_STATE, entry.state());
            blockList.add(block);
        }
        nbt.put(KEY_BLOCKS, blockList);
        nbt.put(KEY_MASK, new NbtLongArray(mask));
        return nbt;
    }

    public static StructureSnapshot fromNbt(NbtCompound nbt) {
        BlockPos anchor = BlockPos.fromLong(nbt.getLong(KEY_ANCHOR));
        BlockPos min = BlockPos.fromLong(nbt.getLong(KEY_MIN));
        BlockPos max = BlockPos.fromLong(nbt.getLong(KEY_MAX));
        NbtList blockList = nbt.getList(KEY_BLOCKS, NbtCompound.COMPOUND_TYPE);
        List<BlockEntry> blocks = new ArrayList<>(blockList.size());
        for (int i = 0; i < blockList.size(); i++) {
            NbtCompound block = blockList.getCompound(i);
            blocks.add(new BlockEntry(block.getLong(KEY_POS), block.getCompound(KEY_STATE)));
        }
        long[] mask = nbt.getLongArray(KEY_MASK);
        return new StructureSnapshot(anchor, min, max, blocks, mask);
    }

    public record BlockEntry(long pos, NbtCompound state) {
    }
}