# Поддержка русского языка через Tesseract OCR

## Обзор

В приложении реализована поддержка русского языка через **Tesseract OCR** - один из лучших open-source движков для распознавания кириллицы. Русский текст распознаётся значительно лучше через Tesseract, чем через ML Kit Latin recognizer.

## Ключевые особенности

- **Высокая точность** для кириллицы
- **Автоматическая инициализация** языковых данных при первом использовании
- **Кэширование** обученных моделей для быстрой работы
- **Seamless интеграция** с существующим API

## Использование

### В приложении

1. Запустите приложение
2. В выпадающем списке "OCR Language" выберите **"Russian (Русский)"**
3. Настройки автоматически сохранятся
4. Нажмите "Start Server"
5. Все последующие запросы будут использовать Tesseract для распознавания русского текста

### Через API

#### HTTP POST запрос

```bash
curl -X POST http://192.168.31.119:8080/ocr \
  -F "image=@russian_text.jpg" \
  -F "language=rus"
```

#### WebSocket

```javascript
const ws = new WebSocket('ws://192.168.31.119:8081/ws');

ws.onopen = () => {
  ws.send(JSON.stringify({
    type: 'ocr',
    image: 'data:image/jpeg;base64,...',
    language: 'rus'
  }));
};
```

#### Python клиент

```bash
python test_client.py \
  --host 192.168.31.119 \
  --port 8080 \
  --image russian_text.jpg \
  --language rus \
  --mode http
```

## Технические детали

### Языковые данные

Языковые данные Tesseract (файл `rus.traineddata`) автоматически загружаются из assets приложения при первом использовании и кэшируются в директории `app_files/tessdata/`.

Размер файла: ~19 MB

### Производительность

- **Первый запрос**: ~1-3 секунды (инициализация + распознавание)
- **Последующие запросы**: ~0.3-1 секунда (только распознавание)

### Уверенность (confidence)

Tesseract возвращает `meanConfidence` от 0 до 100, которое нормализуется к диапазону 0.0-1.0 для консистентности с ML Kit.

## Отличия от ML Kit

| Характеристика | ML Kit Latin | Tesseract Russian |
|---|---|---|
| Точность для кириллицы | Средняя | Высокая |
| Скорость | Быстрее | Немного медленнее |
| Размер модели | ~10 MB | ~19 MB |
| Поддержка блоков | Да | Нет (в текущей реализации) |
| Offline работа | Да | Да |

## Поддерживаемые языки

### Через Tesseract
- `rus` - Русский

### Через ML Kit
- `eng` - Английский
- `chinese` - Китайский
- `japanese` - Японский
- `korean` - Корейский
- `devanagari` - Деванагари

## Добавление других языков Tesseract

Для добавления других языков Tesseract:

1. Скачайте `.traineddata` файл для нужного языка:
   ```bash
   curl -L -o app/src/main/assets/tessdata/deu.traineddata \
     https://github.com/tesseract-ocr/tessdata/raw/main/deu.traineddata
   ```

2. Добавьте язык в `OcrLanguage.kt`:
   ```kotlin
   GERMAN("deu", "German (Deutsch)", "deu")
   ```

3. Пересоберите приложение

## Troubleshooting

### "Tesseract initialization failed"

**Причина**: Не найден файл `rus.traineddata` в assets

**Решение**: Убедитесь что файл `app/src/main/assets/tessdata/rus.traineddata` существует и пересоберите APK

### Медленное распознавание

**Причина**: Большое изображение

**Решение**: Изображения автоматически масштабируются до 2048px, но можно уменьшить лимит в `ImageUtils.kt`

### Низкая точность

**Причина**: Плохое качество изображения или неправильный язык

**Решение**: 
- Используйте четкие изображения с хорошим контрастом
- Убедитесь что выбран правильный язык (`rus` для русского)
- Проверьте что текст не слишком мелкий

## Ресурсы

- [Tesseract GitHub](https://github.com/tesseract-ocr/tesseract)
- [Tesseract Trained Data](https://github.com/tesseract-ocr/tessdata)
- [Tess-Two Android Wrapper](https://github.com/rmtheis/tess-two)

## Лицензия языковых данных

Языковые данные Tesseract распространяются под лицензией Apache 2.0.

