package com.icy404.observer.world;

import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class BuildQueue {
    private static final int DEFAULT_MAX_PER_TICK = 64;

    private final Queue<Placement> placements = new ArrayDeque<>();
    private int maxPerTick = DEFAULT_MAX_PER_TICK;

    public void enqueue(BlockPos pos, BlockState state) {
        placements.add(new Placement(pos.toImmutable(), state, 3));
    }

    public void enqueue(BlockPos pos, BlockState state, int flags) {
        placements.add(new Placement(pos.toImmutable(), state, flags));
    }

    public void setMaxPerTick(int maxPerTick) {
        this.maxPerTick = Math.max(1, maxPerTick);
    }

    public int getMaxPerTick() {
        return maxPerTick;
    }

    public int getQueueSize() {
        return placements.size();
    }

    public void tick(ServerWorld world) {
        int remaining = maxPerTick;
        while (remaining > 0 && !placements.isEmpty()) {
            Placement placement = placements.poll();
            if (placement != null) {
                world.setBlockState(placement.pos(), placement.state(), placement.flags());
            }
            remaining--;
        }
    }

    private record Placement(BlockPos pos, BlockState state, int flags) {
    }
}
