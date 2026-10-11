package net.serverplugins.trim;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class ArmorTrimPlugin extends JavaPlugin {
    private UsageLimits limits;
    private FlightService flight;
    private BukkitTask task;
    private String error;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            limits = new UsageLimits(new UsageStore(getDataFolder().toPath().resolve("usage")), readSettings(), Clock.systemUTC());
        } catch (Exception exception) {
            error = exception.getMessage();
        }
        TrimCommand executor = new TrimCommand(TrimCatalog.load(), this);
        PluginCommand command = Objects.requireNonNull(getCommand("trim"));
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        LimitsCommand admin = new LimitsCommand(this);
        Objects.requireNonNull(getCommand("trimadmin")).setExecutor(admin);
        Objects.requireNonNull(getCommand("trimadmin")).setTabCompleter(admin);
        Objects.requireNonNull(getCommand("flighttime")).setExecutor(admin);
        Plugin essentials = getServer().getPluginManager().getPlugin("Essentials");
        if (limits != null && essentials != null && essentials.isEnabled()) {
            EssentialsBridge bridge = new EssentialsBridge(essentials);
            flight = new FlightService(limits, bridge, Clock.systemUTC());
            bridge.attach(flight);
            getServer().getPluginManager().registerEvents(bridge, this);
            getServer().getPluginManager().registerEvents(new FlightListener(this, flight), this);
            task = getServer().getScheduler().runTaskTimer(this,
                    () -> getServer().getOnlinePlayers().forEach(flight::poll), 1, 20);
        }
    }

    @Override
    public void onDisable() {
        if (task != null) {
            task.cancel();
        }
        if (flight != null) {
            flight.settle(getServer().getOnlinePlayers());
        }
    }

    UsageLimits limits() {
        return limits;
    }

    String error() {
        return error;
    }

    boolean flightAvailable() {
        return flight != null;
    }

    void reloadLimits() throws Exception {
        LimitSettings replacement = readSettings();
        if (limits == null) {
            throw new IllegalStateException("Restart after fixing the startup error: " + error);
        }
        if (flight != null) {
            flight.settle(getServer().getOnlinePlayers());
        }
        limits.reload(replacement);
        error = null;
    }

    private LimitSettings readSettings() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.load(new File(getDataFolder(), "config.yml"));
        if (config.contains("flight.daily-reset-timezone", true) && !config.contains("daily-reset.timezone", true)) {
            config.set("daily-reset.timezone", config.getString("flight.daily-reset-timezone"));
        }
        try (var input = new InputStreamReader(Objects.requireNonNull(getResource("config.yml")), StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.load(input);
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
        }
        return LimitSettings.load(config);
    }
}
