package io.github.shyyyuki.regionbridge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ChunkLocal<T> implements AutoCloseable {
    private record Key(UUID world, int x, int z) {
    }

    private final RegionBridge bridge;
    private final Supplier<T> factory;
    private final Map<Key, T> data = new ConcurrentHashMap<>();
    private Listener listener;

    ChunkLocal(RegionBridge bridge, Supplier<T> factory) {
        this.bridge = bridge;
        this.factory = factory;
    }

    private static Key keyOf(Location location) {
        return new Key(location.getWorld().getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    public T get(Location location) {
        bridge.assertOwned(location);
        return data.computeIfAbsent(keyOf(location), key -> factory.get());
    }

    public T get(World world, int chunkX, int chunkZ) {
        return get(new Location(world, chunkX << 4, 0, chunkZ << 4));
    }

    public T getIfPresent(Location location) {
        bridge.assertOwned(location);
        return data.get(keyOf(location));
    }

    public T remove(Location location) {
        return data.remove(keyOf(location));
    }

    public T remove(World world, int chunkX, int chunkZ) {
        return data.remove(new Key(world.getUID(), chunkX, chunkZ));
    }

    public void clear(World world) {
        UUID id = world.getUID();
        data.keySet().removeIf(key -> key.world().equals(id));
    }

    public void clear() {
        data.clear();
    }

    public int size() {
        return data.size();
    }

    public synchronized ChunkLocal<T> cleanOnUnload(Plugin plugin) {
        if (listener == null) {
            listener = new UnloadListener(this);
            Bukkit.getPluginManager().registerEvents(listener, plugin);
        }
        return this;
    }

    @Override
    public synchronized void close() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
    }

    public static final class UnloadListener implements Listener {
        private final ChunkLocal<?> owner;

        public UnloadListener(ChunkLocal<?> owner) {
            this.owner = owner;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onUnload(ChunkUnloadEvent event) {
            owner.remove(event.getChunk().getWorld(), event.getChunk().getX(), event.getChunk().getZ());
        }
    }
}
