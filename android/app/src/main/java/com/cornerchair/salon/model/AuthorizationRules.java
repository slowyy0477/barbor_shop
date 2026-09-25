package com.cornerchair.salon.model;

/** Server-side authorization helpers. These checks must run again on every sensitive API call. */
public final class AuthorizationRules {
    private AuthorizationRules() {
    }

    public static boolean can(StaffMember actor,
                              String targetSalonId,
                              Permission permission) {
        return actor != null
                && targetSalonId != null
                && targetSalonId.equals(actor.getSalonId())
                && actor.hasPermission(permission);
    }

    public static void require(StaffMember actor,
                               String targetSalonId,
                               Permission permission) {
        if (!can(actor, targetSalonId, permission)) {
            String actorId = actor == null ? "anonymous" : actor.getId();
            throw new UnauthorizedException(actorId, targetSalonId, permission);
        }
    }

    public static final class UnauthorizedException extends SecurityException {
        private static final long serialVersionUID = 1L;
        private final String actorId;
        private final String targetSalonId;
        private final Permission permission;

        private UnauthorizedException(String actorId,
                                      String targetSalonId,
                                      Permission permission) {
            super("actor " + actorId + " lacks " + permission + " for salon " + targetSalonId);
            this.actorId = actorId;
            this.targetSalonId = targetSalonId == null ? "" : targetSalonId;
            this.permission = permission;
        }

        public String getActorId() {
            return actorId;
        }

        public String getTargetSalonId() {
            return targetSalonId;
        }

        public Permission getPermission() {
            return permission;
        }
    }
}
