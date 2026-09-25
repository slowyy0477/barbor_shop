package com.ayan.salon.server.service;

import java.util.UUID;

/** Adapter seam for official merchant APIs. Manual providers intentionally do not fake API calls. */
public interface PaymentProvider {
    String providerCode();
    ProviderResult verifyIncoming(UUID salonId, String providerReference, long amountMinor);
    ProviderResult payout(UUID salonId, String destinationToken, long amountMinor);
    record ProviderResult(boolean accepted, String externalReference, String reason) {}
}
