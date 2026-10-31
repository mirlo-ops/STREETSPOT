# Проверка Firebase для этапа 11

Тесты используют только демонстрационный проект `demo-streetspot` и локальные Auth и Firestore Emulator. Firebase login, реальный Firebase-проект и платёжный аккаунт не нужны. Фото хранятся в Supabase; проверки Edge Function и private bucket требуют отдельного тестового Firebase/Supabase проекта и не входят в этот набор.

## Требования

- Node.js 24 LTS и npm.
- Firebase CLI 15.34.1 или новее в `PATH`.
- Java Runtime 21 или новее в `PATH`.

## Запуск

Из этой папки выполните:

```powershell
npm install
npm test
```

CLI запускает Emulator Suite, выполняет сценарии из `rules.test.mjs` и останавливает эмуляторы после завершения. Firebase Emulator Suite загружает свои исполняемые файлы при первом запуске.
