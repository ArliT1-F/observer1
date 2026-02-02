package com.icy404.observer.profile;

import com.icy404.observer.TickManager;
import com.icy404.observer.observer.ObserverLifecycle;
import com.icy404.observer.state.ObserverState;
import com.icy404.observer.util.LogUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class HomeProfiler {
    private static final int GRID_SIZE = 8;
    private static final int HOME_SCORE_THRESHOLD = 600;
    private static final int RECOMPUTE_INTERVAL_TICKS = 20 * 60;
    private static final int DECAY_INTERVAL_TICKS = 20 * 120;
    private static final int ACTION_RECENT_TICKS = 20 * 8;

    private static final int WEIGHT_BLOCK_BREAK = 6;
    private static final int WEIGHT_BLOCK_PLACE = 5;
    private static final int WEIGHT_USE_BLOCK = 4;
    private static final int WEIGHT_ATTACK_BLOCK = 3;
    private static final int WEIGHT_USE_ITEM = 2;
    private static final int WEIGHT_SLEEP = 240;

    private static final double MOVE_THRESHOLD_SQ = 0.08;
    private static final double DECAY_FACTOR = 0.985;

    private static final Map<UUID, Long> LAST_SAMPLE_TICK = new HashMap<>();
    private static final Map<UUID, BlockPos> LAST_SAMPLE_POS = new HashMap<>();
    private static final Map<UUID, Long> LAST_RECOMPUTE_TICK = new HashMap<>();
    private static final Map<UUID, Long> LAST_DECAY_TICK = new HashMap<>();

    private HomeProfiler() {
    }

    public static void init() {
        TickManager.registerPlayerTick(HomeProfiler::handlePlayerTick);
    }

    public static void recordBlockBreak(PlayerEntity player) {
        recordAction(player, WEIGHT_BLOCK_BREAK);
    }

    public static void recordBlockPlace(PlayerEntity player) {
        recordAction(player, WEIGHT_BLOCK_PLACE);
    }

    public static void recordUseBlock(PlayerEntity player) {
        recordAction(player, WEIGHT_USE_BLOCK);
    }

    public static void recordAttackBlock(PlayerEntity player) {
        recordAction(player, WEIGHT_ATTACK_BLOCK);
    }

    public static void recordUseItem(PlayerEntity player) {
        recordAction(player, WEIGHT_USE_ITEM);
    }

    public static void recordSleep(ServerPlayerEntity player, BlockPos bedPos) {
        if (!isServerPlayer(player)) {
            return;
        }
        if (ObserverLifecycle.isObserverDisabled(player.getServerWorld())) {
            return;
        }
        HomeProfile profile = getProfile(player);
        addScore(profile, bedPos, WEIGHT_SLEEP);
        profile.lastActionTick = player.getServerWorld().getTime();
        persist(player.getServerWorld());
    }

    public static Optional<BlockPos> getHomeAnchor(ServerPlayerEntity player) {
        if (!isServerPlayer(player)) {
            return Optional.empty();
        }
        HomeProfile profile = getProfile(player);
        if (profile.chosenHomeAnchor == Long.MIN_VALUE) {
            return Optional.empty();
        }
        return Optional.of(BlockPos.fromLong(profile.chosenHomeAnchor));
    }

    public static boolean isActive(ServerPlayerEntity player) {
        return player != null && player.isAlive() && !player.isSpectator() && !player.isRemoved();
    }

    public static HomeProfile fromNbt(NbtCompound nbt) {
        HomeProfile profile = new HomeProfile();
        NbtCompound candidates = nbt.getCompound("candidates");
        for (String key : candidates.getKeys()) {
            profile.candidates.put(Long.parseLong(key), candidates.getInt(key));
        }
        profile.lastActionTick = nbt.getLong("lastActionTick");
        profile.activeTicks = nbt.getLong("activeTicks");
        profile.afkTicks = nbt.getLong("afkTicks");
        profile.chosenHomeAnchor = nbt.getLong("chosenHomeAnchor");
        return profile;
    }

    public static NbtCompound toNbt(HomeProfile profile) {
        NbtCompound nbt = new NbtCompound();
        NbtCompound candidates = new NbtCompound();
        for (Map.Entry<Long, Integer> entry : profile.candidates.entrySet()) {
            candidates.putInt(String.valueOf(entry.getKey()), entry.getValue());
        }
        nbt.put("candidates", candidates);
        nbt.putLong("lastActionTick", profile.lastActionTick);
        nbt.putLong("activeTicks", profile.activeTicks);
        nbt.putLong("afkTicks", profile.afkTicks);
        nbt.putLong("chosenHomeAnchor", profile.chosenHomeAnchor);
        return nbt;
    }

    private static void handlePlayerTick(ServerPlayerEntity player) {
        if (!isServerPlayer(player)) {
            return;
        }

        ServerWorld world = player.getServerWorld();
        if (ObserverLifecycle.isObserverDisabled(world)) {
            return;
        }
        long worldTime = world.getTime();
        HomeProfile profile = getProfile(player);

        sampleDwell(player, profile, worldTime);
        recomputeIfNeeded(player, profile, worldTime);
        decayIfNeeded(player, profile, worldTime);
    }

    private static void sampleDwell(ServerPlayerEntity player, HomeProfile profile, long worldTime) {
        UUID playerId = player.getUuid();
        long lastSampleTick = LAST_SAMPLE_TICK.getOrDefault(playerId, worldTime);
        if (worldTime - lastSampleTick < 20) {
            return;
        }

        Vec3d currentPos = player.getPos();
        BlockPos lastPos = LAST_SAMPLE_POS.get(playerId);
        boolean moved = lastPos == null || currentPos.squaredDistanceTo(Vec3d.ofCenter(lastPos)) > MOVE_THRESHOLD_SQ;
        boolean recentAction = worldTime - profile.lastActionTick <= ACTION_RECENT_TICKS;
        if (moved || recentAction) {
            profile.activeTicks++;
            addScore(profile, BlockPos.ofFloored(currentPos), 1);
        } else {
            profile.afkTicks++;
        }

        LAST_SAMPLE_TICK.put(playerId, worldTime);
        LAST_SAMPLE_POS.put(playerId, BlockPos.ofFloored(currentPos));
        persist(player.getServerWorld());
    }

    private static void recomputeIfNeeded(ServerPlayerEntity player, HomeProfile profile, long worldTime) {
        UUID playerId = player.getUuid();
        long lastRecompute = LAST_RECOMPUTE_TICK.getOrDefault(playerId, worldTime);
        if (worldTime - lastRecompute < RECOMPUTE_INTERVAL_TICKS) {
            return;
        }

        LAST_RECOMPUTE_TICK.put(playerId, worldTime);
        if (profile.candidates.isEmpty()) {
            return;
        }

        Map.Entry<Long, Integer> best = null;
        for (Map.Entry<Long, Integer> entry : profile.candidates.entrySet()) {
            if (best == null || entry.getValue() > best.getValue()) {
                best = entry;
            }
        }
        if (best == null || best.getValue() < HOME_SCORE_THRESHOLD) {
            return;
        }

        if (profile.chosenHomeAnchor != best.getKey()) {
            profile.chosenHomeAnchor = best.getKey();
            BlockPos anchor = BlockPos.fromLong(best.getKey());
            LogUtil.info("Home anchor set for " + player.getName().getString() + " at " + anchor);
            persist(player.getServerWorld());
        }
    }

    private static void decayIfNeeded(ServerPlayerEntity player, HomeProfile profile, long worldTime) {
        UUID playerId = player.getUuid();
        long lastDecay = LAST_DECAY_TICK.getOrDefault(playerId, worldTime);
        if (worldTime - lastDecay < DECAY_INTERVAL_TICKS) {
            return;
        }
        LAST_DECAY_TICK.put(playerId, worldTime);

        Map<Long, Integer> updated = new HashMap<>();
        for (Map.Entry<Long, Integer> entry : profile.candidates.entrySet()) {
            int decayed = (int) Math.floor(entry.getValue() * DECAY_FACTOR);
            if (decayed > 0) {
                updated.put(entry.getKey(), decayed);
            }
        }
        profile.candidates.clear();
        profile.candidates.putAll(updated);
        persist(player.getServerWorld());
    }

    private static void recordAction(PlayerEntity player, int weight) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        if (ObserverLifecycle.isObserverDisabled(world)) {
            return;
        }
        HomeProfile profile = getProfile(serverPlayer);
        addScore(profile, BlockPos.ofFloored(serverPlayer.getPos()), weight);
        profile.lastActionTick = serverPlayer.getServerWorld().getTime();
        persist(serverPlayer.getServerWorld());
    }

    private static void addScore(HomeProfile profile, BlockPos pos, int weight) {
        long key = quantize(pos).asLong();
        profile.candidates.merge(key, weight, Integer::sum);
    }

    private static BlockPos quantize(BlockPos pos) {
        int x = Math.floorDiv(pos.getX(), GRID_SIZE) * GRID_SIZE;
        int y = Math.floorDiv(pos.getY(), GRID_SIZE) * GRID_SIZE;
        int z = Math.floorDiv(pos.getZ(), GRID_SIZE) * GRID_SIZE;
        return new BlockPos(x, y, z);
    }

    private static HomeProfile getProfile(ServerPlayerEntity player) {
        return ObserverState.get(player.getServerWorld()).getHomeProfile(player.getUuid());
    }

    private static void persist(ServerWorld world) {
        ObserverState.get(world).markDirty();
    }

    private static boolean isServerPlayer(ServerPlayerEntity player) {
        return player != null && !player.getWorld().isClient();
    }

    public static final class HomeProfile {
        private final Map<Long, Integer> candidates = new HashMap<>();
        private long lastActionTick;
        private long activeTicks;
        private long afkTicks;
        private long chosenHomeAnchor = Long.MIN_VALUE;

        public Map<Long, Integer> candidates() {
            return candidates;
        }

        public long lastActionTick() {
            return lastActionTick;
        }

        public long activeTicks() {
            return activeTicks;
        }

        public long afkTicks() {
            return afkTicks;
        }

        public long chosenHomeAnchor() {
            return chosenHomeAnchor;
        }
    }
}