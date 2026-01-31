package com.icy404.observer;

import com.icy404.observer.profile.HomeProfiler;
import com.icy404.observer.snapshot.Snapshotter;
import com.icy404.observer.state.ObserverState;
import com.icy404.observer.util.LogUtil;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;

public final class ObserverMod implements ModInitializer {
    @Override
    public void onInitialize() {
        LogUtil.info("Observer mod initializing.");

        ServerTickEvents.END_SERVER_TICK.register(TickManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> TickManager.reset());
        Snapshotter.init();
        HomeProfiler.init();

        PlayerBlockBreakEvents.AFTER
                .register((world, player, pos, state, blockEntity) -> HomeProfiler.recordBlockBreak(player));
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            HomeProfiler.recordUseBlock(player);
            return ActionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            HomeProfiler.recordUseItem(player);
            return TypedActionResult.pass(player.getStackInHand(hand));
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            HomeProfiler.recordAttackBlock(player);
            return ActionResult.PASS;
        });

        ServerTickEvents.END_WORLD_TICK.register(this::ensurePersistentState);
    }

    private void ensurePersistentState(ServerWorld world) {
        ObserverState.get(world);
    }
}