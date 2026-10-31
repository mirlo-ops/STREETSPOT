# Продолжение разработки

## Состояние

Рабочая папка: `D:/Project/STREETSPOT`. Локальный commit `9d1e6bd` содержит исходное состояние проекта; remote `origin` указывает на `https://github.com/mirlo-ops/STREETSPOT.git`. Ветка `main` пока не отправлялась на GitHub.

Этап 0 проверен: на момент начала этапа 1 были семь согласованных Markdown-файлов, а Android-код и Gradle-файлы отсутствовали. Таблица уровней и ключевые решения проекта соответствовали документации.

Основные экраны этапа 11 ранее установлены отдельным debug-пакетом `com.streetspot.app.stage11` на Xiaomi M2010J19SY; на телефоне проверялись гостевой вход, основные разделы, Profile/Community, новая находка и системный Back. После замены Firebase Storage на Supabase фото ещё требуют повторной проверки правил и интеграции. Подписи нижней панели помещаются в одну строку, квадратный логотип из `res/` назначен launcher-иконкой. Пользователь подтвердил, что прежний `com.streetspot.app` удалил сам. Перед закрытием последовательности этапов также остаётся документированная проверка предыдущего этапа: Room 2→3 и архивы v1–v3.

## Что сделано на этапе 1

- Создан один Android-модуль `app` на Kotlin и Jetpack Compose с точкой входа `MainActivity`.
- Добавлена минимальная приветственная поверхность STREETSPOT в стиле Midnight JDM.
- Настроены цветовые токены и типографика из `docs/design-system.md`; размеры текста заданы в `sp`, поэтому Compose использует системное масштабирование шрифта.
- Включён edge-to-edge и безопасные системные отступы. Движущихся элементов пока нет, отдельная логика уменьшения анимации на этом этапе не требовалась.
- Сохранена офлайн-архитектура: серверных сервисов и сетевых зависимостей в код приложения не добавлено.
- Зафиксированы версии сборки: Gradle Wrapper 9.5.1, AGP 9.3.1, Kotlin Gradle Plugin и Compose Compiler 2.4.21, Compose BOM 2026.09.00, Activity Compose 1.13.0; `compileSdk 37`, `targetSdk 36`, `minSdk 26`, JVM 17.
- Версии сверялись с официальными таблицами совместимости Kotlin/Gradle и документацией Android по AGP, Compose и целевому API.

## Файлы этапа 1

- Сборка: `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`.
- Модуль и ресурсы: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `colors.xml`, `themes.xml`.
- Kotlin: `app/src/main/java/com/streetspot/app/MainActivity.kt`, `ui/StreetSpotRoot.kt`, `ui/theme/Color.kt`, `Type.kt`, `Theme.kt`.
- Состояние этапа: `docs/continue-here.md`.

## Проверки и результат

- `gradlew.bat --no-daemon :app:assembleDebug --console=plain` — `BUILD SUCCESSFUL`; повторная сборка через Wrapper завершилась за 1 мин 16 сек, все задачи были актуальны.
- Первичная сборка тем же проектом завершилась успешно за 16 мин 29 сек.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, размер 11 646 551 байт. Папка `app/build/` исключена из Git через `.gitignore`; APK не входит в переносимый набор исходников.
- Wrapper Gradle 9.5.1 скачался примерно за 43 секунды после того, как пользователь увеличил скорость интернета. Это подтвердило улучшение загрузки. Долгая первая сборка также включала локальную обработку Kotlin/Android-зависимостей и D8; значит, на время влияли и сеть, и вычислительная нагрузка ПК.
- По записи предыдущего этапа, были установлены JDK 17, Android SDK Platform 37.0 (revision 2), Platform 37.2, Build Tools 36.0.0, Platform Tools 37.0.1 и Emulator 37.2.12. В текущем сеансе `adb` отсутствовал в `PATH`; для ручной проверки Platform Tools 37.0.1 распакованы во временный каталог вне проекта.
- Для проверки телефона загружен образ `system-images;android-36;google_apis;x86_64` и создан AVD `StreetSpotPhoneApi36`. Холодная загрузка AVD за отведённое время не завершилась: `adb` показывал `offline`. Предустановленный AVD `CleanYourTVPreview` также не стал доступным устройством. APK на AVD не устанавливался; визуальная проверка темы, insets и крупного шрифта не заявляется как выполненная.
- На этом ПК было 4 логических ядра и около 5,9 ГБ оперативной памяти. Эмуляторная проверка здесь ограничена как производительностью, так и тем, что единственный ранее настроенный образ был Google TV.

### Ручная проверка на физическом телефоне — 29.10.2026

- Официальные Android Platform Tools 37.0.1 временно распакованы вне проекта; `adb` обнаружил устройство Xiaomi (`M2010J19SY`, serial `48ad224f0521`, состояние `device`).
- Первые попытки установки были отклонены MIUI (`INSTALL_FAILED_USER_RESTRICTED`). После того, как пользователь включил **Install via USB** в настройках разработчика, `adb install -r` завершился успешно. `am start -W -n com.streetspot.app/.MainActivity` вернул `Status: ok`; MainActivity отображалась как возобновлённая Activity.
- Устройство: Android 12 / API 31, 1080×2340, 440 dpi. На экране видны тёмный фон, мятный акцент, карточка и системные status/navigation bars; контент не перекрывает системные области.
- Крупный системный шрифт проверен через настройки телефона (`XXL`): заголовки и карточка остались читаемыми и не обрезались. Исходный размер `S` восстановлен; `settings get system font_scale` подтвердил `1.0`. Приложение оставлено открытым на телефоне.
- Пользователь включил **Install via USB** в настройках разработчика, чтобы установка через `adb` работала. Проект системные настройки телефона не менял.

## Что сделано на этапе 2

