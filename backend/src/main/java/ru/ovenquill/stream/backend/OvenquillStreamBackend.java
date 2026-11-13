package ru.ovenquill.stream.backend;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;

public final class OvenquillStreamBackend extends JavaPlugin implements PluginMessageListener, Listener {
    private static final String CHANNEL = "ovenquill:stream";
    private static final Component TITLE = Component.text("OVENQUILL • Редактор", NamedTextColor.DARK_PURPLE);
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final Map<UUID, EditorSession> sessions = new HashMap<>();
    private final Map<UUID, List<Node>> restrictionNodes = new HashMap<>();
    private final Map<UUID, Component> originalListNames = new HashMap<>();
    private LuckPerms luckPerms;

    @Override public void onEnable() {
        luckPerms = LuckPermsProvider.get();
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, () -> sessions.entrySet().removeIf(e -> e.getValue().expiresAt < Instant.now().getEpochSecond()), 1200L, 1200L);
    }

    @Override public void onDisable() { sessions.clear(); }

    @Override public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!CHANNEL.equals(channel) || data.length > 4096) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            String action = in.readUTF();
            if (action.equals("TAB_APPLY") || action.equals("TAB_RESTORE")) {
                UUID playerId = UUID.fromString(in.readUTF()); if (!player.getUniqueId().equals(playerId)) return;
                if (action.equals("TAB_APPLY")) {
                    String prefix = in.readUTF(); String prefixColor = in.readUTF(); String nameColor = in.readUTF();
                    Bukkit.getScheduler().runTask(this, () -> applyTab(player, prefix, prefixColor, nameColor));
                } else Bukkit.getScheduler().runTask(this, () -> restoreTab(player));
                return;
            }
            if (action.equals("RESTRICT") || action.equals("RESTORE")) {
                UUID playerId = UUID.fromString(in.readUTF()); UUID nonce = UUID.fromString(in.readUTF());
                int count = in.readInt(); if (count < 0 || count > 100) return;
                List<String> prefixes = new ArrayList<>(); for (int i = 0; i < count; i++) prefixes.add(in.readUTF().toLowerCase());
                if (!player.getUniqueId().equals(playerId)) return;
                Bukkit.getScheduler().runTask(this, () -> acknowledge(player, nonce, action,
                        action.equals("RESTRICT") ? restrict(player, prefixes) : restore(player)));
                return;
            }
            if (!"OPEN_EDITOR".equals(action)) return;
            UUID playerId = UUID.fromString(in.readUTF());
            UUID nonce = UUID.fromString(in.readUTF());
            String type = in.readUTF();
            String currentTemplate = in.readUTF();
            if (!player.getUniqueId().equals(playerId) || !(type.equals("start") || type.equals("off"))) return;
            if (currentTemplate.length() > 4000) return;
            EditorSession session = new EditorSession(nonce, type, currentTemplate);
            sessions.put(playerId, session);
            Bukkit.getScheduler().runTask(this, () -> openMenu(player, session));
        } catch (Exception ex) {
            getLogger().warning("Отклонено некорректное сообщение редактора: " + ex.getMessage());
        }
    }

    private void applyTab(Player player, String prefix, String prefixColor, String nameColor) {
        originalListNames.putIfAbsent(player.getUniqueId(), player.playerListName());
        net.kyori.adventure.text.format.TextColor prefixParsed = net.kyori.adventure.text.format.TextColor.fromHexString(prefixColor);
        net.kyori.adventure.text.format.TextColor nameParsed = net.kyori.adventure.text.format.TextColor.fromHexString(nameColor);
        if (prefixParsed == null) prefixParsed = NamedTextColor.RED;
        if (nameParsed == null) nameParsed = NamedTextColor.LIGHT_PURPLE;
        player.playerListName(Component.text(prefix, prefixParsed).append(Component.text(player.getName(), nameParsed)));
    }

    private void restoreTab(Player player) {
        if (originalListNames.containsKey(player.getUniqueId())) player.playerListName(originalListNames.remove(player.getUniqueId()));
    }

    private boolean restrict(Player player, List<String> prefixes) {
        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId()); if (user == null) return false;
            removeTransient(user, player.getUniqueId());
            List<Node> added = new ArrayList<>();
            for (PermissionNode node : user.resolveInheritedNodes(NodeType.PERMISSION, user.getQueryOptions())) {
                String permission = node.getPermission().toLowerCase();
                boolean preserved = permission.equals("ovenquill.stream.official") || permission.equals("ovenquill.stream.off");
                boolean admin = !preserved && (permission.equals("ovenquill.stream.admin")
                        || permission.equals("ovenquill.stream.info")
                        || prefixes.stream().anyMatch(permission::startsWith));
                if (!node.getValue() || !admin) continue;
                Node negative = PermissionNode.builder(permission).value(false).build();
                user.transientData().add(negative); added.add(negative);
            }
            restrictionNodes.put(player.getUniqueId(), added);
            String path = "restrictions." + player.getUniqueId() + ".was-op";
            if (!getConfig().contains(path)) { getConfig().set(path, player.isOp()); saveConfig(); }
            if (player.isOp()) player.setOp(false);
            return !player.isOp();
        } catch (Exception exception) { getLogger().severe("Не удалось ограничить права " + player.getUniqueId() + ": " + exception); return false; }
    }

    private boolean restore(Player player) {
        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user != null) removeTransient(user, player.getUniqueId());
            String root = "restrictions." + player.getUniqueId();
            if (getConfig().contains(root + ".was-op")) player.setOp(getConfig().getBoolean(root + ".was-op"));
            getConfig().set(root, null); saveConfig();
            return true;
        } catch (Exception exception) { getLogger().severe("Не удалось восстановить права " + player.getUniqueId() + ": " + exception); return false; }
    }

    private void removeTransient(User user, UUID uuid) {
        List<Node> previous = restrictionNodes.remove(uuid);
        if (previous != null) previous.forEach(user.transientData()::remove);
    }

    private void acknowledge(Player player, UUID nonce, String action, boolean success) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("PERMISSION_ACK"); out.writeUTF(player.getUniqueId().toString()); out.writeUTF(nonce.toString());
            out.writeUTF(action); out.writeBoolean(success); player.sendPluginMessage(this, CHANNEL, bytes.toByteArray());
        } catch (Exception exception) { getLogger().warning("Не удалось подтвердить изменение прав: " + exception.getMessage()); }
    }

    private void openMenu(Player player, EditorSession session) {
        Inventory menu = Bukkit.createInventory(player, 54, TITLE);
        session.inventory = menu;
        fill(menu, Material.PURPLE_STAINED_GLASS_PANE);
        NamedTextColor[] colors = {NamedTextColor.RED, NamedTextColor.GOLD, NamedTextColor.YELLOW, NamedTextColor.GREEN,
                NamedTextColor.AQUA, NamedTextColor.BLUE, NamedTextColor.LIGHT_PURPLE, NamedTextColor.WHITE, NamedTextColor.GRAY};
        Material[] dyes = {Material.RED_DYE, Material.ORANGE_DYE, Material.YELLOW_DYE, Material.LIME_DYE,
                Material.LIGHT_BLUE_DYE, Material.BLUE_DYE, Material.MAGENTA_DYE, Material.WHITE_DYE, Material.GRAY_DYE};
        for (int i = 0; i < colors.length; i++) menu.setItem(9 + i, item(dyes[i], Component.text("Цвет текста", colors[i]), List.of(Component.text("Нажмите, чтобы выбрать", NamedTextColor.GRAY))));
        menu.setItem(21, toggle(Material.NETHER_STAR, "Жирный / акцент", session.bold));
        menu.setItem(22, toggle(Material.FEATHER, "Курсив", session.italic));
        menu.setItem(23, toggle(Material.STRING, "Подчёркивание", session.underlined));
        menu.setItem(24, toggle(Material.IRON_SWORD, "Зачёркивание", session.strikethrough));
        menu.setItem(31, preview(session));
        menu.setItem(45, item(Material.WRITABLE_BOOK, Component.text("Изменить текст", NamedTextColor.AQUA), List.of(Component.text("Ввод будет только в чате", NamedTextColor.GRAY), Component.text("Сообщение не увидят игроки", NamedTextColor.DARK_GRAY))));
        menu.setItem(48, item(Material.RECOVERY_COMPASS, Component.text("Сбросить до стандартного", NamedTextColor.YELLOW), List.of(Component.text("Удалить персональные настройки", NamedTextColor.GRAY), Component.text("Вернуть шаблон из messages.yml", NamedTextColor.DARK_GRAY))));
        menu.setItem(49, item(Material.BARRIER, Component.text("Отмена", NamedTextColor.RED), List.of()));
        menu.setItem(53, item(Material.LIME_CONCRETE, Component.text("Сохранить", NamedTextColor.GREEN), List.of(Component.text("Отправить настройки на Velocity", NamedTextColor.GRAY))));
        player.openInventory(menu);
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null || event.getView().getTopInventory() != session.inventory) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != session.inventory) return;
        int slot = event.getRawSlot();
        NamedTextColor[] colors = {NamedTextColor.RED, NamedTextColor.GOLD, NamedTextColor.YELLOW, NamedTextColor.GREEN,
                NamedTextColor.AQUA, NamedTextColor.BLUE, NamedTextColor.LIGHT_PURPLE, NamedTextColor.WHITE, NamedTextColor.GRAY};
        if (slot >= 9 && slot <= 17) session.color = colors[slot - 9];
        else if (slot == 21) session.bold = !session.bold;
        else if (slot == 22) session.italic = !session.italic;
        else if (slot == 23) session.underlined = !session.underlined;
        else if (slot == 24) session.strikethrough = !session.strikethrough;
        else if (slot == 45) {
            session.awaitingChat = true;
            player.closeInventory();
            player.sendMessage(Component.text("Введите текст оповещения в чат. Для отмены: отмена", NamedTextColor.LIGHT_PURPLE));
            return;
        } else if (slot == 48) { reset(player, session); return;
        } else if (slot == 49) {
            sessions.remove(player.getUniqueId()); player.closeInventory();
            player.sendMessage(Component.text("Редактирование отменено.", NamedTextColor.GRAY)); return;
        } else if (slot == 53) { save(player, session); return; }
        openMenu(player, session);
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        EditorSession session = sessions.get(event.getWhoClicked().getUniqueId());
        if (session != null && event.getView().getTopInventory() == session.inventory) event.setCancelled(true);
    }

    @EventHandler public void onChat(AsyncChatEvent event) {
        EditorSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || !session.awaitingChat) return;
        event.setCancelled(true);
        String input = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(this, () -> {
            if (input.equalsIgnoreCase("отмена") || input.equalsIgnoreCase("cancel")) {
                session.awaitingChat = false; openMenu(event.getPlayer(), session); return;
            }
            if (input.isBlank() || input.length() > 240) {
                event.getPlayer().sendMessage(Component.text("Текст должен содержать от 1 до 240 символов.", NamedTextColor.RED)); return;
            }
            session.text = input; session.awaitingChat = false; openMenu(event.getPlayer(), session);
        });
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { sessions.remove(event.getPlayer().getUniqueId()); }

    private void save(Player player, EditorSession session) {
        String template = format(session);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("SAVE_EDITOR"); out.writeUTF(player.getUniqueId().toString()); out.writeUTF(session.nonce.toString());
            out.writeUTF(session.type); out.writeUTF(template);
            player.sendPluginMessage(this, CHANNEL, bytes.toByteArray());
            sessions.remove(player.getUniqueId()); player.closeInventory();
        } catch (Exception ex) {
            getLogger().warning("Не удалось сохранить текст: " + ex.getMessage());
            player.sendMessage(Component.text("Не удалось отправить изменения на прокси.", NamedTextColor.RED));
        }
    }

    private void reset(Player player, EditorSession session) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("RESET_EDITOR"); out.writeUTF(player.getUniqueId().toString()); out.writeUTF(session.nonce.toString());
            out.writeUTF(session.type); player.sendPluginMessage(this, CHANNEL, bytes.toByteArray());
            sessions.remove(player.getUniqueId()); player.closeInventory();
        } catch (Exception ex) {
            getLogger().warning("Не удалось сбросить текст: " + ex.getMessage());
            player.sendMessage(Component.text("Не удалось отправить сброс на прокси.", NamedTextColor.RED));
        }
    }

    private static String format(EditorSession s) {
        String escaped = MINI.escapeTags(s.text);
        StringBuilder open = new StringBuilder("<").append(s.color.toString()).append(">");
        StringBuilder close = new StringBuilder("</").append(s.color.toString()).append(">");
        if (s.bold) { open.append("<bold>"); close.insert(0, "</bold>"); }
        if (s.italic) { open.append("<italic>"); close.insert(0, "</italic>"); }
        if (s.underlined) { open.append("<underlined>"); close.insert(0, "</underlined>"); }
        if (s.strikethrough) { open.append("<strikethrough>"); close.insert(0, "</strikethrough>"); }
        return open + escaped + close;
    }

    private static ItemStack preview(EditorSession s) {
        Component shown = Component.text(s.text, s.color)
                .decoration(TextDecoration.BOLD, s.bold).decoration(TextDecoration.ITALIC, s.italic)
                .decoration(TextDecoration.UNDERLINED, s.underlined).decoration(TextDecoration.STRIKETHROUGH, s.strikethrough);
        return item(Material.PAPER, Component.text("Предпросмотр", NamedTextColor.LIGHT_PURPLE), List.of(shown));
    }

    private static ItemStack toggle(Material material, String label, boolean active) {
        return item(material, Component.text(label, active ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                List.of(Component.text(active ? "Включено" : "Выключено", active ? NamedTextColor.GREEN : NamedTextColor.RED)));
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta();
        meta.itemName(name.decoration(TextDecoration.ITALIC, false)); meta.lore(lore); stack.setItemMeta(meta); return stack;
    }

    private static void fill(Inventory inventory, Material material) {
        ItemStack filler = item(material, Component.empty(), List.of());
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
    }

    private static final class EditorSession {
        final UUID nonce; final String type; final long expiresAt = Instant.now().plusSeconds(300).getEpochSecond();
        Inventory inventory; String text; NamedTextColor color = NamedTextColor.LIGHT_PURPLE;
        boolean bold; boolean italic; boolean underlined; boolean strikethrough; boolean awaitingChat;
        EditorSession(UUID nonce, String type, String currentTemplate) {
            this.nonce = nonce; this.type = type;
            String plain;
            try { plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(MINI.deserialize(currentTemplate)); }
            catch (Exception ignored) { plain = currentTemplate; }
            this.text = plain.isBlank() ? (type.equals("start") ? "{player} начал трансляцию! Смотреть: {link}" : "{player} завершил трансляцию.") : plain;
        }
    }
}
