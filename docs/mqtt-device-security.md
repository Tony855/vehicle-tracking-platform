# MQTT device authentication and topic authorization

The EMQX service is configured with anonymous access disabled. Device usernames are their registered `device_uid`; passwords are the one-time-issued device credentials. EMQX delegates authentication to the backend, which validates the same credential hash used by HTTPS ingestion.

The backend-to-broker HTTP authentication and ACL callbacks require `X-MQTT-Auth-Secret`. Configure a randomly generated value of at least 32 characters as `MQTT_AUTH_SHARED_SECRET` in the deployment environment. It is also supplied to EMQX as `EMQX_AUTH_SHARED_SECRET`. Never commit production values.

Device ACL is derived from the active device record, not from a tenant ID supplied by the client. A device may publish only to its own tenant/device `location`, `status`, `event`, and `command-ack` topics, and may subscribe only to its own `command` topic. All other topics, actions, mismatched tenant/device identifiers, and unknown devices are denied.

## Topic shape

`v1/tenants/{tenantId}/devices/{deviceId}/{channel}`

## Development and production

The compose profile publishes TCP 1883 for development connectivity. MQTT over TLS is not enabled by this baseline; do not expose this listener to untrusted networks or production devices until TLS termination/listener certificates and network restrictions are configured. The backend callback endpoints must remain reachable only from the private compose network or an equivalently protected network segment.

The current slice supplies broker authentication and topic ACL callbacks. MQTT message consumption, JSON schema validation and persistence through the shared GPS ingestion pipeline are the next implementation step; a broker accepting a connection does not yet mean its location payload is stored.
