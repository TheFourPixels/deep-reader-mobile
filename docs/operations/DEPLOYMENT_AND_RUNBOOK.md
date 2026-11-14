# 🚀 Руководство по развертыванию, эксплуатации и SRE-ранбук

Данный документ содержит практические инструкции для DevOps, SRE и бэкенд-инженеров по локальному запуску, тестированию, контейнеризации, развёртыванию в Kubernetes и реагированию на инциденты (SRE Runbook).

---

## 1. Локальный запуск и окружение разработки

### 1.1. Быстрый запуск через Docker Compose (Backend + PostgreSQL)

Для быстрого старта всех сервисов «из коробки» подготовлен `docker-compose.yml`:
```bash
# 1. Сборка и запуск в фоновом режиме:
docker compose up --build -d

# 2. Проверка статуса контейнеров:
docker compose ps

# 3. Просмотр логов в реальном времени:
docker compose logs -f deep-reader-app
```

После старта сервис доступен по адресу: **`http://localhost:8080/api/v1/health`**.

#### 💡 Режим заглушек (Offline Mock Mode):
Если у вас нет действующих API-ключей для OpenAI и DeepL, сервис можно запустить полностью автономно, передав переменные окружения:
```bash
AI_LLM_PROVIDER=mock AI_TRANSLATOR_PROVIDER=mock docker compose up -d
```
В этом режиме активируются `MockLlmAdapter` и `MockTranslationAdapter`: они возвращают предсказуемые тестовые ответы без выхода в интернет и без расхода баланса.

---

### 1.2. Запуск через Maven и локальный профиль

1. **Создайте файл `.env`** в корне репозитория (на основе `.env.example`):
   ```properties
   PORT=8080
   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/deep_reader
   SPRING_DATASOURCE_USERNAME=postgres
   SPRING_DATASOURCE_PASSWORD=postgres
   OPENAI_API_KEY=sk-...
   DEEPL_API_KEY=...:fx
   ```
2. **Запустите приложение:**
   ```bash
   ./mvnw spring-boot:run
   ```
3. **Проверьте готовность сервиса:**
   ```bash
   curl -s http://localhost:8080/api/v1/health | jq .
   ```

---

### 1.3. Запуск тестов (Test Suite)

Проект покрыт всесторонними модульными, срезовыми (`@WebMvcTest`) и интеграционными тестами с использованием **Testcontainers**:
```bash
# Чистая сборка и запуск всех тестов:
./mvnw clean test
```

> [!NOTE]
> Интеграционный тест [`PostgresCacheAdapterIT`](../../src/test/java/com/teamfourpixels/deepreaderbackend/PostgresCacheAdapterIT.java) автоматически проверяет доступность локального демона Docker через `DockerClientFactory.instance().isDockerAvailable()`. Если Docker запущен, тест поднимает реальный контейнер PostgreSQL 16, накатывает Liquibase-миграции и проверяет UNLOGGED-кэш. Если Docker не запущен (например, на легковесных CI-агентах), тест корректно пропускается, не ломая сборку проекта.

---

## 2. Развертывание в Kubernetes (Kustomize)

Вся инфраструктура развёртывания описана декларативными манифестами в директории `k8s/` и управляется с помощью **Kustomize**.

### 2.1. Пошаговый деплой в Minikube / K8s

1. **Подготовка окружения (Minikube):**
   ```bash
   minikube start --driver=docker --cpus=4 --memory=8192
   
   # Направляем локальный Docker CLI на демон Minikube:
   # PowerShell:
   minikube docker-env | Invoke-Expression
   # Linux / macOS / Bash:
   eval $(minikube docker-env)
   ```

2. **Сборка production Docker-образа:**
   ```bash
   docker build -t deep-reader-app:latest .
   ```

3. **Применение манифестов Kustomize:**
   ```bash
   kubectl apply -k k8s/
   ```

4. **Проверка статуса подов:**
   ```bash
   kubectl get pods -n deep-reader
   kubectl get pods -n monitoring
   ```

