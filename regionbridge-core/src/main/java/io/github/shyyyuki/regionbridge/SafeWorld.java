package io.github.shyyyuki.regionbridge;

import io.github.shyyyuki.regionbridge.internal.Futures;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class SafeWorld {
    private static final int MAX_CHUNKS = 1024;

    private final RegionBridge bridge;
    private final World world;

    SafeWorld(RegionBridge bridge, World world) {
        this.bridge = bridge;
        this.world = world;
    }

    public World world() {
        return world;
    }

    private ChunkAccess chunkOf(int blockX, int blockZ) {
        return bridge.chunkAt(world, blockX >> 4, blockZ >> 4);
    }

    public CompletableFuture<Void> setBlock(int x, int y, int z, Material material) {
        return chunkOf(x, z).run(chunk -> world.getBlockAt(x, y, z).setType(material));
    }

    public CompletableFuture<Void> setBlock(Location location, Material material) {
        return setBlock(location.getBlockX(), location.getBlockY(), location.getBlockZ(), material);
    }

    public CompletableFuture<Void> setBlockData(int x, int y, int z, BlockData data) {
        return chunkOf(x, z).run(chunk -> world.getBlockAt(x, y, z).setBlockData(data));
    }

    public CompletableFuture<Void> setBlockData(Location location, BlockData data) {
        return setBlockData(location.getBlockX(), location.getBlockY(), location.getBlockZ(), data);
    }

    public CompletableFuture<Material> getType(int x, int y, int z) {
        return chunkOf(x, z).supply(chunk -> world.getBlockAt(x, y, z).getType());
    }

    public CompletableFuture<Material> getType(Location location) {
        return getType(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public CompletableFuture<BlockData> getBlockData(int x, int y, int z) {
        return chunkOf(x, z).supply(chunk -> world.getBlockAt(x, y, z).getBlockData());
    }

    public CompletableFuture<BlockData> getBlockData(Location location) {
        return getBlockData(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public CompletableFuture<Integer> fill(Location a, Location b, Material material) {
        int minX = Math.min(a.getBlockX(), b.getBlockX());
        int maxX = Math.max(a.getBlockX(), b.getBlockX());
        int minY = Math.max(Math.min(a.getBlockY(), b.getBlockY()), world.getMinHeight());
        int maxY = Math.min(Math.max(a.getBlockY(), b.getBlockY()), world.getMaxHeight() - 1);
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        int maxZ = Math.max(a.getBlockZ(), b.getBlockZ());

        int chunkCount = ((maxX >> 4) - (minX >> 4) + 1) * ((maxZ >> 4) - (minZ >> 4) + 1);
        if (chunkCount > MAX_CHUNKS) {
            throw new IllegalArgumentException("Region spans " + chunkCount + " chunks, maximum is " + MAX_CHUNKS);
        }

        List<CompletableFuture<Integer>> parts = new ArrayList<>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                final int x0 = Math.max(minX, cx << 4);
                final int x1 = Math.min(maxX, (cx << 4) + 15);
                final int z0 = Math.max(minZ, cz << 4);
                final int z1 = Math.min(maxZ, (cz << 4) + 15);
                final int y0 = minY;
                final int y1 = maxY;
                parts.add(bridge.chunkAt(world, cx, cz).supply(chunk -> {
                    int changed = 0;
                    for (int x = x0; x <= x1; x++) {
                        for (int y = y0; y <= y1; y++) {
                            for (int z = z0; z <= z1; z++) {
                                world.getBlockAt(x, y, z).setType(material);
                                changed++;
                            }
                        }
                    }
                    return changed;
                }));
            }
        }

        return Futures.all(parts).thenApply(list -> list.stream().mapToInt(Integer::intValue).sum());
    }

    public <T extends Entity> CompletableFuture<T> spawn(Location location, Class<T> type) {
        return chunkOf(location.getBlockX(), location.getBlockZ())
                .supply(chunk -> world.spawn(location, type));
    }

    public <T extends Entity> CompletableFuture<T> spawn(
            Location location,
            Class<T> type,
            Consumer<? super T> beforeSpawn
    ) {
        return chunkOf(location.getBlockX(), location.getBlockZ())
                .supply(chunk -> world.spawn(location, type, beforeSpawn));
    }

    public CompletableFuture<Entity> spawn(Location location, EntityType type) {
        return chunkOf(location.getBlockX(), location.getBlockZ())
                .supply(chunk -> world.spawnEntity(location, type));
    }

    public CompletableFuture<Item> dropItem(Location location, ItemStack item) {
        return chunkOf(location.getBlockX(), location.getBlockZ())
                .supply(chunk -> world.dropItemNaturally(location, item));
    }

    public CompletableFuture<List<Entity>> getNearbyEntities(Location center, double radius) {
        return getNearbyEntities(center, radius, entity -> true);
    }

    public CompletableFuture<List<Entity>> getNearbyEntities(
            Location center,
            double radius,
            Predicate<Entity> filter
    ) {
        if (radius < 0) {
            throw new IllegalArgumentException("radius must not be negative");
        }

        final double cx = center.getX();
        final double cy = center.getY();
        final double cz = center.getZ();
        final double radiusSquared = radius * radius;

        int minChunkX = ((int) Math.floor(cx - radius)) >> 4;
        int maxChunkX = ((int) Math.floor(cx + radius)) >> 4;
        int minChunkZ = ((int) Math.floor(cz - radius)) >> 4;
        int maxChunkZ = ((int) Math.floor(cz + radius)) >> 4;

        int chunkCount = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
        if (chunkCount > MAX_CHUNKS) {
            throw new IllegalArgumentException("Radius spans " + chunkCount + " chunks, maximum is " + MAX_CHUNKS);
        }

        List<CompletableFuture<List<Entity>>> parts = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                parts.add(bridge.chunkAt(world, chunkX, chunkZ).supplyExisting(chunk -> {
                    List<Entity> found = new ArrayList<>();
                    for (Entity entity : chunk.getEntities()) {
                        Location l = entity.getLocation();
                        double dx = l.getX() - cx;
                        double dy = l.getY() - cy;
                        double dz = l.getZ() - cz;
                        if (dx * dx + dy * dy + dz * dz <= radiusSquared && filter.test(entity)) {
                            found.add(entity);
                        }
                    }
                    return found;
                }, List.<Entity>of()));
            }
        }

        return Futures.all(parts).thenApply(lists -> {
            List<Entity> merged = new ArrayList<>();
            lists.forEach(merged::addAll);
            return merged;
        });
    }

    public CompletableFuture<List<Player>> getNearbyPlayers(Location center, double radius) {
        return getNearbyEntities(center, radius, entity -> entity instanceof Player)
                .thenApply(list -> list.stream().map(Player.class::cast).toList());
    }
}
