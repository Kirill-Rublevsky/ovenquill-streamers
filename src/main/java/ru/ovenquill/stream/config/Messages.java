package ru.ovenquill.stream.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Messages {
    private final Path file;
    private final Map<String, String> defaults;
    private volatile Map<String, String> values;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Messages(Path file) throws IOException {
        this.file = file;
        try (InputStream input = Messages.class.getClassLoader().getResourceAsStream("messages.yml")) {
            if (input == null) throw new IOException("Missing bundled messages.yml");
            Map<String, String> loadedDefaults = new Yaml().load(input);
            this.defaults = Map.copyOf(loadedDefaults == null ? Map.of() : loadedDefaults);
        }
        reload();
    }

    public synchronized void reload() throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            Map<String, String> loaded = new Yaml().load(input);
            Map<String, String> merged = new LinkedHashMap<>(defaults);
            if (loaded != null) merged.putAll(loaded);
            this.values = Map.copyOf(merged);
        }
    }

    public synchronized void update(String key, String value) throws IOException {
        Map<String, String> updated = new LinkedHashMap<>(values);
        updated.put(key, value);
        Files.writeString(file, new Yaml().dump(updated));
        values = Map.copyOf(updated);
    }

    public Component prefixed(String key) { return render("prefix").append(render(key)); }
    public String raw(String key) { return values.getOrDefault(key, defaults.getOrDefault(key, "")); }
    public Component render(String key, String... replacements) {
        String value = values.getOrDefault(key, "<red>Missing message: " + key + "</red>");
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            value = value.replace("{" + replacements[i] + "}", escape(replacements[i + 1]));
        }
        return miniMessage.deserialize(value);
    }
    private String escape(String input) {
        return input.replace("\\", "\\\\").replace("<", "&lt;").replace(">", "&gt;")
                .replace("'", "&#39;").replace("\"", "&quot;");
    }
}

