package ru.ovenquill.stream.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

public record StreamUrl(String value, String platform) {
    public static Optional<StreamUrl> parse(String raw, int maxLength) {
        if (raw == null || raw.isBlank() || raw.length() > maxLength || raw.chars().anyMatch(Character::isISOControl)) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(raw);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                return Optional.empty();
            }
            String normalized = uri.toASCIIString();
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String platform = host.equals("twitch.tv") || host.endsWith(".twitch.tv") ? "Twitch"
                    : host.equals("youtube.com") || host.endsWith(".youtube.com") || host.equals("youtu.be") ? "YouTube"
                    : host.equals("kick.com") || host.endsWith(".kick.com") ? "Kick" : "Другая";
            return Optional.of(new StreamUrl(normalized, platform));
        } catch (URISyntaxException exception) {
            return Optional.empty();
        }
    }
}

