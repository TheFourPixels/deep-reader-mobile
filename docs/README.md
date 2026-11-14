# 📚 Навигационный хаб документации Deep Reader Backend

Добро пожаловать в репозиторий бэкенд-сервиса **Deep Reader Backend** (`com.teamfourpixels.deepreaderbackend`).  
Сервис спроектирован по методологии **Spec-Driven Development (SDD)** и принципам **Гексагональной архитектуры (Ports & Adapters)**. Он выполняет роль легковесного, высокоскоростного **Zero-Knowledge AI-прокси** для мобильных и веб-читалок книг.

Данный раздел содержит исчерпывающую проектную документацию, структурированную по ролям разработчиков и предметным областям.

---

## 🧭 Карта документации по ролям

| Роль / Задача | Рекомендуемый документ | Описание |
| :--- | :--- | :--- |
| 📡 **REST API Справочник** | [**`api/API_REFERENCE.md`**](api/API_REFERENCE.md) | **Отдельный справочник API**: все эндпоинты, обязательные/опциональные параметры, ограничения, примеры JSON-запросов и ответов (`200` и `4xx/5xx`). |
| 📱 **Android / Mobile Developer** | [**`mobile/ANDROID_INTEGRATION_GUIDE.md`**](mobile/ANDROID_INTEGRATION_GUIDE.md) | Полный контракт API, правила извлечения контекста из EPUB, спойлер-безопасность, справочник enum-значений для UI-бейджей и гайд по Bottom Sheet. |
| 📐 **Backend Engineer / Архитектор** | [**`architecture/SYSTEM_DESIGN.md`**](architecture/SYSTEM_DESIGN.md) | Общая архитектура системы (Hexagonal Architecture), Mermaid-диаграммы потоков данных, алгоритм обрезки контекста (`jtokkit`), Resilience4j и карта пакетов. |
| 🗄 **Database / Data Engineer** | [**`database/DATABASE_SCHEMA.md`**](database/DATABASE_SCHEMA.md) | Zero-Knowledge концепция, ER-диаграмма таблицы `ai_cache_entries`, обоснование выбора UNLOGGED PostgreSQL, формулы кэш-ключей (SHA-256), TTL и фоновая очистка. |
| 🚀 **DevOps / SRE / Инфраструктура** | [**`operations/DEPLOYMENT_AND_RUNBOOK.md`**](operations/DEPLOYMENT_AND_RUNBOOK.md) | Запуск в Docker Compose, развёртывание в Kubernetes (Kustomize), конфигурация окружения (`.env`), форматирование логов в JSON (`trace_id`), мониторинг в Grafana/Prometheus и ранбук алертов. |
| 🤖 **AI Guidelines & SDD** | [**`AI_DEVELOPMENT_RULES.md`**](AI_DEVELOPMENT_RULES.md) | Единые правила разработки для AI-ассистентов, методология SDD, стандарты Zero-Knowledge и требования SRE. |
| 🔒 **Замороженные контракты (FROZEN)** | [**`specs/`**](specs/) | Канонические спецификации SDD, не подлежащие произвольной модификации: архитектура, контракт OpenAPI 3.0.3 и AI-логика. |

---

## 🗂 Структура директории `docs/`

```text
docs/
├── README.md                              # Этот документ (навигационный хаб)
├── AI_DEVELOPMENT_RULES.md                # 🤖 Правила разработки, методология SDD и Zero-Knowledge
├── api/
│   └── API_REFERENCE.md                   # 📡 Выделенный справочник чистого REST API (параметры, cURL, JSON)
├── mobile/
│   └── ANDROID_INTEGRATION_GUIDE.md       # Руководство по интеграции мобильного приложения
├── database/
│   └── DATABASE_SCHEMA.md                 # Описание схемы базы данных и двухуровневого кэша
├── architecture/
│   └── SYSTEM_DESIGN.md                   # Системный дизайн, гексагональная архитектура, диаграммы
├── operations/
│   └── DEPLOYMENT_AND_RUNBOOK.md          # Инструкции по развертыванию, запуску и SRE-ранбук
└── specs/                                 # ❄️ ЗАМОРОЖЕННЫЕ СПЕЦИФИКАЦИИ (FROZEN SPECS)
    ├── 01_architecture.md                 # Архитектурный контракт и правила двухуровневого кэша
    ├── 02_openapi.yaml                    # Спецификация REST API (OpenAPI 3.0.3)
    └── 03_ai_logic.md                     # Системные промпты, токенизация, формулы кэша и метрики
```

---

## 🔒 Принцип Spec-Driven Development (SDD)

Все спецификации в папке [**`specs/`**](specs/) являются **замороженными (FROZEN)**. Это означает:
1. **Неизменность API:** Поля DTO (все в `snake_case`), HTTP-статусы, эндпоинты и структура ошибок `ErrorResponse` строго зафиксированы в [`02_openapi.yaml`](specs/02_openapi.yaml).
2. **Алгоритмическая целостность:** Формулы нормализации текста и вычисления хэшей ключей кэша (SHA-256) в [`03_ai_logic.md`](specs/03_ai_logic.md) едины для бэкенда и клиента.
3. **Чистота домена:** Доменный слой не зависит от фреймворков и библиотек базы данных или веб-контроллеров. Все изменения сначала формализуются в контрактах, и только затем реализуются в коде.
