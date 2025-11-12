# Инструкции по сборке и запуску

## Предварительные требования

1. **Android Studio** (последняя версия)
   - Скачать: https://developer.android.com/studio

2. **Android SDK**
   - API Level 24 (Android 7.0) минимум
   - API Level 34 (Android 14) для целевой платформы

3. **JDK 17**
   - Обычно включен в Android Studio

## Шаг 1: Открытие проекта

1. Запустите Android Studio
2. Выберите `File` → `Open`
3. Выберите папку `/Users/core/code/AOCR-Engine-App`
4. Дождитесь синхронизации Gradle (это может занять несколько минут)

## Шаг 2: Настройка SDK

1. Откройте `File` → `Project Structure` → `SDK Location`
2. Убедитесь, что путь к Android SDK установлен правильно
3. Или отредактируйте файл `local.properties` и укажите путь к SDK:
   ```
   sdk.dir=/путь/к/вашему/Android/sdk
   ```

## Шаг 3: Сборка APK

### Вариант 1: Через Android Studio

1. В меню выберите `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
2. Дождитесь завершения сборки
3. Файл APK будет находиться в `app/build/outputs/apk/debug/app-debug.apk`
4. Нажмите на уведомление `locate` для открытия папки с APK

### Вариант 2: Через командную строку

```bash
cd /Users/core/code/AOCR-Engine-App

# Сборка debug версии
./gradlew assembleDebug

# Сборка release версии (требуется подпись)
./gradlew assembleRelease
```

APK будет находиться в:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

## Шаг 4: Установка на устройство

### Через Android Studio

1. Подключите Android устройство через USB (с включенной отладкой по USB)
2. Или запустите эмулятор Android
3. Нажмите на зеленую кнопку `Run` (или Shift+F10)
4. Выберите устройство и нажмите OK

### Через ADB (Android Debug Bridge)

```bash
# Убедитесь, что устройство подключено
adb devices

# Установка APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Или установка с перезаписью
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Ручная установка на физическом устройстве

1. Скопируйте APK файл на устройство (через USB или любым другим способом)
2. На устройстве откройте файл-менеджер
3. Найдите APK файл и нажмите на него
4. Разрешите установку из неизвестных источников, если потребуется
5. Нажмите "Установить"

## Шаг 5: Первый запуск

1. Откройте приложение "OCR Server" на устройстве
2. Приложение запросит разрешения:
   - Уведомления (для показа статуса сервера)
   - Все разрешения следует предоставить
3. Убедитесь, что устройство подключено к WiFi сети
4. Нажмите кнопку "Start Server"
5. Запишите IP адрес, отображаемый на экране

## Шаг 6: Тестирование

### Проверка статуса сервера

```bash
# Замените IP адрес на тот, что показан в приложении
curl http://192.168.1.10:8080/status
```

### Отправка изображения через HTTP

```bash
curl -X POST http://192.168.1.10:8080/ocr \
  -F "image=@test_image.jpg"
```

### Тестирование с помощью Python клиента

```bash
# Установка зависимостей
pip install requests websocket-client

# Проверка статуса
python test_client.py --host 192.168.1.10 --mode status

# Тестирование HTTP API
python test_client.py --host 192.168.1.10 --image test.jpg --mode http

# Тестирование WebSocket
python test_client.py --host 192.168.1.10 --image test.jpg --mode ws
```

### Тестирование с помощью Node.js клиента

```bash
# Установка зависимостей
npm install ws axios form-data

# Проверка статуса
node test_client.js --host 192.168.1.10 --mode status

# Тестирование HTTP API
node test_client.js --host 192.168.1.10 --image test.jpg --mode http
```

## Решение проблем

### Ошибка "SDK not found"

Отредактируйте `local.properties` и укажите правильный путь к Android SDK:

```properties
sdk.dir=/Users/ВАШ_ПОЛЬЗОВАТЕЛЬ/Library/Android/sdk
```

### Ошибка при сборке Gradle

1. Очистите проект: `./gradlew clean`
2. Удалите папки `.gradle` и `build`
3. Синхронизируйте проект заново: `./gradlew build --refresh-dependencies`

### Приложение не устанавливается

1. Удалите старую версию приложения
2. Убедитесь, что включена установка из неизвестных источников
3. Проверьте, что на устройстве достаточно места

### Сервер не запускается

1. Проверьте подключение к WiFi
2. Убедитесь, что разрешения приложению предоставлены
3. Проверьте, что порт 8080 не занят другим приложением

### Не могу подключиться к серверу с компьютера

1. Убедитесь, что компьютер и устройство в одной WiFi сети
2. Используйте IP адрес, показанный в приложении
3. Проверьте firewall на устройстве и компьютере
4. Попробуйте пинг устройства: `ping 192.168.1.10`

## Дополнительные возможности

### Создание signed APK для публикации

1. В Android Studio: `Build` → `Generate Signed Bundle / APK`
2. Выберите APK
3. Создайте новый keystore или используйте существующий
4. Заполните информацию о keystore
5. Выберите build type: release
6. Нажмите Finish

### Включение ProGuard для оптимизации

Отредактируйте `app/build.gradle.kts`:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

## Архитектура приложения

```
┌─────────────────────────────────────┐
│         Android UI (MainActivity)   │
├─────────────────────────────────────┤
│      OcrServerService (Foreground)  │
├──────────────┬──────────────────────┤
│ OcrHttpServer│  OcrWebSocketServer  │
├──────────────┴──────────────────────┤
│           OcrEngine (ML Kit)        │
└─────────────────────────────────────┘
```

## Полезные команды

```bash
# Просмотр логов
adb logcat | grep "OcrServer"

# Остановка приложения
adb shell am force-stop com.example.ocrserver

# Удаление приложения
adb uninstall com.example.ocrserver

# Просмотр установленных пакетов
adb shell pm list packages | grep ocrserver

# Очистка данных приложения
adb shell pm clear com.example.ocrserver
```

## Поддержка

Для вопросов и поддержки, смотрите README.md и документацию в проекте.

