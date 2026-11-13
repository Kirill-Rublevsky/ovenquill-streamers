package ru.ovenquill.stream.service;

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;
import ru.ovenquill.stream.config.Messages;
import ru.ovenquill.stream.config.PluginConfig;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class EditorBridge {
    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("ovenquill:stream");
    private final Messages messages;
    private final Supplier<PluginConfig> config;
    private final Logger logger;
    private final StreamService service;
    private final Map<UUID, PendingEditor> pending = new ConcurrentHashMap<>();

    public EditorBridge(Messages messages, Supplier<PluginConfig> config, Logger logger, StreamService service) {
        this.messages = messages; this.config = config; this.logger = logger; this.service = service;
    }

    public void open(Player player, String type) {
        if (!canEdit(player)) {
            player.sendMessage(messages.prefixed("no-permission")); return;
        }
        service.template(player, type).thenAccept(currentTemplate -> player.getCurrentServer().ifPresentOrElse(connection -> {
            UUID nonce = UUID.randomUUID();
            pending.put(player.getUniqueId(), new PendingEditor(nonce, type, Instant.now().plus(Duration.ofMinutes(5))));
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeUTF("OPEN_EDITOR"); out.writeUTF(player.getUniqueId().toString());
                out.writeUTF(nonce.toString()); out.writeUTF(type);
                out.writeUTF(currentTemplate);
                connection.sendPluginMessage(CHANNEL, bytes.toByteArray());
            } catch (Exception exception) {
                pending.remove(player.getUniqueId());
                logger.error("Failed to open editor", exception);
                player.sendMessage(messages.prefixed("internal-error"));
            }
        }, () -> player.sendMessage(messages.prefixed("internal-error"))))
                .exceptionally(error -> { logger.error("Failed to load personal stream template", error); player.sendMessage(messages.prefixed("internal-error")); return null; });
    }

    public void handle(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection source) || !(event.getTarget() instanceof Player player)) return;
        if (player.getCurrentServer().map(current -> !current.getServerInfo().equals(source.getServerInfo())).orElse(true)) return;
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
            if (!"SAVE_EDITOR".equals(input.readUTF())) return;
            UUID playerId = UUID.fromString(input.readUTF());
            UUID nonce = UUID.fromString(input.readUTF());
            String type = input.readUTF();
            String template = input.readUTF();
            PendingEditor expected = pending.remove(playerId);
            if (!player.getUniqueId().equals(playerId) || expected == null || expected.expiresAt().isBefore(Instant.now())
                    || !expected.nonce().equals(nonce) || !expected.type().equals(type)
                    || !(type.equals("start") || type.equals("off"))
                    || !canEdit(player)) return;
            if (template.length() > config.get().maxMessageLength()) {
                player.sendMessage(messages.prefixed("info-too-long")); return;
            }
            messages.validateTemplate(template);
            service.saveTemplate(player, type, template).thenAccept(saved -> {
                if (saved) player.sendMessage(messages.prefixed("info-saved"));
            });
        } catch (Exception exception) {
            logger.warn("Rejected malformed editor response from {}", source.getServerInfo().getName(), exception);
            player.sendMessage(messages.prefixed("internal-error"));
        }
    }

    private boolean canEdit(Player player) {
        return player.hasPermission("ovenquill.stream.info") || player.hasPermission(StreamService.OFFICIAL_PERMISSION);
    }

    private record PendingEditor(UUID nonce, String type, Instant expiresAt) { }
}
