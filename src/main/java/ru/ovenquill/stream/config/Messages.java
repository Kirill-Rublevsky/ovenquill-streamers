package ru.ovenquill.stream.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class Messages {
    private final Map<String, String> values;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Messages(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) { this.values = new Yaml().load(input); }
    }

    public Component prefixed(String key) { return render("prefix").append(render(key)); }
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

