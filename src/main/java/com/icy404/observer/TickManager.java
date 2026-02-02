package com.icy404.observer;

import com.icy404.observer.world.BuildQueue;
import com.icy404.observer.observer.ObserverLifecycle;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

public final class TickManager {
    private static final Map<Identifier, BuildQueue> WORLD_QUEUES = new ConcurrentHashMap<>();
    private static final List<Consumer<ServerPlayerEntity>> PLAYER_TICK_HANDLERS = new CopyOnWriteArrayList<>();

    private TickManager() {
    }

    public static void registerPlayerTick(Consumer<ServerPlayerEntity> handler) {
        PLAYER_TICK_HANDLERS.add(handler);
    }

    public static BuildQueue getQueue(ServerWorld world) {
        Identifier worldId = world.getRegistryKey().getValue();
        return WORLD_QUEUES.computeIfAbsent(worldId, id -> new BuildQueue());
    }
    public static void clearWorldQueue(ServerWorld world) {
        Identifier worldId = world.getRegistryKey().getValue();
        BuildQueue queue = WORLD_QUEUES.get(worldId);
        if (queue != null) {
            queue.clear();
        }
    }
    public static void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            for (Consumer<ServerPlayerEntity> handler : PLAYER_TICK_HANDLERS) {
                handler.accept(player);
            }
        }

        for (ServerWorld world : server.getWorlds()) {
            if (ObserverLifecycle.shouldRun(world)) {
                getQueue(world).tick(world);
            }

        }
    }

    public static void reset() {
        WORLD_QUEUES.clear();
        PLAYER_TICK_HANDLERS.clear();
    }
}