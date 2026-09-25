package com.ayan.salon.server.domain;

import com.ayan.salon.server.domain.DomainTypes.ReminderStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReminderTest {
    @Test
    void confirmedBookingConvertsScheduledAndAlreadySentReminderWithoutDeletingHistory() {
        Reminder scheduled = new Reminder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plusSeconds(3600), UUID.randomUUID());
        scheduled.booked();
        assertEquals(ReminderStatus.BOOKED, scheduled.getStatus());

        Reminder sent = new Reminder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plusSeconds(3600), UUID.randomUUID());
        sent.sent();
        sent.booked();
        assertEquals(ReminderStatus.BOOKED, sent.getStatus());
    }
}
