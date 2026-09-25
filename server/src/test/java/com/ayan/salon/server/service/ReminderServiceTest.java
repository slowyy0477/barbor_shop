package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.Customer;
import com.ayan.salon.server.domain.Reminder;
import com.ayan.salon.server.domain.DomainTypes.ReminderStatus;
import com.ayan.salon.server.domain.repository.CustomerRepository;
import com.ayan.salon.server.domain.repository.ReminderRepository;
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
class ReminderServiceTest {
    private final UUID salon = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();
    private final UUID visitId = UUID.randomUUID();

    @Mock ReminderRepository reminders;
    @Mock CustomerRepository customers;
    @Mock NotificationGateway notifications;

    private ReminderService service;

    @BeforeEach
    void setUp() {
        service = new ReminderService(reminders, notifications, customers);
        when(reminders.save(any(Reminder.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void scheduleRecordsOptedOutWhenCustomerHasNotConsented() {
        Customer customer = customer(false);
        when(reminders.findBySalonIdAndCustomerIdAndServiceIdAndSourceVisitId(salon, customerId, serviceId, visitId))
                .thenReturn(Optional.empty());
        when(customers.findBySalonIdAndId(salon, customerId)).thenReturn(Optional.of(customer));

        Reminder reminder = service.schedule(salon, customerId, serviceId, Instant.now().plusSeconds(3600), visitId);

        assertEquals(ReminderStatus.CANCELLED, reminder.getStatus());
        verify(notifications, never()).send(any(), any(), any(), any());
    }

    @Test
    void dispatchRechecksConsentBeforeSendingQueuedReminder() {
        Customer customer = customer(false);
        when(customers.findBySalonIdAndId(salon, customerId)).thenReturn(Optional.of(customer));
        Reminder reminder = new Reminder(salon, customerId, serviceId, Instant.now().minusSeconds(1), visitId);
        when(reminders.findByStatusAndDueAtBeforeAndOptedOutFalse(eq(ReminderStatus.SCHEDULED), any(Instant.class)))
                .thenReturn(List.of(reminder));

        service.dispatchDueReminders();

        assertEquals(ReminderStatus.CANCELLED, reminder.getStatus());
        verify(notifications, never()).send(any(), any(), any(), any());
    }

    @Test
    void dispatchSendsOnlyForActiveCustomerWithConsent() {
        Customer customer = customer(true);
        when(customers.findBySalonIdAndId(salon, customerId)).thenReturn(Optional.of(customer));
        Reminder reminder = new Reminder(salon, customerId, serviceId, Instant.now().minusSeconds(1), visitId);
        when(reminders.findByStatusAndDueAtBeforeAndOptedOutFalse(eq(ReminderStatus.SCHEDULED), any(Instant.class)))
                .thenReturn(List.of(reminder));

        service.dispatchDueReminders();

        assertEquals(ReminderStatus.SENT, reminder.getStatus());
        verify(notifications).send(any(), any(), any(), any());
    }

    private Customer customer(boolean consent) {
        Customer customer = new Customer(salon, "Customer", "03001234567", "phone-hash");
        customer.setMarketingConsent(consent);
        return customer;
    }
}
