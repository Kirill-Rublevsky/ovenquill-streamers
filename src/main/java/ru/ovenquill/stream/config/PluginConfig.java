package ru.ovenquill.stream.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;

public record PluginConfig(int cooldownSeconds, int maxUrlLength, int maxMessageLength,
                           int permissionSyncSeconds, boolean tabEnabled, int tabPriority,
                           String tabPrefix, String tabPrefixColor, String tabNameColor,
                           boolean notificationsEnabled, boolean restrictAdmin, boolean failClosed,
                           List<String> adminPermissionPrefixes, boolean allowOtherHttps, String storageFile) {
    public static PluginConfig load(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        copyDefault(dataDirectory, "config.yml");
        copyDefault(dataDirectory, "messages.yml");
        try (InputStream input = Files.newInputStream(dataDirectory.resolve("config.yml"))) {
            Map<String, Object> root = new Yaml().load(input);
            Map<String, Object> stream = section(root, "stream");
            Map<String, Object> tab = section(root, "tab");
            Map<String, Object> notifications = section(root, "notifications");
            Map<String, Object> permissions = section(root, "permissions");
            Map<String, Object> platforms = section(root, "platforms");
            Map<String, Object> storage = section(root, "storage");
            return new PluginConfig(integer(stream, "cooldown-seconds", 60), integer(stream, "max-url-length", 512),
                    integer(stream, "max-message-length", 2000),
                    integer(stream, "permission-sync-seconds", 15), bool(tab, "enabled", true),
                    integer(tab, "priority", 100), string(tab, "prefix", "⬤ "),
                    string(tab, "prefix-color", "#FF5555"), string(tab, "name-color", "#C8A2FF"),
                    bool(notifications, "enabled", true),
                    bool(permissions, "restrict-admin-during-stream", true), bool(permissions, "fail-closed", true),
                    strings(permissions, "admin-prefixes", List.of("luckperms.", "velocity.command.", "minecraft.command.",
                            "bukkit.command.", "essentials.", "worldedit.", "worldguard.", "coreprotect.",
                            "litebans.", "advancedban.", "vanish.")),
                    bool(platforms, "allow-other-https", true), string(storage, "file", "streams.db"));
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
    private static String string(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key); return value instanceof String text && !text.isBlank() ? text : fallback;
    }
    private static List<String> strings(Map<String, Object> map, String key, List<String> fallback) {
        Object value = map.get(key);
        if (!(value instanceof List<?> list)) return fallback;
        List<String> result = list.stream().filter(String.class::isInstance).map(String.class::cast)
                .map(String::trim).filter(item -> !item.isEmpty()).toList();
        return result.isEmpty() ? fallback : result;
    }
}

