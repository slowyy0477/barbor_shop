package com.ayan.salon.server.config;

import com.ayan.salon.server.domain.NotificationOutbox;
import com.ayan.salon.server.service.NotificationProvider;
import com.ayan.salon.server.service.SmsDeliveryClient;
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
            @Value("${ayan.notifications.provider-token:}") String providerToken,
            @Value("${ayan.sms.provider:}") String smsProvider,
            @Value("${ayan.sms.twilio.account-sid:}") String twilioSid,
            @Value("${ayan.sms.twilio.auth-token:}") String twilioToken,
            @Value("${ayan.sms.twilio.from-number:}") String twilioFrom,
            @Value("${ayan.sms.whatsapp.token:}") String whatsappToken,
            @Value("${ayan.sms.whatsapp.phone-number-id:}") String whatsappPhoneId,
            @Value("${ayan.sms.webhook.url:}") String webhookUrl,
            @Value("${ayan.sms.webhook.token:}") String webhookToken) {
        SmsDeliveryClient sms = SmsDeliveryClient.from(smsProvider,
                SmsDeliveryClient.Config.from(twilioSid, twilioToken, twilioFrom, whatsappToken,
                        whatsappPhoneId, webhookUrl, webhookToken));
        return new HttpNotificationProvider(client, providerUrl, providerToken, sms);
    }

    static final class HttpNotificationProvider implements NotificationProvider {
        private final RestClient.Builder client;
        private final String providerUrl;
        private final String providerToken;
        private final SmsDeliveryClient sms;
        HttpNotificationProvider(RestClient.Builder client, String providerUrl, String providerToken) {
            this(client, providerUrl, providerToken, null);
        }
        HttpNotificationProvider(RestClient.Builder client, String providerUrl, String providerToken,
                                 SmsDeliveryClient sms) {
            this.client = client;
            this.providerUrl = providerUrl == null ? "" : providerUrl.trim();
            this.providerToken = providerToken == null ? "" : providerToken.trim();
            this.sms = sms;
        }
        @Override
        public void deliver(NotificationOutbox message) {
            deliver(message, null);
        }

        @Override
        public void deliver(NotificationOutbox message, String destination) {
            // SMS receipts and reminders go straight to the customer's phone when
            // a provider is connected, and stay in the outbox for a retry when the
            // provider is unreachable.
            if (sms != null && sms.isConfigured() && "SMS".equalsIgnoreCase(message.getChannel())) {
                if (destination == null || destination.isBlank()) {
                    throw new IllegalStateException("Notification SMS destination is unavailable; outbox message remains pending");
                }
                sms.send(destination, notificationText(message));
                return;
            }
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

        /** The stored payload is already the customer-facing text for salon templates. */
        static String notificationText(NotificationOutbox message) {
            String payload = message.getPayload() == null ? "" : message.getPayload().trim();
            return payload.isEmpty() ? message.getTemplate() : payload;
        }
    }
}
