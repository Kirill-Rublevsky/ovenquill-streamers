package ru.ovenquill.stream.service;

import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PermissionNode;
import org.slf4j.Logger;
import ru.ovenquill.stream.config.PluginConfig;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class PermissionService {
    private final Supplier<PluginConfig> config;
    private final Logger logger;
    private final LuckPerms luckPerms;
    private final Map<UUID, List<Node>> proxyNodes = new ConcurrentHashMap<>();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public PermissionService(Supplier<PluginConfig> config, Logger logger) {
        this.config = config; this.logger = logger; this.luckPerms = LuckPermsProvider.get();
    }

    public CompletableFuture<Boolean> restrict(Player player) {
        if (!config.get().restrictAdmin()) return CompletableFuture.completedFuture(true);
        boolean proxyApplied = applyProxy(player);
        if (!proxyApplied && config.get().failClosed()) return CompletableFuture.completedFuture(false);
        return requestBackend(player, "RESTRICT").thenApply(backend -> {
            if (!backend && config.get().failClosed()) { restoreProxy(player.getUniqueId()); return false; }
            return proxyApplied || backend;
        });
    }

    public CompletableFuture<Boolean> restore(Player player) {
        boolean proxyRestored = restoreProxy(player.getUniqueId());
        return requestBackend(player, "RESTORE").thenApply(backend -> proxyRestored && backend);
    }

    public void ensure(Player player, boolean active) {
        if (active) restrict(player).thenAccept(ok -> { if (!ok) logger.error("Failed to reapply LIVE restrictions to {}", player.getUniqueId()); });
        else requestBackend(player, "RESTORE");
    }

    private boolean applyProxy(Player player) {
        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) return false;
            restoreProxy(player.getUniqueId());
            List<Node> added = new ArrayList<>();
            for (PermissionNode node : user.resolveInheritedNodes(NodeType.PERMISSION, user.getQueryOptions())) {
                String permission = node.getPermission().toLowerCase();
                if (!node.getValue() || !isAdministrative(permission)) continue;
                Node negative = PermissionNode.builder(permission).value(false).build();
                user.transientData().add(negative); added.add(negative);
            }
            proxyNodes.put(player.getUniqueId(), added);
            return true;
        } catch (Exception exception) {
            logger.error("Unable to apply Velocity permission restrictions to {}", player.getUniqueId(), exception);
            return false;
        }
    }

    private boolean restoreProxy(UUID uuid) {
        try {
            User user = luckPerms.getUserManager().getUser(uuid);
            List<Node> nodes = proxyNodes.remove(uuid);
            if (user != null && nodes != null) nodes.forEach(user.transientData()::remove);
            return true;
        } catch (Exception exception) {
            logger.error("Unable to restore Velocity permissions for {}", uuid, exception); return false;
        }
    }

    private boolean isAdministrative(String permission) {
        if (permission.equals("ovenquill.stream.official") || permission.equals("ovenquill.stream.off")) return false;
        if (permission.equals("ovenquill.stream.admin") || permission.equals("ovenquill.stream.info")) return true;
        return config.get().adminPermissionPrefixes().stream().anyMatch(permission::startsWith);
    }

    private CompletableFuture<Boolean> requestBackend(Player player, String action) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        player.getCurrentServer().ifPresentOrElse(connection -> {
            UUID nonce = UUID.randomUUID();
            pending.put(nonce, new Pending(player.getUniqueId(), action, Instant.now().plus(Duration.ofSeconds(10)), result));
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeUTF(action); out.writeUTF(player.getUniqueId().toString()); out.writeUTF(nonce.toString());
                List<String> prefixes = config.get().adminPermissionPrefixes(); out.writeInt(prefixes.size());
                for (String prefix : prefixes) out.writeUTF(prefix);
                connection.sendPluginMessage(EditorBridge.CHANNEL, bytes.toByteArray());
            } catch (Exception exception) { pending.remove(nonce); result.complete(false); }
        }, () -> result.complete(!config.get().failClosed()));
        CompletableFuture.delayedExecutor(10, java.util.concurrent.TimeUnit.SECONDS).execute(() -> {
            pending.entrySet().removeIf(entry -> {
                if (entry.getValue().expiresAt.isAfter(Instant.now())) return false;
                entry.getValue().result.complete(false); return true;
            });
        });
        return result;
    }

    public void handle(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(EditorBridge.CHANNEL)
                || !(event.getSource() instanceof ServerConnection source)
                || !(event.getTarget() instanceof Player player)) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
            if (!"PERMISSION_ACK".equals(in.readUTF())) return;
            UUID uuid = UUID.fromString(in.readUTF()); UUID nonce = UUID.fromString(in.readUTF());
            String action = in.readUTF(); boolean success = in.readBoolean();
            Pending expected = pending.remove(nonce);
            if (expected == null || !uuid.equals(player.getUniqueId()) || !uuid.equals(expected.playerId)
                    || !action.equals(expected.action) || expected.expiresAt.isBefore(Instant.now())
                    || player.getCurrentServer().map(c -> !c.getServerInfo().equals(source.getServerInfo())).orElse(true)) return;
            expected.result.complete(success);
        } catch (Exception exception) { logger.warn("Rejected malformed permission acknowledgement", exception); }
    }

    private record Pending(UUID playerId, String action, Instant expiresAt, CompletableFuture<Boolean> result) { }
}
