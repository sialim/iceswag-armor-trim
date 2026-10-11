package net.serverplugins.trim;

import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

final class FlightService {
    private final UsageLimits limits;
    private final FlightController controller;
    private final Clock clock;
    private final Map<UUID, Long> sessions = new HashMap<>();
    private final Map<UUID, Long> fallProtection = new HashMap<>();

    FlightService(UsageLimits limits, FlightController controller, Clock clock) {
        this.limits = limits;
        this.controller = controller;
        this.clock = clock;
    }

    boolean canEnable(Player player) {
        if (!limitedMode(player)) {
            return true;
        }
        if (sessions.containsKey(player.getUniqueId())) {
            poll(player);
        }
        try {
            return player.hasPermission("essentials.fly") && limits.flightRemaining(player) != 0;
        } catch (IOException exception) {
            return false;
        }
    }

    void poll(Player player) {
        if (!limitedMode(player) || !controller.enabled(player) || !player.getAllowFlight()) {
            sessions.remove(player.getUniqueId());
            return;
        }
        long now = clock.millis();
        Long previous = sessions.put(player.getUniqueId(), now);
        try {
            UsageLimits.FlightCharge charge = limits.charge(player, previous == null ? now : previous, now);
            if (charge.remainingMillis() == 0 || !player.hasPermission("essentials.fly")) {
                expire(player);
                return;
            }
            if (!charge.warnings().isEmpty() && !limits.settings().warningMessage().isBlank()) {
                int seconds = charge.warnings().getLast();
                String message = limits.settings().warningMessage()
                        .replace("{time}", time(seconds)).replace("{seconds}", Integer.toString(seconds));
                player.sendMessage(Component.text(message, NamedTextColor.YELLOW));
            }
        } catch (IOException exception) {
            expire(player);
        }
    }

    void beforeToggle(Player player, boolean enabling) {
        poll(player);
        if (enabling && limitedMode(player)) {
            sessions.put(player.getUniqueId(), clock.millis());
        } else {
            sessions.remove(player.getUniqueId());
        }
    }

    void quit(Player player) {
        poll(player);
        sessions.remove(player.getUniqueId());
        fallProtection.remove(player.getUniqueId());
        limits.forget(player);
    }

    void settle(Iterable<? extends Player> players) {
        for (Player player : players) {
            poll(player);
        }
    }

    boolean protectFall(Player player) {
        Long until = fallProtection.remove(player.getUniqueId());
        return until != null && clock.millis() <= until;
    }

    private boolean limitedMode(Player player) {
        return limits.settings().flightEnabled()
                && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE);
    }

    private void expire(Player player) {
        if (player.isFlying() && limits.settings().fallProtectionSeconds() > 0) {
            fallProtection.put(player.getUniqueId(), clock.millis() + limits.settings().fallProtectionSeconds() * 1000L);
        }
        controller.disable(player);
        sessions.remove(player.getUniqueId());
    }

    private static String time(int seconds) {
        if (seconds % 60 == 0) {
            int minutes = seconds / 60;
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        return seconds + (seconds == 1 ? " second" : " seconds");
    }
}
