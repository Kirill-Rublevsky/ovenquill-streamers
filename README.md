# OVENQUILL Stream

Сетевая система официальных стримеров OVENQUILL для **Velocity 4.3.0** и **Purpur 26.2**. Плагин хранит ссылки и LIVE-состояние по UUID, отправляет сетевые уведомления, выделяет стримера в TAB и предоставляет внутриигровой редактор текста.

## Состав и установка

- `OVENQUILL-STREAM-VELOCITY-1.0.0-RC3.jar` — основной плагин для `velocity/plugins/`: команды, SQLite, уведомления, permissions и TAB.
- `OVENQUILL-STREAM-PURPUR-1.0.0-RC3.jar` — модуль для `plugins/` **каждого** Purpur-сервера: Minecraft GUI, ограничение backend-прав и защищённая связь с Velocity.

Остановите всю сеть, удалите старые JAR OVENQUILL Stream, установите оба файла в указанные каталоги и запустите сначала Purpur-серверы, затем Velocity. Требуются Java 21+ и LuckPerms 5.5 на Velocity и на каждом Purpur. SQLite JDBC уже включён в основной JAR.

Архив `OVENQUILL-STREAM-1.0.0-RC3-INSTALL.zip` содержит правильную структуру каталогов. RC3 хранит шаблоны начала и завершения отдельно для UUID каждого стримера и автоматически использует API плагина TAB, если он установлен.

### Ошибка `ClassNotFoundException: ru.ovenquill.stream.StreamPlugin`

В `velocity/plugins/` загружен неверный, неполный либо дублирующий JAR. Полностью остановите прокси, удалите **все** файлы OVENQUILL Stream из этого каталога и загрузите туда только файл с `VELOCITY` в имени. Purpur-файл нельзя помещать на Velocity.

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

Тексты сохраняются персонально по UUID. Изменение шаблона одним стримером больше не влияет на остальных. `messages.yml` содержит только глобальные значения по умолчанию для игроков, которые ещё не создавали собственный шаблон.

Плейсхолдеры: `{player}`, `{link}`, `{server}`, `{platform}`.

Редактор защищён одноразовым UUID-токеном на пять минут. Velocity принимает сохранение только от текущего backend-сервера игрока и повторно проверяет permission.

## Ограничение административных прав

При `/stream start` Velocity и текущий Purpur получают временные отрицательные LuckPerms nodes для всех выданных прав, совпадающих с `permissions.admin-prefixes`. Purpur дополнительно снимает OP, предварительно сохраняя исходное значение на диске. Запуск LIVE подтверждается только после ответа backend; при `fail-closed: true` ошибка или отсутствие backend/LuckPerms отменяет запуск.

Постоянные группы и permissions не изменяются. При `/stream off` временные nodes снимаются, OP восстанавливается, и только после подтверждения выполняется завершение. При переходе между серверами ограничения применяются повторно. После аварии исходный OP хранится в `plugins/OvenquillStreamBackend/config.yml` и восстанавливается после подтверждённого завершения.

Обязательно дополните `permissions.admin-prefixes` префиксами **всех** административных плагинов вашей сети. Плагин не может семантически определить неизвестный permission стороннего плагина. `ovenquill.stream.official` и возможность `/stream off` сохраняются даже при широком префиксе `ovenquill.`.

## Конфигурация

Настройки создаются в `velocity/plugins/ovenquill-stream/`:

- `stream.cooldown-seconds` — задержка повторного запуска;
- `stream.max-url-length` и `stream.max-message-length` — лимиты;
- `stream.permission-sync-seconds` — период проверки official-права;
- `platforms.allow-other-https` — поддержка других HTTPS-платформ;
- `tab.*` — круг, цвета, приоритет и восстановление TAB;
- `notifications.enabled` — глобальные оповещения;
- `storage.file` — SQLite-файл;
- `permissions.restrict-admin-during-stream` и `permissions.fail-closed` — режим ограничения;
- `permissions.admin-prefixes` — область контролируемых административных прав;
- `messages.yml` — все тексты и шаблоны MiniMessage.

## Безопасность и хранение

- UUID является ключом данных; ник хранится только для отображения.
- Все permissions и сообщения канала проверяются сервером.
- SQLite работает в WAL-режиме, активный статус переживает рестарт.
- TAB восстанавливается после перехода между серверами.
- Если установлен NEZNAMY/TAB, цвет, индикатор и верхняя сортировка задаются через его API; отключать `tablist-name-formatting` не требуется.
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

## Проверка и границы подтверждения

Автоматические тесты проверяют URL, SQLite, сообщения и production-конфигурацию. Отдельный smoke-тест запускает SQLite непосредственно из итогового shaded JAR. Оба модуля компилируются против Velocity 4.3.0, Purpur 26.2 и LuckPerms API 5.5.

Интеграционные сценарии с реальной сетью (конкретная версия стороннего TAB, административные плагины, аварийное отключение процесса) должны быть подтверждены на staging-копии вашей сети перед заменой RC3 на стабильный `1.0.0`; они не выдаются за автоматически пройденные.
