# Архитектура сервиса: Deep Reader Backend (MVP)

**Версия спецификации:** 1.1.0 (Утверждено и ЗАМОРОЖЕНО / FROZEN)  
**Статус:** Утверждено  
**Дата:** 2026-11-12  
**Авторы:** Senior Backend Architect / SRE Engineer  

---

## 1. Концепция сервиса и бизнес-миссия

**Deep Reader Backend** — высокопроизводительный Cloud-Native proxy-бэкенд для мобильного приложения осознанного чтения художественной литературы (Android).

### Ключевые архитектурные принципы:
1. **Zero-Knowledge / Data Privacy (Безопасность авторского контента):**  
   Сервис **НЕ сохраняет тексты книг, глав и параграфов в постоянную реляционную БД**. Все книги читаются и хранятся исключительно на клиентском устройстве (Android). Бэкенд выступает в роли защищенного stateless-прокси между мобильным клиентом и внешними провайдерами искусственного интеллекта (LLM) и машинного перевода.
2. **Изоляция секретов и провайдеров:**  
   Мобильное приложение никогда не обращается к OpenAI, DeepL или другим поставщикам напрямую. Ключи API, системные промпты, параметры моделей и биллинг полностью изолированы внутри бэкенда.
3. **Архитектура адаптеров (Hexagonal / Ports & Adapters):**  
   Смена поставщика LLM (OpenAI -> Anthropic -> локальная vLLM/Ollama) или сервиса перевода (DeepL -> Google -> Mock) осуществляется переключением конфигурации в `.env` без переписывания бизнес-логики.
4. **Умное временное кэширование (Hash-based Caching):**  
   Для минимизации задержек (p95 latency) и сокращения расходов на внешние API используется детерминированное кэширование по SHA-256 хэшу запроса с TTL.
5. **Строгая SRE-надежность и наблюдаемость:**  
   Метрики RED, сквозная трассировка (MDC, `trace_id`), структурированное логирование в JSON, таймауты, Circuit Breaker, мониторинг расхода токенов и кэш-хитов.

---

## 2. Аудит и рефакторинг репозитория (SRE Template -> AI Proxy)

### 2.1. Что ОСТАВЛЯЕМ и адаптируем (SRE & Infrastructure Assets)
| Компонент | Расположение | Назначение в новой архитектуре |
|---|---|---|
| **JSON Logging (Logback + MDC)** | `logback-spring.xml`, `CustomJsonLayout` | Сохраняем. Поля MDC: `trace_id`, `span_id`, `provider`, `cache_hit`, `tokens_prompt`, `tokens_completion`. |
| **Сборщик логов (Vector)** | `k8s/09-vector.yaml` | Сохраняем. Парсит JSON-логи подов и отправляет в VictoriaLogs. |
| **VictoriaLogs & VictoriaMetrics** | `k8s/08-victoria-logs.yaml`, `k8s/monitoring/*` | Сохраняем. Единая платформа сбора логов и Prometheus-метрик. |
| **vmalert & Alertmanager** | `k8s/11-vmalert.yaml`, `k8s/13-alertmanager.yaml`, `k8s/14-alerting-rules.yaml` | Сохраняем. Заменяем устаревший алерт `DeepReaderHighAnnotationLatency` на алерты деградации LLM/переводчика (`AiLlmHighLatency`, `AiExternalProviderErrors`, `AiHighEmptyResponseRate`). |
| **Grafana Dashboards** | `grafana-dashboard.json` | Адаптируем под метрики AI: латентность внешних API, Hit Rate кэша L1/L2, расход токенов. |
| **Actuator & Micrometer** | `pom.xml`, `application.yaml` | Сохраняем. Экспорт `/actuator/prometheus` и liveness/readiness `/actuator/health`. |
| **PostgreSQL 16 & HikariCP** | `docker-compose.yml`, `k8s/04-postgres-deployment.yaml` | Сохраняем. Используется как высокоскоростное L2-хранилище кэша (`UNLOGGED` таблица `ai_cache_entries`). |
| **Docker & K8s Kustomize** | `Dockerfile`, `docker-compose.yml`, `k8s/*` | Сохраняем multi-stage сборку и декларативный деплой. |

### 2.2. Что УДАЛЯЕМ (Полная очистка легаси без обратной совместимости)
| Компонент / Файл | Причина удаления |
|---|---|
| `BookController`, `ChapterController`, `AnnotationController` | Монолитный CRUD для книг/глав. Нарушает требование «не сохранять тексты книг в БД». |
| `Book`, `Chapter`, `Annotation` (`model/*`) | JPA-сущности для персистентного хранения авторского текста. |
| `BookRepository`, `ChapterRepository`, `AnnotationRepository` | Реляционный доступ к удаляемым сущностям. |
| `BookService`, `ChapterService`, `AnnotationService` | Устаревшая CRUD бизнес-логика. |
| `DataInitializerService` | Первичное наполнение базы демонстрационными книгами. |
| `ChaosController`, `ChaosService`, `ChaosException`, `ChaosServiceTest` | Учебный симулятор сбоев для лабораторных работ SRE. Опасен в production. |
| `V1__initial_schema.sql` | Заменяется на чистую миграцию Liquibase `V1__create_ai_cache.sql`. |

