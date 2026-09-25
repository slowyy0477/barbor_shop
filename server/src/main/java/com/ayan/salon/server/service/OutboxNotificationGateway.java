package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.NotificationOutbox;
import com.ayan.salon.server.domain.repository.NotificationOutboxRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** NotificationGateway implementation that durably records intent before delivery. */
@Component
public class OutboxNotificationGateway implements NotificationGateway {
    private final NotificationOutboxRepository outbox;
    public OutboxNotificationGateway(NotificationOutboxRepository outbox) { this.outbox = outbox; }

    @Override
    @Transactional
    public void send(UUID salonId, UUID customerId, String template, String payload) {
        if (salonId == null || customerId == null || template == null || template.isBlank()) return;
        outbox.save(new NotificationOutbox(salonId, customerId, "SMS", template,
                payload == null ? "" : payload, Instant.now()));
    }
}
