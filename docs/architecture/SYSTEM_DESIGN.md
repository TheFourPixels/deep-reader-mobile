# 📐 Системный дизайн и архитектура сервиса

Сервис **Deep Reader Backend** спроектирован по каноническим принципам **Гексагональной архитектуры (Ports & Adapters)** и методологии **The Twelve-Factor App**.

Главная цель архитектуры — полная независимость ядра бизнес-логики от внешних фреймворков, баз данных и провайдеров искусственного интеллекта, а также обеспечение максимальной надежности (High Availability) при интеграции с нестабильными внешними облачными API (OpenAI, DeepL).

---

## 1. Общая компонентная диаграмма (Mermaid Flowchart)

```mermaid
flowchart TD
    Client["📱 Mobile App (Android / Compose)"]

    subgraph INBOUND["Адаптеры входящего трафика (Inbound Adapters)"]
        CtrlExplain["ExplainController\n(POST /api/v1/explain)"]
        CtrlTranslate["TranslateController\n(POST /api/v1/translate)"]
        CtrlHealth["SystemController\n(GET /api/v1/health)"]
        ExcHandler["GlobalExceptionHandler\n(Mapping to ErrorResponse)"]
    end

    subgraph APPLICATION["Слой приложения (Application Services)"]
        UCExplain["ExplainUseCaseImpl"]
        UCTranslate["TranslateUseCaseImpl"]
    end

    subgraph DOMAIN["Доменное ядро (Domain Core - Pure Java)"]
        TruncService["ContextTruncationService\n(BPE Tokenizer: jtokkit)"]
        KeyGen["CacheKeyGenerator\n(SHA-256 + Canonical NFKC)"]
        DomainModels["Доменные модели\n(Records & Exceptions)"]
    end

    subgraph OUTBOUND_CACHE["Адаптеры двухуровневого кэша"]
        L1["L1: CaffeineCacheAdapter\n(In-Memory JVM LRU)"]
        L2["L2: PostgresCacheAdapter\n(PostgreSQL UNLOGGED)"]
    end

    subgraph OUTBOUND_EXTERNAL["Адаптеры внешних AI-провайдеров"]
        subgraph RESILIENCE["Resilience4j Layer"]
            CB["Circuit Breakers\n(openai / deepl)"]
            Retry["Retries with Jitter\n(Exponential Backoff)"]
        end
        OpenAIAdapter["OpenAiLlmAdapter\n(gpt-4o-mini, JSON mode, <=12s)"]
        DeepLAdapter["DeepLTranslationAdapter\n(Auto-host, <=3s)"]
    end

    subgraph EXTERNAL_APIS["Внешние облачные API"]
        OpenAI["OpenAI API / Local vLLM"]
        DeepL["DeepL API (Free / Pro)"]
        Postgres[(PostgreSQL 16)]
    end

    Client -->|JSON / REST| CtrlExplain
    Client -->|JSON / REST| CtrlTranslate
    Client -->|JSON / REST| CtrlHealth

    CtrlExplain --> UCExplain
    CtrlTranslate --> UCTranslate

    UCExplain --> TruncService
    UCExplain --> KeyGen
    UCExplain --> L1
    UCExplain --> CB
    UCTranslate --> KeyGen
    UCTranslate --> L1
    UCTranslate --> CB

    L1 --> L2
    L2 --> Postgres

    CB --> Retry
    Retry --> OpenAIAdapter
    Retry --> DeepLAdapter

    OpenAIAdapter --> OpenAI
    DeepLAdapter --> DeepL
```

---

## 2. Sequence-диаграмма обработки запроса `POST /api/v1/explain`

Диаграмма иллюстрирует сквозной жизненный цикл обработки запроса литературного анализа: от проверки ограничений токенов до двухуровневого кэширования и вызова нейросети.

