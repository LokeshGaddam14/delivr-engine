# 🔔 Notification Engine

> A production-grade notification delivery system with exponential backoff retry logic, full audit logging, and an admin analytics dashboard — built with Spring Boot 3, MySQL, and JWT.

---

## The Problem It Solves

In real systems, notification delivery fails silently. SMTP servers go down. SMS gateways timeout. Nobody knows what failed, when it failed, or how many times it was retried. Support teams get flooded with "I never got the OTP" tickets with zero visibility into what went wrong.

This engine solves that by:
- Queueing every notification before attempting delivery
- Retrying failed notifications with **exponential backoff** (5s → 10s → 20s → 40s → DEAD)
- Logging **every delivery attempt** — timestamp, error, response time
- Giving admins a **live dashboard** to see failures and manually recover dead notifications

---

## Architecture

```
Client Request
     │
     ▼
NotificationController  ──▶  NotificationService  ──▶  MySQL (notifications table)
                                                              │
                                          RetryScheduler ◀───┘
                                          (every 30s)
                                               │
                                     NotificationProcessorService
                                          │           │
                               EmailSenderService   SMS (simulated)
                                          │
                                   notification_attempts table (audit log)
```

**Key design decision:** The scheduler uses a DB-level atomic lock (`UPDATE ... WHERE status IN ('PENDING','SCHEDULED')`) before processing any notification. This means even if 10 instances of this service run simultaneously, no notification gets processed twice.

---

## Tech Stack

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2-green)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)
![JWT](https://img.shields.io/badge/Auth-JWT-red)
![Maven](https://img.shields.io/badge/Build-Maven-yellow)
![Swagger](https://img.shields.io/badge/Docs-Swagger_UI-85EA2D)

---

## Key Features

- **Multi-channel**: EMAIL (real SMTP via Gmail) + SMS (simulated with 30% random failure to test retry)
- **Exponential backoff retry**: 5s → 10s → 20s → 40s → DEAD (5 attempts max, configurable)
- **Concurrent-safe scheduler**: DB-level atomic locking — safe for multi-instance deployments
- **Full audit trail**: Every attempt logged with attempt number, timestamp, failure reason, response time
- **Admin recovery**: Manually retry any DEAD notification via API
- **Analytics endpoint**: Delivery success rate, avg retries, 24h breakdown, avg response time
- **JWT authentication**: Role-based access (USER submits, ADMIN sees failures + analytics)
- **Swagger UI**: All APIs documented and testable live — no Postman setup needed

---

## API Endpoints

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| POST | `/api/auth/register` | Public | Register new user |
| POST | `/api/auth/login` | Public | Login, get JWT token |
| POST | `/api/notifications/send` | USER | Submit a notification |
| GET | `/api/notifications/{id}` | USER | Check status + retry count |
| GET | `/api/notifications/my` | USER | My submitted notifications |
| GET | `/api/notifications/{id}/attempts` | USER | Full audit trail |
| GET | `/api/notifications/failed` | ADMIN | All DEAD notifications |
| GET | `/api/notifications/status/{status}` | USER | Filter by status |
| POST | `/api/notifications/{id}/retry` | ADMIN | Manually retry a DEAD one |
| GET | `/api/notifications/analytics` | ADMIN | Full delivery dashboard |

**Live Swagger UI:** `http://localhost:8080/swagger-ui.html`

---

## How to Run Locally

**Prerequisites:** Java 17, MySQL 8.0, Maven

```bash
# 1. Clone
git clone https://github.com/YOUR_USERNAME/notification-engine.git
cd notification-engine

# 2. Create the database
mysql -u root -p < src/main/resources/schema.sql

# 3. Configure credentials
# Edit src/main/resources/application.properties:
# - spring.datasource.password = your MySQL password
# - spring.mail.username = your Gmail
# - spring.mail.password = your Gmail App Password (not regular password)

# 4. Run
mvn spring-boot:run

# 5. Open Swagger UI
# http://localhost:8080/swagger-ui.html
```

**Default admin login:**
```
Email:    admin@notificationengine.com
Password: admin123
```

---

## Gmail App Password Setup

1. Go to Google Account → Security → 2-Step Verification (enable it)
2. Search "App passwords" → Create one for "Mail"
3. Use the 16-character password in `spring.mail.password`

---

## Database Schema

Two core tables:

**`notifications`** — one row per notification, tracks lifecycle  
**`notification_attempts`** — one row per delivery attempt, full audit trail

See: [`src/main/resources/schema.sql`](src/main/resources/schema.sql)

---

## Retry Flow (The Core Logic)

```
Notification submitted → status: PENDING
        │
   Scheduler picks it up (every 30s)
        │
   Attempt delivery
        │
  ┌─────┴─────┐
SUCCESS      FAIL
  │            │
DELIVERED   retry_count < 5?
             │         │
            YES        NO
             │         │
          SCHEDULED   DEAD ← Admin gets alerted
          (backoff)
```

---

## What I Learned

- How real notification systems handle partial failures without losing messages
- Why exponential backoff is used over fixed retry intervals (prevents thundering herd)
- How DB-level locking prevents race conditions in concurrent schedulers
- Spring AOP and `@Transactional` boundary design for safe state transitions

---


