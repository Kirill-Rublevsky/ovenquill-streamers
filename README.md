# OVENQUILL Stream

Сетевая система официальных стримеров OVENQUILL для **Velocity 4.3.0** и **Purpur 26.2**. Плагин хранит ссылки и LIVE-состояние по UUID, отправляет сетевые уведомления, выделяет стримера в TAB и предоставляет внутриигровой редактор текста.

## Состав и установка

- `ovenquill-stream-0.3.1-SNAPSHOT.jar` — основной плагин для `velocity/plugins/`: команды, SQLite, уведомления, permissions и TAB.
- `ovenquill-stream-backend-0.3.1-SNAPSHOT.jar` — модуль для `plugins/` **каждого** Purpur-сервера: Minecraft GUI и защищённая связь с Velocity.

Остановите всю сеть, удалите старые JAR OVENQUILL Stream, установите оба файла в указанные каталоги и запустите сначала Purpur-серверы, затем Velocity. Требуется Java 21 или новее. SQLite JDBC уже включён в основной JAR.

Чтобы исключить путаницу, используйте явно названные файлы из `release/`: `OVENQUILL-STREAM-VELOCITY-0.3.1.jar` для Velocity и `OVENQUILL-STREAM-PURPUR-0.3.1.jar` для Purpur. Архив `OVENQUILL-STREAM-0.3.1-INSTALL.zip` уже содержит правильную структуру каталогов.

### Ошибка `ClassNotFoundException: ru.ovenquill.stream.StreamPlugin`

В `velocity/plugins/` загружен неверный, неполный либо дублирующий JAR. Полностью остановите прокси, удалите **все** файлы OVENQUILL Stream из этого каталога и загрузите туда только `OVENQUILL-STREAM-VELOCITY-0.3.1.jar`. Purpur-файл нельзя помещать на Velocity. Затем проверьте SHA-256 Velocity JAR: `53348600B3B3B17A243105EAC027AEE51937ACE176DFC2A302C5AEEEEC73D6ED`.

## Команды игроков

| Команда | Назначение | Право |
|---|---|---|
| `/stream help` | Показывает только доступные игроку команды. | Нет |
| `/stream link <https-ссылка>` | Сохраняет или меняет ссылку Twitch, YouTube, Kick либо другого HTTPS-ресурса. | `ovenquill.stream.official` |
| `/stream start` | Запускает LIVE, сохраняет статус, меняет TAB и отправляет оповещение. | `ovenquill.stream.official` |
| `/stream off` | Завершает LIVE, восстанавливает TAB и отправляет оповещение. | `ovenquill.stream.official` |
| `/stream info start` | Открывает GUI уведомления о начале трансляции. | Official или `ovenquill.stream.info` |
| `/stream info off` | Открывает GUI уведомления о завершении трансляции. | Official или `ovenquill.stream.info` |
| `/stream info start <MiniMessage>` | Напрямую записывает шаблон начала. | Official или `ovenquill.stream.info` |
| `/stream info off <MiniMessage>` | Напрямую записывает шаблон завершения. | Official или `ovenquill.stream.info` |

`ovenquill.stream.use` ничего не разрешает. OP также не назначает официальный статус автоматически.

## Административные команды

Требуют `ovenquill.stream.admin` и доступны из консоли Velocity.

| Команда | Функция |
|---|---|
| `/stream admin list` | Показывает активные трансляции и ссылки. |
| `/stream admin stop <player>` | Принудительно завершает трансляцию независимо от official-статуса. |
| `/stream admin status <player>` | Показывает UUID, ссылку, платформу, активность и время запуска. |
| `/stream admin reload` | Перечитывает `config.yml` и `messages.yml`. |

## Permissions и LuckPerms

| Permission | Описание |
|---|---|
| `ovenquill.stream.official` | Официальный стример: link, start, off и GUI. |
| `ovenquill.stream.info` | Редактор уведомлений без official-статуса. |
| `ovenquill.stream.admin` | Административные команды. |
| `ovenquill.stream.bypasscooldown` | Обход задержки запуска. |

Назначение и отзыв через единый источник permissions на Velocity:

```text
/lpv user <ник> permission set ovenquill.stream.official true
/lpv user <ник> permission unset ovenquill.stream.official
```

Не добавляйте official-право в общедоступную группу. После отзыва активный режим автоматически завершается при синхронизации, входе или смене backend.

## Minecraft-редактор

GUI предоставляет палитру, жирный визуальный акцент, курсив, подчёркивание, зачёркивание, предпросмотр, сохранение и отмену. Реальный размер шрифта без ресурс-пака Minecraft не поддерживает. После нажатия «Изменить текст» следующий ввод перехватывается и не появляется в общем чате; `отмена` возвращает в меню.

Плейсхолдеры: `{player}`, `{link}`, `{server}`, `{platform}`.

Редактор защищён одноразовым UUID-токеном на пять минут. Velocity принимает сохранение только от текущего backend-сервера игрока и повторно проверяет permission.

## Конфигурация

Настройки создаются в `velocity/plugins/ovenquill-stream/`:

- `stream.cooldown-seconds` — задержка повторного запуска;
- `stream.max-url-length` и `stream.max-message-length` — лимиты;
- `stream.permission-sync-seconds` — период проверки official-права;
- `platforms.allow-other-https` — поддержка других HTTPS-платформ;
- `tab.*` — круг, цвета, приоритет и восстановление TAB;
- `notifications.enabled` — глобальные оповещения;
- `storage.file` — SQLite-файл;
- `messages.yml` — все тексты и шаблоны MiniMessage.

## Безопасность и хранение

- UUID является ключом данных; ник хранится только для отображения.
- Все permissions и сообщения канала проверяются сервером.
- SQLite работает в WAL-режиме, активный статус переживает рестарт.
- TAB восстанавливается после перехода между серверами.
- Ответ старого backend после перехода отклоняется.
- Административный stop работает после отзыва official-права.
- URL принимает только корректный HTTPS без управляющих символов.

## Сборка

JDK 21 и Maven 3.9+:

```bash
mvn clean package
mvn -f backend/pom.xml clean package
```

Результаты находятся в `target/` и `backend/target/`.

## Известное ограничение

Автоматическое временное снятие всех административных прав во время LIVE пока не включено. Для безопасной реализации требуется подтверждённая интеграция с конфигурацией LuckPerms конкретной сети; постоянные права и OP плагин вслепую не изменяет.
