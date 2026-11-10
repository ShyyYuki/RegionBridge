package io.github.shyyyuki.regionbridge.api;

public enum Platform {
    SPIGOT("io.github.shyyyuki.regionbridge.spigot.SpigotAdapter", false),
    PAPER("io.github.shyyyuki.regionbridge.paper.PaperAdapter", false),
    PURPUR("io.github.shyyyuki.regionbridge.purpur.PurpurAdapter", false),
    FOLIA("io.github.shyyyuki.regionbridge.folia.FoliaAdapter", true);

    private final String adapterClass;
    private final boolean regionized;

    Platform(String adapterClass, boolean regionized) {
        this.adapterClass = adapterClass;
        this.regionized = regionized;
    }

    public String adapterClass() { return adapterClass; }

    public boolean isRegionized() { return regionized; }
}
