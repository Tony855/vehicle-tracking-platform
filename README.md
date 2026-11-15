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

The repository is being built in vertical slices. Implemented: backend service baseline, health endpoint, PostgreSQL/PostGIS schema, normalized telemetry DTO validation, tenant-scoped device enrollment with quota/expiry/revocation checks, one-time device credential issuance and server-side credential verification, authenticated GPS location ingestion with PostGIS persistence and event deduplication, device heartbeat presence, and device-scoped latest/history location query APIs, EMQX broker integration with backend-validated device authentication and topic ACLs, a restricted internal MQTT GPS subscriber, shared HTTPS/MQTT PostGIS ingestion and CI checks. Not yet implemented: public MQTT TLS listener, credential rotation/revocation APIs, mobile apps and web console. Hardware integrations and production security configuration must be validated before production use.

## Local development

Copy `.env.example` to `.env`, set development-only credentials, then run:

```bash
docker compose up -d --build
```

Backend health: `http://localhost:8080/actuator/health`

Do not use sample credentials in production. See `docs/` for architecture, telemetry, and security contracts.
