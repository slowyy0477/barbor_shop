package com.cornerchair.salon.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Suggested defaults for staff accounts. Explicit grants can further restrict or extend them. */
public enum StaffRole {
    OWNER,
    MANAGER,
    BARBER,
    STAFF,
    RECEPTIONIST;

    public Set<Permission> defaultPermissions() {
        EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
        switch (this) {
            case OWNER:
                permissions = EnumSet.allOf(Permission.class);
                break;
            case MANAGER:
                permissions.add(Permission.MANAGE_STAFF);
                permissions.add(Permission.MANAGE_SERVICES);
                permissions.add(Permission.MANAGE_PRICES);
                permissions.add(Permission.APPROVE_DEPOSITS);
                permissions.add(Permission.APPROVE_WITHDRAWALS);
                permissions.add(Permission.COMPLETE_SERVICE);
                permissions.add(Permission.VIEW_CUSTOMERS);
                permissions.add(Permission.VIEW_FINANCIAL_REPORTS);
                permissions.add(Permission.VIEW_AUDIT_LOG);
                permissions.add(Permission.MANAGE_REFERRALS);
                permissions.add(Permission.MANAGE_BOOKINGS);
                permissions.add(Permission.MANAGE_WALLET);
                permissions.add(Permission.REVIEW_FRAUD);
                break;
            case BARBER:
                permissions.add(Permission.COMPLETE_SERVICE);
                permissions.add(Permission.VIEW_CUSTOMERS);
                permissions.add(Permission.MANAGE_BOOKINGS);
                break;
            case RECEPTIONIST:
                permissions.add(Permission.VIEW_CUSTOMERS);
                permissions.add(Permission.MANAGE_BOOKINGS);
                break;
            case STAFF:
            default:
                permissions.add(Permission.VIEW_CUSTOMERS);
                break;
        }
        return Collections.unmodifiableSet(permissions);
    }
}
