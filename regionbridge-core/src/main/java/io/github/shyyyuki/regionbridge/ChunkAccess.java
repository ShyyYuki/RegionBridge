package io.github.shyyyuki.regionbridge;

import io.github.shyyyuki.regionbridge.api.RegionExecutor;
import io.github.shyyyuki.regionbridge.internal.ChunkLoader;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ChunkAccess {
    private final RegionBridge bridge;
    private final World world;
    private final int x;
    private final int z;

    ChunkAccess(RegionBridge bridge, World world, int x, int z) {
        this.bridge = bridge;
        this.world = world;
        this.x = x;
        this.z = z;
    }

    public World world() {
        return world;
    }

    public int x() {
        return x;
    }

    public int z() {
        return z;
    }

    public RegionExecutor executor() {
        return bridge.chunk(world, x, z);
    }

    public CompletableFuture<Chunk> load() {
        return load(true);
    }

    public CompletableFuture<Chunk> load(boolean generate) {
        return ChunkLoader.load(bridge.global(), world, x, z, generate);
    }

    public <T> CompletableFuture<T> supply(Function<Chunk, T> task) {
        return load(true).thenCompose(chunk -> {
            if (chunk == null) {
                return CompletableFuture.<T>failedFuture(
                        new IllegalStateException("Chunk " + x + "," + z + " could not be loaded"));
            }
            return executor().<T>supply(() -> task.apply(chunk));
        });
    }

    public <T> CompletableFuture<T> supplyExisting(Function<Chunk, T> task, T absent) {
        return load(false).thenCompose(chunk -> {
            if (chunk == null) {
                return CompletableFuture.completedFuture(absent);
            }
            return executor().<T>supply(() -> task.apply(chunk));
        });
    }

    public CompletableFuture<Void> run(Consumer<Chunk> task) {
        return this.<Void>supply(chunk -> {
            task.accept(chunk);
            return null;
        });
    }

    public CompletableFuture<List<Entity>> entities() {
        return entities(entity -> true);
    }

    public CompletableFuture<List<Entity>> entities(Predicate<Entity> filter) {
        return supplyExisting(
                chunk -> Arrays.stream(chunk.getEntities()).filter(filter).toList(),
                List.<Entity>of()
        );
    }
}
