package app.SkillSync.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "billing_subscriptions")
public class BillingSubscription {

    @Id
    private String id;

    @Indexed(unique = true)
    private String organizationId;

    @Indexed
    private String stripeCustomerId;

    @Indexed
    private String stripeSubscriptionId;

    private String planId;
    private String status;
    private Instant billingPeriodEndsAt;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public String getId() {
        return id;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public String getStripeCustomerId() {
        return stripeCustomerId;
    }

    public String getStripeSubscriptionId() {
        return stripeSubscriptionId;
    }

    public String getPlanId() {
        return planId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getBillingPeriodEndsAt() {
        return billingPeriodEndsAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public void setStripeCustomerId(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }

    public void setStripeSubscriptionId(String stripeSubscriptionId) {
        this.stripeSubscriptionId = stripeSubscriptionId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setBillingPeriodEndsAt(Instant billingPeriodEndsAt) {
        this.billingPeriodEndsAt = billingPeriodEndsAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
