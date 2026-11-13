package ru.ovenquill.stream;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import ru.ovenquill.stream.command.StreamCommand;
import ru.ovenquill.stream.config.Messages;
import ru.ovenquill.stream.config.PluginConfig;
import ru.ovenquill.stream.service.StreamService;
import ru.ovenquill.stream.service.EditorBridge;
import ru.ovenquill.stream.service.TabService;
import ru.ovenquill.stream.service.PermissionService;
import ru.ovenquill.stream.storage.StreamRepository;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Plugin(id = "ovenquill-stream", name = "OVENQUILL Stream", version = "1.0.0-RC4",
        description = "Official streamer system for OVENQUILL", authors = {"OVENQUILL"},
        dependencies = {@Dependency(id = "luckperms")})
public final class StreamPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ovenquill-stream-db"); thread.setDaemon(true); return thread;
    });
    private StreamRepository repository;
    private StreamService service;
    private volatile PluginConfig config;
    private Messages messages;
    private EditorBridge editorBridge;
    private PermissionService permissionService;

    @Inject public StreamPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy; this.logger = logger; this.dataDirectory = dataDirectory;
    }

    @Subscribe public void onInitialize(ProxyInitializeEvent event) {
        try {
            config = PluginConfig.load(dataDirectory);
            messages = new Messages(dataDirectory.resolve("messages.yml"));
            Path storagePath = dataDirectory.resolve(config.storageFile()).normalize();
            if (!storagePath.startsWith(dataDirectory.normalize())) throw new IllegalArgumentException("storage.file escapes plugin directory");
            repository = new StreamRepository(storagePath);
            TabService tabService = new TabService(proxy, config.tabEnabled(), config.tabPriority(), config.tabPrefix(),
                    config.tabPrefixColor(), config.tabNameColor(), logger);
            permissionService = new PermissionService(() -> config, logger);
            service = new StreamService(proxy, repository, config, messages, tabService, permissionService,
                    this::scheduleTabRecovery, databaseExecutor, logger);
            editorBridge = new EditorBridge(messages, () -> config, logger, service);
            proxy.getChannelRegistrar().register(EditorBridge.CHANNEL);
            proxy.getCommandManager().register(proxy.getCommandManager().metaBuilder("stream").plugin(this).build(),
                    new StreamCommand(service, messages, () -> config, this::reload, editorBridge::open));
            proxy.getScheduler().buildTask(this, service::synchronizePermissions)
                    .repeat(Duration.ofSeconds(Math.max(5, config.permissionSyncSeconds()))).schedule();
            service.synchronizePermissions();
            logger.info("OVENQUILL Stream 1.0.0-RC4 initialized from {}",
                    StreamPlugin.class.getProtectionDomain().getCodeSource().getLocation());
        } catch (Exception exception) {
            logger.error("OVENQUILL Stream failed to initialize", exception);
        }
    }

    @Subscribe public void onPostLogin(PostLoginEvent event) {
        if (service != null) {
            service.synchronizePermissions();
            service.synchronizePlayerRestrictions(event.getPlayer());
            scheduleTabRecovery();
        }
    }

    @Subscribe public void onServerChange(ServerPostConnectEvent event) {
        if (service != null) {
            service.synchronizePermissions();
            service.synchronizePlayerRestrictions(event.getPlayer());
            scheduleTabRecovery();
        }
    }

    @Subscribe public void onPluginMessage(PluginMessageEvent event) {
        if (editorBridge != null) editorBridge.handle(event);
        if (permissionService != null) permissionService.handle(event);
    }

    private void scheduleTabRecovery() {
        // Backend Player Info packets can arrive after ServerPostConnectEvent and overwrite our values.
        proxy.getScheduler().buildTask(this, service::refreshTab).delay(Duration.ofMillis(500)).schedule();
        proxy.getScheduler().buildTask(this, service::refreshTab).delay(Duration.ofSeconds(2)).schedule();
    }

    private void reload() {
        try {
            config = PluginConfig.load(dataDirectory);
            messages.reload();
            service.updateConfig(config);
            service.refreshTab();
        } catch (Exception exception) {
            logger.error("Failed to reload OVENQUILL Stream", exception);
            throw new IllegalStateException("Reload failed", exception);
        }
    }

    @Subscribe public void onShutdown(ProxyShutdownEvent event) {
        databaseExecutor.shutdown();
        if (repository != null) try { repository.close(); } catch (Exception exception) { logger.error("Failed to close database", exception); }
    }
}

