package io.github.shyyyuki.regionbridge.plugin;

import io.github.shyyyuki.regionbridge.BridgeDebug;
import io.github.shyyyuki.regionbridge.RegionBridge;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class RegionBridgePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        RegionBridge bridge = RegionBridge.forPlugin(this);
        getLogger().info("Detected platform: " + bridge.platform()
                + (bridge.platform().isRegionized() ? " (regionized)" : ""));
    }

    @Override
    public void onDisable() {
        RegionBridge.releaseAll();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            sender.sendMessage("RegionBridge: Platform=" + RegionBridge.forPlugin(this).platform()
                    + ", Debug=" + BridgeDebug.mode());
            return true;
        }

        if (args[0].equalsIgnoreCase("debug") && args.length == 2) {
            try {
                BridgeDebug.setMode(BridgeDebug.Mode.valueOf(args[1].toUpperCase(Locale.ROOT)));
                sender.sendMessage("Debug mode: " + BridgeDebug.mode());
            } catch (IllegalArgumentException e) {
                sender.sendMessage("Usage: /regionbridge debug <off|warn|strict>");
            }
            return true;
        }

        return false;
    }
}