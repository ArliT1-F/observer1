package com.icy404.observer.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class PosUtil {
    private PosUtil() {
    }

    public static BlockPos toBlockPos(Vec3d vec) {
        return new BlockPos((int) Math.floor(vec.x), (int) Math.floor(vec.y), (int) Math.floor(vec.z));
    }

    public static Vec3d centerOf(BlockPos pos) {
        return Vec3d.ofCenter(pos);
    }
}