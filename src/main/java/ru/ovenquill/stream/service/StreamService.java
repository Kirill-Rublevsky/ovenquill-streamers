package ru.ovenquill.stream.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import ru.ovenquill.stream.config.Messages;
import ru.ovenquill.stream.config.PluginConfig;
import ru.ovenquill.stream.model.StreamRecord;
import ru.ovenquill.stream.storage.StreamRepository;
import ru.ovenquill.stream.util.StreamUrl;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class StreamService {
    public static final String OFFICIAL_PERMISSION = "ovenquill.stream.official";
    private final ProxyServer proxy;
    private final StreamRepository repository;
    private final PluginConfig config;
    private final Messages messages;
    private final TabService tabService;
    private final Executor executor;
    private final Logger logger;

    public StreamService(ProxyServer proxy, StreamRepository repository, PluginConfig config, Messages messages,
                         TabService tabService, Executor executor, Logger logger) {
        this.proxy = proxy; this.repository = repository; this.config = config; this.messages = messages;
        this.tabService = tabService; this.executor = executor; this.logger = logger;
    }

    public boolean isOfficial(Player player) { return player.hasPermission(OFFICIAL_PERMISSION); }

    public CompletableFuture<Boolean> saveLink(Player player, String raw) {
        if (!isOfficial(player)) {
            player.sendMessage(messages.prefixed("official-only"));
            return CompletableFuture.completedFuture(false);
        }
        Optional<StreamUrl> parsed = StreamUrl.parse(raw, config.maxUrlLength());
        if (parsed.isEmpty()) { player.sendMessage(messages.prefixed("invalid-link")); return CompletableFuture.completedFuture(false); }
        return CompletableFuture.supplyAsync(() -> {
            try { repository.saveLink(player.getUniqueId(), player.getUsername(), parsed.get().value(), parsed.get().platform()); return true; }
            catch (SQLException exception) { throw new RuntimeException(exception); }
        }, executor).whenComplete((saved, error) -> complete(player, saved, error, "link-saved"));
    }

    public CompletableFuture<Boolean> start(Player player) {
        if (!isOfficial(player)) {
            player.sendMessage(messages.prefixed("official-only"));
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isOfficial(player)) return false;
                Optional<StreamRecord> current = repository.find(player.getUniqueId());
                if (current.isEmpty() || current.get().url() == null) { player.sendMessage(messages.prefixed("link-required")); return false; }
                if (current.get().active()) { player.sendMessage(messages.prefixed("already-active")); return false; }
                if (current.get().endedAt() != null) {
                    long remaining = config.cooldownSeconds() - Duration.between(current.get().endedAt(), Instant.now()).toSeconds();
                    if (remaining > 0 && !player.hasPermission("ovenquill.stream.bypasscooldown")) {
                        player.sendMessage(messages.prefixed("cooldown").replaceText(builder -> builder.matchLiteral("{seconds}").replacement(Long.toString(remaining))));
                        return false;
                    }
                }
                return repository.activate(player.getUniqueId(), player.getUsername(), Instant.now());
            } catch (SQLException exception) { throw new RuntimeException(exception); }
        }, executor).thenApply(started -> {
            if (started) {
                player.sendMessage(messages.prefixed("started"));
                refreshTab();
                if (config.notificationsEnabled()) find(player.getUniqueId()).ifPresent(record ->
                        proxy.sendMessage(messages.render("start-announcement", "player", player.getUsername(), "link", record.url())));
            }
            return started;
        }).exceptionally(error -> { fail(player, error); return false; });
    }

    public CompletableFuture<Boolean> stop(UUID uuid, String actor, String action, boolean announce) {
        return CompletableFuture.supplyAsync(() -> {
            try { return repository.deactivate(uuid, Instant.now(), action, actor); }
            catch (SQLException exception) { throw new RuntimeException(exception); }
        }, executor).thenApply(stopped -> {
            if (stopped) {
                refreshTab();
                proxy.getPlayer(uuid).ifPresent(player -> player.sendMessage(messages.prefixed("stopped")));
                if (announce && config.notificationsEnabled()) find(uuid).ifPresent(record ->
                        proxy.sendMessage(messages.render("off-announcement", "player", record.lastName())));
            }
            return stopped;
        }).exceptionally(error -> {
            logger.error("Failed to stop stream for {}", uuid, error);
            proxy.getPlayer(uuid).ifPresent(player -> player.sendMessage(messages.prefixed("internal-error")));
            return null;
        });
    }

    public void synchronizePermissions() {
        CompletableFuture.supplyAsync(() -> {
            try { return repository.findActive(); } catch (SQLException exception) { throw new RuntimeException(exception); }
        }, executor).thenAccept(active -> {
            List<StreamRecord> retained = active.stream().filter(record -> proxy.getPlayer(record.uuid())
                    .map(this::isOfficial).orElse(true)).toList();
            for (StreamRecord record : active) proxy.getPlayer(record.uuid()).filter(player -> !isOfficial(player))
                    .ifPresent(player -> stop(record.uuid(), "permission-sync", "OFFICIAL_PERMISSION_REVOKED", true));
            tabService.refresh(retained);
        }).exceptionally(error -> { logger.error("Permission synchronization failed", error); return null; });
    }

    public void refreshTab() {
        CompletableFuture.supplyAsync(() -> {
            try { return repository.findActive(); } catch (SQLException exception) { throw new RuntimeException(exception); }
        }, executor).thenAccept(tabService::refresh).exceptionally(error -> { logger.error("TAB refresh failed", error); return null; });
    }

    public Optional<StreamRecord> find(UUID uuid) {
        try { return repository.find(uuid); } catch (SQLException exception) { logger.error("Database read failed", exception); return Optional.empty(); }
    }

    public List<StreamRecord> active() {
        try { return repository.findActive(); } catch (SQLException exception) { throw new RuntimeException(exception); }
    }

    private void complete(Player player, Boolean success, Throwable error, String successMessage) {
        if (error != null) fail(player, error); else if (Boolean.TRUE.equals(success)) player.sendMessage(messages.prefixed(successMessage));
    }
    private void fail(Player player, Throwable error) { logger.error("Stream operation failed for {}", player.getUniqueId(), error); player.sendMessage(messages.prefixed("internal-error")); }
}

