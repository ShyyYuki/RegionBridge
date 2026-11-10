package io.github.shyyyuki.regionbridge.api;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface RegionExecutor {

    TaskHandle execute(Runnable task);

    TaskHandle schedule(Runnable task);

    TaskHandle executeLater(Runnable task, long delayTicks);

    TaskHandle executeTimer(Runnable task, long delayTicks, long periodTicks);

    CompletableFuture<Void> run(Runnable task);

    <T> CompletableFuture<T> supply(Supplier<T> task);

    boolean isOwned();
}