```mermaid
sequenceDiagram
    autonumber
    actor Client as 📱 Mobile Client
    participant Controller as ExplainController
    participant UseCase as ExplainUseCaseImpl
    participant Trunc as ContextTruncationService
    participant KeyGen as CacheKeyGenerator
    participant L1 as Caffeine L1 Cache
    participant L2 as Postgres L2 Cache
    participant Resilience as Resilience4j (CB + Retry)
    participant LLM as OpenAI (gpt-4o-mini)
    participant Metrics as DeepReaderMetrics

    Client->>Controller: POST /api/v1/explain (ExplainRequest)
    Controller->>UseCase: explain(domainRequest)
    
    %% Шаг 1: Токенизация и обрезка
    UseCase->>Trunc: truncate(request)
    alt Ядро > 1000 токенов
        Trunc-->>UseCase: throw TextTooLongException
        UseCase-->>Controller: TextTooLongException
        Controller-->>Client: 400 Bad Request (TEXT_TOO_LONG)
    else Контекст усечен до <= 1000 токенов
        Trunc-->>UseCase: truncatedRequest
    end

    %% Шаг 2: Генерация ключа
    UseCase->>KeyGen: forExplain(truncatedRequest)
    KeyGen-->>UseCase: sha256Key (64 hex chars)

    %% Шаг 3: Проверка кэша
    UseCase->>L1: get(sha256Key, "explain")
    alt L1 Hit (<1ms)
        L1->>Metrics: ai.cache.requests.total{action="explain", level="L1", result="hit"}
        L1-->>UseCase: cachedJson
        UseCase-->>Controller: ExplainResponseDomain (cached=true)
        Controller-->>Client: 200 OK (cached=true, processing_time_ms < 15)
    else L1 Miss
        L1->>Metrics: ai.cache.requests.total{action="explain", level="L1", result="miss"}
        L1->>L2: get(sha256Key)
        alt L2 Hit (<15ms)
            L2->>Metrics: ai.cache.requests.total{action="explain", level="L2", result="hit"}
            L2-->>L1: cachedJson (Promo to L1)
            L1-->>UseCase: cachedJson
            UseCase-->>Controller: ExplainResponseDomain (cached=true)
            Controller-->>Client: 200 OK (cached=true)
        else L2 Miss
            L2->>Metrics: ai.cache.requests.total{action="explain", level="L2", result="miss"}
            
            %% Шаг 4: Вызов LLM через Resilience
            UseCase->>Resilience: executeSupplier()
            Resilience->>LLM: POST /chat/completions (JSON Mode, timeout=12s)
            alt Таймаут > 12s
                LLM-->>Resilience: SocketTimeoutException
                Resilience-->>UseCase: ProviderTimeoutException
                UseCase-->>Controller: ProviderTimeoutException
                Controller-->>Client: 504 Gateway Timeout (EXTERNAL_PROVIDER_TIMEOUT)
            else Успешный ответ LLM
                LLM-->>Resilience: ChatResponse (choices, usage)
                Resilience-->>UseCase: parsedExplainDomain
                
                %% Шаг 5: Сохранение в кэш
                UseCase->>L1: put(sha256Key, json, EXPLAIN, TTL=30d)
                L1->>L2: put(sha256Key, json, EXPLAIN, TTL=30d)
                UseCase->>Metrics: recordLlmDuration, recordTokens
                UseCase-->>Controller: ExplainResponseDomain (cached=false)
                Controller-->>Client: 200 OK (cached=false, processing_time_ms=1200)
            end
        end
    end
```

---

## 3. Алгоритм ступенчатой обрезки контекста (`ContextTruncationService`)

Для оптимизации затрат на токены и предотвращения переполнения контекстного окна модели установлен жесткий лимит: **не более 1000 BPE-токенов** на суммарный промпт (FROZEN SPEC: [`docs/specs/03_ai_logic.md`](../specs/03_ai_logic.md) §1).

Токенизация выполняется локально на CPU без внешних вызовов с использованием библиотеки `com.knuddels:jtokkit` (кодировка `O200K_BASE` для моделей семейства GPT-4o / `CL100K_BASE`). При вычислении доступного бюджета для литературного контекста сервис автоматически вычитает объем токенов `PromptTemplates.SYSTEM_PROMPT`.

