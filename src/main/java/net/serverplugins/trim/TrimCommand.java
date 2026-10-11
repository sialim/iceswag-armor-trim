package net.serverplugins.trim;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

final class TrimCommand implements CommandExecutor, TabCompleter {
    private final TrimCatalog catalog;
    private final ArmorTrimPlugin plugin;

    TrimCommand(TrimCatalog catalog, ArmorTrimPlugin plugin) {
        this.catalog = catalog;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            error(sender, "Only players can use this command.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            LimitsCommand.showStatus(plugin, player, player);
            return true;
        }
        if (plugin.limits() == null) {
            return true;
        }
        if (!player.hasPermission("armortrim.use")) {
            error(player, "You do not have permission to trim armor.");
            return true;
        }
        boolean clear = args.length == 1 && args[0].equalsIgnoreCase("clear");
        if (!clear && (args.length != 2 || args[0].equalsIgnoreCase("clear"))) {
            error(player, "Usage: /trim <pattern> <material> or /trim clear");
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand().clone();
        if (!(held.getItemMeta() instanceof ArmorMeta meta)) {
            error(player, "Hold an armor piece in your main hand.");
            return true;
        }
        if (clear) {
            if (!meta.hasTrim()) {
                error(player, "That armor does not have a trim.");
                return true;
            }
            meta.setTrim(null);
        } else {
            TrimPattern pattern = catalog.patterns().get(TrimCatalog.normalize(args[0]));
            TrimMaterial material = catalog.materials().get(TrimCatalog.normalize(args[1]));
            if (pattern == null) {
                error(player, "Unknown pattern. Patterns: " + String.join(", ", catalog.patterns().keySet().stream().sorted().toList()));
                return true;
            }
            if (material == null) {
                error(player, "Unknown material. Materials: " + String.join(", ", catalog.materials().keySet().stream().sorted().toList()));
                return true;
            }
            ArmorTrim trim = new ArmorTrim(material, pattern);
            if (trim.equals(meta.getTrim())) {
                error(player, "That armor already has this trim.");
                return true;
            }
            meta.setTrim(trim);
        }
        if (!held.setItemMeta(meta)) {
            error(player, "This item does not support armor trims.");
            return true;
        }
        try {
            if (!plugin.limits().consumeTrim(player, clear)) {
                error(player, "You have no trim uses remaining.");
                return true;
            }
        } catch (java.io.IOException exception) {
            error(player, "Trim usage is unavailable. Ask an administrator to check /trimadmin status.");
            return true;
        }
        player.getInventory().setItemInMainHand(held);
        if (plugin.limits().settings().commandFeedback()) {
            player.sendMessage(Component.text(clear ? "Armor trim cleared." : "Armor trim applied.", NamedTextColor.GREEN));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("armortrim.use")) {
            return List.of();
        }
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.add("clear");
            suggestions.add("status");
            suggestions.addAll(catalog.patterns().keySet());
        } else if (args.length == 2 && catalog.patterns().containsKey(TrimCatalog.normalize(args[0]))) {
            suggestions.addAll(catalog.materials().keySet());
        } else {
            return List.of();
        }
        String prefix = TrimCatalog.normalize(args[args.length - 1]).toLowerCase(Locale.ROOT);
        return suggestions.stream().filter(value -> value.startsWith(prefix)).sorted().toList();
    }

    private void error(CommandSender sender, String message) {
        if (plugin.limits() != null && plugin.limits().settings().commandFeedback()) {
            sender.sendMessage(Component.text(message, NamedTextColor.RED));
        }
    }
}
