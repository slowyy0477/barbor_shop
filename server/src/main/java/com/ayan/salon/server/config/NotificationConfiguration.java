package com.ayan.salon.server.config;

import com.ayan.salon.server.domain.NotificationOutbox;
import com.ayan.salon.server.service.NotificationProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

@Configuration
public class NotificationConfiguration {
    /**
     * Delivery is separate from the durable outbox. The provider is intentionally
     * configured by environment and never stores payment credentials.
     */
    @Bean
    @Profile({"dev", "test"})
    NotificationProvider developmentNotificationProvider() {
        return message -> org.slf4j.LoggerFactory.getLogger("AyanNotifications")
                .info("Development notification delivered template={} customer={} (no external message sent)",
                        message.getTemplate(), message.getCustomerId());
    }

    @Bean
    @Profile("!dev & !test")
    NotificationProvider httpNotificationProvider(
            RestClient.Builder client,
            @Value("${ayan.notifications.provider-url:}") String providerUrl,
            @Value("${ayan.notifications.provider-token:}") String providerToken) {
        return new HttpNotificationProvider(client, providerUrl, providerToken);
    }

    static final class HttpNotificationProvider implements NotificationProvider {
        private final RestClient.Builder client;
        private final String providerUrl;
        private final String providerToken;
        HttpNotificationProvider(RestClient.Builder client, String providerUrl, String providerToken) {
            this.client = client;
            this.providerUrl = providerUrl == null ? "" : providerUrl.trim();
            this.providerToken = providerToken == null ? "" : providerToken.trim();
        }
        @Override
        public void deliver(NotificationOutbox message) {
            deliver(message, null);
        }

        @Override
        public void deliver(NotificationOutbox message, String destination) {
            if (providerUrl.isBlank() || providerToken.isBlank()) {
                throw new IllegalStateException("Notification provider is not configured; outbox message remains pending");
            }
            if ("SMS".equalsIgnoreCase(message.getChannel()) && (destination == null || destination.isBlank())) {
                throw new IllegalStateException("Notification SMS destination is unavailable; outbox message remains pending");
            }
            client.build().post().uri(providerUrl)
                    .header("Authorization", "Bearer " + providerToken)
                    .body(new NotificationPayload(message.getChannel(), destination, message.getCustomerId(), message.getTemplate(), message.getPayload()))
                    .retrieve().toBodilessEntity();
        }
        private record NotificationPayload(String channel, String destination, java.util.UUID customerId, String template, String payload) {}
    }
}
