package io.github.shyyyuki.regionbridge.internal;

import io.github.shyyyuki.regionbridge.api.Platform;

public final class PlatformDetector {
    private PlatformDetector() { }

    public static Platform detect() {
        if (has("io.papermc.paper.threadedregions.RegionizedServer")) return Platform.FOLIA;
        if (has("org.purpurmc.purpur.PurpurConfig") || has("net.pl3x.purpur.PurpurConfig")) return Platform.PURPUR;
        if (has("io.papermc.paper.configuration.GlobalConfiguration")
                || has("com.destroystokyo.paper.PaperConfig")) return Platform.PAPER;
        return Platform.SPIGOT;
    }

    private static boolean has(String cls) {
        try {
            Class.forName(cls, false, PlatformDetector.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
