package ru.ovenquill.stream.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import ru.ovenquill.stream.model.StreamRecord;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class TabService {
    private final ProxyServer proxy;
    private final int priority;

    public TabService(ProxyServer proxy, int priority) { this.proxy = proxy; this.priority = priority; }

    public void refresh(Collection<StreamRecord> active) {
        Map<UUID, StreamRecord> byId = active.stream().collect(Collectors.toMap(StreamRecord::uuid, value -> value));
        for (Player viewer : proxy.getAllPlayers()) {
            for (Player subject : proxy.getAllPlayers()) {
                viewer.getTabList().getEntry(subject.getUniqueId()).ifPresent(entry -> {
                    if (byId.containsKey(subject.getUniqueId())) {
                        entry.setDisplayName(Component.text("● ", TextColor.color(0xFF5555))
                                .append(Component.text(subject.getUsername(), TextColor.color(0xC8A2FF))));
                        entry.setListOrder(priority);
                    } else {
                        entry.setDisplayName(null);
                        entry.setListOrder(0);
                    }
                });
            }
        }
    }
}

