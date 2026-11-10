package io.github.shyyyuki.regionbridge.purpur;

import io.github.shyyyuki.regionbridge.api.Platform;
import io.github.shyyyuki.regionbridge.paper.PaperAdapter;
import org.bukkit.plugin.Plugin;

public class PurpurAdapter extends PaperAdapter {
    public PurpurAdapter(Plugin plugin) { super(plugin); }

    @Override public Platform platform() { return Platform.PURPUR; }
}
