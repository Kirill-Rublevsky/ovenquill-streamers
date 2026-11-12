package ru.ovenquill.stream.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PluginConfigTest {
    @TempDir Path directory;

    @Test void loadsProductionDefaults() throws Exception {
        PluginConfig config = PluginConfig.load(directory);
        assertTrue(config.restrictAdmin());
        assertTrue(config.failClosed());
        assertTrue(config.tabEnabled());
        assertEquals("⬤ ", config.tabPrefix());
        assertTrue(config.adminPermissionPrefixes().contains("luckperms."));
        assertEquals("streams.db", config.storageFile());
    }
}
