package com.icy404.observer.ending;

import com.icy404.observer.convergence.ConvergenceManager;
import com.icy404.observer.observer.ObserverLifecycle;
import com.icy404.observer.revelation.RevelationBookGenerator;
import com.icy404.observer.state.ObserverState;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class EndingManager {
    private static final int FREEZE_TICKS = 60;
    private static final int MESSAGE_GAP = 30;

    private static final Map<UUID, FreezeState> FREEZE_STATES = new HashMap<>();

    private EndingManager() {
    }

    public static void init() {
        // Registered via TickManager in ObserverMod.
    }

    public static void handlePlayerTick(ServerPlayerEntity player) {
        if (player == null || player.getWorld().isClient()) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        if (ObserverLifecycle.isObserverDisabled(world)) {
            return;
        }
        ObserverState observerState = ObserverState.get(world);
        if (observerState.getEndingState(player.getUuid()).isPresent() && !FREEZE_STATES.containsKey(player.getUuid())) {
            ObserverLifecycle.disableObserver(world);
            return;
        }
        FreezeState freezeState = FREEZE_STATES.get(player.getUuid());
        if (freezeState == null) {
            return;
        }
        int elapsed = (int) (world.getTime() - freezeState.startTick());
        if (elapsed <= FREEZE_TICKS) {
            freezePlayer(player, freezeState.anchor());
            if (elapsed == 0) {
                player.sendMessage(Text.literal("replication complete"), false);
            } else if (elapsed == MESSAGE_GAP) {
                player.sendMessage(Text.literal("pattern accepted"), false);
            }
        } else {
            FREEZE_STATES.remove(player.getUuid());
            player.setNoGravity(freezeState.hadNoGravity());
            ObserverLifecycle.disableObserver(world);
        }
    }

    public static void onBlockInteracted(ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (player == null || player.getWorld().isClient()) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        if (ObserverLifecycle.isObserverDisabled(world)) {
            return;
        }
        if (state.isOf(Blocks.LECTERN) && world.getBlockEntity(pos) instanceof LecternBlockEntity lectern) {
            if (RevelationBookGenerator.isRevelationBook(lectern.getBook())) {
                RevelationBookGenerator.markRevelationDecoded(world, player);
                ConvergenceManager.onFinalLoreDecoded(world, player);
                if (isFinalArchiveLectern(world, player, pos)) {
                    triggerAssimilation(player);
                }
            }
        }
        if (state.getBlock() instanceof net.minecraft.block.BedBlock && ConvergenceManager.isWithinMostPerfectHouse(player, pos)) {
            triggerAssimilation(player);
        }
    }

    public static void onBlockBroken(ServerPlayerEntity player, BlockPos pos, BlockState state) {
        if (player == null || player.getWorld().isClient()) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        if (ObserverLifecycle.isObserverDisabled(world)) {
            return;
        }
        ObserverState observerState = ObserverState.get(world);
        if (!observerState.isObserverConvergence()) {
            return;
        }
        if (!observerState.isRevelationDecoded(player.getUuid())) {
            return;
        }
        if (state.isOf(Blocks.LECTERN) && isFinalArchiveLectern(world, player, pos)) {
            triggerEscape(world, player);
        }
    }

    public static void onSleep(ServerPlayerEntity player, BlockPos bedPos) {
        if (player == null || player.getWorld().isClient()) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        if (ObserverLifecycle.isObserverDisabled(world)) {
            return;
        }
        if (ConvergenceManager.isConvergenceActive(world)) {
            triggerAssimilation(player);
        }
    }

    private static boolean isFinalArchiveLectern(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        Optional<BlockPos> lecternPos = RevelationBookGenerator.getFinalArchiveLectern(world, player);
        return lecternPos.filter(pos::equals).isPresent();
    }

    private static void triggerEscape(ServerWorld world, ServerPlayerEntity player) {
        ObserverState state = ObserverState.get(world);
        if (state.getEndingState(player.getUuid()).isPresent()) {
            return;
        }
        state.setEndingState(player.getUuid(), "escape");
        player.sendMessage(Text.literal("variance restored"), false);
        ObserverLifecycle.disableObserver(world);
    }

    private static void triggerAssimilation(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        ObserverState state = ObserverState.get(world);
        if (state.getEndingState(player.getUuid()).isPresent()) {
            return;
        }
        state.setEndingState(player.getUuid(), "assimilation");
        if (!FREEZE_STATES.containsKey(player.getUuid())) {
            FreezeState freeze = new FreezeState(world.getTime(), player.getPos(), player.hasNoGravity());
            FREEZE_STATES.put(player.getUuid(), freeze);
        }
    }

    private static void freezePlayer(ServerPlayerEntity player, Vec3d anchor) {
        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;
        player.setNoGravity(true);
        player.teleport(player.getServerWorld(), anchor.x, anchor.y, anchor.z, player.getYaw(), player.getPitch());
    }

    private record FreezeState(long startTick, Vec3d anchor, boolean hadNoGravity) {
    }
}