- Добавлена зависимость Navigation Compose 2.10.2; версия разрешилась в графе Gradle и сборка прошла.
- `StreetSpotRoot` подключён к Navigation Compose. Созданы маршруты Home, коллекции, достижений, статистики, настроек, добавления находки, деталей и награды.
- Добавлена нижняя навигация с четырьмя вкладками и визуально выделенной центральной кнопкой «Новая находка». Для вкладок включены восстановление сохранённого back stack и одиночное открытие маршрута.
- Дополнительные экраны открываются отдельно, используют верхнюю панель с Back; обработку системного Back оставляет Navigation Compose.
- Все видимые подписи, действия и тексты заглушек переведены на русский. Название STREETSPOT сохранено. Заглушки прямо сообщают о ещё не подключённых функциях и не показывают вымышленные данные.
- На этом этапе нет поиска, фильтров и списка данных, состояние которых можно было бы сохранять.

## Файлы этапа 2

- Сборка и зависимость: `app/build.gradle.kts`.
- Навигация и экраны: `app/src/main/java/com/streetspot/app/ui/StreetSpotRoot.kt`, `ui/navigation/StreetSpotNavigation.kt`, `ui/screens/PlaceholderScreen.kt`.
- Подписи интерфейса: `app/src/main/res/values/strings.xml`.
- Состояние проекта: `docs/continue-here.md`.

## Проверки этапа 2

- `gradlew.bat --no-daemon :app:dependencies --configuration debugRuntimeClasspath --console=plain` — `BUILD SUCCESSFUL`; Navigation Compose 2.10.2 разрешилась.
- `gradlew.bat --no-daemon :app:assembleDebug --console=plain` — `BUILD SUCCESSFUL`. Финальная сборка с русскими строками завершилась за 1 мин 15 сек; APK `app/build/outputs/apk/debug/app-debug.apk`, 12 689 752 байта.
- До перевода проверен установленный этап 2 на телефоне: Home → Spotdex → Badges → Stats, центральный New Spot, Settings, Reward, Spot Details и возврат системной кнопкой Back. Заглушки отображались вместо отсутствующих данных.
- Русская сборка не устанавливалась на телефон после перевода; визуальную проверку русских строк пользователь попросил перенести на этап 3. На телефоне остаётся установленный до перевода тестовый APK этапа 2 с английскими подписями. Поворот экрана, пересоздание процесса и узкая ширина также остаются непроверенными.

## Среда сборки текущего ПК

- JDK 17 и Android SDK находятся вне проекта в `D:/Tools`; SDK root — `D:/Tools/StreetSpot-AndroidSdk-20261029` (Platform 37.0, Build Tools 36.0.0, Platform Tools 37.0.1).
- `local.properties` указывает на локальный SDK и исключён из Git через `.gitignore`; при переносе на другой ПК путь нужно задать заново. SDK Manager установил только лицензируемые компоненты, необходимые этой сборке.

## Что сделано на этапе 3

- Добавлена Room 2.8.5 с KSP 2.3.12 и Coroutines Android 1.11.0. Версии стабильные на дату настройки и сверены с официальными release notes.
- Создана схема Room версии 1 для Brand, CarModel, Spot, XPTransaction и AchievementState; схемы экспортируются в `app/schemas/`.
- Добавлены DAO для сущностей, внешние ключи и индексы. Нормализованные имена брендов уникальны без учёта регистра; модель уникальна по марке, имени и поколению. Удаление используемой марки/модели ограничено, XP-транзакции удаляются вместе со Spot.
- Репозиторий нормализует пробелы и регистр, хранит поколение пустой строкой в составном ключе, не позволяет пользовательской модели получить редкость выше COMMON и не создаёт дублей при повторном вводе.
- При старте приложения в фоновом потоке транзакционно загружается идемпотентный каталог из 16 марок и 16 моделей, покрывающий все шесть категорий и пять уровней редкости. Seed игнорирует конфликты и не перезаписывает пользовательские записи.
- Добавлены локальные тесты нормализации и целостности seed, а также Android-инструментальные тесты DAO и внешних ключей.

## Файлы этапа 3

- Gradle/KSP и тестовый runner: `build.gradle.kts`, `app/build.gradle.kts`.
- Схема: `app/schemas/com.streetspot.app.data.local.StreetSpotDatabase/1.json`.
- Модели, типы и нормализация: `app/src/main/java/com/streetspot/app/data/model/`.
- Room, DAO, репозиторий и seed: `app/src/main/java/com/streetspot/app/data/local/`, `app/src/main/java/com/streetspot/app/data/repository/`.
- Инициализация приложения: `app/src/main/java/com/streetspot/app/StreetSpotApplication.kt`, `app/src/main/AndroidManifest.xml`.
- Unit-тесты: `app/src/test/java/com/streetspot/app/data/`; тесты Room: `app/src/androidTest/java/com/streetspot/app/data/local/StreetSpotDatabaseTest.kt`.

## Проверки этапа 3

- `gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --console=plain` — `BUILD SUCCESSFUL`.
- Локальные unit-тесты — 5 пройдено, ошибок нет.
- Android-тесты Room скомпилированы в `app-debug-androidTest.apk`, но не запускались: пользователь попросил отложить проверки на телефоне до финального APK.
- Сгенерированную схему проверили: уникальные индексы Brand и CarModel, `RESTRICT` для Brand/CarModel и `CASCADE` для Spot → XPTransaction присутствуют.
- Seed и CRUD на реальном устройстве пока не проверялись. Это остаётся финальной проверкой; UI каталога будет подключён на следующих этапах.

## Что сделано на этапе 4

- Добавлены CameraX 1.6.2, ExifInterface 1.4.2 и Lifecycle 2.11.0; доступны встроенная съёмка и Android Photo Picker. Разрешение камеры запрашивается только при её использовании, отказ не мешает выбрать существующее фото.
- Фото копируется в приватное хранилище приложения, ориентируется по EXIF, уменьшается до длинной стороны не более 2048 px и перекодируется в JPEG quality 88. GPS-поля EXIF удаляются, для подготовленного файла рассчитывается SHA-256.
- Временные и финальные файлы очищаются при обработке ошибок. При запуске приложения очищаются неиспользуемые файлы старше 24 часов, чтобы не затронуть текущую операцию сохранения.
- Добавлен трёхшаговый сценарий Photo → Identify → Save: поиск встроенной модели, создание своей модели с редкостью COMMON, дата, локальное время, цвет, заметка и ручная подпись места без координат.
- Spot сохраняется через Room и RoomSpotRepository. Точный дубль определяется тройкой модель + локальная дата + SHA-256; пользователь может открыть прежнюю запись либо подтвердить отдельную запись. Маршрут к деталям с ID подготовлен, сам экран будет реализован на этапе 5.
- Файлы этапа: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `StreetSpotApplication.kt`, `data/photo/PhotoStorage.kt`, `data/repository/SpotRepository.kt`, `RoomSpotRepository.kt`, `data/local/dao/SpotDao.kt`, `ui/screens/addspot/`, `ui/navigation/StreetSpotNavigation.kt`, `app/src/androidTest/java/com/streetspot/app/data/local/StreetSpotDatabaseTest.kt`, `app/src/androidTest/java/com/streetspot/app/data/photo/PhotoStorageTest.kt`.

