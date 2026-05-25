package app.SkillSync.dto;

import java.time.Instant;
import java.util.Map;

public class BillingSubscriptionResponse {

    private String planId;
    private String status;
    private Instant billingPeriodEndsAt;
    private Map<String, Object> usage;
    private SubscriptionPlanResponse plan;

    public String getPlanId() {
        return planId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getBillingPeriodEndsAt() {
        return billingPeriodEndsAt;
    }

    public Map<String, Object> getUsage() {
        return usage;
    }

    public SubscriptionPlanResponse getPlan() {
        return plan;
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

    public void setUsage(Map<String, Object> usage) {
        this.usage = usage;
    }

    public void setPlan(SubscriptionPlanResponse plan) {
        this.plan = plan;
    }
}
