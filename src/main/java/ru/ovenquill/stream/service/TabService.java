package ru.ovenquill.stream.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import ru.ovenquill.stream.model.StreamRecord;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TabService {
    private final ProxyServer proxy;
    private volatile boolean enabled;
    private volatile int priority;
    private volatile String prefix;
    private volatile TextColor prefixColor;
    private volatile TextColor nameColor;
    private final Map<EntryKey, OriginalEntry> originals = new HashMap<>();

    public TabService(ProxyServer proxy, boolean enabled, int priority, String prefix, String prefixColor, String nameColor) {
        this.proxy = proxy; update(enabled, priority, prefix, prefixColor, nameColor);
    }

    public void update(boolean enabled, int priority, String prefix, String prefixColor, String nameColor) {
        this.enabled = enabled; this.priority = priority; this.prefix = prefix;
        this.prefixColor = color(prefixColor, 0xFF5555); this.nameColor = color(nameColor, 0xC8A2FF);
    }

    public synchronized void refresh(Collection<StreamRecord> active) {
        Collection<StreamRecord> effective = enabled ? active : List.of();
        List<StreamRecord> ordered = effective.stream().sorted(Comparator
                .comparing(StreamRecord::startedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StreamRecord::uuid)).toList();
        Map<UUID, Integer> orderById = new HashMap<>();
        for (int index = 0; index < ordered.size(); index++) {
            orderById.put(ordered.get(index).uuid(), priority - index);
        }
        Set<EntryKey> visibleKeys = new HashSet<>();
        for (Player viewer : proxy.getAllPlayers()) {
            for (Player subject : proxy.getAllPlayers()) {
                viewer.getTabList().getEntry(subject.getUniqueId()).ifPresent(entry -> {
                    EntryKey key = new EntryKey(viewer.getUniqueId(), subject.getUniqueId());
                    Integer listOrder = orderById.get(subject.getUniqueId());
                    if (listOrder != null) {
                        visibleKeys.add(key);
                        originals.computeIfAbsent(key, ignored -> new OriginalEntry(
                                entry.getDisplayNameComponent().orElse(null), entry.getListOrder()));
                        entry.setDisplayName(Component.text(prefix, prefixColor)
                                .append(Component.text(subject.getUsername(), nameColor)));
                        entry.setListOrder(listOrder);
                    }
                });
            }
        }
        originals.entrySet().removeIf(saved -> {
            if (visibleKeys.contains(saved.getKey())) return false;
            proxy.getPlayer(saved.getKey().viewer()).ifPresent(viewer ->
                    viewer.getTabList().getEntry(saved.getKey().subject()).ifPresent(entry -> {
                        entry.setDisplayName(saved.getValue().displayName());
                        entry.setListOrder(saved.getValue().listOrder());
                    }));
            return true;
        });
    }

    private record EntryKey(UUID viewer, UUID subject) { }
    private record OriginalEntry(Component displayName, int listOrder) { }
    private static TextColor color(String input, int fallback) {
        TextColor parsed = TextColor.fromHexString(input); return parsed == null ? TextColor.color(fallback) : parsed;
    }
}

