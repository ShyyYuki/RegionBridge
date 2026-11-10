package io.github.shyyyuki.regionbridge;

import io.github.shyyyuki.regionbridge.api.TaskHandle;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class ConcurrentEntityMap<V> implements AutoCloseable {
    private record Entry<V>(WeakReference<Entity> entity, V value) {
    }

    private final RegionBridge bridge;
    private final Map<UUID, Entry<V>> data = new ConcurrentHashMap<>();
    private Listener listener;
    private TaskHandle sweeper;

    ConcurrentEntityMap(RegionBridge bridge) {
        this.bridge = bridge;
    }

    public V put(Entity entity, V value) {
        Entry<V> old = data.put(entity.getUniqueId(), new Entry<V>(new WeakReference<>(entity), value));
        return old == null ? null : old.value();
    }

    public V get(Entity entity) {
        return get(entity.getUniqueId());
    }

    public V get(UUID id) {
        Entry<V> entry = data.get(id);
        return entry == null ? null : entry.value();
    }

    public V computeIfAbsent(Entity entity, Function<? super Entity, ? extends V> factory) {
        return data.computeIfAbsent(
                entity.getUniqueId(),
                id -> new Entry<V>(new WeakReference<>(entity), factory.apply(entity))
        ).value();
    }

    public V remove(Entity entity) {
        return remove(entity.getUniqueId());
    }

    public V remove(UUID id) {
        Entry<V> entry = data.remove(id);
        return entry == null ? null : entry.value();
    }

    public boolean contains(Entity entity) {
        return data.containsKey(entity.getUniqueId());
    }

    public boolean contains(UUID id) {
        return data.containsKey(id);
    }

    public int size() {
        return data.size();
    }

    public Collection<V> values() {
        return data.values().stream().map(Entry::value).toList();
    }

    public void forEach(BiConsumer<UUID, V> action) {
        data.forEach((id, entry) -> action.accept(id, entry.value()));
    }

    public void clear() {
        data.clear();
    }

    public CompletableFuture<Void> sweep() {
        List<CompletableFuture<Void>> checks = new ArrayList<>();

        for (Map.Entry<UUID, Entry<V>> mapEntry : data.entrySet()) {
            UUID id = mapEntry.getKey();
            Entity entity = mapEntry.getValue().entity().get();

            if (entity == null) {
                data.remove(id);
                continue;
            }

            checks.add(bridge.entity(entity).supply(entity::isValid).handle((valid, error) -> {
                if (error != null || !Boolean.TRUE.equals(valid)) {
                    data.remove(id);
                }
                return (Void) null;
            }));
        }

        return CompletableFuture.allOf(checks.toArray(new CompletableFuture<?>[0]));
    }

    public synchronized ConcurrentEntityMap<V> autoClean(Plugin plugin, long sweepPeriodTicks) {
        if (listener == null) {
            listener = new CleanupListener(this);
            Bukkit.getPluginManager().registerEvents(listener, plugin);
        }
        if (sweeper == null && sweepPeriodTicks > 0) {
            sweeper = bridge.global().executeTimer(() -> sweep(), sweepPeriodTicks, sweepPeriodTicks);
        }
        return this;
    }

    @Override
    public synchronized void close() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        if (sweeper != null) {
            sweeper.cancel();
            sweeper = null;
        }
    }

    public static final class CleanupListener implements Listener {
        private final ConcurrentEntityMap<?> owner;

        public CleanupListener(ConcurrentEntityMap<?> owner) {
            this.owner = owner;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent event) {
            owner.remove(event.getPlayer().getUniqueId());
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onDeath(EntityDeathEvent event) {
            if (event.getEntity() instanceof Player) {
                return;
            }
            owner.remove(event.getEntity().getUniqueId());
        }
    }
}
