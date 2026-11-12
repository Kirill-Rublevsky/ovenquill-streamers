package ru.ovenquill.stream.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import ru.ovenquill.stream.config.Messages;
import ru.ovenquill.stream.config.PluginConfig;
import ru.ovenquill.stream.model.StreamRecord;
import ru.ovenquill.stream.service.StreamService;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.BiConsumer;

public final class StreamCommand implements SimpleCommand {
    private static final String ADMIN = "ovenquill.stream.admin";
    private static final String INFO = "ovenquill.stream.info";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final StreamService service;
    private final Messages messages;
    private final Supplier<PluginConfig> config;
    private final Runnable reload;
    private final BiConsumer<Player, String> openEditor;

    public StreamCommand(StreamService service, Messages messages, Supplier<PluginConfig> config, Runnable reload,
                         BiConsumer<Player, String> openEditor) {
        this.service = service;
        this.messages = messages;
        this.config = config;
        this.reload = reload;
        this.openEditor = openEditor;
    }

    @Override public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { help(source); return; }
        if (args[0].equalsIgnoreCase("info")) { info(source, args); return; }
        if (args[0].equalsIgnoreCase("admin")) { admin(source, args); return; }
        if (!(source instanceof Player player)) {
            source.sendMessage(Component.text("Эта подкоманда доступна только игроку.", NamedTextColor.RED));
            return;
        }
        if (!service.isOfficial(player)) { player.sendMessage(messages.prefixed("official-only")); return; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "link" -> {
                if (args.length == 2) service.saveLink(player, args[1]);
                else player.sendMessage(Component.text("/stream link <ссылка>"));
            }
            case "start" -> service.start(player);
            case "off" -> service.stop(player.getUniqueId(), player.getUsername(), "PLAYER_STOP", true)
                    .thenAccept(stopped -> { if (Boolean.FALSE.equals(stopped)) player.sendMessage(messages.prefixed("not-active")); });
            default -> help(source);
        }
    }

    private void info(CommandSource source, String[] args) {
        if (!canEditInfo(source)) { source.sendMessage(messages.prefixed("no-permission")); return; }
        if (args.length == 2 && source instanceof Player player
                && (args[1].equalsIgnoreCase("start") || args[1].equalsIgnoreCase("off"))) {
            openEditor.accept(player, args[1].toLowerCase(Locale.ROOT));
            return;
        }
        if (args.length < 3 || !(args[1].equalsIgnoreCase("start") || args[1].equalsIgnoreCase("off"))) {
            source.sendMessage(Component.text("/stream info <start|off> <текст>"));
            return;
        }
        String template = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        if (template.length() > config.get().maxMessageLength()) {
            source.sendMessage(messages.prefixed("info-too-long"));
            return;
        }
        try {
            messages.update(args[1].equalsIgnoreCase("start") ? "start-announcement" : "off-announcement", template);
            source.sendMessage(messages.prefixed("info-saved"));
        } catch (Exception exception) {
            source.sendMessage(messages.prefixed("internal-error"));
        }
    }

    private void admin(CommandSource source, String[] args) {
        if (!source.hasPermission(ADMIN)) { source.sendMessage(messages.prefixed("no-permission")); return; }
        if (args.length < 2) { adminHelp(source); return; }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> service.activeAsync().thenAccept(records -> {
                if (records.isEmpty()) source.sendMessage(messages.prefixed("admin-list-empty"));
                else {
                    source.sendMessage(Component.text("Активные стримеры:", NamedTextColor.GREEN));
                    records.forEach(record -> source.sendMessage(Component.text("• " + record.lastName() + " — " + record.url())));
                }
            }).exceptionally(error -> { source.sendMessage(messages.prefixed("internal-error")); return null; });
            case "stop" -> {
                if (args.length != 3) { source.sendMessage(Component.text("/stream admin stop <player>")); return; }
                service.findByNameAsync(args[2]).thenAccept(found -> {
                    if (found.isEmpty()) { source.sendMessage(messages.prefixed("player-not-found")); return; }
                    service.stop(found.get().uuid(), sourceName(source), "ADMIN_STOP", true).thenAccept(stopped -> {
                        if (Boolean.TRUE.equals(stopped)) source.sendMessage(messages.render("admin-stop-complete", "player", found.get().lastName()));
                        else if (Boolean.FALSE.equals(stopped)) source.sendMessage(messages.prefixed("not-active"));
                    });
                }).exceptionally(error -> { source.sendMessage(messages.prefixed("internal-error")); return null; });
            }
            case "status" -> {
                if (args.length != 3) { source.sendMessage(Component.text("/stream admin status <player>")); return; }
                service.findByNameAsync(args[2]).thenAccept(found -> status(source, found))
                        .exceptionally(error -> { source.sendMessage(messages.prefixed("internal-error")); return null; });
            }
            case "reload" -> {
                try {
                    reload.run();
                    source.sendMessage(messages.prefixed("reload-complete"));
                } catch (RuntimeException exception) {
                    source.sendMessage(messages.prefixed("internal-error"));
                }
            }
            default -> adminHelp(source);
        }
    }

    private void status(CommandSource source, Optional<StreamRecord> found) {
        if (found.isEmpty()) { source.sendMessage(messages.prefixed("player-not-found")); return; }
        StreamRecord record = found.get();
        source.sendMessage(Component.text("Статус " + record.lastName(), NamedTextColor.GREEN));
        source.sendMessage(Component.text("UUID: " + record.uuid()));
        source.sendMessage(Component.text("Ссылка: " + (record.url() == null ? "не задана" : record.url())));
        source.sendMessage(Component.text("Платформа: " + (record.platform() == null ? "не определена" : record.platform())));
        source.sendMessage(Component.text("Активен: " + (record.active() ? "да" : "нет")));
        if (record.startedAt() != null) source.sendMessage(Component.text("Начало: " + TIME.format(record.startedAt())));
    }

    private String sourceName(CommandSource source) {
        return source instanceof Player player ? player.getUsername() + " (" + player.getUniqueId() + ")" : "console";
    }

    @Override public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        CommandSource source = invocation.source();
        if (args.length <= 1) {
            ArrayList<String> result = new ArrayList<>();
            result.add("help");
            if (source instanceof Player player && service.isOfficial(player)) result.addAll(List.of("link", "start", "off"));
            if (canEditInfo(source)) result.add("info");
            if (source.hasPermission(ADMIN)) result.add("admin");
            return result;
        }
        if (args[0].equalsIgnoreCase("info") && args.length == 2 && canEditInfo(source)) return List.of("start", "off");
        if (args[0].equalsIgnoreCase("admin") && args.length == 2 && source.hasPermission(ADMIN)) return List.of("list", "stop", "status", "reload");
        return List.of();
    }

    private void help(CommandSource source) {
        source.sendMessage(Component.text("━━━━━━━━ OVENQUILL STREAM ━━━━━━━━", NamedTextColor.DARK_GREEN));
        if (source instanceof Player player && service.isOfficial(player)) {
            source.sendMessage(Component.text("/stream link <ссылка> — сохранить канал"));
            source.sendMessage(Component.text("/stream start — начать трансляцию"));
            source.sendMessage(Component.text("/stream off — завершить трансляцию"));
        }
        if (canEditInfo(source)) source.sendMessage(Component.text("/stream info <start|off> — открыть редактор уведомления"));
        if (source.hasPermission(ADMIN)) source.sendMessage(Component.text("/stream admin ... — административные команды"));
        source.sendMessage(Component.text("/stream help — показать помощь"));
        source.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GREEN));
    }

    private void adminHelp(CommandSource source) {
        source.sendMessage(Component.text("/stream admin list"));
        source.sendMessage(Component.text("/stream admin stop <player>"));
        source.sendMessage(Component.text("/stream admin status <player>"));
        source.sendMessage(Component.text("/stream admin reload"));
    }

    private boolean canEditInfo(CommandSource source) {
        return source.hasPermission(INFO) || source instanceof Player player && service.isOfficial(player);
    }
}
