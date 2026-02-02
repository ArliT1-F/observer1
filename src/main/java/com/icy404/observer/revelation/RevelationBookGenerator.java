package com.icy404.observer.revelation;

import com.icy404.observer.state.ObserverState;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class RevelationBookGenerator {
    private static final String REVELATION_TAG = "observer_revelation";

    private RevelationBookGenerator() {
    }

    public static ItemStack createFinalBook(ServerWorld world, ServerPlayerEntity player, double fidelity) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NbtCompound tag = new NbtCompound();
        tag.putString("title", "RECORD");
        tag.putString("author", hashAuthor(world, player.getUuid()));
        tag.putBoolean(REVELATION_TAG, true);

        NbtList pages = new NbtList();
        pages.add(NbtString.of(Text.Serializer.toJson(Text.literal("we do not store people"))));
        pages.add(NbtString.of(Text.Serializer.toJson(Text.literal("we store patterns"))));
        pages.add(NbtString.of(Text.Serializer.toJson(Text.literal("you were consistent"))));
        pages.add(NbtString.of(Text.Serializer.toJson(Text.literal("that was sufficient"))));
        pages.add(NbtString.of(Text.Serializer.toJson(Text.literal(formatMetrics(world, player, fidelity)))));
        tag.put("pages", pages);
        book.setNbt(tag);
        return book;
    }

    public static boolean isRevelationBook(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        NbtCompound tag = stack.getNbt();
        return tag != null && tag.getBoolean(REVELATION_TAG);
    }

    public static Optional<BlockPos> getFinalArchiveLectern(ServerWorld world, ServerPlayerEntity player) {
        return ObserverState.get(world).getFinalArchiveLectern(player.getUuid());
    }

    public static void markRevelationDecoded(ServerWorld world, ServerPlayerEntity player) {
        ObserverState.get(world).markRevelationDecoded(player.getUuid());
    }

    private static String formatMetrics(ServerWorld world, ServerPlayerEntity player, double fidelity) {
        double variance = Math.max(0.0, 1.0 - fidelity);
        String uuidHash = shortHash(player.getUuid());
        return String.format(Locale.ROOT, "fit %.2f\nvariance %.2f\nsig %s\ncost %d",
                fidelity,
                variance,
                uuidHash,
                Math.abs(world.getSeed()) % 9973);
    }

    private static String hashAuthor(ServerWorld world, UUID playerId) {
        String base = playerId.toString() + ":" + world.getSeed();
        return shortHash(base);
    }

    private static String shortHash(UUID playerId) {
        return shortHash(playerId.toString());
    }

    private static String shortHash(String input) {
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        int hash = 0x811C9DC5;
        for (byte b : bytes) {
            hash ^= b;
            hash *= 0x01000193;
        }
        return String.format(Locale.ROOT, "%08x", hash);
    }
}