## Проверки этапа 4

- `gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --console=plain` — `BUILD SUCCESSFUL`.
- Локальные unit-тесты — 5 пройдено, ошибок нет. APK этапа 4 создан: `app/build/outputs/apk/debug/app-debug.apk` (19 051 093 байта).
- Android-инструментальные тесты проверки точного дубля, сжатия и удаления GPS EXIF скомпилированы в `app-debug-androidTest.apk` (412 828 байт), но не запускались.
- Съёмка, Photo Picker, EXIF на пользовательских фото и экран на устройстве не проверялись. Проверку устройства отложить до готового финального APK и отдельного решения пользователя.

## Что сделано на этапе 5

- Spotdex показывает уникальные модели в двухколоночной сетке. Room группирует Spot по модели, считает встречи, выбирает главную фотографию последней находки и сортирует модели по времени последней находки.
- Добавлены поиск по марке, модели и поколению, фильтры по всем шести категориям и пяти уровням редкости. Строка поиска, фильтры и позиция сетки используют saveable-состояние навигационного экрана.
- Детали модели показывают каталоговые данные и все отдельные Spot с фотографиями, датой/временем, цветом, подписью места и заметкой.
- Реализованы изменение модели и полей Spot, замена фото через Photo Picker, подтверждение удаления. Room обновляет запись до удаления старого фото; ошибка записи удаляет новую копию. Удаление записи каскадно удаляет её XP-транзакции, если они есть.
- Ресурсы интерфейса этапа добавлены на русском. Фото загружаются из приватной директории, текст и действия остаются видимыми при ошибке изображения.
- Файлы этапа: `data/model/CarModelSpotdexEntry.kt`, `data/local/dao/CarModelDao.kt`, `SpotDao.kt`, `data/repository/CarCatalogRepository.kt`, `RoomCarCatalogRepository.kt`, `SpotRepository.kt`, `RoomSpotRepository.kt`, `data/photo/PhotoStorage.kt`, `ui/components/StoredPhotoImage.kt`, `ui/screens/spotdex/`, `ui/screens/spotdetails/`, `ui/navigation/StreetSpotNavigation.kt`, русские ресурсы, `app/src/androidTest/java/com/streetspot/app/data/local/StreetSpotDatabaseTest.kt`.

## Проверки этапа 5

- `gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --console=plain` — `BUILD SUCCESSFUL`.
- Локальные unit-тесты — 5 пройдено. Android-инструментальный тест группировки Spot, выбора главного фото и сортировки собран, но не запускался.
- Экраны и восстановление состояния на устройстве не проверялись. Это отложено до финального APK по просьбе пользователя.

## Что сделано на этапе 6

- Реализован чистый XpCalculator с детерминированной сортировкой событий и полным пересбором XPTransaction. За первую модель начисляются base XP +40 XP за модель; новая марка добавляет +20 XP. Повтор даёт +5 XP не чаще одного раза на модель за локальную дату. Точное совпадение модели, даты и SHA-256 полностью исключается из XP.
- Идентификаторы транзакций стабильны (`Spot ID + причина`); повторный расчёт сначала удаляет производный журнал и строит его заново. Уровень, ранг и доля прогресса рассчитываются по таблице 1–30 из документации.
- `RoomSpotRepository` пересчитывает XP в той же транзакции Room, что сохранение, редактирование модели/даты/фото и удаление. Удаление также каскадно убирает XP-транзакции. При первом запуске выполняется восстановительный пересчёт из локальных Spot.
- Home показывает уровень, ранг, общий XP, прогресс до следующего порога, число записей, найденных марок, уникальных редких моделей и две последние находки. Новое повышение уровня сообщается только при фактическом переходе вверх после добавления Spot; исправления и удаления не показывают награду.
- Добавлены unit-тесты пяти значений базового XP, новых моделей/марок, дневного ограничения, точных дублей, стабильности и всех порогов уровней. Android-тест дополнительно проверяет пересчёт после сохранения, изменения и удаления.
- Файлы этапа: `data/model/SpotWithModel.kt`, `data/local/dao/SpotDao.kt`, `CarModelDao.kt`, `XpTransactionDao.kt`, `data/repository/ProgressRepository.kt`, `RoomProgressRepository.kt`, `RoomSpotRepository.kt`, `domain/progression/XpCalculator.kt`, `LevelCalculator.kt`, `ui/screens/home/`, изменения Add Spot и инициализации приложения, `app/src/test/java/com/streetspot/app/domain/progression/`, `app/src/androidTest/java/com/streetspot/app/data/repository/ProgressRepositoryTest.kt`.

## Проверки этапа 6

- `gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --console=plain` — `BUILD SUCCESSFUL`.
- 10 локальных unit-тестов пройдены, ошибок нет. Пороговая таблица уровней совпала с документацией; приложение и APK этапа 6 собрались.
- Android-тест пересчёта Room собран, но не запускался. Устройство, экран Home и показ Level Up в интерфейсе не проверялись по просьбе пользователя.

## Что сделано на этапе 7

