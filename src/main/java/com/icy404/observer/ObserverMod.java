package com.icy404.observer;

import com.icy404.observer.snapshot.Snapshotter;
import com.icy404.observer.state.ObserverState;
import com.icy404.observer.util.LogUtil;


import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;


public final class ObserverMod implements ModInitializer {
	@Override
	public void onInitialize() {
		LogUtil.info("Observer mod initializing.");

		ServerTickEvents.END_SERVER_TICK.register(TickManager::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> TickManager.reset());
		Snapshotter.init();

		ServerTickEvents.END_WORLD_TICK.register(this::ensurePersistentState);
	}

	private void ensurePersistentState(ServerWorld world) {
		ObserverState.get(world);
	}
}