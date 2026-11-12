# OVENQUILL Stream

> **Версия:** `0.3.0-SNAPSHOT` для Velocity 4.3.0 и Purpur 26.2.

Velocity-плагин системы официальных стримеров OVENQUILL. Текущая версия — первый компилируемый вертикальный срез разработки.

## Установка

1. Полностью остановите Velocity.
2. Удалите из `velocity/plugins/` **все старые JAR OVENQUILL Stream**. Одновременно должен находиться только один JAR с plugin id `ovenquill-stream`.
3. Поместите `ovenquill-stream-0.3.0-SNAPSHOT.jar` в `velocity/plugins/`.
4. Поместите `ovenquill-stream-backend-0.3.0-SNAPSHOT.jar` в `plugins/` каждого Purpur-сервера.
5. Перезапустите все backend-серверы и Velocity.

Если в стеке `StreamRepository` вызов SQLite указан на строке 17, сервер загрузил старую сборку 0.1.0, а не 0.1.2.

## Реализовано в 0.3.0-SNAPSHOT

- обязательная серверная проверка `ovenquill.stream.official` при каждой пользовательской команде;
- скрытие недоступных команд из `/stream help` и автодополнения;
- сохранение и строгая проверка HTTPS-ссылок;
- SQLite/WAL, UUID как ключ, транзакционное завершение и аудит;
- запуск/завершение LIVE с защитой от повторного запуска;
- cooldown;
- глобальные Adventure/MiniMessage-уведомления;
- TAB-цвет и реальный `listOrder`;
- сохранение LIVE после выхода/перезапуска;
- периодическое обнаружение отзыва official permission, а также проверки при входе и смене backend;
- идемпотентное автоматическое завершение после отзыва permission.
- защищённый одноразовым токеном канал Velocity ↔ текущий Purpur backend;
- Minecraft-интерфейс редактирования уведомлений (`/stream info start` и `/stream info off`);
- палитра, жирный акцент, курсив, подчёркивание, зачёркивание и предпросмотр;
- плейсхолдеры `{player}`, `{link}`, `{server}` и `{platform}`.

## Пока не реализовано

- безопасное временное ограничение всех административных прав;
- автоматическое временное ограничение всех административных прав во время LIVE;
- полноценный reload;
- интеграционные тесты с реальным Velocity 4.3.0 и Minecraft 26.2;
- совместимость со сторонними TAB-плагинами.

При включённом полном ограничении прав production-релиз не должен считаться готовым до появления backend-модуля и подтверждённой LuckPerms-интеграции.

## Сборка

Требуется JDK 21. `mvn clean package` создаёт Velocity JAR, а `mvn -f backend/pom.xml clean package` — Purpur JAR.

Установите JAR только на Velocity в `plugins/`, затем назначьте статус:

```text
/lpv user <ник> permission set ovenquill.stream.official true
```

