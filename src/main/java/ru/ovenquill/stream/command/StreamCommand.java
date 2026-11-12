package ru.ovenquill.stream.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import ru.ovenquill.stream.config.Messages;
import ru.ovenquill.stream.service.StreamService;

import java.util.List;

public final class StreamCommand implements SimpleCommand {
    private final StreamService service;
    private final Messages messages;

    public StreamCommand(StreamService service, Messages messages) { this.service = service; this.messages = messages; }

    @Override public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { help(source); return; }
        if (!(source instanceof Player player)) { source.sendMessage(Component.text("Эта подкоманда доступна только игроку.", NamedTextColor.RED)); return; }
        if (!service.isOfficial(player)) { player.sendMessage(messages.prefixed("official-only")); return; }
        switch (args[0].toLowerCase()) {
            case "link" -> { if (args.length == 2) service.saveLink(player, args[1]); else player.sendMessage(Component.text("/stream link <ссылка>")); }
            case "start" -> service.start(player);
            case "off" -> service.stop(player.getUniqueId(), player.getUsername(), "PLAYER_STOP", true)
                    .thenAccept(stopped -> { if (Boolean.FALSE.equals(stopped)) player.sendMessage(messages.prefixed("not-active")); });
            default -> help(source);
        }
    }

    @Override public List<String> suggest(Invocation invocation) {
        if (!(invocation.source() instanceof Player player) || !service.isOfficial(player)) return List.of("help");
        if (invocation.arguments().length <= 1) return List.of("help", "link", "start", "off");
        return List.of();
    }

    private void help(CommandSource source) {
        source.sendMessage(Component.text("━━━━━━━━ OVENQUILL STREAM ━━━━━━━━", NamedTextColor.DARK_GREEN));
        if (source instanceof Player player && service.isOfficial(player)) {
            source.sendMessage(Component.text("/stream link <ссылка> — сохранить канал"));
            source.sendMessage(Component.text("/stream start — начать трансляцию"));
            source.sendMessage(Component.text("/stream off — завершить трансляцию"));
        }
        source.sendMessage(Component.text("/stream help — показать помощь"));
        source.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GREEN));
    }
}

