package ru.ovenquill.stream.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamRepositoryTest {
    @TempDir Path temporaryDirectory;

    @Test void opensPackagedDriverAndPersistsStreamer() throws Exception {
        UUID uuid = UUID.randomUUID();
        try (StreamRepository repository = new StreamRepository(temporaryDirectory.resolve("streams.db"))) {
            repository.saveLink(uuid, "Tester", "https://twitch.tv/tester", "Twitch");
            assertTrue(repository.find(uuid).isPresent());
            assertEquals("https://twitch.tv/tester", repository.find(uuid).orElseThrow().url());
        }
    }
}
