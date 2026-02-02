package com.icy404.observer.observer;

import com.icy404.observer.TickManager;
import com.icy404.observer.state.ObserverState;
import net.minecraft.server.world.ServerWorld;

public final class ObserverLifecycle {
    private ObserverLifecycle() {
    }

    public static boolean isObserverDisabled(ServerWorld world) {
        return ObserverState.get(world).isObserverDisabled();
    }

    public static boolean shouldRun(ServerWorld world) {
        return !isObserverDisabled(world);
    }

    public static void markConvergence(ServerWorld world, long tick) {
        ObserverState.get(world).markObserverConvergence(tick);
    }

    public static void disableObserver(ServerWorld world) {
        ObserverState state = ObserverState.get(world);
        if (state.isObserverDisabled()) {
            return;
        }
        state.setObserverDisabled(true);

        TickManager.clearWorldQueue(world);
    }
}
