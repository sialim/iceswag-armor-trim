package net.serverplugins.trim;

import java.lang.reflect.Method;
import net.ess3.api.events.FlyStatusChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

final class EssentialsBridge implements FlightController, Listener {
    private final Plugin essentials;
    private final Method getUser;
    private final Method setFlyMode;
    private FlightService service;

    EssentialsBridge(Plugin plugin) {
        essentials = plugin;
        Method user = null;
        Method setter = null;
        try {
            user = plugin.getClass().getMethod("getUser", Player.class);
            setter = user.getReturnType().getMethod("setFlyModeEnabled", boolean.class);
        } catch (ReflectiveOperationException exception) {
            setter = null;
        }
        getUser = user;
        setFlyMode = setter;
    }

    void attach(FlightService service) {
        this.service = service;
    }

    @Override
    public boolean enabled(Player player) {
        return player.getAllowFlight();
    }

    @Override
    public void disable(Player player) {
        if (setFlyMode != null) {
            try {
                setFlyMode.invoke(getUser.invoke(essentials, player), false);
            } catch (ReflectiveOperationException exception) {
                player.setAllowFlight(false);
            }
        }
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setFallDistance(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlightChange(FlyStatusChangeEvent event) {
        if (event.getValue() && !service.canEnable(event.getAffected().getBase())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlightChanged(FlyStatusChangeEvent event) {
        service.beforeToggle(event.getAffected().getBase(), event.getValue());
    }
}