### 2.2. Проброс портов к сервисам (Port-Forwarding Cheat Sheet)

```bash
# 1. REST API бэкенда:
kubectl port-forward -n deep-reader service/deep-reader-app 8080:8080

# 2. VictoriaMetrics (PromQL / TSDB):
kubectl port-forward -n deep-reader service/victoria-metrics 8428:8428

# 3. VictoriaLogs UI (веб-интерфейс поиска логов):
kubectl port-forward -n deep-reader service/victoria-logs 9428:9428

# 4. Alertmanager (управление алертами и оповещениями):
kubectl port-forward -n deep-reader service/alertmanager 9093:9093

# 5. Grafana (дашборды метрик, JVM и SLA):
kubectl port-forward -n deep-reader service/grafana 3000:3000
```

---

## 3. Наблюдаемость (Observability)

### 3.1. Структурированные JSON-логи (Logback Custom Layout)
Все логи приложения выводятся в `STDOUT` в формате однострочного JSON (`CustomJsonLayout`).
Каждая запись содержит сквозной `trace_id` из MDC, проставляемый в [`TraceContextFilter`](../../src/main/java/com/teamfourpixels/deepreaderbackend/config/TraceContextFilter.java):

```json
{
  "timestamp": "2026-11-13T12:00:15.123Z",
  "level": "INFO",
  "service": "deep-reader-backend",
  "trace_id": "c1f7b7e2-4c28-4e89-8d7d-2be68e64b633",
  "span_id": "8f3b2a1c0e4d",
  "thread": "http-nio-8080-exec-1",
  "message": "POST /api/v1/explain: book_id='1984', selected='clocks were striking thirteen'",
  "context": {
    "request_path": "/api/v1/explain",
    "http_method": "POST"
  }
}
```
Логи собираются через **Vector DaemonSet** и направляются в **VictoriaLogs**. Поиск по запросу пользователя выполняется по фильтру `trace_id: "..."`.

---

### 3.2. SRE Метрики Micrometer & Prometheus

Метрики отдаются через стандартный эндпоинт **`/actuator/prometheus`**.

| Имя метрики | Тип | Теги | Назначение |
| :--- | :--- | :--- | :--- |
| `ai.llm.duration.seconds` | Timer / Histogram | `provider`, `model`, `status` | Задержка вызовов OpenAI API (p50, p95, p99). |
| `ai.translation.duration.seconds` | Timer / Histogram | `provider`, `status` | Задержка внешнего вызова DeepL API. |
| `ai.tokens.consumed.total` | Counter | `model`, `token_type` (`prompt`, `completion`) | Суммарное потребление токенов для учёта расходов. |
| `ai.cache.requests.total` | Counter | `action`, `level` (`L1`, `L2`), `result` (`hit`, `miss`) | Эффективность двухуровневого кэша. |
| `ai.errors.total` | Counter | `error_code`, `provider` | Счётчик ошибок по классам и источникам. |
| `ai.requests.total` | Counter | `endpoint`, `status` | Общий входящий трафик к API-прокси. |
| `http.server.requests` | Timer / SLA | `uri`, `status`, `method` | RED-метрики HTTP-слоя (Rate, Errors, Duration). |

---

### 3.3. Дашборды Grafana (`k8s/monitoring/`)

В директории `k8s/monitoring/` расположены готовые JSON-модели дашбордов для импорта в Grafana:
- [**`k8s/monitoring/grafana-dashboard.json`**](../../k8s/monitoring/grafana-dashboard.json) — основной дашборд сервиса:
  - **SLA & Availability:** доступность API (порог 99%), задержки p50/p95/p99.
  - **Application Performance:** RED-метрики (RPS, статус-коды HTTP, распределение по эндпоинтам).
  - **Runtime and System Resources:** использование кучи JVM Heap, сборка мусора GC, исчерпание пула соединений HikariCP.
  - **AI Proxy & Cache Performance:** попадания/промахи кэша L1 vs L2 (`ai_cache_requests_total`), задержки LLM p95 (`ai_llm_duration_seconds_bucket`), скорость расхода токенов (`ai_tokens_consumed_total`) и график ошибок по классам (`ai_errors_total`).
