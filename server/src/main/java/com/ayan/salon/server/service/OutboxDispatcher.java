package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.NotificationOutbox;
import com.ayan.salon.server.domain.Customer;
import com.ayan.salon.server.domain.DomainTypes.AccountStatus;
import com.ayan.salon.server.domain.repository.CustomerRepository;
import com.ayan.salon.server.domain.repository.NotificationOutboxRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Retries queued messages without coupling delivery failure to wallet/booking transactions. */
@Service
public class OutboxDispatcher {
    private final NotificationOutboxRepository outbox;
    private final NotificationProvider provider;

    /** Optional only for compatibility with isolated tests/adapters. */
    private final CustomerRepository customers;

    public OutboxDispatcher(NotificationOutboxRepository outbox, NotificationProvider provider) {
        this(outbox, provider, null);
    }

    @Autowired
    public OutboxDispatcher(NotificationOutboxRepository outbox, NotificationProvider provider,
                            CustomerRepository customers) {
        this.outbox = outbox;
        this.provider = provider;
        this.customers = customers;
    }

    @Scheduled(fixedDelayString = "${ayan.notifications.dispatch-delay-ms:30000}")
    @Transactional
    public void dispatch() {
        Instant now = Instant.now();
        for (NotificationOutbox message : outbox.lockDue(now, PageRequest.of(0, 50))) {
            try {
                provider.deliver(message, resolveDestination(message));
                message.markSent(now);
            } catch (RuntimeException failure) {
                long delaySeconds = Math.min(3600, 30L * (1L << Math.min(6, message.getAttempts())));
                message.markFailure(failure.getMessage(), now.plus(Duration.ofSeconds(delaySeconds)));
            }
        }
    }

    /**
     * SMS destinations are looked up at dispatch time rather than copied into
     * the durable payload. This keeps the outbox free of phone numbers while
     * still giving a real provider the address it needs to send the message.
     */
    private String resolveDestination(NotificationOutbox message) {
        if (!"SMS".equalsIgnoreCase(message.getChannel()) || customers == null) return null;
        Customer customer = customers.findBySalonIdAndId(message.getSalonId(), message.getCustomerId())
                .filter(value -> value.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Notification recipient is unavailable"));
        String phone = customer.getPhone();
        if (phone == null || phone.isBlank()) {
            throw new IllegalStateException("Notification recipient has no mobile number");
        }
        return phone;
    }
}
