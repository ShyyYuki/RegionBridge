package io.github.shyyyuki.regionbridge.internal;

import io.github.shyyyuki.regionbridge.BridgeDebug;

public final class TaskTracer {
    private static final ThreadLocal<int[]> DEPTH =
            ThreadLocal.withInitial(() -> new int[1]);

    private TaskTracer() {
    }

    public static boolean insideTickTask() {
        return DEPTH.get()[0] > 0;
    }

    public static Runnable wrap(String label, Runnable task, boolean tick) {
        Throwable origin = BridgeDebug.enabled()
                ? new Throwable("Task '" + label + "' was scheduled here")
                : null;

        return () -> run(label, task, tick, origin);
    }

    public static void runInline(String label, Runnable task, boolean tick) {
        run(label, task, tick, null);
    }

    private static void run(String label, Runnable task, boolean tick, Throwable origin) {
        int[] depth = DEPTH.get();

        if (tick) {
            depth[0]++;
        }

        long start = System.nanoTime();

        try {
            task.run();
        } catch (Throwable t) {
            if (origin != null) {
                t.addSuppressed(origin);
            }
            throw t;
        } finally {
            if (tick) {
                depth[0]--;
            }

            BridgeDebug.reportDuration(label, System.nanoTime() - start);
        }
    }
}