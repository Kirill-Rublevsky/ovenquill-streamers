package ru.ovenquill.stream.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MessagesTest {
    @TempDir Path temporaryDirectory;

    @Test void preservesUpdatesAndSuppliesNewBundledDefaults() throws Exception {
        Path file = temporaryDirectory.resolve("messages.yml");
        Files.writeString(file, "prefix: '<green>Custom</green> '");
        Messages messages = new Messages(file);
        messages.update("start-announcement", "<red>{player}</red>");
        messages.reload();
        assertTrue(Files.readString(file).contains("start-announcement"));
        assertTrue(messages.prefixed("no-permission").toString().contains("Custom"));
        assertNotEquals(messages.raw("start-announcement"), messages.defaultRaw("start-announcement"));
        assertTrue(messages.defaultRaw("start-announcement").contains("СМОТРЕТЬ СТРИМ"));
    }
}
