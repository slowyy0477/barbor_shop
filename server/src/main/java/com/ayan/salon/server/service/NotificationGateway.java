package com.ayan.salon.server.service;

import java.util.UUID;

/**
 * Notification boundary. Production should bind this to an approved SMS/WhatsApp/push
 * provider. The default implementation records no credentials and sends nothing.
 */
public interface NotificationGateway {
    void send(UUID salonId, UUID customerId, String template, String payload);
}
