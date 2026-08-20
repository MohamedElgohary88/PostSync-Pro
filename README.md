<div align="center">

# PostSync Pro

**Open-source API testing & synchronization toolkit built with Compose Desktop**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Desktop](https://img.shields.io/badge/Compose_Desktop-1.7+-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Tests](https://img.shields.io/badge/tests-Gradle-brightgreen)]()

</div>

---

## What is PostSync Pro?

A desktop application that brings together contract-aware API execution, Postman collection management, and QA automation in one local-first tool. Built with **Kotlin** and **Jetpack Compose Desktop**.

### Core Features

| Feature | Description |
|---|---|
| **API Workbench v2** | Send REST requests, switch named environments, save requests in local collections, run safe declarative assertions, prefill from OpenAPI, validate live responses, and replay a redacted bounded history |
| **Postman Sync** | Merge old Postman collections with new Swagger/OpenAPI specs, preserving test scripts |
| **SRS Validator** | Validate API specs against Software Requirements using AI |
| **Fuzz Testing** | Generate fuzz test cases (SQL injection, XSS, boundary values) |
| **Mock Server** | Spin up local mock servers from API specs |
| **Test Chain** | Build sequential API test workflows with variable passing |
| **Auto Flow** | AI-generated test flows from API specs |
| **QA Report** | Generate comprehensive test coverage reports |
| **Coverage Map** | Visual heatmap of API endpoint test coverage |
| **Breaking Changes** | Detect breaking changes between API versions |
| **Regression** | Track test results over time, detect regressions |
| **Dependency Graph** | Visualize API endpoint relationships |

### Preview surfaces

The following screens are product previews. They are clearly marked in the UI when they use simulated data; they must not be treated as production integrations yet.

| Feature | Description |
|---|---|
| **Scheduler** | Cron-style automated test scheduling |
| **Custom Fuzz Rules** | Create custom fuzzing patterns |
| **Test History** | Historical test run analytics & trends |
| **Branded Reports** | Company-branded QA report generation |
| **Notifications** | Slack/Email/Webhook alert channels |
| **Issue Tracker** | Jira/GitHub Issues integration |
| **API Monitor** | Endpoint uptime & performance monitoring |
| **Multi-Environment** | Run tests across dev/staging/prod |
| **CI/CD Export** | Generate GitHub Actions/GitLab CI/Jenkins configs |
| **Smart Tests** | AI-powered test scenario generation |

---

## API Workbench v2

Workbench v2 extends the contract-aware REST client without weakening its local security boundary:

- **Named environments:** create, update, select, and delete reusable variable sets. Values marked secret—or using credential-shaped names—are encrypted at rest with AES-256-GCM. Non-secret variables and environment metadata remain ordinary local data; the encryption key is file-backed, not an OS keychain.
- **Collections and saved requests:** organize reusable requests in local collections, search their metadata, and reopen them without copying credential values to disk. Authentication values, secret variables, sensitive fields, URL credentials, sensitive body values, and sensitive assertion operands are removed or replaced with restoration markers before persistence.
- **Safe custom assertions:** check exact/ranged status, maximum response time, header existence/equality/containment, body containment, and typed JSON-path existence/equality. Evaluation is cancellable and bounded. The JSON-path implementation intentionally supports only root/property/quoted-property/array-index traversal—no filters, wildcards, recursive descent, regular-expression assertions, or executable scripts.
- **Transient raw responses:** contract validation and assertions can inspect the bounded raw response in memory. Only the separately redacted display response and generic assertion diagnostics may enter UI state or history.
- **Fail-safe local stores:** environment and saved-request files are size- and schema-validated, symlink-protected, cross-process locked, permission-restricted where supported, and atomically replaced. Corrupt or unsupported stores block normal mutations instead of being silently treated as empty; explicit recovery preserves the raw file as a sibling backup before reset.
- **URL credential defense:** HTTP(S) `user:password@host` user-info is rejected for execution and redacted from saved drafts, history, and diagnostics. Use the Auth editor instead.

Workbench v2 is local-first. It does not claim arbitrary pre-request/test scripting, a JavaScript sandbox, shared cloud workspaces, or team collaboration.

---

## Tech Stack

- **Language:** Kotlin 2.0+
- **UI:** Jetpack Compose Desktop (Material 3)
- **Architecture:** MVVM + Clean Architecture
- **DI:** Koin
- **Networking:** Ktor Client
- **Serialization:** kotlinx.serialization
- **Testing:** kotlin.test + MockK
- **AI Integration:** Google Gemini, Groq, or a local GGUF model

## Architecture

```
src/main/kotlin/com/postsyncpro/
├── data/              # Repository implementations, parsers, network
│   ├── database/      # Local storage
│   ├── network/       # API clients
│   ├── parser/        # Postman/Swagger parsers
│   ├── repository/    # Repository implementations
│   └── security/      # Encryption utilities
├── di/                # Koin dependency injection modules
├── domain/            # Business logic layer
│   ├── model/         # Data models
│   ├── repository/    # Repository interfaces
│   └── usecase/       # Use cases
└── presentation/      # UI layer
    ├── components/    # Reusable UI components
    ├── i18n/          # Internationalization (EN/AR/FR/DE)
    ├── navigation/    # Screen routing
    ├── screen/        # Feature screens (21 screens)
    └── theme/         # Dark/Light theme system
```

The Workbench follows a strict transient-raw/persisted-redacted execution pipeline. See [ARCHITECTURE.md](ARCHITECTURE.md) for boundaries and compatibility rules, [SECURITY.md](SECURITY.md) for the implemented threat controls and residual risks, and [ROADMAP.md](ROADMAP.md) for the product sequence.

---

## Getting Started

### Prerequisites

- **JDK 17+** (recommended: [Eclipse Temurin](https://adoptium.net/))
- **Git**

### Run

```bash
git clone https://github.com/MohamedElgohary88/PostSync-Pro.git
cd PostSync-Pro
./gradlew run
```

### Build distributable

```bash
./gradlew createDistributable # unpacked native app for the current OS
./gradlew packageDmg        # macOS
./gradlew packageMsi        # Windows
./gradlew packageDeb        # Linux
```

### Run tests

```bash
./gradlew test
```

---

## Internationalization

Fully supports 4 languages with RTL layout:

| Language | Code | Direction |
|---|---|---|
| English | `en` | LTR |
| Arabic | `ar` | RTL |
| French | `fr` | LTR |
| German | `de` | LTR |

Switch languages from the sidebar — UI flips instantly.

---

## AI Configuration

PostSync Pro uses AI for several features (Validator, Fuzz, Auto Flow, Smart Tests, Reports).

On first launch, the onboarding wizard guides you through:
1. System detection (RAM, OS)
2. AI mode selection (Cloud API or Local)
3. API key configuration

Supports **Google Gemini**, **Groq**, and verified downloadable or user-selected local GGUF models.

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

**Quick start:**
1. Fork the repo
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit changes (`git commit -m 'Add my feature'`)
4. Push (`git push origin feature/my-feature`)
5. Open a Pull Request

### Good first issues

- Add more fuzz test patterns
- Add Compose UI regression tests for compact and wide Workbench layouts
- Add new language translations
- Write UI tests with Compose Testing
- Add Swagger 2.0 support (the Workbench currently targets OpenAPI 3.x)

---

## License

[MIT License](LICENSE) — use it however you want.

---

## Author

**Mohamed Elgohary** — [@MohamedElgohary88](https://github.com/MohamedElgohary88)

---

<div align="center">

Built with Kotlin and Compose Desktop

If this project helped you, consider giving it a star!

</div>
