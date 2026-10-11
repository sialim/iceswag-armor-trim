package net.serverplugins.trim;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;

final class UsageLimits {
    private final UsageStore store;
    private final Clock clock;
    private LimitSettings settings;

    UsageLimits(UsageStore store, LimitSettings settings, Clock clock) {
        this.store = store;
        this.settings = settings;
        this.clock = clock;
    }

    LimitSettings settings() {
        return settings;
    }

    void reload(LimitSettings replacement) {
        settings = replacement;
    }

    RankAllowance rank(Player player) {
        return settings.rank(player::hasPermission);
    }

    boolean consumeTrim(Player player, boolean clear) throws IOException {
        if (clear && !settings.countClear()) {
            return true;
        }
        Usage usage = usage(player.getUniqueId());
        long allowance = trimAllowance(player);
        if (allowance >= 0 && usage.trims() >= allowance) {
            return false;
        }
        store.write(player.getUniqueId(), new Usage(Math.min(Long.MAX_VALUE - 1, usage.trims()) + 1,
                usage.day(), usage.flightMillis(), usage.warnings()));
        return true;
    }

    long trimAllowance(Player player) {
        return !settings.trimEnabled() || player.hasPermission("armortrim.trim.unlimited") ? -1 : rank(player).trimUses();
    }

    long flightAllowance(Player player) {
        return player.hasPermission("armortrim.fly.unlimited") ? -1 : rank(player).flySeconds();
    }

    long flightRemaining(Player player) throws IOException {
        long allowance = flightAllowance(player);
        return allowance < 0 ? -1 : Math.max(0, allowance * 1000 - usage(player.getUniqueId()).flightMillis());
    }

    FlightCharge charge(Player player, long start, long end) throws IOException {
        UUID id = player.getUniqueId();
        Usage usage = usage(id);
        long allowance = flightAllowance(player);
        if (allowance < 0) {
            return new FlightCharge(-1, List.of());
        }
        long chargedStart = Math.max(start, today().atStartOfDay(settings.resetZone()).toInstant().toEpochMilli());
        long elapsed = Math.max(0, end - chargedStart);
        long limitMillis = allowance * 1000;
        long before = Math.max(0, limitMillis - usage.flightMillis());
        long spent = Math.min(Long.MAX_VALUE - elapsed, usage.flightMillis()) + elapsed;
        long remaining = Math.max(0, limitMillis - spent);
        HashSet<Integer> warned = new HashSet<>(usage.warnings());
        warned.removeIf(seconds -> before > seconds * 1000L);
        List<Integer> crossed = settings.warnings().stream()
                .filter(seconds -> before >= seconds * 1000L && remaining <= seconds * 1000L && !warned.contains(seconds)).toList();
        warned.addAll(crossed);
        if (elapsed > 0 || !warned.equals(usage.warnings())) {
            store.write(id, new Usage(usage.trims(), usage.day(), spent, warned));
        }
        return new FlightCharge(remaining, crossed);
    }

    void resetTrim(Player player) throws IOException {
        Usage usage = usage(player.getUniqueId());
        store.write(player.getUniqueId(), new Usage(0, usage.day(), usage.flightMillis(), usage.warnings()));
    }

    void resetFlight(Player player) throws IOException {
        Usage usage = usage(player.getUniqueId());
        store.write(player.getUniqueId(), new Usage(usage.trims(), today(), 0, java.util.Set.of()));
    }

    String status(Player player) throws IOException {
        Usage usage = usage(player.getUniqueId());
        long trims = trimAllowance(player);
        long flight = flightRemaining(player);
        return "Rank: " + rank(player).name() + " | Trims used: " + usage.trims()
                + " | Trims remaining: " + (trims < 0 ? "unlimited" : Math.max(0, trims - usage.trims()))
                + " | Flight remaining: " + (flight < 0 ? "unlimited" : (flight + 999) / 1000 + " seconds")
                + " | Daily reset: " + settings.resetZone();
    }

    String error(Player player) {
        return store.error(player.getUniqueId());
    }

    void forget(Player player) {
        store.forget(player.getUniqueId());
    }

    private Usage usage(UUID id) throws IOException {
        Usage loaded = store.read(id, today());
        Usage current = loaded.onDay(today());
        if (!current.equals(loaded)) {
            store.write(id, current);
        }
        return current;
    }

    private LocalDate today() {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(clock.millis()), settings.resetZone()).toLocalDate();
    }

    record FlightCharge(long remainingMillis, List<Integer> warnings) {
    }
}