- Добавлен локальный AchievementChecker для всех шести правил: первая Spot, 10 уникальных JDM-моделей, 5 ночных Spot, 5 уникальных RARE/LEGENDARY моделей, 20 марок и 100 Spot-записей. Модели/марки считаются уникально, ночной диапазон — с 20:00 включительно до 06:00 исключительно.
- Состояние каждого достижения и `unlockedAt` пересчитываются вместе с XP в одной транзакции Room. Время первого открытия сохраняется, пока условие выполнено; при потере условия очищается, а при повторном выполнении записывается заново. ID `100_SPOTS` хранится в точном виде из документации.
- В Badges показываются все достижения, текущий прогресс, закрытое/открытое состояние и дата открытия. Home показывает незавершённое достижение с самым высоким процентом прогресса; равенство разрешается фиксированным порядком из документа.
- Достижения не добавляют XP. Пересчёт вызывается при сохранении, редактировании и удалении Spot, поэтому счётчики Badges и Next Achievement обновляются сразу.
- Добавлены unit-тесты границ NIGHT RIDER, различия между количеством Spot и уникальными моделями/марками, порога 100 записей и разрешения равного прогресса. Инструментальный тест проверяет сохранение и сброс `unlockedAt` после удаления последней записи.
- Файлы этапа: `domain/progression/AchievementChecker.kt`, `data/local/dao/AchievementStateDao.kt`, `data/repository/ProgressRepository.kt`, `RoomProgressRepository.kt`, `ui/screens/achievements/`, `ui/screens/home/HomeScreen.kt`, `ui/navigation/StreetSpotNavigation.kt`, `app/src/test/java/com/streetspot/app/domain/progression/AchievementCheckerTest.kt`, `app/src/androidTest/java/com/streetspot/app/data/repository/ProgressRepositoryTest.kt`, русские ресурсы.

## Проверки этапа 7

- `gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --console=plain` — `BUILD SUCCESSFUL`.
- 13 локальных unit-тестов пройдены, ошибок нет. APK этапа 7 собран: `app/build/outputs/apk/debug/app-debug.apk` (19 051 211 байт).
- Инструментальные тесты пересчёта и сохранения состояний скомпилированы, но не запускались. Badges и Home на телефоне не проверялись.

## Что сделано на этапе 8

- Добавлен чистый `StatisticsCalculator`: Total Spots, Unique Models, Brands Discovered, Rare Finds, Top Brands по числу Spot, локальный календарный месяц и распределение по шести категориям.
- Rare Finds и Unique Models считаются по уникальному ID модели; месячный счётчик считает отдельные Spot с начала месяца в часовом поясе устройства до текущего момента. События из будущего не попадают в текущий месяц.
- Top Brands ограничен пятью строками; равенство количества разрешается по алфавиту имени марки, затем стабильному ID. Это визуальный лимит экрана, исходный порядок остаётся детерминированным.
- Месячная сводка обновляется при открытом Stats на границе локального месяца. Тикер действует только пока экран подписан на поток и не является фоновым сервисом.
- Stats подключён к Room и рисует метрики, топ марок и горизонтальные распределения средствами Compose Material 3 без графической зависимости.
- Добавлены unit-тесты пустого журнала, перехода месяца, записей за границей и из будущего, уникальности моделей и редких находок, категорий, а также алфавитной сортировки Top Brands.
- Файлы этапа: `domain/statistics/StatisticsCalculator.kt`, `data/repository/ProgressRepository.kt`, `RoomProgressRepository.kt`, `ui/screens/stats/`, `ui/navigation/StreetSpotNavigation.kt`, `app/src/test/java/com/streetspot/app/domain/statistics/StatisticsCalculatorTest.kt`, строки `strings.xml`.

## Проверки этапа 8

- `gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --console=plain` — `BUILD SUCCESSFUL`.
- 16 локальных unit-тестов пройдены, ошибок нет. APK этапа 8 собран: `app/build/outputs/apk/debug/app-debug.apk` (19 051 270 байт).
- Графики/сводки на Android-устройстве не запускались. После этого прогона подпись локали месяца обновлена с устаревшего конструктора `Locale` на `Locale.forLanguageTag`; повторная сборка будет выполнена на следующем этапе.

## Что сделано на этапе 9

