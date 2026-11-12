package ru.ovenquill.stream.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public record PluginConfig(int cooldownSeconds, int maxUrlLength, int permissionSyncSeconds, boolean tabEnabled,
                           int tabPriority, boolean notificationsEnabled) {
    public static PluginConfig load(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        copyDefault(dataDirectory, "config.yml");
        copyDefault(dataDirectory, "messages.yml");
        try (InputStream input = Files.newInputStream(dataDirectory.resolve("config.yml"))) {
            Map<String, Object> root = new Yaml().load(input);
            Map<String, Object> stream = section(root, "stream");
            Map<String, Object> tab = section(root, "tab");
            Map<String, Object> notifications = section(root, "notifications");
            return new PluginConfig(integer(stream, "cooldown-seconds", 60), integer(stream, "max-url-length", 512),
                    integer(stream, "permission-sync-seconds", 15), bool(tab, "enabled", true),
                    integer(tab, "priority", 100), bool(notifications, "enabled", true));
        }
    }

    private static void copyDefault(Path directory, String name) throws IOException {
        Path target = directory.resolve(name);
        if (Files.notExists(target)) {
            try (InputStream input = PluginConfig.class.getClassLoader().getResourceAsStream(name)) {
                if (input == null) throw new IOException("Missing bundled resource " + name);
                Files.copy(input, target);
            }
        }
    }

    @SuppressWarnings("unchecked") private static Map<String, Object> section(Map<String, Object> root, String key) {
        Object value = root.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
    private static int integer(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key); return value instanceof Number number ? number.intValue() : fallback;
    }
    private static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key); return value instanceof Boolean bool ? bool : fallback;
    }
}

