package com.cornerchair.salon.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reminder scheduling, opt-out, and duplicate suppression rules. */
public final class ReminderRules {
    private ReminderRules() {
    }

    public static ScheduleResult scheduleAfterCompletedVisit(String reminderId,
                                                              Visit visit,
                                                              boolean optedOut,
                                                              List<Reminder> existing) {
        DomainTime.requireNonBlank(reminderId, "reminderId");
        if (visit == null || existing == null) {
            throw new IllegalArgumentException("visit and existing reminders are required");
        }
        for (Reminder reminder : existing) {
            if (reminder != null && reminder.getVisitId().equals(visit.getId())) {
                return new ScheduleResult(Collections.unmodifiableList(new ArrayList<Reminder>(existing)),
                        reminder, false);
            }
        }
        Reminder reminder = optedOut
                ? Reminder.optedOut(reminderId, visit)
                : Reminder.scheduled(reminderId, visit);
        ArrayList<Reminder> next = new ArrayList<Reminder>(existing);
        next.add(reminder);
        return new ScheduleResult(Collections.unmodifiableList(next), reminder, true);
    }

    /**
     * A confirmed booking consumes the active reminder for the same customer/service cycle. The
     * reminder record remains in history with BOOKED status, so duplicate sends can be audited.
     */
    public static List<Reminder> suppressForConfirmedBooking(List<Reminder> existing,
                                                              Booking confirmedBooking) {
        if (existing == null || confirmedBooking == null) {
            throw new IllegalArgumentException("existing reminders and booking are required");
        }
        ArrayList<Reminder> next = new ArrayList<Reminder>();
        for (Reminder reminder : existing) {
            if (reminder != null && reminder.isActive()
                    && reminder.getSalonId().equals(confirmedBooking.getSalonId())
                    && reminder.getCustomerId().equals(confirmedBooking.getCustomerId())
                    && reminder.getServiceId().equals(confirmedBooking.getServiceId())
                    && reminder.getCycleKey().equals(confirmedBooking.getServiceFamilyKey())) {
                next.add(reminder.markBooked());
            } else {
                next.add(reminder);
            }
        }
        return Collections.unmodifiableList(next);
    }

    public static final class ScheduleResult {
        private final List<Reminder> reminders;
        private final Reminder reminder;
        private final boolean createdNewReminder;

        private ScheduleResult(List<Reminder> reminders,
                               Reminder reminder,
                               boolean createdNewReminder) {
            this.reminders = reminders;
            this.reminder = reminder;
            this.createdNewReminder = createdNewReminder;
        }

        public List<Reminder> getReminders() {
            return reminders;
        }

        public Reminder getReminder() {
            return reminder;
        }

        public boolean createdNewReminder() {
            return createdNewReminder;
        }
    }
}