- Добавлены Level Widget и Collection Widget примерно 2×2 на стабильном Jetpack Glance 1.2.0. Первый показывает уровень, ранг и прогресс XP; второй — число находок, марки и находки текущего месяца. Пустая коллекция показывает нулевые значения и подсказку.
- Виджеты читают те же `ProgressRepository`/Room-потоки, что и экраны. Загрузка идёт на `Dispatchers.IO`; постоянного фонового сервиса нет.
- Виджеты обновляются после успешного добавления, редактирования и удаления Spot, а также после подготовки локальных данных при старте приложения. Ошибка обновления не откатывает уже сохранённую Spot-запись.
- Нажатие Level Widget открывает Home, Collection Widget — Spotdex. Для маршрута при уже открытом приложении обрабатывается новый Intent Activity.
- По умолчанию виджеты используют Midnight JDM. Переключение на Material You подключается к DataStore вместе с настройкой на этапе 10.
- Файлы этапа: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/*_widget_info.xml`, `widget/`, `MainActivity.kt`, `StreetSpotApplication.kt`, `RoomSpotRepository.kt`, `ui/navigation/StreetSpotNavigation.kt`, `ui/theme/Theme.kt`, `strings.xml`.

## Проверки этапа 9

- `gradlew.bat --no-daemon :app:assembleDebug --console=plain` — `BUILD SUCCESSFUL`; APK этапа 9 собран локально.
- Виджеты не добавлялись на экран устройства, нажатия и обновления на Android не запускались по просьбе пользователя. Ручная проверка остаётся на финальном APK.
- Стабильная версия Glance 1.2.0 сверена с [официальными release notes](https://developer.android.com/jetpack/androidx/releases/glance); реализация сверялась с [официальным руководством по созданию Glance-виджетов](https://developer.android.com/develop/ui/compose/glance/create-app-widget) и [руководством по темам Glance](https://developer.android.com/develop/ui/compose/glance/theme).

## Что сделано на этапе 10

- Экран Settings полностью переведён на русский. DataStore Preferences 1.2.1 хранит выключенный по умолчанию выбор динамической темы; системные цвета доступны с Android 12. Текущая палитра Material 3 применяется во всех экранах, навигации и виджетах.
- Добавлены экспорт и восстановление ZIP-архива через системные `CreateDocument` и `OpenDocument`. Архив содержит версию формата, локальный каталог, Spot-записи, сохранённые даты достижений и фотографии. XP-транзакции пересчитываются по текущим правилам при восстановлении. Настройка темы остаётся локальной и в архив не входит.
- До изменения Room проверяются версия формата, контрольная сумма данных, связи Brand/Model/Spot, уникальные ID и индексы, даты, ZIP-пути, размеры файлов и SHA-256 фотографий. Экспорт также сверяет сохранённое фото с контрольной суммой Spot, чтобы не создавать заведомо невосстанавливаемый архив. Фото с GPS EXIF отклоняется. Новые фото сначала сохраняются под отдельными именами, затем все таблицы заменяются в одной Room-транзакции; при ошибке транзакции исходные таблицы и фотографии остаются на месте. Если файл фото уже отсутствовал при экспорте, запись сохраняется, а экран сообщает число недоступных фото.
- Решение по системной копии: Android Auto Backup отключён через `allowBackup=false`; extraction rules отдельно исключают cloud backup и device transfer, а legacy full-backup rules исключают данные на Android 11 и ниже. Пользовательская копия создаётся только вручную. ZIP не шифруется приложением.
- `docs/architecture.md` обновлён с учётом принятого поведения Android Backup.

## Проверки этапа 10 и финальный результат

- Команда `gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:assembleRelease --console=plain` завершилась `BUILD SUCCESSFUL`; включая `lintVitalRelease`.
- Пройдены все 16 локальных unit-тестов. Android-тесты скомпилированы, но не запускались. Приложение, виджеты, SAF-экспорт и восстановление архива на устройстве не запускались по просьбе пользователя.
- После финальной доводки повторно собраны debug и release, заново пройдены все 16 unit-тестов; Android-тестовый APK скомпилирован. Динамическая тема и сравнение контрольной суммы фото при экспорте включены в эту сборку.
- `aapt dump permissions` подтвердил отсутствие `android.permission.INTERNET`; `apksigner verify` подтвердил подпись installable debug APK. Merged manifest включает два widget receiver и `allowBackup=false` с правилами извлечения.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 24 896 480 байт, подписан стандартным Android Debug-ключом и предназначен для ручной установки/проверки.
- Release-вариант также собран: `app/build/outputs/apk/release/app-release-unsigned.apk`, 14 928 861 байт. Он не подписан ключом распространения; для проверки на устройстве используйте debug APK.
- Ручное добавление виджетов и полный сценарий «экспорт → восстановление → сверка данных/фото», включая повреждённый архив, остаются для следующей совместной проверки финальной сборки.
- Версии сверялись по [официальным release notes DataStore](https://developer.android.com/jetpack/androidx/releases/datastore), [описанию Auto Backup](https://developer.android.com/identity/data/autobackup) и [описанию `android:allowBackup`](https://developer.android.com/guide/topics/manifest/application-element).

## Launcher-иконка и установка финальной сборки — 29.10.2026

- Добавлена адаптивная launcher-иконка: фронтальный силуэт автомобиля в мятном цвете на графитовом фоне. Добавлен монохромный foreground для тематических иконок Android 13+, иконка назначена в AndroidManifest.xml.
- Файлы: app/src/main/res/drawable/ic_launcher_foreground.xml, app/src/main/res/drawable/ic_launcher_monochrome.xml, app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml, app/src/main/AndroidManifest.xml.
- :app:assembleDebug завершился BUILD SUCCESSFUL; aapt dump badging подтвердил ресурс res/mipmap-anydpi-v26/ic_launcher.xml.
- Через adb install -r APK установлен на Xiaomi M2010J19SY (Android 12 / API 31). adb shell pm path com.streetspot.app подтвердил установленный пакет. Приложение после установки не запускалось.
- Текущий debug APK: app/build/outputs/apk/debug/app-debug.apk, 24 896 733 байта.
## Продолжение на другом ПК

Проект не находится под Git-контролем. Для переноса скопируйте папку `F:/Projects/STREETSPOT` целиком или передайте все перечисленные выше файлы этапов 1 и 2 вместе с `docs/` и `AGENTS.md`. Локальные папки `.gradle/`, `.kotlin/` и `app/build/` — кэш и результаты сборки; они не требуются для исходников. Android SDK и AVD находятся вне папки проекта и автоматически на другой ПК не переносятся.

Нужны JDK 17 и Android SDK с Platform 37.0 и Build Tools 36.0.0. Wrapper сам скачает Gradle 9.5.1 при первой сборке; Android SDK-компоненты можно установить через SDK Manager. Сборка:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug --console=plain
```

Во время разработки пользователь попросил не проверять приложение на телефоне; позже он отдельно разрешил установить финальную сборку. 29.10.2026 debug APK с launcher-иконкой установлен на подключённый Xiaomi через adb install -r. Приложение после установки не запускалось; визуальная проверка остаётся для совместного сеанса.

## Переработка выбора автомобиля — 29.10.2026

- Шаг Identify теперь предлагает последовательный выбор марки и модели; результаты моделей ограничены выбранной маркой и показывают поколение и категорию STREETSPOT.
- Добавление пользовательской модели из выбранной марки автоматически привязывает её к этой марке. Если марки ещё нет, её можно добавить вместе с моделью. Редкость пользовательской модели остаётся COMMON.
- Добавлены необязательные год выпуска и тип кузова конкретной находки. Они отображаются в деталях, доступны при редактировании и хранятся в Room.
- Room переведён на схему версии 2 с миграцией 1→2. Формат резервной копии версии 2 записывает новые поля и продолжает принимать архивы версии 1.
- Firebase, профили, друзья и отдельный редактор каталога в эту работу не входили.
- По просьбе пользователя сборка и проверка на телефоне не запускались. Файл экспортированной Room-схемы `2.json` ещё не создан: он будет сформирован при следующей разрешённой сборке.

## Документация социального этапа — 29.10.2026

- По просьбе пользователя зафиксирован план необязательных аккаунтов, профилей друзей и Firebase для этапа 11. Решение сохраняет Room как источник истины личного дневника и не включает полную синхронизацию журнала.
- Добавлен `docs/social-and-firebase.md`; согласованы гостевой режим, Firebase Authentication, Firestore-проекция профиля, случайный код добавления друга, подтверждение запросов, opt-in публикация профиля/фото, закрытые Security Rules, ограничения, стоимость Storage и критерии проверки.
- Обновлены `AGENTS.md`, `docs/specification.md`, `docs/architecture.md`, `docs/design-system.md`, `docs/xp-and-achievements.md` и `docs/development-plan.md`, чтобы не оставалось конфликта с прежним решением «без аккаунтов и Firebase» для базовой версии.
- [Приватный концепт-дэшборд](https://streetspot-social-layer.brainybead32768.chatgpt.site) служит визуальным референсом экранов и решений.
- Первая контрольная точка этапа 11: по прямой команде пользователя добавлены Firebase SDK (Auth, Firestore, Storage), разрешение INTERNET, первые экраны выбора входа/регистрации/сообщества, модели и репозиторий социальных данных, локальная отметка выбора гостевого режима, Firestore/Storage Security Rules и индексы. На той точке навигация ещё не была подключена; проверки не запускались, Firebase-проект и `app/google-services.json` отсутствовали. Платный Storage не подключался.

## Локальный каталог VehiclesDB — 29.10.2026

- Встроен закреплённый VehiclesDB v2026.10.4: 315 марок, 5 541 легковая модель. Сжатый JSON-ресурс занимает 76 419 байт; запросы приложения не обращаются к сети.
- При старте `VehiclesDbCatalogSeeder` транзакционно объединяет каталог с текущими марками и моделями. Поиск учитывает псевдонимы; модельные подсказки сортируются по `mass_decile`, затем `global_decile`. Кузова используются как варианты для ручного выбора, год/поколение остаются ручными полями.
- Для совпавших локальных моделей сохраняются ID, имя, категория и редкость. Новые внешние модели получают OTHER / COMMON; popularity deciles не участвуют в XP. В доступных 18 странах нет России, поэтому приложение это явно указывает.
- В Settings добавлены кредит VehiclesDB, ссылка и просмотр полного `ATTRIBUTION.md`; в assets включены `manifest.json`, `LICENSE` и `SOURCES.md` из того же релиза.
- Room обновлён до версии 3, добавлена миграция 2→3 для стабильных ID и метаданных. Формат резервной копии стал v3 и продолжает принимать v1 и v2. Восстановление старого архива повторно дополняется локальным каталогом.
- Обновлены `docs/specification.md` и `docs/architecture.md`; детали выбора и ограничений источника записаны в `docs/vehicle-catalog.md`.
- Сборка, тесты, запуск и установка на телефон не выполнялись по текущему указанию. Схема Room `3.json` не экспортирована; работу Room migration, импорт, резервную копию и экран Settings считать непроверенными.
- Перед изменениями изучены текущая схема, DAO, импортер и резервное копирование. Вручную распакованный сжатый ресурс сверён с ожидаемыми version/count и проверен на дубли ID и имен моделей; это не подтверждает работу приложения.

## Следующий шаг

Реализация этапа 11 и её локальные проверки завершены 30.10.2026. Полное закрытие последовательности пока ожидает проверку миграции Room 2→3 и резервных архивов v1–v3, указанную как входное условие этапа 11. Production Firebase-проект не подключался, Security Rules не развёртывались, платный Storage не включался.

## Результат этапа 11 — 30.10.2026

### Реализовано ранее

- `build.gradle.kts`, `app/build.gradle.kts`: добавлены Google Services Gradle plugin `4.5.0`, Firebase BoM `35.1.0` и библиотеки Auth, Firestore, Storage. Плагин Google Services применяется только при наличии `app/google-services.json`.
- `app/src/main/AndroidManifest.xml`: добавлен INTERNET.
- Добавлен `app/src/main/java/com/streetspot/app/data/social/`: локальная отметка первого выбора и модели, Firebase-репозиторий для email/password, профиля, кода друзей, запросов, выбранных фото и удаления аккаунта.
- Добавлены `app/src/main/java/com/streetspot/app/ui/screens/social/`: экран первого выбора, экран авторизации и экран сообщества с профилем/друзьями.
- Добавлен `app/src/main/res/values/strings_social.xml`.
- `app/src/main/java/com/streetspot/app/ui/StreetSpotRoot.kt`: добавлен выбор первого маршрута и необязательная отправка сводки локального прогресса при изменении дневника/состояния входа.
- Добавлены `firebase.json`, `firestore.rules`, `storage.rules`, `firestore.indexes.json`.
- Обновлены этот файл, `docs/social-and-firebase.md` и раздел версий в `docs/architecture.md`.

### Изменено в текущем продолжении

- `ui/navigation/StreetSpotNavigation.kt`: подключены маршруты первого выбора, регистрации/входа и сообщества. Гостевой выбор сохраняется локально; после входа приложение возвращается на Home. Добавлена вкладка «Сообщество» с существующими разделами «Профиль / Друзья».
- `data/social/FirebaseSocialRepository.kt`, `SocialModels.kt`, `CommunityScreen.kt`: загрузка помечает снимок источником (сервер/кэш) и временем локальной проверки; при отключённой сети UI сохраняет старые данные, сообщает об их возможной неполноте и предлагает повторить загрузку, если снимок не получен. Удаление облачного аккаунта разбито на пакетные удаления по 150 связей, чтобы не превышать лимит записей Firestore за один пакет.
- `firestore.rules`: скрытые переключателями поля должны отсутствовать в профильном документе; принятие заявки и создание связи запрещены при активной блокировке. Края связи нельзя удалить отдельно от отзыва/удаления заявки; принятую заявку можно удалить только одновременно с обоими краями. Для опубликованной Spot ограничены типы/размеры строк и допустимый год.
- `CommunityScreen.kt`: поле пароля при удалении аккаунта скрывает ввод; принять запрос можно только после подтверждения email.
- Обновлены `strings.xml`, `strings_social.xml` и этот файл.
- Для debug без `google-services.json` создаётся только demo FirebaseApp `demo-streetspot`; Auth/Firestore/Storage подключаются к локальным эмуляторам. Для release demo-конфигурация не создаётся. `app/src/debug/AndroidManifest.xml` разрешает cleartext только в debug.
- `firebase.json` задаёт порты Auth 9099, Firestore 8080 и Storage 9199. Добавлен `tools/firebase-rules-tests/` с Auth lifecycle и Security Rules сценариями.
- `firestore.rules`: создание block-документа запрещено, пока сохраняются края дружбы; блокировка должна атомарно отзывать связь, поэтому Storage немедленно теряет доступ по edge.
- Исправлены compile blockers, обнаруженные полной сборкой: чтение `CarModelWithBrand` из suspend DAO, ссылка документа при пакетном удалении Firestore, импорт `awaitClose`, подписи `CONVERTIBLE`/`MPV` и итерация suspend `CarModelDao.update`. Последняя правка не меняет каталог или Room-схему.
- В `app/build.gradle.kts` добавлен необязательный Gradle property `streetspotDebugApplicationIdSuffix`; с `.stage11` тестовая установка использует отдельный package name и не требует удаления прежнего приложения.
- В `StreetSpotNavigation.kt` центральная кнопка `+` получила доступное описание «Новая находка», а короткие подписи вкладок (`Модели`, `Награды`, `Стат.`, `Профиль`) устранили переносы и обрезание на шести пунктах панели. Экранные заголовки остались полными.
- `res/логотип для приложения STREETSPOT.png` скопирован в `app/src/main/res/mipmap-nodpi/ic_launcher_logo.png`; в manifest `icon` и `roundIcon` указывают на этот квадратный логотип. Широкий вариант логотипа не назначался и не менялся.
- Установлены в пользовательское окружение Kotlin compiler 2.4.21, Firebase CLI 15.34.1, Node.js 24.22.2/npm 11.19.1 и Temurin JRE 21.0.13. `kotlinc` работает на JRE 17; Emulator Suite использует JRE 21. Gradle daemon по `gradle/gradle-daemon-jvm.properties` запущен на JDK 25, а target bytecode приложения остаётся JVM 17.

### Проверки и ограничения

- `gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest --console=plain` — `BUILD SUCCESSFUL`; Kotlin/Compose компиляция прошла, 16/16 локальных unit-тестов выполнены. После этого APK с отдельным package suffix собран с `-PstreetspotDebugApplicationIdSuffix=.stage11`.
- После изменения вкладок и иконки команда `gradlew.bat --no-daemon :app:assembleDebug "-PstreetspotDebugApplicationIdSuffix=.stage11" --console=plain` завершилась `BUILD SUCCESSFUL` за 5:03. Unit-тесты повторно не запускались после этих UI/resource-only изменений.
- `npm test` из `tools/firebase-rules-tests` — 10/10 сценариев прошли на Auth, Firestore и Storage Emulator с demo project. Проверены регистрация, подтверждение email, вход, сброс пароля, удаление аккаунта, приватность профиля, коды/заявки, принятие/отзыв и блокировка, доступ и ограничения фото.
- На Xiaomi `48ad224f0521` переустановка `.stage11` завершилась `Success`; `pm path` и `aapt dump badging` подтвердили пакет `com.streetspot.app.stage11` и launcher-ресурс `res/mipmap-nodpi-v4/ic_launcher_logo.png`. На телефоне проверены выбор гостя, главные разделы, переход в «Профиль»/сообщество, открытие формы новой находки и Back. Новая запись и Firebase-аккаунт не создавались. `com.streetspot.app` отсутствует в списке пакетов; пользователь подтвердил, что удалил его сам.
- Путь SDK в `local.properties` указывал на отсутствующий каталог; он заменён на фактический Android SDK path. `local.properties` — локальный игнорируемый файл, не переносить его путь на другой ПК.
- `app/google-services.json` отсутствует; Auth Emulator проверен автоматически, production-проект не подключён и Security Rules не развёртывались.
- Рабочая папка не является Git-репозиторием, поэтому автоматическое сравнение с базовым состоянием недоступно.

### Осталось до закрытия этапа 11

1. Проверить предыдущий этап 10: миграцию Room 2→3, экспорт/восстановление архива v3 и обратную совместимость v1/v2. В текущем наборе `androidTest` есть только тесты базы данных в памяти; тестов миграции и резервного архива нет, они в этой работе не запускались и не добавлялись, чтобы не расширять порученный этап.
2. Для интеграционной проверки выбрать тестовые Firebase и Supabase проекты, добавить локальный `app/google-services.json` и Supabase URL/publishable key, развернуть Firestore Rules и Edge Function. Firebase Cloud Storage и тариф Blaze не используются.

### Ограничения и сохранность данных

- Production Firebase и ручной Auth-flow на телефоне не проверялись: регистрация, подтверждение email, вход, сброс пароля и удаление аккаунта проверены тестами локального Emulator Suite. Реальные правила не развёртывались.
- Новая bitmap launcher-иконка проверена в APK через `aapt`; отдельный themed monochrome icon не назначен, хотя прежние vector-файлы оставлены в `res`.
- Входное условие из этапа 11 — проверка Room 2→3 и архивов v1–v3 — не закрыто: соответствующих migration/archive тестов в проекте нет. На этапе 11 схема Room и код архива не менялись.
- Изменение `VehiclesDbCatalogSeeder.kt` ограничено заменой ссылки на suspend DAO обычным `for`-циклом после фактической ошибки компиляции; алгоритм, данные, Room-схема и DAO не менялись.
- Не развёртывать Security Rules и не включать платёжный аккаунт без проекта и отдельного решения пользователя.
## Официальные источники версий

- [Совместимость Kotlin Gradle Plugin и Gradle/AGP](https://kotlinlang.org/docs/gradle-configure-project.html)
- [Примечания к Android Gradle Plugin](https://developer.android.com/build/releases/about-agp)
- [Настройка Compose и Compose Compiler](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
- [Официальные release notes AndroidX Navigation](https://developer.android.com/jetpack/androidx/releases/navigation)
- [Официальные release notes Room](https://developer.android.com/jetpack/androidx/releases/room)
- [Официальный репозиторий KSP и release notes](https://github.com/google/ksp/releases)
- [Официальные release notes AndroidX Test](https://developer.android.com/jetpack/androidx/releases/test)
- [Официальные release notes kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines/releases)
- [Официальные release notes AndroidX Camera](https://developer.android.com/jetpack/androidx/releases/camera)
- [Официальные release notes AndroidX ExifInterface](https://developer.android.com/jetpack/androidx/releases/exifinterface)
- [Android Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker)
- [Официальные release notes AndroidX Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle)
- [Официальные release notes AndroidX Glance](https://developer.android.com/jetpack/androidx/releases/glance)
- [Создание виджета Glance](https://developer.android.com/develop/ui/compose/glance/create-app-widget)
- [Темы Glance](https://developer.android.com/develop/ui/compose/glance/theme)
- [Официальные release notes AndroidX DataStore](https://developer.android.com/jetpack/androidx/releases/datastore)
- [Android Auto Backup](https://developer.android.com/identity/data/autobackup)
- [Требования Google Play к target API](https://developer.android.com/google/play/requirements/target-sdk)

## Решение о бесплатном фото-хранилище — 30.10.2026

Пользователь выбрал Supabase вместо Firebase Cloud Storage, чтобы оставить Firebase на Spark без платёжного аккаунта. Начаты изменения этапа 11; пользователь попросил срочно зафиксировать состояние и остановиться. Сборка и новые проверки после изменений не запускались.

### Сделано в этом продолжении

- В `FirebaseSocialRepository` удалена зависимость Firebase Storage; операции публикации, получения и удаления фото направлены через новый `SupabasePhotoStorage` в Supabase Edge Function `shared-photos`. При отключении публикации и удалении аккаунта вызывается очистка файлов и метаданных.
- Добавлен `SupabasePhotoStorage.kt`: HTTP upload с прогрессом, скачивание в приватный cacheDir, удаление, обработка ошибок и конфигурация из `local.properties` (`streetspot.supabase.url`, `streetspot.supabase.publishableKey`). BuildConfig содержит только URL и publishable key.
- Добавлен `supabase/functions/shared-photos/index.ts`: проверяет Firebase ID token подписью Google JWKS и claims; для чтения фото обращается к Firestore с тем же token, чтобы Security Rules проверили публикацию, дружбу и блокировку; приватный Storage доступен функции через серверный ключ. Upload ограничен JPEG/5 МБ, APP1 metadata (включая EXIF GPS) очищается сервером. GET не выдаёт постоянную публичную ссылку.
- Добавлены Supabase `config.toml` и миграция private bucket `shared-spot-photos` с ограничением JPEG/5 МБ. Удалены Firebase Storage SDK, emulator/rules config и `storage.rules`; Firebase Emulator Suite оставлена для Auth и Firestore.
- Обновлены README, спецификация, архитектура, план этапа 11, `docs/social-and-firebase.md`, тестовая инструкция и локальный `AGENTS.md`. В интерфейсе добавлено сообщение и отключены фото-действия, пока Supabase URL/key не настроены. `.gitignore` скрывает локальные Supabase secrets/state.

### Что осталось

1. Проверить код и конфигурацию: Kotlin/Gradle DSL, Supabase Edge Function TypeScript/REST-вызовы, SQL bucket migration и Firebase Emulator tests. Эти проверки не запускались в срочном остановленном продолжении.
2. Production/test credentials отсутствуют. Для удалённой проверки пользователь должен настроить тестовые Firebase и Supabase проекты, применить миграцию, задать `FIREBASE_PROJECT_ID`, развернуть функцию и добавить локальный URL/publishable key. Firebase Emulator token не проходит удалённую проверку функции.
3. Проверить upload/download для владельца и друга, отказ постороннему/заблокированному, revoke, EXIF, очистку всех файлов и удаление аккаунта. Не выполнять сборку без отдельной команды.
4. Ранее задокументированная проверка этапа 10 — Room 2→3 и backup archives v1–v3 — всё ещё не закрыта.

В первоначальном checkpoint файлы были сохранены напрямую в рабочей папке. Firebase Storage и Firebase billing не подключать.

## Продолжение этапа 11 — 31.10.2026

- Исправлена очистка фото при отключении публикации: с настроенным Supabase файлы удаляются даже при отсутствии Firestore metadata; без Supabase локальный demo-режим не делает пустой сетевой вызов.
- Проверены JSON-синтаксис `firebase.json` и `tools/firebase-rules-tests/package.json`, а также синтаксис `tools/firebase-rules-tests/rules.test.mjs` командой `node --check`.
- После миграции на Supabase локальные Auth/Firestore Emulator tests успешно не завершены. Запуск через установленный Firebase CLI и временный `npm exec` не выдал логов и был остановлен; поэтому текущие правила не заявляются как прошедшие тесты. `storage.rules` и Storage Emulator в актуальном наборе не используются.
- Проверку TypeScript для `supabase/functions/shared-photos/index.ts` выполнить не удалось: Deno и TypeScript compiler в доступном окружении отсутствуют; встроенный Node.js не поддерживает синтаксис Deno TypeScript. Проверка удалённой Edge Function и Supabase private bucket также требует тестовых Firebase/Supabase проектов; production credentials в проект не добавлены.
- Сборка APK, Gradle assemble и Gradle tests в этом продолжении не запускались.
- Создан локальный commit `9d1e6bd` с исходным состоянием проекта; `origin` указывает на GitHub-репозиторий `mirlo-ops/STREETSPOT`. Push на GitHub ещё не выполнялся. `.gitignore` исключает `local.properties`, `AGENTS.md`, локальные Supabase secrets, `node_modules` и build outputs.

### Следующие действия

1. В окружении с рабочим Firebase CLI запустить `npm test` из `tools/firebase-rules-tests`; отдельно проверить TypeScript Edge Function и SQL migration средствами Deno/Supabase CLI.
2. После настройки тестовых Firebase и Supabase проектов проверить upload/download, отказ постороннему и заблокированному другу, revoke, EXIF-очистку и полное удаление фото.
3. Перед закрытием последовательности этапов выполнить оставшуюся проверку этапа 10: Room 2→3 и архивы v1–v3.
