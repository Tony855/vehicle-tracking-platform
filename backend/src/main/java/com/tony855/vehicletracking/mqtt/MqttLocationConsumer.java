package com.tony855.vehicletracking.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tony855.vehicletracking.device.DeviceCredentialService;
import com.tony855.vehicletracking.telemetry.LocationIngestionService;
import com.tony855.vehicletracking.telemetry.LocationTelemetryRequest;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MqttLocationConsumer {
    private static final Logger log = LoggerFactory.getLogger(MqttLocationConsumer.class);
    private static final String LOCATION_FILTER = "v1/tenants/+/devices/+/location";
    private static final int MAX_PAYLOAD_BYTES = 65_536;

    private final String brokerUrl;
    private final String username;
    private final String password;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final DeviceCredentialService credentials;
    private final LocationIngestionService ingestion;
    private final ScheduledExecutorService connector =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "mqtt-location-connector");
            thread.setDaemon(true);
            return thread;
        });
    private volatile MqttClient client;

    public MqttLocationConsumer(
        @Value("${MQTT_BROKER_URL:tcp://emqx:1883}") String brokerUrl,
        @Value("${MQTT_INGESTOR_USERNAME:internal-gps-ingestor}") String username,
        @Value("${MQTT_INGESTOR_PASSWORD:}") String password,
        ObjectMapper objectMapper,
        Validator validator,
        DeviceCredentialService credentials,
        LocationIngestionService ingestion) {
        this.brokerUrl = brokerUrl;
        this.username = username;
        this.password = password;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.credentials = credentials;
        this.ingestion = ingestion;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startConnector() {
        if (password.length() < 32) {
            log.error("MQTT consumer disabled: MQTT_INGESTOR_PASSWORD must contain at least 32 characters");
            return;
        }
        connector.scheduleWithFixedDelay(this::connectIfNeeded, 0, 5, TimeUnit.SECONDS);
    }

    private synchronized void connectIfNeeded() {
        try {
            if (client != null && client.isConnected()) return;
            MqttClient candidate = new MqttClient(brokerUrl,
                "vehicle-tracking-gps-ingestor", new MemoryPersistence());
            candidate.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    try {
                        candidate.subscribe(LOCATION_FILTER, 1);
                        log.info("MQTT GPS consumer subscribed to authorized location topics");
                    } catch (MqttException e) {
                        log.error("Unable to subscribe MQTT GPS consumer", e);
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("MQTT GPS consumer connection lost: {}",
                        cause == null ? "unknown" : cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    consume(topic, message.getPayload());
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(username);
            options.setPassword(password.toCharArray());
            options.setAutomaticReconnect(true);
            options.setCleanSession(false);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);
            candidate.connect(options);
            client = candidate;
        } catch (Exception e) {
            log.warn("MQTT broker is not ready; retrying connection: {}", e.getMessage());
        }
    }

    private void consume(String topic, byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            log.warn("Discarding MQTT GPS payload with invalid size");
            return;
        }
        try {
            String[] parts = topic.split("/", -1);
            if (parts.length != 6 || !"v1".equals(parts[0])
                || !"tenants".equals(parts[1]) || !"devices".equals(parts[3])
                || !"location".equals(parts[5])) {
                log.warn("Discarding MQTT message with invalid location topic");
                return;
            }
            UUID tenantId = UUID.fromString(parts[2]);
            UUID deviceId = UUID.fromString(parts[4]);
            var principal = credentials.findActiveDevice(tenantId, deviceId).orElse(null);
            if (principal == null) {
                log.warn("Discarding MQTT location for unknown or inactive device");
                return;
            }
            LocationTelemetryRequest request = objectMapper.readValue(
                new String(payload, StandardCharsets.UTF_8), LocationTelemetryRequest.class);
            if (!principal.deviceUid().equals(request.deviceUid())
                || !validator.validate(request).isEmpty()) {
                log.warn("Discarding MQTT location with device identity or schema mismatch");
                return;
            }
            ingestion.ingestAuthenticated(principal, request);
        } catch (Exception e) {
            log.warn("Discarding invalid MQTT GPS message: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stopConnector() {
        connector.shutdownNow();
        MqttClient current = client;
        if (current != null) {
            try {
                if (current.isConnected()) current.disconnect();
                current.close();
            } catch (MqttException e) {
                log.warn("Error closing MQTT GPS consumer: {}", e.getMessage());
            }
        }
    }
}
