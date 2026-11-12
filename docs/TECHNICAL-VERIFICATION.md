# Техническая проверка

- Minecraft Java Edition 26.2 выпущен 16 июня 2026 года.
- Velocity 4.3.0 выпущен в октябре 2026 года и совместим с указанной эпохой протокола.
- Velocity API предоставляет изменение display name и реальный `listOrder` Player Info, поэтому TAB-функция выполнима на прокси для современных клиентов.
- Чистый Velocity не управляет backend OP и не может доказуемо перекрыть все административные возможности Bukkit/Paper-плагинов. Для полного выполнения §8 нужен минимальный доверенный backend-модуль и единая LuckPerms-модель deny-контекста.
- Изменение одних только нескольких command permissions не удовлетворяет §8; такая реализация намеренно не заявлена завершённой.

Источники:

- https://www.minecraft.net/article/minecraft-java-edition-26-2
- https://github.com/PaperMC/Velocity/commit/4d93ece
- https://jd.papermc.io/velocity/4.2.0/com/velocitypowered/api/proxy/player/TabListEntry.html
- https://docs.papermc.io/velocity/dev/api/
- https://luckperms.net/wiki/Advanced-Setup
