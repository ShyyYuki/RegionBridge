package io.github.shyyyuki.regionbridge;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class BridgeDebug {
    public enum Mode { OFF, WARN, STRICT }

    private static final Logger LOG = Logger.getLogger("RegionBridge");
    private static volatile Mode mode = Mode.OFF;
    private static volatile long slowTaskMs = 50;

    private BridgeDebug() { }

    public static Mode mode() { return mode; }
    public static void setMode(Mode m) { mode = m; }
    public static boolean enabled() { return mode != Mode.OFF; }
    public static long slowTaskThresholdMs() { return slowTaskMs; }
    public static void setSlowTaskThresholdMs(long ms) { slowTaskMs = ms; }

    public static void violation(String message) {
        if (mode == Mode.OFF) return;
        IllegalStateException ex = new IllegalStateException("[RegionBridge] " + message);
        if (mode == Mode.STRICT) throw ex;
        LOG.log(Level.WARNING, ex.getMessage(), ex);
    }

    public static void reportDuration(String label, long nanos) {
        if (mode == Mode.OFF) return;
        long ms = nanos / 1_000_000;
        if (ms >= slowTaskMs) {
            LOG.warning("[RegionBridge] Slow Task '" + label + "' (" + ms + " ms) on Thread "
                    + Thread.currentThread().getName());
        }
    }
}
