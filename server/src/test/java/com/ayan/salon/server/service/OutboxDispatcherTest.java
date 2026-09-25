package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.Customer;
import com.ayan.salon.server.domain.NotificationOutbox;
import com.ayan.salon.server.domain.repository.CustomerRepository;
import com.ayan.salon.server.domain.repository.NotificationOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxDispatcherTest {
    private final UUID salon = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @Mock NotificationOutboxRepository outbox;
    @Mock NotificationProvider provider;
    @Mock CustomerRepository customers;

    private OutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new OutboxDispatcher(outbox, provider, customers);
    }

    @Test
    void resolvesActiveCustomerPhoneOnlyAtDeliveryTime() {
        NotificationOutbox message = new NotificationOutbox(salon, customerId, "SMS",
                "WALLET_DEPOSIT_RECEIPT", "{\"paidCreditMinor\":50000}", Instant.now().minusSeconds(1));
        Customer customer = new Customer(salon, "Customer", "03001234567", "phone-hash");
        when(outbox.lockDue(any(Instant.class), any())).thenReturn(List.of(message));
        when(customers.findBySalonIdAndId(salon, customerId)).thenReturn(Optional.of(customer));

        dispatcher.dispatch();

        verify(provider).deliver(message, "03001234567");
        assertEquals("SENT", message.getStatus());
    }

    @Test
    void leavesMessagePendingWhenRecipientCannotBeResolved() {
        NotificationOutbox message = new NotificationOutbox(salon, customerId, "SMS",
                "WALLET_PAYMENT_RECEIPT", "{}", Instant.now().minusSeconds(1));
        when(outbox.lockDue(any(Instant.class), any())).thenReturn(List.of(message));
        when(customers.findBySalonIdAndId(salon, customerId)).thenReturn(Optional.empty());

        dispatcher.dispatch();

        verify(provider, never()).deliver(any(NotificationOutbox.class), any(String.class));
        assertEquals("PENDING", message.getStatus());
        assertEquals(1, message.getAttempts());
    }
}
