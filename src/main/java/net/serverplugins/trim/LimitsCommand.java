package net.serverplugins.trim;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

final class LimitsCommand implements CommandExecutor, TabCompleter {
    private final ArmorTrimPlugin plugin;

    LimitsCommand(ArmorTrimPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("flighttime")) {
            if (sender instanceof Player player) {
                showStatus(plugin, sender, player);
            }
            return true;
        }
        if (!sender.hasPermission("armortrim.admin")) {
            return true;
        }
        try {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                plugin.reloadLimits();
                reply(sender, "Configuration reloaded.");
            } else if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
                reply(sender, plugin.limits() == null ? "Unavailable: " + plugin.error()
                        : "Essentials flight integration: " + plugin.flightAvailable() + " | Daily reset: "
                        + plugin.limits().settings().resetZone() + " | Trim allowance: daily");
            } else if (args.length == 2 && List.of("status", "resettrim", "resetfly").contains(args[0].toLowerCase(Locale.ROOT))) {
                Player player = plugin.getServer().getPlayerExact(args[1]);
                if (player == null) {
                    reply(sender, "That player is not online.");
                } else if (args[0].equalsIgnoreCase("status")) {
                    showStatus(plugin, sender, player);
                } else if (plugin.limits() == null) {
                    reply(sender, "Unavailable: " + plugin.error());
                } else {
                    if (args[0].equalsIgnoreCase("resettrim")) {
                        plugin.limits().resetTrim(player);
                    } else {
                        plugin.limits().resetFlight(player);
                    }
                    reply(sender, "Usage reset for " + player.getName() + ".");
                }
            } else {
                reply(sender, "/trimadmin reload | status [player] | resettrim <player> | resetfly <player>");
            }
        } catch (Exception exception) {
            reply(sender, "Could not complete request: " + exception.getMessage());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("armortrim.admin") || args.length == 0) {
            return List.of();
        }
        List<String> suggestions = args.length == 1 ? List.of("reload", "status", "resettrim", "resetfly")
                : args.length == 2 && List.of("status", "resettrim", "resetfly").contains(args[0].toLowerCase(Locale.ROOT))
                ? plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList() : List.of();
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return suggestions.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }

    static void showStatus(ArmorTrimPlugin plugin, CommandSender sender, Player player) {
        if (plugin.limits() == null) {
            reply(sender, "Usage unavailable.");
            return;
        }
        try {
            reply(sender, plugin.limits().status(player));
        } catch (java.io.IOException exception) {
            reply(sender, "Usage unavailable." + (sender.hasPermission("armortrim.admin") ? " " + exception.getMessage() : ""));
        }
    }

    private static void reply(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message));
    }
}
