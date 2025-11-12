# AOCR Engine App

Android OCR Server приложение для распознавания текста с изображений через Google ML Kit.

## Возможности

- 📱 **Android приложение** с современным UI на Material Design 3
- 🚀 **HTTP REST API** для загрузки изображений и получения распознанного текста
- ⚡ **WebSocket сервер** для real-time стриминга результатов OCR
- 🔤 **Поддержка языков**: Английский (ML Kit), Русский (Tesseract OCR), Китайский, Японский, Корейский, Деванагари
- 🔒 **Архитектура с заделом под аутентификацию**
- 📊 **Мониторинг запросов** и активных WebSocket соединений
- 🔔 **Foreground Service** для стабильной работы в фоне

## Технологии

- **Kotlin** - язык программирования
- **Google ML Kit** - OCR движок для английского и других языков
- **Tesseract OCR** - специализированный движок для русского языка с высокой точностью
- **NanoHTTPD** - легковесный HTTP сервер
- **Java-WebSocket** - WebSocket сервер
- **Material Design 3** - современный UI
- **Coroutines** - асинхронная обработка

## Требования

- Android 7.0 (API 24) или выше
- Target SDK: Android 14 (API 34)

## Build

### For mac

```bash
export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH" && export JAVA_HOME="/opt/homebrew/opt/openjdk@17" && export ANDROID_HOME=/Users/core/Library/Android/sdk && ./gradlew assembleDebug
```

```bash
adb install -s serial_number app-debug.apk
```

## Установка

1. Откройте проект в Android Studio
2. Синхронизируйте Gradle файлы
3. Соберите и установите APK на устройство

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Использование

### Запуск сервера

1. Откройте приложение на Android устройстве
2. Нажмите кнопку "Start Server"
3. Запишите IP адрес и порт сервера (отображается на экране)
4. Убедитесь, что устройство и компьютер находятся в одной WiFi сети

### HTTP API

#### Распознавание текста (POST /ocr)

**Отправка файла:**
```bash
curl -X POST http://192.168.1.10:8080/ocr \
  -F "image=@photo.jpg"
```

**Отправка файла с указанием языка (русский):**
```bash
curl -X POST http://192.168.1.10:8080/ocr \
  -F "image=@photo.jpg" \
  -F "language=rus"
```

**Поддерживаемые языковые коды:**
- `eng` - английский (ML Kit)
- `rus` - русский (Tesseract OCR - высокая точность для кириллицы)
- `auto` - автоопределение
- `chinese` - китайский
- `japanese` - японский
- `korean` - корейский
- `devanagari` - деванагари

**Отправка base64:**
```bash
curl -X POST http://192.168.1.10:8080/ocr \
  -H "Content-Type: application/json" \
  -d '{"image": "data:image/jpeg;base64,/9j/4AAQ..."}'
```

**Ответ:**
```json
{
  "success": true,
  "text": "Распознанный текст",
  "confidence": 0.95,
  "language": "ru",
  "processingTimeMs": 245,
  "blocks": [
    {
      "text": "Распознанный текст",
      "boundingBox": {
        "left": 10,
        "top": 20,
        "right": 200,
        "bottom": 50
      }
    }
  ]
}
```

#### Проверка статуса (GET /status)

```bash
curl http://192.168.1.10:8080/status
```

**Ответ:**
```json
{
  "status": "running",
  "uptime": 123456,
  "requestCount": 42,
  "port": 8080,
  "ocrEngine": "Google ML Kit"
}
```

#### Health check (GET /health)

```bash
curl http://192.168.1.10:8080/health
```

### WebSocket API

#### Подключение и отправка изображения

```javascript
const ws = new WebSocket('ws://192.168.1.10:8080/ws');

ws.onopen = () => {
  console.log('Connected to OCR server');
  
  // Отправка изображения для распознавания
  ws.send(JSON.stringify({
    type: 'ocr',
    image: base64ImageData
  }));
};

ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  
  switch(data.type) {
    case 'status':
      console.log('Status:', data.message);
      break;
    case 'progress':
      console.log('Progress:', data.progress + '%');
      break;
    case 'result':
      console.log('OCR Result:', data.text);
      console.log('Language:', data.language);
      console.log('Confidence:', data.confidence);
      break;
    case 'error':
      console.error('Error:', data.error);
      break;
  }
};

ws.onerror = (error) => {
  console.error('WebSocket error:', error);
};

ws.onclose = () => {
  console.log('Disconnected from server');
};
```

