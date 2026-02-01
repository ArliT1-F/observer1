package com.icy404.observer.state;

import com.icy404.observer.ghost.GhostHouseManager;
import com.icy404.observer.profile.HomeProfiler;
import com.icy404.observer.snapshot.StructureSnapshot;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.util.math.BlockPos;

public final class ObserverState extends PersistentState {
    private static final String DATA_KEY = "observer_state";

    private final Map<UUID, NbtCompound> playerData = new HashMap<>();
    private final Map<UUID, List<StructureSnapshot>> snapshotHistory = new HashMap<>();
    private final Map<UUID, HomeProfiler.HomeProfile> homeProfiles = new HashMap<>();
    private final Map<UUID, Integer> attemptCounts = new HashMap<>();
    private final Map<UUID, List<GhostHouseManager.AttemptRecord>> attemptRecords = new HashMap<>();
    private final Map<UUID, Long> lastGhostHouseDay = new HashMap<>();
    private final Map<UUID, Long> archiveOrigins = new HashMap<>();
    private final Map<UUID, Set<Integer>> archivePastedAttempts = new HashMap<>();

    public static ObserverState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
            ObserverState::fromNbt,
            ObserverState::new,
            DATA_KEY
        );
    }

    public static ObserverState fromNbt(NbtCompound nbt) {
        ObserverState state = new ObserverState();
        NbtCompound players = nbt.getCompound("players");
        for (String key : players.getKeys()) {
            try {
                UUID playerId = UUID.fromString(key);
                state.playerData.put(playerId, players.getCompound(key));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed UUIDs.
            }
        }
        NbtCompound snapshots = nbt.getCompound("snapshots");
        for (String key : snapshots.getKeys()) {
            try {
                UUID playerId = UUID.fromString(key);
                NbtList list = snapshots.getList(key, NbtCompound.COMPOUND_TYPE);
                List<StructureSnapshot> entries = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    entries.add(StructureSnapshot.fromNbt(list.getCompound(i)));
                }
                state.snapshotHistory.put(playerId, entries);
            } catch (IllegalArgumentException ignored) {
                // Skip malformed UUIDs.
            }
        }
        NbtCompound profiles = nbt.getCompound("homeProfiles");
        for (String key : profiles.getKeys()) {
            try {
                UUID playerId = UUID.fromString(key);
                state.homeProfiles.put(playerId, HomeProfiler.fromNbt(profiles.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed UUIDs.
            }
        }
        NbtCompound attempts = nbt.getCompound("ghostAttempts");
        for (String key : attempts.getKeys()) {
            try {
                UUID playerId = UUID.fromString(key);
                NbtList list = attempts.getList(key, NbtCompound.COMPOUND_TYPE);
                List<GhostHouseManager.AttemptRecord> records = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    records.add(GhostHouseManager.AttemptRecord.fromNbt(list.getCompound(i)));
                }
                state.attemptRecords.put(playerId, records);
            } catch (IllegalArgumentException ignored) {
                // Skip malformed UUIDs.
            }
        }
        NbtCompound attemptCounts = nbt.getCompound("ghostAttemptCounts");
        for (String key : attemptCounts.getKeys()) {
            try {
                UUID playerId = UUID.fromString(key);
                state.attemptCounts.put(playerId, attemptCounts.getInt(key));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed UUIDs.
            }
        }
        NbtCompound lastDays = nbt.getCompound("ghostAttemptDays");
        for (String key : lastDays.getKeys()) {
            try {
                UUID playerId = UUID.fromString(key);
                state.lastGhostHouseDay.put(playerId, lastDays.getLong(key));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed UUIDs.
            }
        }
        NbtCompound archiveOrigins = nbt.getCompound("archiveOrigins");
        for (String key : archiveOrigins.getKeys()) {
            try {
                UUID playerId = UUID.fromString(key);
                state.archiveOrigins.put(playerId, archiveOrigins.getLong(key));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed UUIDs.
            }
        }
        NbtCompound archivePasted = nbt.getCompound("archivePastedAttempts");
        for (String key : archivePasted.getKeys()) {
            try {
                UUID playerId = UUID.fromString(key);
                NbtList list = archivePasted.getList(key, NbtElement.INT_TYPE);
                Set<Integer> attempts = new HashSet<>();
                for (int i = 0; i < list.size(); i++) {
                    attempts.add(list.getInt(i));
                }
                state.archivePastedAttempts.put(playerId, attempts);
            } catch (IllegalArgumentException ignored) {
                // Skip malformed UUIDs.
            }
        }
        return state;
    }

    public NbtCompound getPlayerData(UUID playerId) {
        NbtCompound data = playerData.get(playerId);
        return data == null ? new NbtCompound() : data.copy();
    }

    public void setPlayerData(UUID playerId, NbtCompound data) {
        playerData.put(playerId, data.copy());
        markDirty();
    }

    public HomeProfiler.HomeProfile getHomeProfile(UUID playerId) {
        return homeProfiles.computeIfAbsent(playerId, id -> new HomeProfiler.HomeProfile());
    }

    public int incrementAttemptCount(UUID playerId) {
        int next = attemptCounts.getOrDefault(playerId, 0) + 1;
        attemptCounts.put(playerId, next);
        markDirty();
        return next;
    }

    public void addAttemptRecord(UUID playerId, GhostHouseManager.AttemptRecord record) {
        List<GhostHouseManager.AttemptRecord> records = attemptRecords.computeIfAbsent(playerId, id -> new ArrayList<>());
        records.add(record);
        markDirty();
    }

    public List<GhostHouseManager.AttemptRecord> getAttemptRecords(UUID playerId) {
        List<GhostHouseManager.AttemptRecord> records = attemptRecords.get(playerId);
        return records == null ? List.of() : List.copyOf(records);
    }

    public Optional<BlockPos> getArchiveOrigin(UUID playerId) {
        Long origin = archiveOrigins.get(playerId);
        return origin == null ? Optional.empty() : Optional.of(BlockPos.fromLong(origin));
    }

    public void setArchiveOrigin(UUID playerId, BlockPos origin) {
        archiveOrigins.put(playerId, origin.asLong());
        markDirty();
    }

    public boolean isArchiveAttemptPasted(UUID playerId, int attemptId) {
        Set<Integer> attempts = archivePastedAttempts.get(playerId);
        return attempts != null && attempts.contains(attemptId);
    }

    public void markArchiveAttemptPasted(UUID playerId, int attemptId) {
        archivePastedAttempts.computeIfAbsent(playerId, id -> new HashSet<>()).add(attemptId);
        markDirty();
    }

    public boolean shouldAttemptGhostHouse(UUID playerId, long day) {
        return lastGhostHouseDay.getOrDefault(playerId, -1L) < day;
    }

    public void markAttemptedToday(UUID playerId, long day) {
        lastGhostHouseDay.put(playerId, day);
        markDirty();
    }

    public List<StructureSnapshot> getSnapshots(UUID playerId) {
        List<StructureSnapshot> snapshots = snapshotHistory.get(playerId);
        return snapshots == null ? List.of() : List.copyOf(snapshots);
    }

    public void addSnapshot(UUID playerId, StructureSnapshot snapshot, int maxSnapshots) {
        List<StructureSnapshot> snapshots = snapshotHistory.computeIfAbsent(playerId, id -> new ArrayList<>());
        snapshots.add(snapshot);
        if (snapshots.size() > maxSnapshots) {
            snapshots.subList(0, snapshots.size() - maxSnapshots).clear();
        }
        markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound players = new NbtCompound();
        for (Map.Entry<UUID, NbtCompound> entry : playerData.entrySet()) {
            players.put(entry.getKey().toString(), entry.getValue().copy());
        }
        nbt.put("players", players);
        NbtCompound snapshots = new NbtCompound();
        for (Map.Entry<UUID, List<StructureSnapshot>> entry : snapshotHistory.entrySet()) {
            NbtList list = new NbtList();
            for (StructureSnapshot snapshot : entry.getValue()) {
                list.add(snapshot.toNbt());
            }
            snapshots.put(entry.getKey().toString(), list);
        }
        nbt.put("snapshots", snapshots);
        NbtCompound profiles = new NbtCompound();
        for (Map.Entry<UUID, HomeProfiler.HomeProfile> entry : homeProfiles.entrySet()) {
            profiles.put(entry.getKey().toString(), HomeProfiler.toNbt(entry.getValue()));
        }
        nbt.put("homeProfiles", profiles);
        NbtCompound attempts = new NbtCompound();
        for (Map.Entry<UUID, List<GhostHouseManager.AttemptRecord>> entry : attemptRecords.entrySet()) {
            NbtList list = new NbtList();
            for (GhostHouseManager.AttemptRecord record : entry.getValue()) {
                list.add(record.toNbt());
            }
            attempts.put(entry.getKey().toString(), list);
        }
        nbt.put("ghostAttempts", attempts);
        NbtCompound counts = new NbtCompound();
        for (Map.Entry<UUID, Integer> entry : attemptCounts.entrySet()) {
            counts.putInt(entry.getKey().toString(), entry.getValue());
        }
        nbt.put("ghostAttemptCounts", counts);
        NbtCompound days = new NbtCompound();
        for (Map.Entry<UUID, Long> entry : lastGhostHouseDay.entrySet()) {
            days.putLong(entry.getKey().toString(), entry.getValue());
        }
        nbt.put("ghostAttemptDays", days);
        NbtCompound originNbt = new NbtCompound();
        for (Map.Entry<UUID, Long> entry : archiveOrigins.entrySet()) {
            originNbt.putLong(entry.getKey().toString(), entry.getValue());
        }
        nbt.put("archiveOrigins", originNbt);
        NbtCompound pastedNbt = new NbtCompound();
        for (Map.Entry<UUID, Set<Integer>> entry : archivePastedAttempts.entrySet()) {
            NbtList list = new NbtList();
            for (Integer attemptId : entry.getValue()) {
                list.add(NbtInt.of(attemptId));
            }
            pastedNbt.put(entry.getKey().toString(), list);
        }
        nbt.put("archivePastedAttempts", pastedNbt);
        return nbt;
    }