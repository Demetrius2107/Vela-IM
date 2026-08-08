<div align="center">

# Vela — Enterprise IM & Office Ecosystem Platform

**Spring Boot + Netty + Vue 3 + Kotlin Multiplatform Full-Stack IM Solution**

[**中文**](README.md) | [**English**](README.en.md) | [**日本語**](README.ja.md)

![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)
![Java](https://img.shields.io/badge/Java-17%20%2F%208-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.3.2-brightgreen.svg)
![Netty](https://img.shields.io/badge/Netty-4.1-green.svg)
![Vue 3](https://img.shields.io/badge/Vue-3-4FC08D.svg)
![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)

</div>

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Quick Start](#quick-start)
- [Project Structure](#project-structure)
- [Clients](#clients)
- [Documentation](#documentation)
- [Development Guidelines](#development-guidelines)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

Vela is a full-stack project covering **IM instant messaging, admin console, office ecosystem, and audio/video calls**. The backend is built on a **DDD hexagonal architecture**, supporting dual-protocol **TCP / WebSocket** long connections; the frontend covers **Web / Android / Electron / Flutter / iOS**.

- Full-link connectivity: TCP gateway → business services → message store, Phase 1 connectivity tests 11/11 passed
- Reliable delivery: ACK re-push, exponential backoff retry, incremental offline-message pull, DB degradation compensation
- Microservice architecture: 12 modules split by DDD layers, MQ async decoupling, independently deployable

### Core Architecture

```
Client ──TCP/WS──→ vela-tcp(gateway) ──MQ──→ vela-service(business) ──MQ──→ vela-message-store(storage)
                       │                         │
                       ├── Redis (cache/session)  ├── MySQL (persistence)
                       ├── RabbitMQ (events)      ├── Elasticsearch (full-text search)
                       └── ZooKeeper (registry)    └── Logstash (log collection)
                                                       └── Kibana (visualization)
```

### Stats

| Metric | Value |
|:----|:----:|
| Java sources | ~400+ files |
| Unit tests | 123 |
| REST endpoints | 60+ |
| Service modules | 12 |
| Docker containers | 16 |
| Git commits | 100+ |

---

## Features

### IM Core (Phase 0-4) ✅

| Module | Feature |
|:----|:------|
| Text messaging | P2P + group chat send/receive, ACK, dedup, multi-device sync |
| Message recall | Configurable recall window + clock-skew tolerance |
| Read receipts | Read notifications for direct & group chats |
| Offline messages | Redis ZSet incremental pull + DB degradation when over limit |
| Conversations | Pin / Do-Not-Disturb / Delete / Mark-as-read |
| Friendships | CRUD / groups / blacklist / request approval |
| Group management | Create/dissolve/mute/transfer/roles/announcements/polls |
| Multi-device login | 4 policies (single device ~ unlimited) |
| TCP/WS gateway | Netty dual protocol + heartbeat + registry discovery |
| Traceability | MDC TraceId full-chain propagation |

### L2 Exception Boundaries (Phase 0.5) ✅

| Feature | Description |
|:----|:------|
| Message retry | Exponential backoff (configurable, 3 tries) |
| ACK re-push | PendingAckTracker + scheduled scanning |
| Degradation framework | ServiceDegradationManager (Redis/MQ circuit breaker) |
| DB compensation | MessageCompensationStore + scheduled retry |
| Concurrency locks | MessageLockManager (ReadWriteLock for recall ↔ push) |
| Clock tolerance | Configurable clock skew + reverse-skew check |

### Admin Console (Phase 5) ✅

| Module | Feature |
|:----|:------|
| Dashboard | Stat cards + message trends + Top 10 groups |
| User management | Search/paging/details/batch disable/login logs |
| Group management | List/status filter/details/dissolve/export |
| Message audit | ES full-text search + SQL LIKE fallback |
| Operation logs | Automatic recording of all admin actions |
| Administrators | Super/operator/auditor three-level roles |
| System config | Dynamic parameter tuning |

### Office Ecosystem (Phase 6) ✅

| Module | Feature |
|:----|:------|
| Schedules | Create/list/status/delete |
| Todos | Create/list/priority/complete |
| Approvals | Submit/approve/reject |
| Knowledge base | Document CRUD + online editor |
| Bot market | Bot install/subscribe/manage + command config |
| Message favorites | Favorite CRUD + cross-device sync |

---

## Tech Stack

| Category | Technology | Purpose |
|------|------|------|
| Language | Java 8/17 + Kotlin | Backend + Android |
| Framework | Spring Boot 2.3.2 | Service container |
| Networking | Netty 4.1 | TCP/WebSocket long connections |
| ORM | MyBatis-Plus 3.4.2 | Database access |
| Cache | Redis 6.2 | Session/offline messages/sequence |
| Message queue | RabbitMQ 3.8 | Async decoupling/event-driven |
| Registry | ZooKeeper 3.6 | Gateway node discovery |
| Full-text search | Elasticsearch 7.17 | Message search + log storage |
| Log collection | Logstash + Kibana 7.17 | ELK logging |
| Serialization | Protostuff | TCP protocol codec |
| Frontend | Vue 3 + Naive UI | Web IM client |
| Desktop | Electron 28 | Desktop IM client |
| Mobile | Kotlin + Jetpack Compose | Android client |
| Monitoring | Prometheus + Grafana + SkyWalking | Metrics/APM |
| Build | Maven + Gradle | Backend + Android |

---

## Architecture

Follows DDD layering: **interfaces → application → domain ← infrastructure**; cross-module references are only allowed as one-way dependencies.

Detailed design docs under [`docs/architecture/`](docs/architecture/):

| Doc | Description |
|------|------|
| [system-architecture.md](docs/architecture/system-architecture.md) | Overall system architecture |
| [DDD-Hexagonal-Architecture.md](docs/architecture/DDD-Hexagonal-Architecture.md) | DDD hexagonal architecture design |
| [concurrent-conflict-handling.md](docs/architecture/concurrent-conflict-handling.md) | Concurrent conflict handling |
| [e2e-encryption-design.md](docs/architecture/e2e-encryption-design.md) | End-to-end encryption (E2EE) design |

---

## Quick Start

### One-Click Docker Startup (Recommended)

```bash
# 1. Build the backend
mvn clean package -DskipTests -q

# 2. Start all services
docker-compose up -d
```

### Manual Startup

```bash
# 1. Start middleware: MySQL / Redis / RabbitMQ / ZooKeeper
docker-compose -f docker-compose.middleware.yml up -d

# 2. Start the API gateway (port 8889)
cd vela-gateway && mvn spring-boot:run

# 3. Start business services (user / friendship / group / message / conversation ...)
cd vela-service-user && mvn spring-boot:run

# 4. Start the TCP/WS gateway (port 9000)
cd vela-tcp && mvn spring-boot:run

# 5. Start the frontend
cd web && npm install && npm run dev
```

> See [`docs/guide/deployment-guide.md`](docs/guide/deployment-guide.md) and [`docs/guide/docker-troubleshooting.md`](docs/guide/docker-troubleshooting.md) for deployment & troubleshooting.

### Access Endpoints

| Entry | URL |
|:----|:-----|
| IM Web | http://localhost:3000 |
| Admin console | http://localhost:3000/#/admin |
| Office ecosystem | http://localhost:3000/#/office |
| Kibana | http://localhost:5601 |
| Grafana | http://localhost:3000 (admin/admin) |

---

## Project Structure

```
Vela/
├── vela-common/           # Shared kernel (enums/constants/message types/config)
├── vela-codec/            # Infrastructure: TCP/WS protocol codec
├── vela-tcp/              # Adapter layer: Netty TCP/WS gateway
├── vela-gateway/          # API gateway
├── vela-service-*/        # Business services (DDD layers, 12 modules)
│   ├── user/              # User domain
│   ├── friendship/        # Friendship domain
│   ├── group/             # Group domain (announcements/polls/tags/files)
│   ├── message/           # Message domain (ES search/read tracking)
│   ├── conversation/      # Conversation domain
│   ├── admin/             # Admin console
│   ├── bot/               # Bot
│   ├── office/            # Office ecosystem (schedule/todo/approval)
│   └── ...
├── vela-message-store/    # Infrastructure: message persistence service
├── web/                   # Vue 3 frontend (IM/admin/office)
├── android/               # Android client (Kotlin + Compose)
├── electron/              # Electron desktop client
├── flutter_desktop/       # Flutter desktop (experimental)
├── ios/                   # iOS client (SwiftUI)
├── deploy/                # Deployment config (Logstash/Prometheus/scripts)
├── docs/                  # Documentation center
│   ├── guide/             # Deployment/Docker/integration-testing guides
│   ├── analysis/          # Gap analysis/feature comparison
│   ├── roadmap/           # Roadmap/TODO lists
│   ├── architecture/      # Architecture design docs
│   ├── api/               # REST API docs
│   ├── logs/              # Historical runtime logs archive
│   └── 会议记录/           # Session work records
├── docker-compose.yml     # 16-container orchestration
└── AGENTS.md              # Project development guidelines (AI-assisted coding)
```

---

## Clients

| Platform | Status | Description |
|:----|:----:|:------|
| Web (Vue 3) | ✅ | Full IM + admin console + office ecosystem |
| Android (Compose) | ✅ | Login/register/conversations/chat/contacts |
| Electron desktop | ✅ | Web wrapper + system tray + window management |
| Flutter desktop | 🚧 | Experimental cross-platform solution |
| iOS (SwiftUI) | 🚧 | Native client in development |

---

## Documentation

| Category | Doc |
|------|------|
| API reference | [`docs/api/api-documentation.md`](docs/api/api-documentation.md) |
| Deployment guide | [`docs/guide/deployment-guide.md`](docs/guide/deployment-guide.md) |
| Docker guide | [`docs/guide/docker-complete-guide.md`](docs/guide/docker-complete-guide.md) |
| Docker troubleshooting | [`docs/guide/docker-troubleshooting.md`](docs/guide/docker-troubleshooting.md) |
| Integration testing plan | [`docs/guide/integration-testing-plan.md`](docs/guide/integration-testing-plan.md) |
| Gap analysis | [`docs/analysis/current-state-gap-analysis.md`](docs/analysis/current-state-gap-analysis.md) |
| Feature comparison | [`docs/analysis/feature-gap-analysis.md`](docs/analysis/feature-gap-analysis.md) |
| Roadmap | [`docs/roadmap/feature-roadmap.md`](docs/roadmap/feature-roadmap.md) |
| Startup issues checklist | [`docs/Vela项目启动问题完整清单.md`](docs/Vela项目启动问题完整清单.md) |
| MySQL refactor | [`docs/MySQL/database-refactor-plan.md`](docs/MySQL/database-refactor-plan.md) |

---

## Development Guidelines

See [`AGENTS.md`](AGENTS.md). Core rules:

```
1. DDD layering: interfaces → application → domain ← infrastructure
2. Constructor injection, not @Autowired
3. Functions under 50 lines; extract hardcoded constants to config
4. New entity → matching DDL; message model change → update OfflineMessageContent
5. Comments explain "why", not "what"
6. Git commit format: <type>(<scope>): <subject>
```

---

## Contributing

1. Fork this repo and create a feature branch: `git checkout -b feat/<description>`
2. Follow the coding & commit guidelines in [`AGENTS.md`](AGENTS.md)
3. Run `mvn -B clean compile` before submitting
4. Open a Pull Request against the `master` branch

---

## License

This project is open-sourced under the [MIT License](LICENSE).

---

> Copyright © 2026 Vela Contributors. Released under the MIT License.
