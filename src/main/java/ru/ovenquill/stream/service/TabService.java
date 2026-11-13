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
import org.slf4j.Logger;

public final class TabService {
    private final ProxyServer proxy;
    private volatile boolean enabled;
    private volatile int priority;
    private volatile String prefix;
    private volatile TextColor prefixColor;
    private volatile TextColor nameColor;
    private final Logger logger;
    private final Set<UUID> externalStyled = new HashSet<>();
    private boolean externalTabReported;
    private final Map<EntryKey, OriginalEntry> originals = new HashMap<>();

    public TabService(ProxyServer proxy, boolean enabled, int priority, String prefix, String prefixColor, String nameColor,
                      Logger logger) {
        this.proxy = proxy; this.logger = logger; update(enabled, priority, prefix, prefixColor, nameColor);
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
        Set<UUID> activeIds = orderById.keySet();
        for (int index = 0; index < ordered.size(); index++) {
            StreamRecord record = ordered.get(index); applyExternalTab(record.uuid(), record.lastName(), true, index);
        }
        for (UUID previous : new HashSet<>(externalStyled)) if (!activeIds.contains(previous)) applyExternalTab(previous, null, false, 0);
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

    private void applyExternalTab(UUID uuid, String username, boolean active, int order) {
        try {
            Class<?> apiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object tabPlayer = apiClass.getMethod("getPlayer", UUID.class).invoke(api, uuid);
            Object manager = apiClass.getMethod("getTabListFormatManager").invoke(api);
            if (tabPlayer == null || manager == null) return;
            java.lang.reflect.Method setPrefix = java.util.Arrays.stream(manager.getClass().getMethods())
                    .filter(method -> method.getName().equals("setPrefix") && method.getParameterCount() == 2).findFirst().orElseThrow();
            java.lang.reflect.Method setName = java.util.Arrays.stream(manager.getClass().getMethods())
                    .filter(method -> method.getName().equals("setName") && method.getParameterCount() == 2).findFirst().orElseThrow();
            Object sortingManager = apiClass.getMethod("getSortingManager").invoke(api);
            java.lang.reflect.Method forceTeamName = sortingManager == null ? null : java.util.Arrays.stream(sortingManager.getClass().getMethods())
                    .filter(method -> method.getName().equals("forceTeamName") && method.getParameterCount() == 2).findFirst().orElse(null);
            if (active) {
                setPrefix.invoke(manager, tabPlayer, "<" + prefixColor.asHexString() + ">" + prefix);
                setName.invoke(manager, tabPlayer, "<" + nameColor.asHexString() + ">" + username);
                if (forceTeamName != null) forceTeamName.invoke(sortingManager, tabPlayer, teamName(uuid, order));
                externalStyled.add(uuid);
            } else {
                setPrefix.invoke(manager, tabPlayer, (Object) null); setName.invoke(manager, tabPlayer, (Object) null);
                if (forceTeamName != null) forceTeamName.invoke(sortingManager, tabPlayer, (Object) null);
                externalStyled.remove(uuid);
            }
            if (!externalTabReported) { logger.info("TAB plugin API detected; OVENQUILL formatting is applied through TAB"); externalTabReported = true; }
        } catch (ClassNotFoundException ignored) {
            // TAB is optional; native Velocity/Purpur formatting remains active.
        } catch (Exception exception) {
            if (!externalTabReported) { logger.warn("TAB plugin detected but API integration failed", exception); externalTabReported = true; }
        }
    }

    static String teamName(UUID uuid, int order) {
        return String.format("0OQ%04d%08X", Math.max(0, Math.min(order, 9999)), uuid.hashCode());
    }
}

