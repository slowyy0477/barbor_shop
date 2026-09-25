package com.ayan.salon.server.web;

import com.ayan.salon.server.domain.*;
import com.ayan.salon.server.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/salons/{salonId}")
public class WalletController {
    private final WalletService walletService;
    private final AuthenticatedActorResolver actors;
    public WalletController(WalletService walletService, AuthenticatedActorResolver actors) { this.walletService = walletService; this.actors = actors; }

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Deposit submitDeposit(@PathVariable UUID salonId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ApiDtos.DepositRequest request) {
        ActorContext actor = actors.require(); actor.requireSalon(salonId); return walletService.submitDeposit(actor, request.amountMinor(), request.providerCode(), request.providerReference(), request.proofUri(), key);
    }
    @PostMapping("/deposits/{depositId}/approve")
    public Deposit approveDeposit(@PathVariable UUID salonId, @PathVariable UUID depositId, @RequestHeader("Idempotency-Key") String key) { ActorContext actor = actors.require(); actor.requireSalon(salonId); return walletService.approveDeposit(actor, depositId, key); }
    @PostMapping("/deposits/{depositId}/reject")
    public Deposit rejectDeposit(@PathVariable UUID salonId, @PathVariable UUID depositId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ApiDtos.ReviewRequest request) { ActorContext actor = actors.require(); actor.requireSalon(salonId); return walletService.rejectDeposit(actor, depositId, request.reason(), key); }
    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Withdrawal requestWithdrawal(@PathVariable UUID salonId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ApiDtos.WithdrawalRequest request) { ActorContext actor = actors.require(); actor.requireSalon(salonId); return walletService.requestWithdrawal(actor, request.amountMinor(), request.providerCode(), request.destinationToken(), key); }
    @PostMapping("/withdrawals/{withdrawalId}/approve")
    public Withdrawal approveWithdrawal(@PathVariable UUID salonId, @PathVariable UUID withdrawalId, @RequestHeader("Idempotency-Key") String key) { ActorContext actor = actors.require(); actor.requireSalon(salonId); return walletService.approveWithdrawal(actor, withdrawalId, key); }
    @PostMapping("/withdrawals/{withdrawalId}/complete")
    public Withdrawal completeWithdrawal(@PathVariable UUID salonId, @PathVariable UUID withdrawalId, @RequestHeader("Idempotency-Key") String key) { ActorContext actor = actors.require(); actor.requireSalon(salonId); return walletService.completeWithdrawal(actor, withdrawalId, key); }
    @PostMapping("/withdrawals/{withdrawalId}/reject")
    public Withdrawal rejectWithdrawal(@PathVariable UUID salonId, @PathVariable UUID withdrawalId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ApiDtos.ReviewRequest request) { ActorContext actor = actors.require(); actor.requireSalon(salonId); return walletService.rejectWithdrawal(actor, withdrawalId, request.reason(), key); }
    @PostMapping("/bookings/{bookingId}/complete")
    public Booking completeService(@PathVariable UUID salonId, @PathVariable UUID bookingId, @RequestHeader("Idempotency-Key") String key) { ActorContext actor = actors.require(); actor.requireSalon(salonId); return walletService.completeServicePayment(actor, bookingId, key); }
    @PostMapping("/referrals/{referralId}/release")
    public Referral releaseReferral(@PathVariable UUID salonId, @PathVariable UUID referralId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ApiDtos.ReferralReleaseRequest request) { ActorContext actor = actors.require(); actor.requireSalon(salonId); return walletService.releaseReferral(actor, referralId, request.qualifyingBookingId(), key); }
    @PostMapping("/wallet/adjustments")
    public Wallet adjustWallet(@PathVariable UUID salonId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ApiDtos.WalletAdjustmentRequest request) { ActorContext actor = actors.require(); actor.requireSalon(salonId); return walletService.adjustWallet(actor, request.customerId(), request.cashDeltaMinor(), request.promoDeltaMinor(), request.reason(), key); }
    @PostMapping("/wallet/refunds")
    public Wallet refund(@PathVariable UUID salonId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ApiDtos.RefundRequest request) { ActorContext actor = actors.require(); actor.requireSalon(salonId); return walletService.refundServicePayment(actor, request.customerId(), request.bookingId(), request.reason(), key); }
    @PostMapping("/wallet/reversals")
    public Wallet reverse(@PathVariable UUID salonId, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ApiDtos.ReversalRequest request) { ActorContext actor = actors.require(); actor.requireSalon(salonId); return walletService.reverseTransaction(actor, request.customerId(), request.transactionId(), request.reason(), key); }
}
