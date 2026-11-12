package ru.ovenquill.stream.model;

import java.time.Instant;
import java.util.UUID;

public record StreamRecord(
        UUID uuid,
        String lastName,
        String url,
        String platform,
        boolean active,
        Instant startedAt,
        Instant endedAt
) {
}

