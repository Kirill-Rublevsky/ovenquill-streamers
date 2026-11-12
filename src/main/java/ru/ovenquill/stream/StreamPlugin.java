package ru.ovenquill.stream;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import ru.ovenquill.stream.command.StreamCommand;
import ru.ovenquill.stream.config.Messages;
import ru.ovenquill.stream.config.PluginConfig;
import ru.ovenquill.stream.service.StreamService;
import ru.ovenquill.stream.service.EditorBridge;
import ru.ovenquill.stream.service.TabService;
import ru.ovenquill.stream.storage.StreamRepository;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Plugin(id = "ovenquill-stream", name = "OVENQUILL Stream", version = "0.3.0-SNAPSHOT",
        description = "Official streamer system for OVENQUILL", authors = {"OVENQUILL"})
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

    @Inject public StreamPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy; this.logger = logger; this.dataDirectory = dataDirectory;
    }

    @Subscribe public void onInitialize(ProxyInitializeEvent event) {
        try {
            config = PluginConfig.load(dataDirectory);
            messages = new Messages(dataDirectory.resolve("messages.yml"));
            repository = new StreamRepository(dataDirectory.resolve("streams.db"));
            TabService tabService = new TabService(proxy, config.tabPriority());
            service = new StreamService(proxy, repository, config, messages, tabService, databaseExecutor, logger);
            editorBridge = new EditorBridge(messages, () -> config, logger);
            proxy.getChannelRegistrar().register(EditorBridge.CHANNEL);
            proxy.getCommandManager().register(proxy.getCommandManager().metaBuilder("stream").plugin(this).build(),
                    new StreamCommand(service, messages, () -> config, this::reload, editorBridge::open));
            proxy.getScheduler().buildTask(this, service::synchronizePermissions)
                    .repeat(Duration.ofSeconds(Math.max(5, config.permissionSyncSeconds()))).schedule();
            service.synchronizePermissions();
            logger.info("OVENQUILL Stream 0.2.0-SNAPSHOT initialized from {}",
                    StreamPlugin.class.getProtectionDomain().getCodeSource().getLocation());
        } catch (Exception exception) {
            logger.error("OVENQUILL Stream failed to initialize", exception);
        }
    }

    @Subscribe public void onPostLogin(PostLoginEvent event) {
        if (service != null) {
            service.synchronizePermissions();
            scheduleTabRecovery();
        }
    }

    @Subscribe public void onServerChange(ServerPostConnectEvent event) {
        if (service != null) {
            service.synchronizePermissions();
            scheduleTabRecovery();
        }
    }

    @Subscribe public void onPluginMessage(PluginMessageEvent event) {
        if (editorBridge != null) editorBridge.handle(event);
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

