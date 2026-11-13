package ru.ovenquill.stream.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TabServiceTest {
    @Test void tabTeamKeyFitsProtocolLimitAndKeepsOrder() {
        UUID uuid = UUID.randomUUID();
        String first = TabService.teamName(uuid, 0);
        String second = TabService.teamName(uuid, 1);
        assertEquals(15, first.length());
        assertTrue(first.compareTo(second) < 0);
    }
}