### 2.3. Что ДОБАВЛЯЕМ (Компоненты AI-Proxy)
| Компонент | Назначение |
|---|---|
| `TranslateController` (`POST /api/v1/translate`) | Проксирование и кэширование запросов машинного перевода. |
| `ExplainController` (`POST /api/v1/explain`) | AI-разбор контекста книги через LLM с защитой от спойлеров. |
| `ContextTruncationService` (`jtokkit`) | Алгоритм ступенчатого усечения контекста по BPE-токенам (O200K_BASE / CL100K_BASE). |
| `OpenAiLlmAdapter` | Адаптер к OpenAI API (`gpt-4o-mini`, `response_format: {"type": "json_object"}`) с поддержкой кастомного `base-url` (для vLLM/Ollama/прокси). |
| `DeepLTranslationAdapter` | Адаптер к DeepL API с автоопределением Free (`api-free.deepl.com`) и Pro (`api.deepl.com`) по суффиксу `:fx`. |
| `MockLlmAdapter` / `MockTranslationAdapter` | Оффлайн-заглушки для локального запуска и CI/CD без платных API-ключей. |
| `PostgresCacheAdapter` | L2-кэш на базе UNLOGGED таблицы в PostgreSQL с автоочисткой просроченных записей. |
| `CaffeineCacheAdapter` | L1 быстрый in-memory кэш (до 10 000 записей, TTL 1 час). |
| Resilience4j Circuit Breakers | Защита от зависаний и каскадных сбоев внешних провайдеров. |

---

## 3. Целевая структура пакетов (Hexagonal Architecture)

```
com.teamfourpixels.deepreaderbackend/
├── DeepReaderBackendApplication.java
│
├── domain/                                  # Чистая бизнес-логика
│   ├── model/
│   │   ├── ExplainRequestDomain.java
│   │   ├── ExplainResponseDomain.java
│   │   ├── TranslationRequestDomain.java
│   │   ├── TranslationResponseDomain.java
│   │   ├── PhenomenonType.java              # IDIOM, METAPHOR, SLANG, IRONY, etc.
│   │   └── StylisticColoring.java           # NEUTRAL, INFORMAL, LITERARY, etc.
│   ├── service/
│   │   ├── ContextTruncationService.java    # Обрезка контекста по токенам (jtokkit)
│   │   ├── CacheKeyGenerator.java           # Формула SHA-256 кэш-ключей
│   │   └── ResponseValidator.java           # Валидация ответов LLM (не пустой, валидный JSON)
│   └── exception/
│       ├── TextTooLongException.java
│       ├── EmptyAiResponseException.java
│       ├── ExternalProviderException.java
│       └── ProviderTimeoutException.java
│
├── port/                                    # Контракты взаимодействия
│   ├── in/                                  # Входящие юзкейсы
│   │   ├── ExplainUseCase.java
│   │   └── TranslateUseCase.java
│   └── out/                                 # Исходящие порты
│       ├── LlmPort.java                     # Порт к провайдеру LLM
│       ├── TranslationPort.java             # Порт к сервису перевода
│       └── CachePort.java                   # Порт к хранилищу кэша (get/put)
│
├── adapter/
│   ├── in/
│   │   └── web/                             # REST контроллеры и DTO
│   │       ├── ExplainController.java       # POST /api/v1/explain
│   │       ├── TranslateController.java     # POST /api/v1/translate
│   │       ├── SystemController.java        # GET /api/v1/health
│   │       ├── GlobalExceptionHandler.java  # Стандартизированный JSON ошибок
│   │       └── dto/                         # DTO по OpenAPI 3.0.3 контракту
│   │
│   └── out/
│       ├── llm/                             # Адаптеры LLM провайдеров
│       │   ├── OpenAiLlmAdapter.java        # gpt-4o-mini + configurable base-url
│       │   ├── MockLlmAdapter.java          # Для оффлайн тестов и dev-профиля
│       │   └── PromptTemplates.java         # Замороженный системный промпт v1.0
│       │
│       ├── translate/                       # Адаптеры переводчиков
│       │   ├── DeepLTranslationAdapter.java # DeepL (Free/Pro автодетект)
│       │   └── MockTranslationAdapter.java  # Для тестов
│       │
│       └── cache/                           # Адаптеры кэша
│           ├── PostgresCacheAdapter.java    # L2 кэш на UNLOGGED таблице PostgreSQL
│           ├── CaffeineCacheAdapter.java    # L1 in-memory кэш
│           ├── CacheCleanupScheduler.java   # @Scheduled фоновое удаление просрочки
│           └── entity/
│               └── CacheEntry.java
│
├── infrastructure/                          # Инфраструктурные бины и конфиг
│   ├── config/
│   │   ├── WebClientConfig.java             # Пул соединений и таймауты
│   │   ├── ResilienceConfig.java            # CircuitBreaker, Retry, TimeLimiter
│   │   └── AiProperties.java                # Привязка переменных .env
│   ├── metrics/
│   │   └── DeepReaderMetrics.java           # SRE метрики: латентность, токены, кэш-хиты
│   └── logging/
│       └── TraceIdFilter.java               # Проброс X-Trace-Id / MDC
```

