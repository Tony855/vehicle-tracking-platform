# Security baseline

- Give every device an independently revocable identity and credential; store only a strong hash of secrets where password-style credentials are used.
- Require TLS for all public API and MQTT traffic. Do not expose broker listener ports publicly until certificates and per-device ACLs are configured.
- Resolve tenant ownership from authenticated principals and database relationships; never authorize from client-supplied tenant IDs or MQTT topics alone.
- Apply tenant scoping to every query and add automated cross-tenant isolation tests before exposing business endpoints.
- Validate timestamps, coordinates, numeric ranges, payload size, replay windows, and event idempotency.
- Keep command/downlink support disabled until signed commands, expiry, nonce/replay protection, role checks, audit logs, and hardware safety review exist.
- Rotate secrets, back up PostgreSQL, test restores, and keep production secrets outside source control.
- Current compose file is a development baseline, not a production-hardened deployment.