#### Ping/Pong

```javascript
// Отправка ping
ws.send(JSON.stringify({ type: 'ping' }));

// Получение pong
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  if (data.type === 'pong') {
    console.log('Server alive at', data.timestamp);
  }
};
```

## Аутентификация (будущая функциональность)

Приложение имеет заготовку под аутентификацию через `AuthMiddleware`. Для включения:

1. В коде активируйте middleware:
```kotlin
AuthMiddleware.setAuthEnabled(true)
AuthMiddleware.setAuthToken("your-secret-token")
```

2. При запросах добавляйте заголовок:
```bash
curl -X POST http://192.168.1.10:8080/ocr \
  -H "Authorization: Bearer your-secret-token" \
  -F "image=@photo.jpg"
```

## Архитектура

```
app/src/main/java/com/example/ocrserver/
├── MainActivity.kt                      # Главная Activity с UI
├── ocr/
│   ├── OcrEngine.kt                    # OCR движок на базе ML Kit
│   └── OcrResult.kt                    # Модели данных результатов
├── server/
│   ├── OcrHttpServer.kt                # HTTP REST API сервер
│   ├── OcrWebSocketServer.kt           # WebSocket сервер
│   └── RequestLog.kt                   # Модель логов запросов
├── service/
│   └── OcrServerService.kt             # Foreground Service
├── auth/
│   └── AuthMiddleware.kt               # Middleware аутентификации
├── utils/
│   ├── NetworkUtils.kt                 # Утилиты для работы с сетью
│   └── ImageUtils.kt                   # Утилиты для работы с изображениями
└── ui/
    └── RequestLogAdapter.kt            # Адаптер для списка логов
```

## Разрешения

Приложение запрашивает следующие разрешения:
- `INTERNET` - для работы HTTP/WebSocket сервера
- `ACCESS_NETWORK_STATE` - для определения сетевого статуса
- `FOREGROUND_SERVICE` - для работы в фоне
- `POST_NOTIFICATIONS` - для показа уведомлений (Android 13+)
- `WAKE_LOCK` - для предотвращения засыпания устройства

## Тестовые клиенты

В проекте есть готовые тестовые клиенты для проверки OCR сервера.

### Python клиент

```bash
# Проверка статуса сервера
python test_client.py --host 192.168.1.10 --port 8080 --mode status

# HTTP API (английский)
python test_client.py --host 192.168.1.10 --port 8080 --image photo.jpg --mode http

# HTTP API (русский) - Tesseract OCR
python test_client.py --host 192.168.1.10 --port 8080 --image photo.jpg --language rus --mode http

# WebSocket API (русский)
python test_client.py --host 192.168.1.10 --port 8080 --image photo.jpg --language rus --mode ws

# Тестирование обоих API сразу
python test_client.py --host 192.168.1.10 --port 8080 --image photo.jpg --language rus --mode both
```

### JavaScript/Node.js клиент

```bash
# Установка зависимостей
npm install ws axios form-data

# Проверка статуса
node test_client.js --host 192.168.1.10 --port 8080 --mode status

# HTTP API (русский)
node test_client.js --host 192.168.1.10 --port 8080 --image photo.jpg --language rus --mode http

# WebSocket API (русский)
node test_client.js --host 192.168.1.10 --port 8080 --image photo.jpg --language rus --mode ws
```

## Troubleshooting

### Сервер не запускается
- Проверьте, что устройство подключено к WiFi
- Убедитесь, что порты 8080 и 8081 не заняты другим приложением
- Проверьте разрешения приложения в настройках Android

### Не могу подключиться с компьютера
- Убедитесь, что устройство и компьютер в одной сети
- Проверьте firewall на устройстве
- Попробуйте использовать IP адрес, отображаемый в приложении

### OCR не распознает текст
- Убедитесь, что изображение четкое и текст читаемый
- Для русского текста выберите язык "Russian (Русский)" в приложении или укажите `language=rus` в запросе
- Попробуйте изображение меньшего размера (автоматически изменяется до 2048px)

## Лицензия

MIT License - см. файл LICENSE

