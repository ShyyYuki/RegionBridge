package io.github.shyyyuki.regionbridge;

import io.github.shyyyuki.regionbridge.api.RegionExecutor;
import io.github.shyyyuki.regionbridge.internal.Futures;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class RegionFlow<T> {
    private final CompletableFuture<T> future;

    private RegionFlow(CompletableFuture<T> future) {
        this.future = future;
    }

    public static <T> RegionFlow<T> of(T value) {
        return new RegionFlow<>(CompletableFuture.completedFuture(value));
    }

    public static <T> RegionFlow<T> start(RegionExecutor executor, Supplier<T> task) {
        return new RegionFlow<>(executor.supply(task));
    }

    public <R> RegionFlow<R> then(RegionExecutor executor, Function<? super T, ? extends R> task) {
        return new RegionFlow<>(future.thenCompose(value -> executor.<R>supply(() -> task.apply(value))));
    }

    public RegionFlow<T> thenRun(RegionExecutor executor, Consumer<? super T> task) {
        return then(executor, value -> {
            task.accept(value);
            return value;
        });
    }

    public <R> RegionFlow<R> thenCompose(Function<? super T, ? extends CompletableFuture<R>> task) {
        return new RegionFlow<>(future.thenCompose(task));
    }

    public RegionFlow<T> onError(Consumer<Throwable> handler) {
        future.whenComplete((value, error) -> {
            if (error != null) {
                handler.accept(Futures.unwrap(error));
            }
        });
        return this;
    }

    public RegionFlow<T> recover(Function<Throwable, ? extends T> fallback) {
        return new RegionFlow<>(future.exceptionally(error -> fallback.apply(Futures.unwrap(error))));
    }

    public RegionFlow<T> timeout(long millis) {
        return new RegionFlow<>(future.orTimeout(millis, TimeUnit.MILLISECONDS));
    }

    public CompletableFuture<T> future() {
        return future;
    }
}
