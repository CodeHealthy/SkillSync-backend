package app.SkillSync.dto;

import jakarta.validation.constraints.NotBlank;

public class BillingCheckoutSessionRequest {

    @NotBlank
    private String planId;

    private String successUrl;
    private String cancelUrl;

    public String getPlanId() {
        return planId;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }
}
