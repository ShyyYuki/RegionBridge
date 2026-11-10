package io.github.shyyyuki.regionbridge.internal;

import io.github.shyyyuki.regionbridge.api.RegionExecutor;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.CompletableFuture;

public final class ChunkLoader {
    private static final MethodHandle ASYNC = findAsync();

    private ChunkLoader() {
    }

    private static MethodHandle findAsync() {
        try {
            return MethodHandles.publicLookup().findVirtual(
                    World.class,
                    "getChunkAtAsync",
                    MethodType.methodType(CompletableFuture.class, int.class, int.class, boolean.class)
            );
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static boolean isNativeAsync() {
        return ASYNC != null;
    }

    @SuppressWarnings("unchecked")
    public static CompletableFuture<Chunk> load(
            RegionExecutor global,
            World world,
            int chunkX,
            int chunkZ,
            boolean generate
    ) {
        if (ASYNC != null) {
            try {
                return (CompletableFuture<Chunk>) ASYNC.invoke(world, chunkX, chunkZ, generate);
            } catch (Throwable t) {
                return CompletableFuture.failedFuture(t);
            }
        }

        return global.supply(() -> {
            if (generate) {
                return world.getChunkAt(chunkX, chunkZ);
            }
            return world.loadChunk(chunkX, chunkZ, false) ? world.getChunkAt(chunkX, chunkZ) : null;
        });
    }
}