```mermaid
flowchart TD
    Start["Входной контекст книги"] --> CheckCore{"Ядро (selected + current) > maxContextTokens?"}
    CheckCore -- "Да" --> ThrowError["Бросить TextTooLongException\n(HTTP 400 Bad Request)"]
    
    CheckCore -- "Нет" --> CalcTotal["Подсчет токенов полного контекста"]
    CalcTotal --> CheckFit{"Токенов <= maxContextTokens?"}
    CheckFit -- "Да" --> ReturnOrig["Отправка без изменений"]
    
    CheckFit -- "Нет" --> Step1["Шаг 1 (Уровень 1): Удалить chapter_context"]
    Step1 --> CheckAfterStep1{"Токенов <= бюджет?"}
    CheckAfterStep1 -- "Да" --> ReturnS1["Возврат усеченного контекста"]
    
    CheckAfterStep1 -- "Нет" --> Step2["Шаг 2 (Уровень 2): Поочередно удалять дальние предложения\n(before[0] -> after[1] -> after[0] -> before[1])"]
    Step2 --> CheckAfterStep2{"Токенов <= бюджет?"}
    CheckAfterStep2 -- "Да" --> ReturnS2["Возврат усеченного контекста"]
    
    CheckAfterStep2 -- "Нет" --> Step3["Шаг 3 (Уровень 3): Удалить paragraph\n(остается только current_sentence)"]
    Step3 --> ReturnS3["Возврат минимального ядра"]
```

### Пошаговая деградация:
- **Неприкосновенное ядро (Inviolable Core):** `selected_text` и `current_sentence`. Это смысловой центр запроса. Если даже они превышают бюджет токенов, запрос немедленно отклоняется с ошибкой `TEXT_TOO_LONG`.
- **Шаг 1 (Уровень 1):** Удаление заголовка и аннотации главы (`chapter_context`).
- **Шаг 2 (Уровень 2):** Последовательное отбрасывание наиболее удаленных предложений видимой области в строгом порядке:
  1. `sentences_before[0]` (если передано 2 предложения до)
  2. `sentences_after[1]` (если передано 2 предложения после)
  3. `sentences_after[0]`
  4. `sentences_before[1]`
- **Шаг 3 (Уровень 3):** Удаление окружающего текста абзаца (`paragraph`). Остаётся гарантированно укладывающийся в бюджет срез (неприкосновенное ядро).

---

## 4. Политики отказоустойчивости (Resilience4j)

Конфигурация задана в [`ResilienceConfig`](../../src/main/java/com/teamfourpixels/deepreaderbackend/infrastructure/config/ResilienceConfig.java) и [application.yaml](../../src/main/resources/application.yaml).

| Параметр политики | OpenAI (`gpt-4o-mini`) | DeepL API (`translate`) | Обоснование |
| :--- | :--- | :--- | :--- |
| **Строгий HTTP-таймаут** | **12 секунд** | **3 секунды** | Предотвращает зависание потоков сервлета Tomcat. |
| **Размер скользящего окна (Circuit Breaker)** | 10 вызовов | 10 вызовов | Быстрая реакция на деградацию провайдера. |
| **Минимум вызовов для анализа** | 5 | 5 | Защита от ложных срабатываний при прогреве. |
| **Порог процента сбоев (Failure Rate)** | 50% | 50% | Размыкание цепи при половине сбойных ответов. |
| **Время в состоянии OPEN** | **15 секунд** | **10 секунд** | Пауза для восстановления внешнего сервиса. |
| **Пробных вызовов в HALF_OPEN** | 3 | 3 | Плавная проверка доступности сервиса. |
| **Количество попыток Retry** | 2 попытки (1 повтор) | 3 попытки (2 повтора) | Перевод дешевле и быстрее, допускает больше повторов. |
| **Базовая задержка Retry** | 500 мс | 300 мс | Начальный интервал перед повторным вызовом. |
| **Алгоритм задержки Retry** | Экспоненциальный с jitter (`1.5x`, случайность `0.5`) | Экспоненциальный с jitter (`1.5x`, случайность `0.5`) | **Randomized Jitter** исключает «эффект набегающей толпы» (Thundering Herd) на провайдера. |
| **Исключения для Circuit Breaker** | `ExternalProviderException`, `ProviderTimeoutException`, `ResourceAccessException` | `ExternalProviderException`, `ProviderTimeoutException`, `ResourceAccessException` | Системные и сетевые сбои учитываются в метриках надежности. |
| **Игнорируемые исключения** | `EmptyAiResponseException` (422) | — | Ошибки валидации контента не считаются сбоем канала. |

