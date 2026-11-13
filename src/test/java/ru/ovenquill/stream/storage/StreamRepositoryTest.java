package ru.ovenquill.stream.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamRepositoryTest {
    @TempDir Path temporaryDirectory;

    @Test void opensPackagedDriverAndPersistsStreamer() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (StreamRepository repository = new StreamRepository(temporaryDirectory.resolve("streams.db"))) {
            repository.saveLink(uuid, "Tester", "https://twitch.tv/tester", "Twitch");
            repository.saveTemplate(uuid, "Tester", "start", "<red>{player} test</red>");
            assertTrue(repository.find(uuid).isPresent());
            assertEquals("https://twitch.tv/tester", repository.find(uuid).orElseThrow().url());
            assertEquals("<red>{player} test</red>", repository.find(uuid).orElseThrow().startTemplate());
        }
    }

    @Test void migratesLegacyDatabaseForPersonalTemplates() throws Exception {
        Path database = temporaryDirectory.resolve("legacy.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database); var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE streamers (uuid TEXT PRIMARY KEY NOT NULL,last_name TEXT NOT NULL,url TEXT,platform TEXT,active INTEGER NOT NULL DEFAULT 0,started_at INTEGER,ended_at INTEGER,updated_at INTEGER NOT NULL)");
        }
        UUID uuid = UUID.randomUUID();
        try (StreamRepository repository = new StreamRepository(database)) {
            repository.saveTemplate(uuid, "Legacy", "off", "<gray>bye</gray>");
            assertEquals("<gray>bye</gray>", repository.find(uuid).orElseThrow().offTemplate());
        }
    }
}
