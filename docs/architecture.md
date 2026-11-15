# Architecture baseline

## Services
- API: Spring Boot 3 / Java 21; tenant-aware business APIs.
- Database: PostgreSQL 16 + PostGIS; durable tenant, device, vehicle and location data.
- Cache: Redis 7; short-lived online state and rate-limit counters.
- MQTT broker: EMQX 5; device uplink/downlink transport. Broker deployment and ACL provisioning are a separate integration task.
- Mobile: Flutter Android/iOS.
- Web: Vue 3 administration console.

## Device families
The protocol accepts Android head units, ESP32 GNSS devices, and standalone 4G GNSS terminals through a normalized telemetry contract. Device-specific adapters normalize vendor payloads before ingestion.

## Data flow
Device -> MQTT TLS -> authenticated ingestion adapter -> schema validation and tenant/device authorization -> idempotent location persistence -> client fanout.

## Scaling principles
Partition high-volume location history by time only after measured workload justifies it; keep latest-position projections separate from immutable history; use asynchronous ingestion and bounded queues. Capacity claims require documented load tests.
