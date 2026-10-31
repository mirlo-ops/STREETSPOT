# Социальный слой: Firebase и Supabase

## Решение и состояние

Этап 11 добавляет добровольные аккаунты, профили и друзей. Firebase Authentication и Cloud Firestore работают в бесплатном Firebase Spark; для фото выбран Supabase Storage Free, так как Firebase Storage требует Blaze. Полный журнал Room, личные заметки, подписи места и локальный архив не синхронизируются.

В Android-коде фото отправляются только в закрытый бакет Supabase `shared-spot-photos` через Edge Function `shared-photos`. Функция проверяет Firebase ID token и использует Firestore Security Rules как источник прав доступа. Приложение и этот репозиторий пока не подключены к production Firebase/Supabase проектам; поэтому удалённый upload/download и настройки реальных проектов требуют отдельной проверки после заполнения локальной конфигурации.

Для локальной проверки Auth и Firestore используются Firebase Emulator Suite на портах 9099 и 8080. Firebase Storage Emulator и `storage.rules` больше не используются. Демонстрационные Firebase Auth Emulator tokens не подходят для удалённой Edge Function; публикацию фото проверяют на тестовых Firebase и Supabase проектах.

До закрытия последовательности разработки остаётся документированная проверка этапа 10: Room 2→3 и архивы резервной копии v1–v3. Социальный слой Room-схему не меняет.

## Принятые продуктовые решения

- При первом запуске доступны «Создать аккаунт», «Войти» и «Продолжить без аккаунта». Гостевой режим не ограничивает локальный дневник и не создаёт анонимную облачную учётную запись.
- Firebase Authentication использует email и пароль. Телефон, дату рождения и другие необязательные данные не собираем.
- Добавление друзей работает по случайному коду STREETSPOT; поиск по email и публичный каталог пользователей не используются.
- Запрос дружбы требует явного принятия. Пользователь может отклонить, отменить, удалить друга, заблокировать и отключить новые запросы.
- Профиль по умолчанию закрыт. Публикация XP/уровня, достижений, моделей и фотографий настраивается отдельно; фото выбранной находки дополнительно публикуется отдельным действием.
- В облако отправляется только согласованная социальная проекция. Полная база Room, локальные фото по умолчанию, заметки, цвет, подписи места и резервная копия не синхронизируются.
- Лента, чат, публичный рейтинг, GPS-карта и распознавание автомобилей не входят в этап 11.

## Данные и архитектура

Room остаётся источником истины для личного журнала и расчётов XP/достижений. Firebase хранит Authentication, профильную проекцию, friend code, заявки/связи и метаданные опубликованных находок. Supabase хранит байты отдельно в закрытом бакете `shared-spot-photos` по пути `users/{uid}/sharedSpots/{spotId}.jpg`.

Edge Function `shared-photos` обслуживает POST (загрузка), GET (чтение) и DELETE (удаление). Для POST функция проверяет владельца, подтверждение email, `sharePhotos` в Firestore, JPEG и лимит 5 МБ, удаляет JPEG APP1 metadata, включая EXIF GPS, и загружает файл служебным ключом. Для GET функция проверяет Firebase ID token, а чтение `profiles/{ownerUid}/sharedSpots/{spotId}` от имени пользователя проходит через Firestore Rules: они требуют включённую публикацию, принятую дружбу и отсутствие блокировки. Если связь отозвана или пользователя заблокировали, Firestore сразу запрещает чтение.

Бакет остаётся private. Приложение не обращается к Storage REST API напрямую, не получает service/secret key и не использует публичные или долговременные подписанные ссылки. Edge Function отдаёт байты с `Cache-Control: private, no-store`. При отключении публикации функция удаляет все файлы владельца, затем приложение удаляет Firestore metadata. При удалении аккаунта фотографии и социальные документы очищаются до удаления Firebase Authentication пользователя; локальный дневник остаётся на устройстве.

Сетевой сбой не откатывает локальную Spot. Ошибка публикации сохраняет локальное фото и позволяет повторить действие. На другом устройстве доступны профиль и опубликованные фото, но не полный личный журнал.

## Безопасность и конфигурация

