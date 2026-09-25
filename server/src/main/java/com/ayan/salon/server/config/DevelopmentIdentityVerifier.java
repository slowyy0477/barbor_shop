package com.ayan.salon.server.config;

import com.ayan.salon.server.domain.DomainTypes.ActorRole;
import com.ayan.salon.server.service.ActorContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Development/test only token format: role:salonUuid:actorUuid. Never enable this profile in production. */
@Component
@Profile({"dev", "test"})
public class DevelopmentIdentityVerifier implements IdentityVerifier {
    @Override public ActorContext verify(String bearerToken) {
        String[] parts = bearerToken.split(":", 3);
        if (parts.length != 3) throw new ActorContext.AuthorizationException("Invalid development token");
        ActorRole role = ActorRole.valueOf(parts[0]);
        Set<String> permissions = new HashSet<>();
        if (role == ActorRole.CUSTOMER) {
            permissions.addAll(Set.of("submit_deposit", "request_withdrawal", "create_booking", "create_referral"));
        } else if (role == ActorRole.MANAGER || role == ActorRole.RECEPTIONIST) {
            permissions.addAll(Set.of("manage_customers", "manage_services", "manage_staff", "manage_bookings", "create_booking", "create_referral", "submit_deposit", "request_withdrawal", "approve_deposits", "approve_withdrawals", "complete_service", "approve_referrals", "adjust_wallet", "refund_service", "reverse_transaction", "modify_business_settings", "view_financial_reports"));
        } else if (role == ActorRole.BARBER || role == ActorRole.STAFF) {
            permissions.addAll(Set.of("create_booking", "complete_service"));
        }
        return new ActorContext(UUID.fromString(parts[2]), UUID.fromString(parts[1]), role, Set.copyOf(permissions));
    }
}