---

## 5. Карта Java-пакетов (Hexagonal Architecture)

```text
com.teamfourpixels.deepreaderbackend/
├── domain/                                # 1. ЧИСТОЕ ДОМЕННОЕ ЯДРО (POJO, без внешних зависимостей)
│   ├── exception/                         # Доменные исключения (TextTooLongException, EmptyAiResponseException, ...)
│   ├── model/                             # Доменные модели (ExplainRequestDomain, PhenomenonType, StylisticColoring, ...)
│   └── service/                           # Сервисы ядра:
│       ├── CacheKeyGenerator.java         # Каноническая нормализация текста и SHA-256 хэширование
│       └── ContextTruncationService.java  # Ступенчатая обрезка контекста по BPE-токенам (jtokkit)
│
├── port/                                  # 2. ПОРТЫ (Контракты взаимодействия ядра с миром)
│   ├── in/                                # Входящие порты (Use Cases):
│   │   ├── ExplainUseCase.java            # Порт сценария литературного анализа
│   │   └── TranslateUseCase.java          # Порт сценария перевода текста
│   └── out/                               # Исходящие порты (SPI):
│       ├── CachePort.java                 # Контракт двухуровневого кэша (get, put, evict)
│       ├── LlmPort.java                   # Контракт взаимодействия с нейросетью
│       └── TranslationPort.java           # Контракт взаимодействия с сервисом перевода
│
├── application/                           # 3. СЛОЙ ПРИЛОЖЕНИЯ (Оркестрация сценариев)
│   ├── ExplainUseCaseImpl.java            # Оркестрация: Truncation -> L1/L2 Cache -> LLM -> Cache Put
│   └── TranslateUseCaseImpl.java          # Оркестрация: L1/L2 Cache -> DeepL -> Cache Put
│
├── adapter/                               # 4. АДАПТЕРЫ (Реализация портов под технологии)
│   ├── in/web/                            # Входящие REST-адаптеры:
│   │   ├── ExplainController.java         # Контроллер POST /api/v1/explain
│   │   ├── TranslateController.java       # Контроллер POST /api/v1/translate
│   │   ├── SystemController.java          # Контроллер GET /api/v1/health
│   │   ├── GlobalExceptionHandler.java    # Централизованный перехват ошибок -> ErrorResponse
│   │   └── dto/                           # REST DTO (валидация Jakarta, поля в snake_case)
│   └── out/                               # Исходящие адаптеры:
│       ├── cache/                         # Адаптеры кэша:
│       │   ├── CaffeineCacheAdapter.java  # L1 In-Memory LRU кэш (@Primary)
│       │   └── PostgresCacheAdapter.java  # L2 UNLOGGED PostgreSQL кэш + @Scheduled очистка
│       ├── llm/                           # LLM-адаптеры:
│       │   ├── OpenAiLlmAdapter.java      # Адаптер к OpenAI API (gpt-4o-mini, JSON Mode, CB+Retry)
│       │   ├── MockLlmAdapter.java        # Локальная заглушка для тестов и CI/CD
│       │   └── PromptTemplates.java       # Системные и пользовательские промпты (Prompt Version v1.0)
│       └── translate/                     # Адаптеры перевода:
│           ├── DeepLTranslationAdapter.java # Адаптер к DeepL API (CB+Retry, автоопределение хоста)
│           └── MockTranslationAdapter.java  # Локальная заглушка перевода
│
└── infrastructure/                        # 5. ИНФРАСТРУКТУРА И ТЕХНИЧЕСКИЕ СЕРВИСЫ
    ├── config/                            # Spring Configuration:
    │   ├── AiProperties.java              # @ConfigurationProperties("app.ai")
    │   ├── ResilienceConfig.java          # Бины CircuitBreaker и Retry для Resilience4j
    │   └── RestClientConfig.java          # Настройка HTTP-клиентов и таймаутов
    └── metrics/                           # Наблюдаемость:
        └── DeepReaderMetrics.java         # Кастомные метрики Micrometer (SLO, задержки, токены, кэш)
```