- Firestore Security Rules закрыты по умолчанию и ограничивают чтение профилей и метаданных принятыми друзьями. Эмуляторные проверки охватывают гостя, владельца, друга, постороннего и блокировку.
- На Edge Function выключена встроенная проверка Supabase JWT (`verify_jwt = false`), потому что клиент передаёт Firebase ID token. Функция сама проверяет RS256 подпись по Google Secure Token JWKS, `iss`, `aud`, `exp`, `iat` и `sub`; затем запрашивает Firestore с тем же Firebase token. Все операции с Storage используют только серверный service key.
- Supabase publishable key и URL в Android не являются секретом. Supabase service/secret key и `FIREBASE_PROJECT_ID` хранятся в секретах Edge Function. Не добавляйте их в APK, `local.properties` или Git.
- Bucket private, лимит 5 МБ и MIME `image/jpeg`; прямых клиентских политик Storage нет. Удаление использует Storage API, а не прямую правку `storage.objects`.
- Android готовит JPEG без GPS EXIF; Edge Function дополнительно удаляет JPEG APP1 metadata перед сохранением.
- App Check можно рассмотреть перед выпуском, но он не заменяет Firebase Rules и проверку токена в Edge Function.
- До публичного выпуска подготовить описание публикуемых данных, согласие, условия сервиса и политику конфиденциальности.

Для локальной сборки добавьте в уже существующий `local.properties`:

```properties
streetspot.supabase.url=https://<project-ref>.supabase.co
streetspot.supabase.publishableKey=<publishable-key>
```

В Firebase Console включите Email/Password Authentication и Firestore. В `app/google-services.json` должен быть тот Firebase project ID, который указан секретом `FIREBASE_PROJECT_ID` в Supabase. В Supabase примените миграцию bucket и разверните функцию по инструкциям в корневом `README.md`.

## Бесплатные лимиты и ограничения

Firebase Cloud Storage не применяется: для него требуется тариф Blaze даже при использовании бесплатного объёма. Firebase Auth и Firestore остаются на Spark. Изображения используют бесплатный тариф Supabase: по текущей документации это 1 ГБ файлового хранилища и 10 ГБ трафика в месяц (5 ГБ cached и 5 ГБ uncached). Бесплатные проекты Supabase могут автоматически приостанавливаться после недели низкой активности. Квоты и условия могут измениться; перед публикацией проверьте текущие тарифы и поведение при превышении лимитов.

## Проверяемые критерии этапа 11

- Гость может без сети добавлять, редактировать и просматривать Spot, коллекцию, XP и достижения.
- Вход, выход, сброс доступа и ошибки Firebase не меняют локальный журнал.
- До согласия профиль и его блоки закрыты; настройки публикации независимы.
- Принятие/отзыв дружбы и блокировка немедленно обновляют доступ к метаданным и фото через Firestore Rules.
- Для загрузки фото пользователь явно выбирает находку; фото до 5 МБ загружается с прогрессом, без GPS EXIF; локальная запись сохраняется при сетевой ошибке.
- В бакет нельзя обращаться публично; сервисный ключ не попадает в приложение; GET Edge Function отказывает владельцу не-другу, ожидающему запрос и заблокированному пользователю.
- Удаление облачного аккаунта очищает связи, заявки, метаданные и файлы; локальный дневник остаётся нетронутым.
- Firebase Emulator Suite покрывает Auth и Firestore Rules. Edge Function, приватный bucket, EXIF, смена аккаунта, удаление и работа без сети должны быть проверены на тестовых проектах и Android-устройстве.

## Официальные источники

- [Firebase Authentication для Android](https://firebase.google.com/docs/auth/android/start)
- [Firestore Security Rules](https://firebase.google.com/docs/firestore/security/get-started)
- [Проверка Firestore Rules через Emulator](https://firebase.google.com/docs/firestore/security/test-rules-emulator)
- [Требование Blaze для Cloud Storage for Firebase](https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024)
- [Тарифы Supabase](https://supabase.com/pricing)
- [Пауза бесплатных проектов Supabase](https://supabase.com/docs/guides/platform/free-project-pausing)
- [Закрытые и публичные Storage buckets](https://supabase.com/docs/guides/storage/buckets/fundamentals)
- [Загрузка и ограничения Storage buckets](https://supabase.com/docs/guides/storage/buckets/creating-buckets)
- [Проверка Edge Functions и авторизация](https://supabase.com/docs/guides/functions/auth)
- [Секреты Supabase Edge Functions](https://supabase.com/docs/guides/functions/secrets)
- [Развёртывание Edge Functions](https://supabase.com/docs/guides/functions/deploy)
- [Удаление объектов Supabase Storage](https://supabase.com/docs/guides/storage/management/delete-objects)
