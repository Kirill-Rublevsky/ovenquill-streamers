package ru.ovenquill.stream.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StreamUrlTest {
    @Test void acceptsKnownHttpsPlatforms() {
        assertEquals("Twitch", StreamUrl.parse("https://twitch.tv/ovenquill", 512).orElseThrow().platform());
        assertEquals("YouTube", StreamUrl.parse("https://youtu.be/example", 512).orElseThrow().platform());
        assertEquals("Kick", StreamUrl.parse("https://kick.com/ovenquill", 512).orElseThrow().platform());
    }
    @Test void rejectsUnsafeUrls() {
        assertTrue(StreamUrl.parse("http://twitch.tv/test", 512).isEmpty());
        assertTrue(StreamUrl.parse("javascript:alert(1)", 512).isEmpty());
        assertTrue(StreamUrl.parse("https://user:secret@example.com", 512).isEmpty());
        assertTrue(StreamUrl.parse("https://example.com/\nattack", 512).isEmpty());
    }
}

