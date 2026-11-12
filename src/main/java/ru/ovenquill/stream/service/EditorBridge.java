package ru.ovenquill.stream.service;

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
    private final Map<UUID, PendingEditor> pending = new ConcurrentHashMap<>();

    public EditorBridge(Messages messages, Supplier<PluginConfig> config, Logger logger) {
        this.messages = messages; this.config = config; this.logger = logger;
    }

    public void open(Player player, String type) {
        if (!player.hasPermission("ovenquill.stream.info")) {
            player.sendMessage(messages.prefixed("no-permission")); return;
        }
        player.getCurrentServer().ifPresentOrElse(connection -> {
            UUID nonce = UUID.randomUUID();
            pending.put(player.getUniqueId(), new PendingEditor(nonce, type, Instant.now().plus(Duration.ofMinutes(5))));
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeUTF("OPEN_EDITOR"); out.writeUTF(player.getUniqueId().toString());
                out.writeUTF(nonce.toString()); out.writeUTF(type);
                out.writeUTF(messages.raw(type.equals("start") ? "start-announcement" : "off-announcement"));
                connection.sendPluginMessage(CHANNEL, bytes.toByteArray());
            } catch (Exception exception) {
                pending.remove(player.getUniqueId());
                logger.error("Failed to open editor", exception);
                player.sendMessage(messages.prefixed("internal-error"));
            }
        }, () -> player.sendMessage(messages.prefixed("internal-error")));
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
                    || !player.hasPermission("ovenquill.stream.info")) return;
            if (template.length() > config.get().maxMessageLength()) {
                player.sendMessage(messages.prefixed("info-too-long")); return;
            }
            MiniMessage.miniMessage().deserialize(template);
            messages.update(type.equals("start") ? "start-announcement" : "off-announcement", template);
            player.sendMessage(messages.prefixed("info-saved"));
        } catch (Exception exception) {
            logger.warn("Rejected malformed editor response from {}", source.getServerInfo().getName(), exception);
            player.sendMessage(messages.prefixed("internal-error"));
        }
    }

    private record PendingEditor(UUID nonce, String type, Instant expiresAt) { }
}
