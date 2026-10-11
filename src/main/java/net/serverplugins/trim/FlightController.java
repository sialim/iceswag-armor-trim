package net.serverplugins.trim;

import org.bukkit.entity.Player;

interface FlightController {
    boolean enabled(Player player);

    void disable(Player player);
}
