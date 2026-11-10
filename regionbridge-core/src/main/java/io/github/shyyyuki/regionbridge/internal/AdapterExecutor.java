package io.github.shyyyuki.regionbridge.internal;

import io.github.shyyyuki.regionbridge.api.EntityRetiredException;
import io.github.shyyyuki.regionbridge.api.PlatformAdapter;
import io.github.shyyyuki.regionbridge.api.RegionExecutor;
import io.github.shyyyuki.regionbridge.api.TaskHandle;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class AdapterExecutor implements RegionExecutor {

    public enum Kind {
        GLOBAL, REGION, ENTITY, ASYNC
    }

    private final PlatformAdapter adapter;
    private final Kind kind;
    private final Location location;
    private final Entity entity;

    private AdapterExecutor(
            PlatformAdapter adapter,
            Kind kind,
            Location location,
            Entity entity
    ) {
        this.adapter = adapter;
        this.kind = kind;
        this.location = location;
        this.entity = entity;
    }

    public static AdapterExecutor global(PlatformAdapter a) {
        return new AdapterExecutor(a, Kind.GLOBAL, null, null);
    }

    public static AdapterExecutor async(PlatformAdapter a) {
        return new AdapterExecutor(a, Kind.ASYNC, null, null);
    }

    public static AdapterExecutor region(PlatformAdapter a, Location l) {
        return new AdapterExecutor(a, Kind.REGION, l.clone(), null);
    }

    public static AdapterExecutor entity(PlatformAdapter a, Entity e) {
        return new AdapterExecutor(a, Kind.ENTITY, null, e);
    }

    @Override
    public boolean isOwned() {
        return switch (kind) {
            case GLOBAL -> adapter.isGlobalThread();
            case REGION -> adapter.isOwned(location);
            case ENTITY -> adapter.isOwned(entity);
            case ASYNC -> false;
        };
    }

    @Override
    public TaskHandle execute(Runnable task) {
        if (isOwned()) {
            TaskTracer.runInline(describe(), task, true);
            return TaskHandle.COMPLETED;
        }

        return dispatch(task, null, 0, 0);
    }

    @Override
    public TaskHandle schedule(Runnable task) {
        return dispatch(task, null, 0, 0);
    }

    @Override
    public TaskHandle executeLater(Runnable task, long delay) {
        return dispatch(task, null, delay, 0);
    }

    @Override
    public TaskHandle executeTimer(Runnable task, long delay, long period) {
        return dispatch(task, null, delay, period);
    }

    @Override
    public CompletableFuture<Void> run(Runnable task) {
        return supply(() -> {
            task.run();
            return null;
        });
    }

    @Override
    public <T> CompletableFuture<T> supply(Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();

        Runnable body = () -> {
            try {
                future.complete(task.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        };

        if (isOwned()) {
            TaskTracer.runInline(describe(), body, true);
        } else {
            dispatch(
                    body,
                    () -> future.completeExceptionally(
                            new EntityRetiredException(
                                    "Entity has been removed: " + describe()
                            )
                    ),
                    0,
                    0
            );
        }

        return future;
    }

    private TaskHandle dispatch(
            Runnable task,
            Runnable retired,
            long delay,
            long period
    ) {
        Runnable wrapped = TaskTracer.wrap(
                describe(),
                task,
                kind != Kind.ASYNC
        );

        return switch (kind) {
            case GLOBAL -> adapter.runGlobal(wrapped, delay, period);
            case REGION -> adapter.runRegion(location, wrapped, delay, period);
            case ENTITY -> adapter.runEntity(entity, wrapped, retired, delay, period);
            case ASYNC -> adapter.runAsync(wrapped, delay, period);
        };
    }

    private String describe() {
        return switch (kind) {
            case GLOBAL -> "global";
            case ASYNC -> "async";
            case REGION -> "region@" + location.getWorld().getName()
                    + ":" + location.getBlockX() + "," + location.getBlockZ();
            case ENTITY -> "entity:" + entity.getUniqueId();
        };
    }
}