package com.cornerchair.salon.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Immutable staff principal used by server-side authorization checks. */
public final class StaffMember {
    private final String id;
    private final String salonId;
    private final String displayName;
    private final StaffRole role;
    /** Grants explicitly assigned by an owner, kept apart from role-derived defaults. */
    private final Set<Permission> explicitPermissions;
    private final Set<Permission> permissions;
    private final boolean active;
    private final long updatedAtMillis;

    public StaffMember(String id,
                       String salonId,
                       String displayName,
                       StaffRole role,
                       Set<Permission> permissions,
                       boolean active,
                       long updatedAtMillis) {
        DomainTime.requireNonBlank(id, "id");
        DomainTime.requireNonBlank(salonId, "salonId");
        DomainTime.requireNonBlank(displayName, "displayName");
        if (role == null) {
            throw new IllegalArgumentException("role is required");
        }
        EnumSet<Permission> explicit = EnumSet.noneOf(Permission.class);
        if (permissions != null) {
            for (Permission permission : permissions) {
                if (permission != null) {
                    explicit.add(permission);
                }
            }
        }
        EnumSet<Permission> grants = EnumSet.noneOf(Permission.class);
        grants.addAll(role.defaultPermissions());
        grants.addAll(explicit);
        this.id = id;
        this.salonId = salonId;
        this.displayName = displayName;
        this.role = role;
        this.explicitPermissions = Collections.unmodifiableSet(explicit);
        this.permissions = Collections.unmodifiableSet(grants);
        this.active = active;
        this.updatedAtMillis = updatedAtMillis;
    }

    public static StaffMember owner(String id,
                                    String salonId,
                                    String displayName,
                                    long updatedAtMillis) {
        return new StaffMember(id, salonId, displayName, StaffRole.OWNER,
                Collections.<Permission>emptySet(), true, updatedAtMillis);
    }

    public String getId() {
        return id;
    }

    public String getSalonId() {
        return salonId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public StaffRole getRole() {
        return role;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    /** Returns owner-issued grants without role defaults. */
    public Set<Permission> getExplicitPermissions() {
        return explicitPermissions;
    }

    public boolean isActive() {
        return active;
    }

    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }

    public boolean hasPermission(Permission permission) {
        return active && permission != null && permissions.contains(permission);
    }

    public StaffMember withActive(boolean nextActive, long atMillis) {
        return copy(role, explicitPermissions, nextActive, atMillis);
    }

    public StaffMember withRole(StaffRole nextRole, long atMillis) {
        if (nextRole == null) {
            throw new IllegalArgumentException("role is required");
        }
        // Keep explicit owner grants while recalculating role defaults for the new role.
        return copy(nextRole, explicitPermissions, active, atMillis);
    }

    public StaffMember withAdditionalPermissions(Set<Permission> additional, long atMillis) {
        EnumSet<Permission> next = EnumSet.noneOf(Permission.class);
        next.addAll(explicitPermissions);
        if (additional != null) {
            for (Permission permission : additional) {
                if (permission != null) {
                    next.add(permission);
                }
            }
        }
        return copy(role, next, active, atMillis);
    }

    private StaffMember copy(StaffRole nextRole,
                             Set<Permission> nextPermissions,
                             boolean nextActive,
                             long atMillis) {
        if (atMillis < updatedAtMillis) {
            throw new IllegalArgumentException("staff timestamps cannot move backwards");
        }
        return new StaffMember(id, salonId, displayName, nextRole, nextPermissions, nextActive, atMillis);
    }
}
