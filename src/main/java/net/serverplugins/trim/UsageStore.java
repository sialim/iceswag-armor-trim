package net.serverplugins.trim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

final class UsageStore {
    private final Path directory;
    private final Map<UUID, Usage> cache = new HashMap<>();
    private final Map<UUID, String> errors = new HashMap<>();

    UsageStore(Path directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory);
    }

    Usage read(UUID id, LocalDate day) throws IOException {
        if (errors.containsKey(id)) {
            throw new IOException(errors.get(id));
        }
        if (cache.containsKey(id)) {
            return cache.get(id);
        }
        Path path = path(id);
        Usage usage = Usage.empty(day);
        if (Files.exists(path)) {
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(path.toFile());
                if (!yaml.isLong("trim-uses") && !yaml.isInt("trim-uses")
                        || !yaml.isLong("flight-millis") && !yaml.isInt("flight-millis")
                        || yaml.getLong("trim-uses") < 0 || yaml.getLong("flight-millis") < 0) {
                    throw new IllegalArgumentException("Invalid usage counters.");
                }
                usage = new Usage(yaml.getLong("trim-uses"), LocalDate.parse(yaml.getString("usage-day", yaml.getString("flight-day", ""))),
                        yaml.getLong("flight-millis"), new HashSet<>(yaml.getIntegerList("warnings")));
            } catch (IOException | InvalidConfigurationException | RuntimeException exception) {
                String message = "Usage unavailable for " + id + ": " + exception.getMessage();
                errors.put(id, message);
                throw new IOException(message, exception);
            }
        }
        cache.put(id, usage);
        return usage;
    }

    void write(UUID id, Usage usage) throws IOException {
        if (errors.containsKey(id)) {
            throw new IOException(errors.get(id));
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("trim-uses", usage.trims());
        yaml.set("usage-day", usage.day().toString());
        yaml.set("flight-millis", usage.flightMillis());
        yaml.set("warnings", new ArrayList<>(usage.warnings().stream().sorted().toList()));
        Path temporary = directory.resolve(id + ".tmp");
        try {
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path(id), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path(id), StandardCopyOption.REPLACE_EXISTING);
            }
            cache.put(id, usage);
        } catch (IOException exception) {
            errors.put(id, "Could not save usage for " + id + ": " + exception.getMessage());
            throw exception;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    void forget(UUID id) {
        cache.remove(id);
    }

    String error(UUID id) {
        return errors.get(id);
    }

    private Path path(UUID id) {
        return directory.resolve(id + ".yml");
    }
}
