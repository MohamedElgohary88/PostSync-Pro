<div align="center">

# PostSync Pro

**Open-source API testing & synchronization toolkit built with Compose Desktop**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Desktop](https://img.shields.io/badge/Compose_Desktop-1.7+-4285F4?logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Tests](https://img.shields.io/badge/Tests-202_passing-brightgreen)]()

</div>

---

## What is PostSync Pro?

A desktop application that brings together API testing, Postman collection management, and QA automation into one tool. Built entirely with **Kotlin** and **Jetpack Compose Desktop**.

### Core Features

| Feature | Description |
|---|---|
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

### Pro Features

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

## Screenshots

> _Coming soon — contributions welcome!_

---

## Tech Stack

- **Language:** Kotlin 2.0+
- **UI:** Jetpack Compose Desktop (Material 3)
- **Architecture:** MVVM + Clean Architecture
- **DI:** Koin
- **Networking:** Ktor Client
- **Serialization:** kotlinx.serialization
- **Testing:** kotlin.test + MockK
- **AI Integration:** Anthropic Claude / OpenAI (configurable)

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

**Stats:** ~33,500 lines of production code | ~2,200 lines of tests | 202 tests

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

Supports: **Anthropic Claude** and **OpenAI GPT** models.

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
- Improve light mode color consistency
- Add new language translations
- Write UI tests with Compose Testing
- Add Swagger 2.0 support (currently OpenAPI 3.0 only)

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
