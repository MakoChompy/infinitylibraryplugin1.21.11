package com.infinitylibrary.listener;

import com.infinitylibrary.InfinityLibraryPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerGenerationListener implements Listener {
    private final InfinityLibraryPlugin plugin;
    public PlayerGenerationListener(InfinityLibraryPlugin plugin) { this.plugin = plugin; }
    @EventHandler public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null || e.getFrom().getBlockX() == e.getTo().getBlockX() && e.getFrom().getBlockY() == e.getTo().getBlockY() && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        plugin.getGenerationEngine().tickPlayer(e.getPlayer());
    }
    @EventHandler public void onJoin(PlayerJoinEvent e) { plugin.getGenerationEngine().tickPlayer(e.getPlayer()); }
}