---

## 4. Схема работы двухуровневого кэша (Caffeine + PostgreSQL UNLOGGED)

### 4.1. Архитектура кэша
1. **L1 (In-Memory Caffeine):**
   - Размер: до 10 000 записей.
   - Срок хранения (TTL): **1 час** (быстрый доступ при листании одной страницы).
2. **L2 (PostgreSQL UNLOGGED таблица `ai_cache_entries`):**
   - Таблица создается со свойством `UNLOGGED` (отключает запись в WAL, обеспечивая скорость записи на уровне Redis без накладных расходов на журналирование транзакций).
   - Срок жизни (TTL):
     - Объяснения (`explain`): **30 суток**.
     - Переводы (`translate`): **90 суток**.
   - **Защита от протухания при чтении:** запрос на чтение всегда содержит предикат `WHERE hash_key = ? AND expires_at > NOW()`.
   - **Фоновая очистка:** `@Scheduled(cron = "0 0 * * * *")` выполняет `DELETE FROM ai_cache_entries WHERE expires_at < NOW()`.

### 4.2. Схема таблицы кэша (Liquibase `V1__create_ai_cache.sql`)
```sql
CREATE UNLOGGED TABLE ai_cache_entries (
    hash_key      VARCHAR(64)              NOT NULL,
    cache_type    VARCHAR(16)              NOT NULL, -- 'EXPLAIN' | 'TRANSLATE'
    response_data TEXT                     NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    expires_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_ai_cache PRIMARY KEY (hash_key)
);

CREATE INDEX idx_ai_cache_expires_at ON ai_cache_entries (expires_at);
```

---

## 5. Внешние провайдеры и отказоустойчивость

### 5.1. LLM Провайдер: OpenAI (`gpt-4o-mini`)
- **JSON Mode:** Передача параметра `response_format: { "type": "json_object" }` гарантирует синтаксическую валидность возвращаемого JSON.
- **Configurable Base URL:** Параметр `OPENAI_BASE_URL` (по умолчанию `https://api.openai.com/v1`) позволяет перенаправлять трафик на локальный `vLLM`, `Ollama` или корпоративный API-шлюз без изменения кода.
- **Таймаут:** 12 секунд. При превышении таймаута запрос прерывается и возвращается `504 Gateway Timeout`.

### 5.2. Сервис перевода: DeepL API
- **Автоопределение хоста:**
  - Если ключ заканчивается на `:fx` -> `https://api-free.deepl.com/v2/translate`.
  - Иначе -> `https://api.deepl.com/v2/translate`.
- **Таймаут:** 3 секунды.

### 5.3. Mock-адаптеры
- Активируются через конфигурацию (`AI_LLM_PROVIDER=mock`, `AI_TRANSLATOR_PROVIDER=mock`).
- Позволяют поднимать и тестировать сервис локально и в CI/CD без реальных ключей и расхода баланса.

---

## 6. SRE Метрики и Алертинг

### 6.1. Метрики (Micrometer)
1. `ai.requests.total{endpoint="explain|translate", status="200|4xx|5xx"}`
2. `ai.cache.requests.total{endpoint="explain|translate", level="L1|L2", result="hit|miss"}`
3. `ai.provider.latency.seconds{provider="openai|deepl", status="ok|error"}`
4. `ai.tokens.consumed.total{model="gpt-4o-mini", type="prompt|completion"}`
5. `ai.errors.total{error_code="TEXT_TOO_LONG|EMPTY_AI_RESPONSE|EXTERNAL_PROVIDER_TIMEOUT"}`

### 6.2. Алерты vmalert (`k8s/14-alerting-rules.yaml`)
- `AiLlmHighLatency`: p95 латентность вызова LLM > 8 с в течение 2 минут (Warning).
- `AiHighEmptyResponseRate`: доля ошибок `EMPTY_AI_RESPONSE` > 2% за 5 минут (Critical).
- `AiExternalProvider5xx`: доля ошибок внешних провайдеров > 5% за 1 минуту (Critical).
