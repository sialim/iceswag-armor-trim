package net.serverplugins.trim;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

record LimitSettings(boolean trimEnabled, boolean countClear, boolean flightEnabled, ZoneId resetZone,
                     int fallProtectionSeconds, List<Integer> warnings, boolean commandFeedback,
                     String warningMessage, List<RankAllowance> ranks) {
    static LimitSettings load(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("ranks");
        if (section == null || section.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException("At least one rank is required.");
        }
        List<RankAllowance> ranks = new ArrayList<>();
        for (String name : section.getKeys(false)) {
            ConfigurationSection rank = section.getConfigurationSection(name);
            if (rank == null) {
                throw new IllegalArgumentException("Invalid rank: " + name);
            }
            String permission = rank.getString("permission", "armortrim.rank." + name);
            if (permission.isBlank()) {
                throw new IllegalArgumentException("Rank permission cannot be blank.");
            }
            ranks.add(new RankAllowance(name, permission, rank.getInt("priority"),
                    allowance(rank, "trim-uses", Long.MAX_VALUE - 1), allowance(rank, "fly-seconds", 86400)));
        }
        ranks.sort(Comparator.comparingInt(RankAllowance::priority).reversed().thenComparing(RankAllowance::name));
        List<Integer> warnings = config.getIntegerList("flight.warning-seconds").stream()
                .filter(seconds -> seconds > 0 && seconds <= 86400).distinct().sorted(Comparator.reverseOrder()).toList();
        return new LimitSettings(config.getBoolean("trim.enabled", true), config.getBoolean("trim.count-clear", true),
                config.getBoolean("flight.enabled", true), ZoneId.of(config.getString("daily-reset.timezone", "UTC")),
                Math.clamp(config.getInt("flight.expiry-fall-protection-seconds", 15), 0, 300), warnings,
                config.getBoolean("messages.command-feedback", false),
                config.getString("messages.flight-warning", "{time} of flight remaining."), List.copyOf(ranks));
    }

    RankAllowance rank(Predicate<String> permissions) {
        return ranks.stream().filter(rank -> permissions.test(rank.permission())).findFirst()
                .orElse(new RankAllowance("member", "", Integer.MIN_VALUE, 0, 0));
    }

    private static long allowance(ConfigurationSection section, String key, long maximum) {
        long value = section.getLong(key, 0);
        if (value < -1 || value > maximum) {
            throw new IllegalArgumentException(key + " must be -1 or between 0 and " + maximum + ".");
        }
        return value;
    }
}
