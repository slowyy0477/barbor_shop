package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.Reminder;
import com.ayan.salon.server.domain.Customer;
import com.ayan.salon.server.domain.DomainTypes.AccountStatus;
import com.ayan.salon.server.domain.repository.CustomerRepository;
import com.ayan.salon.server.domain.repository.ReminderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
public class ReminderService {
    private final ReminderRepository reminders;
    private final NotificationGateway notifications;
    private final CustomerRepository customers;

    /** Compatibility constructor for small unit callers that do not model customers. */
    public ReminderService(ReminderRepository reminders, NotificationGateway notifications) {
        this(reminders, notifications, null);
    }

    @Autowired
    public ReminderService(ReminderRepository reminders, NotificationGateway notifications, CustomerRepository customers) {
        this.reminders = reminders;
        this.notifications = notifications;
        this.customers = customers;
    }

    @Transactional
    public Reminder schedule(UUID salonId, UUID customerId, UUID serviceId, Instant dueAt, UUID visitId) {
        java.util.Optional<Reminder> existing = reminders.findBySalonIdAndCustomerIdAndServiceIdAndSourceVisitId(salonId, customerId, serviceId, visitId);
        if (existing.isPresent()) return existing.get();
        Reminder reminder = new Reminder(salonId, customerId, serviceId, dueAt, visitId);
        if (!hasCommunicationConsent(salonId, customerId)) reminder.optOut();
        return reminders.save(reminder);
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void dispatchDueReminders() {
        Instant now = Instant.now();
        reminders.findByStatusAndDueAtBeforeAndOptedOutFalse(com.ayan.salon.server.domain.DomainTypes.ReminderStatus.SCHEDULED, now)
                .forEach(reminder -> dispatch(reminder));
    }

    private void dispatch(Reminder reminder) {
        if (!hasCommunicationConsent(reminder.getSalonId(), reminder.getCustomerId())) {
            reminder.optOut();
            reminders.save(reminder);
            return;
        }
        notifications.send(reminder.getSalonId(), reminder.getCustomerId(), "SERVICE_DUE", "serviceId=" + reminder.getServiceId() + ", dueAt=" + reminder.getDueAt());
        reminder.sent();
        reminders.save(reminder);
    }

    private boolean hasCommunicationConsent(UUID salonId, UUID customerId) {
        // The two-argument constructor is retained for isolated domain tests; the
        // Spring-managed path always has a repository and therefore fails closed.
        if (customers == null) return true;
        return customers.findBySalonIdAndId(salonId, customerId)
                .filter(customer -> customer.getStatus() == AccountStatus.ACTIVE)
                .map(Customer::isMarketingConsent)
                .orElse(false);
    }
}
