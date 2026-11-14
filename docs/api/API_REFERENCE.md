# 📡 API Reference — Deep Reader Backend

> **Формат данных:** `application/json; charset=UTF-8`  
> **Имена полей:** `snake_case`  
> **Локальный хост:** `http://localhost:8080` (для Android Emulator: `http://10.0.2.2:8080`)  
> **Production хост:** `https://api.deepreader.app`

---

## 1. POST /api/v1/explain — Разбор фразы через AI

Интеллектуальный литературный разбор выделенного фрагмента с учётом контекста книги. Результат кэшируется на 30 дней.

### Параметры запроса:
- **`book_id`** *(строка, **обязательный**)* — ID книги или SHA-256 хэш файла EPUB.
- **`selected_text`** *(строка, **обязательный**, до 500 символов)* — выделенная фраза для разбора.
- **`current_sentence`** *(строка, **обязательный**, до 2000 символов)* — предложение, внутри которого находится фраза.
- **`surrounding_context`** *(объект, **обязательный**)*:
  - `sentences_before` *(массив строк, опционально)* — до 2 предложений до (с текущего экрана).
  - `sentences_after` *(массив строк, опционально)* — до 2 предложений после (только видимый экран, без спойлеров!).
  - `paragraph` *(строка, опционально)* — текущий абзац книги (до 4000 символов).
  - `chapter_context` *(строка, опционально)* — название или тема главы (до 2000 символов).
- **`target_language`** *(строка, опционально, по умолчанию `"ru"`)* — язык ответа (`"ru"`, `"en"` и т.д.).

### Пример запроса:
```json
{
  "book_id": "1984-george-orwell",
  "selected_text": "clocks were striking thirteen",
  "current_sentence": "It was a bright cold day in April, and the clocks were striking thirteen.",
  "surrounding_context": {
    "sentences_before": ["The hallway smelt of boiled cabbage and old rag mats."],
    "sentences_after": ["Winston Smith slipped quickly through the glass doors."],
    "paragraph": "It was a bright cold day in April, and the clocks were striking thirteen.",
    "chapter_context": "Chapter 1"
  },
  "target_language": "ru"
}
```

### Пример ответа (`200 OK`):
```json
{
  "selected_text": "clocks were striking thirteen",
  "contextual_meaning": "Указание на антиутопическую искажённую реальность и 24-часовой формат времени.",
  "phenomenon_type": "metaphor",
  "explanation": "В реальности механические настенные часы отбивают максимум 12 ударов. Удар тринадцатого часа погружает читателя в тревожный мир тоталитарного контроля, где нарушены даже привычные законы природы.",
  "stylistic_coloring": "literary",
  "spoiler_alert": false,
  "cached": false,
  "processing_time_ms": 1120
}
```

---

## 2. POST /api/v1/translate — Контекстный перевод

Быстрый перевод текста через DeepL API. Результат кэшируется на 90 дней.

### Параметры запроса:
- **`text`** *(строка, **обязательный**, до 5000 символов)* — текст для перевода.
- **`target_language`** *(строка, **обязательный**)* — код языка перевода (`"ru"`, `"en"` и т.д.).
- **`source_language`** *(строка, опционально)* — код языка оригинала. Если не передан (`null`), язык определяется автоматически.

### Пример запроса:
```json
{
  "text": "clocks were striking thirteen",
  "source_language": "en",
  "target_language": "ru"
}
```

### Пример ответа (`200 OK`):
```json
{
  "translated_text": "часы били тринадцать",
  "source_language": "en",
  "target_language": "ru",
  "cached": true
}
```

---

## 3. GET /api/v1/health — Проверка состояния сервиса

Параметры отсутствуют.

### Пример ответа (`200 OK`):
```json
{
  "status": "UP",
  "service": "deep-reader-backend",
  "timestamp": "2026-11-13T12:00:00Z",
  "cache": "CONNECTED",
  "active_llm_provider": "OpenAiAdapter (gpt-4o-mini)"
}
```

---

## 4. Ошибки (`ErrorResponse`)

Все ошибки (4xx и 5xx) возвращаются в едином формате:
```json
{
  "error_code": "INVALID_INPUT",
  "message": "Ошибка валидации входных данных",
  "timestamp": "2026-11-13T12:00:05Z",
  "trace_id": "8a3d1f4b-7c9e-4a12-b345-0d9e8f7a6b5c",
  "details": [
    { "field": "selected_text", "issue": "Необходимо указать выделенный текст" }
  ]
}
```

### Коды ошибок и статусы:
| HTTP-код | `error_code` | Описание |
| :---: | :--- | :--- |
| **`400`** | `INVALID_INPUT` | Ошибка валидации полей (пустой текст, невалидный JSON, длина > лимита) |
| **`400`** | `TEXT_TOO_LONG` | Выделенный фрагмент вместе с текущим предложением превышает 1000 токенов |
| **`422`** | `EMPTY_AI_RESPONSE` | Нейросеть вернула пустой ответ или сбой генерации (безопасно повторить) |
| **`502`** | `LLM_PROVIDER_ERROR` | Сбой OpenAI API (5xx, квоты) или сработал предохранитель Circuit Breaker |
| **`502`** | `TRANSLATION_FAILED` | Сбой сервиса DeepL или сработал предохранитель Circuit Breaker |
| **`504`** | `EXTERNAL_PROVIDER_TIMEOUT` | Превышен таймаут ответа провайдера (>12с для LLM, >3с для DeepL) |
| **`500`** | `INTERNAL_ERROR` | Внутренняя непредвиденная ошибка сервера |

---

## 5. Справочники Enum для UI

- **`phenomenon_type`:** `idiom` (идиома), `slang` (сленг), `metaphor` (метафора), `irony` (ирония), `cultural_reference` (культурный код), `allusion` (аллюзия), `archaic` (архаизм), `neologism` (неологизм), `literal_phrase` (прямой смысл).
- **`stylistic_coloring`:** `neutral` (нейтральный), `informal` (разговорный), `slang` (сленговый), `rude` (грубый), `sarcastic` (сарказм), `ironic` (ирония), `literary` (высокий стиль), `poetic` (поэтический), `dramatic` (драматический).
