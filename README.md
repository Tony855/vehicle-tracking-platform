# Vehicle Tracking Platform

Multi-tenant vehicle tracking SaaS for Android head units, standalone 4G GNSS trackers, and ESP32-based devices.

## Technology baseline

- Backend: Java 21, Spring Boot 3, Maven
- Database: PostgreSQL + PostGIS
- Cache: Redis
- Device messaging: MQTT via EMQX
- Deployment: Docker Compose on Debian 13
- Clients: Flutter mobile apps and Vue 3 web console (planned)

## Repository status

The repository is being built in vertical slices. The first implementation slice establishes the backend service, health/readiness endpoints, database migration baseline, and CI checks. Hardware integrations and production security configuration must be validated against real devices and infrastructure before production use.

## Local development

Copy `.env.example` to `.env`, set development-only credentials, then run:

```bash
docker compose up -d --build
```

Backend health: `http://localhost:8080/actuator/health`

Do not use sample credentials in production. See `docs/` for architecture, telemetry, and security contracts.
