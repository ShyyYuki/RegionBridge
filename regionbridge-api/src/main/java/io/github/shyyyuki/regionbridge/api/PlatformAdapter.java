package io.github.shyyyuki.regionbridge.api;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.concurrent.CompletableFuture;

public interface PlatformAdapter {
    Platform platform();

    boolean isGlobalThread();
    boolean isOwned(Location location);
    boolean isOwned(Entity entity);

    TaskHandle runGlobal(Runnable task, long delay, long period);
    TaskHandle runRegion(Location location, Runnable task, long delay, long period);

    TaskHandle runEntity(Entity entity, Runnable task, Runnable retired, long delay, long period);

    TaskHandle runAsync(Runnable task, long delay, long period);

    CompletableFuture<Boolean> teleport(Entity entity, Location target);
}