- [**`k8s/monitoring/postgres-dashboard.json`**](../../k8s/monitoring/postgres-dashboard.json) — мониторинг экземпляра PostgreSQL (транзакции, блокировки, размер UNLOGGED таблицы кэша).

---

## 4. SRE Ранбук по алертам (`k8s/14-alerting-rules.yaml`)

Правила мониторинга непрерывно вычисляются демоном **vmalert**. Ниже приведены инструкции по реагированию для дежурного инженера.

### 4.1. `AiLlmHighLatency` (Warning)
- **Условие:** `histogram_quantile(0.95, sum(rate(ai_llm_duration_seconds_bucket[2m])) by (le)) > 8` в течение 2 минут.
- **Симптом:** p95 времени ответа OpenAI API превышает 8 секунд. Пользователи видят долгий индикатор загрузки.
- **Действия инженера:**
  1. Откройте дашборд Grafana и проверьте график `ai.llm.duration.seconds` в разрезе статусов.
  2. Проверьте статус OpenAI на странице `status.openai.com`.
  3. Проверьте процент кэш-хитов `ai.cache.requests.total`. Если процент хитов упал, убедитесь в доступности L2-кэша (`/api/v1/health`).
  4. При масштабном замедлении OpenAI переключите бэкенд на резервный шлюз или локальный vLLM, изменив `OPENAI_BASE_URL` через ConfigMap без рестарта кода.

---

### 4.2. `AiHighEmptyResponseRate` (Critical)
- **Условие:** Доля ошибок `EMPTY_AI_RESPONSE` превышает 2% от общего числа запросов `/api/v1/explain` за 5 минут.
- **Симптом:** Пользователи получают HTTP 422 Unprocessable Content. Модель возвращает пустой блок `explanation`.
- **Действия инженера:**
  1. В VictoriaLogs найдите примеры запросов с `error_code: "EMPTY_AI_RESPONSE"`.
  2. Проверьте, не активировались ли политики контентной фильтрации (Safety Filters) OpenAI на специфических фрагментах книг.
  3. Проверьте версию системного промпта (`PromptTemplates.SYSTEM_PROMPT`). Убедитесь, что модель не переведена в несовместимый режим форматирования.

---

### 4.3. `AiExternalProvider5xx` (Critical)
- **Условие:** Доля сбоев внешних провайдеров (`ai_provider_latency_seconds_count{status="error"}`) превышает 5% за 1 минуту.
- **Симптом:** Рост числа ответов 502 Bad Gateway (`LLM_PROVIDER_ERROR` или `TRANSLATION_FAILED`). Срабатывание Circuit Breaker.
- **Действия инженера:**
  1. Проверьте состояние Circuit Breaker в логах (`"Circuit breaker open: openai"` или `"deepl"`).
  2. Убедитесь, что баланс API-ключей OpenAI / DeepL не исчерпан.
  3. Проверьте валидность API-токенов в Secret Kubernetes (`kubectl get secret deep-reader-secrets -n deep-reader -o yaml`).
  4. При необходимости включите режим `mock` или увеличьте `waitDurationInOpenState` в ConfigMap.

---

### 4.4. `HikariCPConnectionPoolExhaustion` (Critical)
- **Условие:** Активные соединения пула HikariCP превышают 80% от максимума в течение 10 секунд.
- **Симптом:** Потоки Tomcat блокируются в ожидании соединения с PostgreSQL при записи или чтении L2-кэша.
- **Действия инженера:**
  1. Проверьте активность долгих транзакций в PostgreSQL:
     ```sql
     SELECT pid, now() - query_start AS duration, query FROM pg_stat_activity WHERE state != 'idle';
     ```
  2. Убедитесь, что индекс `idx_ai_cache_expires` не повреждён и фоновая очистка `DELETE FROM ai_cache_entries` не блокирует таблицу.
  3. Увеличьте `DB_POOL_MAX` (по умолчанию 10) в настройках Deployment приложения.
