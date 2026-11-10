package io.github.shyyyuki.regionbridge.paper;

import io.github.shyyyuki.regionbridge.api.Platform;
import io.github.shyyyuki.regionbridge.spigot.SpigotAdapter;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;

public class PaperAdapter extends SpigotAdapter {
    public PaperAdapter(Plugin plugin) { super(plugin); }

    @Override public Platform platform() { return Platform.PAPER; }

    @Override
    public CompletableFuture<Boolean> teleport(Entity entity, Location target) {
        return entity.teleportAsync(target);
    }
}
