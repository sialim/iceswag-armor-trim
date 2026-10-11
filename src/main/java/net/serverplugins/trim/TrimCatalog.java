package net.serverplugins.trim;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

record TrimCatalog(Map<String, TrimPattern> patterns, Map<String, TrimMaterial> materials) {
    TrimCatalog {
        patterns = Map.copyOf(patterns);
        materials = Map.copyOf(materials);
    }

    static TrimCatalog load() {
        RegistryAccess registries = RegistryAccess.registryAccess();
        return new TrimCatalog(entries(registries.getRegistry(RegistryKey.TRIM_PATTERN)),
                entries(registries.getRegistry(RegistryKey.TRIM_MATERIAL)));
    }

    static String normalize(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return normalized.startsWith("minecraft:") ? normalized.substring(10) : normalized;
    }

    private static <T extends Keyed> Map<String, T> entries(Registry<T> registry) {
        Map<String, T> entries = new LinkedHashMap<>();
        for (T entry : registry) {
            NamespacedKey registeredKey = registry.getKeyOrThrow(entry);
            String key = registeredKey.getNamespace().equals("minecraft")
                    ? registeredKey.getKey() : registeredKey.toString();
            entries.put(key, entry);
        }
        return entries;
    }
}
