package app.SkillSync.controller;

import app.SkillSync.dto.BillingCheckoutSessionRequest;
import app.SkillSync.dto.BillingSessionResponse;
import app.SkillSync.dto.BillingSubscriptionResponse;
import app.SkillSync.service.BillingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/subscription")
    public ResponseEntity<BillingSubscriptionResponse> getSubscription() {
        return ResponseEntity.ok(billingService.getCurrentSubscription());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/checkout-session")
    public ResponseEntity<BillingSessionResponse> createCheckoutSession(
            @Valid @RequestBody BillingCheckoutSessionRequest request
    ) {
        return ResponseEntity.ok(billingService.createCheckoutSession(request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/customer-portal-session")
    public ResponseEntity<BillingSessionResponse> createCustomerPortalSession() {
        return ResponseEntity.ok(billingService.createCustomerPortalSession());
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signatureHeader
    ) {
        billingService.handleStripeWebhook(payload, signatureHeader);
        return ResponseEntity.ok().build();
    }
}
