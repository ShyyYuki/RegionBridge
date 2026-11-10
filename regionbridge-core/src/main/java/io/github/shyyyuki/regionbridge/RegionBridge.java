package io.github.shyyyuki.regionbridge;

import io.github.shyyyuki.regionbridge.api.EntityRetiredException;
import io.github.shyyyuki.regionbridge.api.Platform;
import io.github.shyyyuki.regionbridge.api.PlatformAdapter;
import io.github.shyyyuki.regionbridge.api.RegionExecutor;
import io.github.shyyyuki.regionbridge.internal.AdapterExecutor;
import io.github.shyyyuki.regionbridge.internal.Futures;
import io.github.shyyyuki.regionbridge.internal.PlatformDetector;
import io.github.shyyyuki.regionbridge.internal.TaskTracer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

public final class RegionBridge {
    private static final Map<Plugin, RegionBridge> INSTANCES = new ConcurrentHashMap<>();

    private final PlatformAdapter adapter;

    private RegionBridge(PlatformAdapter adapter) {
        this.adapter = adapter;
    }

    public static RegionBridge forPlugin(Plugin plugin) {
        return INSTANCES.computeIfAbsent(plugin, RegionBridge::create);
    }

    public static void release(Plugin plugin) {
        INSTANCES.remove(plugin);
    }

    public static void releaseAll() {
        INSTANCES.clear();
    }

    private static RegionBridge create(Plugin plugin) {
        Platform platform = PlatformDetector.detect();
        try {
            PlatformAdapter adapter = (PlatformAdapter) Class.forName(platform.adapterClass())
                    .getConstructor(Plugin.class).newInstance(plugin);
            return new RegionBridge(adapter);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to load adapter for " + platform, e);
        }
    }

    public Platform platform() {
        return adapter.platform();
    }

    public RegionExecutor global() {
        return AdapterExecutor.global(adapter);
    }

    public RegionExecutor async() {
        return AdapterExecutor.async(adapter);
    }

    public RegionExecutor entity(Entity entity) {
        return AdapterExecutor.entity(adapter, entity);
    }

    public RegionExecutor location(Location location) {
        return AdapterExecutor.region(adapter, location);
    }

    public RegionExecutor block(Block block) {
        return location(block.getLocation());
    }

    public RegionExecutor chunk(World world, int chunkX, int chunkZ) {
        return location(new Location(world, chunkX << 4, 0, chunkZ << 4));
    }

    public ChunkAccess chunkAt(World world, int chunkX, int chunkZ) {
        return new ChunkAccess(this, world, chunkX, chunkZ);
    }

    public ChunkAccess chunkAt(Location location) {
        return chunkAt(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public SafeWorld world(World world) {
        return new SafeWorld(this, world);
    }

    public <T> RegionFlow<T> flow(RegionExecutor executor, Supplier<T> task) {
        return RegionFlow.start(executor, task);
    }

    public <T> ChunkLocal<T> chunkLocal(Supplier<T> factory) {
        return new ChunkLocal<>(this, factory);
    }

    public <V> ConcurrentEntityMap<V> entityMap() {
        return new ConcurrentEntityMap<>(this);
    }

    public <E extends Entity, R> CompletableFuture<List<R>> mapEntities(
            Collection<E> entities,
            Function<? super E, ? extends R> task
    ) {
        List<CompletableFuture<Optional<R>>> parts = new ArrayList<>();

        for (E e : entities) {
            parts.add(entity(e).<R>supply(() -> task.apply(e)).handle((value, error) -> {
                if (error == null) {
                    return Optional.ofNullable(value);
                }
                Throwable cause = Futures.unwrap(error);
                if (cause instanceof EntityRetiredException) {
                    return Optional.<R>empty();
                }
                throw new CompletionException(cause);
            }));
        }

        return Futures.all(parts).thenApply(list -> list.stream().flatMap(Optional::stream).toList());
    }

    public CompletableFuture<Boolean> teleport(Entity entity, Location target) {
        return adapter.teleport(entity, target);
    }

    public void assertOwned(Entity entity) {
        if (BridgeDebug.enabled() && !adapter.isOwned(entity)) {
            BridgeDebug.violation("Unsafe entity access: " + entity.getUniqueId()
                    + " from thread '" + Thread.currentThread().getName() + "'");
        }
    }

    public void assertOwned(Location location) {
        if (BridgeDebug.enabled() && !adapter.isOwned(location)) {
            BridgeDebug.violation("Unsafe location access: " + location.getBlockX() + ","
                    + location.getBlockZ() + " from thread '" + Thread.currentThread().getName() + "'");
        }
    }

    public void assertGlobal() {
        if (BridgeDebug.enabled() && !adapter.isGlobalThread()) {
            BridgeDebug.violation("Global code is running on thread '"
                    + Thread.currentThread().getName() + "'");
        }
    }

    public <T> T await(CompletableFuture<T> future, long timeoutMs) {
        if (BridgeDebug.enabled() && (adapter.isGlobalThread()
                || TaskTracer.insideTickTask()
                || Bukkit.isPrimaryThread())) {
            BridgeDebug.violation(
                    "Blocking wait on a tick thread - potential deadlock risk"
            );
        }

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(e);
        } catch (ExecutionException e) {
            throw new CompletionException(e.getCause());
        } catch (TimeoutException e) {
            throw new CompletionException(
                    "Timeout after " + timeoutMs + " ms - potential deadlock",
                    e
            );
        }
    }
}