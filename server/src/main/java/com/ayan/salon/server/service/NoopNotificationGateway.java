package com.ayan.salon.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

public class NoopNotificationGateway implements NotificationGateway {
    private static final Logger log = LoggerFactory.getLogger(NoopNotificationGateway.class);
    @Override public void send(UUID salonId, UUID customerId, String template, String payload) {
        log.info("Notification queued template={} salon={} customer={}", template, salonId, customerId);
    }
}
