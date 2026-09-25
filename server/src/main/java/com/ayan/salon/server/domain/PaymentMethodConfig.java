package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;
import static com.ayan.salon.server.domain.DomainTypes.ProviderMode;

@Entity
@Table(name = "payment_method_configs")
public class PaymentMethodConfig extends TenantEntity {
    @Column(nullable = false, length = 24) private String provider;
    @Column(name = "display_name", nullable = false, length = 80) private String displayName;
    @Column(name = "account_title", length = 120) private String accountTitle;
    /** Public receiving number/handle shown to customers; never a PIN or secret. */
    @Column(name = "account_reference", length = 120) private String accountReference;
    @JsonIgnore
    @Column(name = "account_token", length = 300) private String accountToken;
    @Column(length = 500) private String instructions;
    @Column(nullable = false) private boolean enabled = true;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ProviderMode mode = ProviderMode.MANUAL;
    protected PaymentMethodConfig() {}
    public PaymentMethodConfig(UUID salonId, String provider, String displayName, String accountTitle, String accountToken, int sortOrder) { this(salonId, provider, displayName, accountTitle, null, accountToken, sortOrder); }
    public PaymentMethodConfig(UUID salonId, String provider, String displayName, String accountTitle, String accountReference, String accountToken, int sortOrder) { super(salonId); this.provider = provider; this.displayName = displayName; this.accountTitle = accountTitle; this.accountReference = accountReference; this.accountToken = accountToken; this.sortOrder = sortOrder; }
    public String getProvider() { return provider; }
    public String getDisplayName() { return displayName; }
    public String getAccountTitle() { return accountTitle; }
    public String getAccountReference() { return accountReference; }
    /** Alias for browser clients that use account-number terminology. */
    public String getAccountNumber() { return accountReference; }
    public String getInstructions() { return instructions; }
    public int getSortOrder() { return sortOrder; }
    @JsonIgnore
    public String getAccountToken() { return accountToken; }
    public boolean isEnabled() { return enabled; }
    public ProviderMode getMode() { return mode; }
    public void update(String displayName, String accountTitle, String accountToken, String instructions, boolean enabled, int sortOrder, ProviderMode mode) { update(displayName, accountTitle, null, accountToken, instructions, enabled, sortOrder, mode); }
    public void update(String displayName, String accountTitle, String accountReference, String accountToken, String instructions, boolean enabled, int sortOrder, ProviderMode mode) { this.displayName = displayName; this.accountTitle = accountTitle; this.accountReference = accountReference; this.accountToken = accountToken; this.instructions = instructions; this.enabled = enabled; this.sortOrder = sortOrder; this.mode = mode; }
}
