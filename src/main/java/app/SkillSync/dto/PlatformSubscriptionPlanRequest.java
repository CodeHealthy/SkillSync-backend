package app.SkillSync.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class PlatformSubscriptionPlanRequest {

    @NotBlank
    @Size(max = 80)
    private String code;

    @NotBlank
    @Size(max = 120)
    private String name;

    @Size(max = 500)
    private String description;

    @DecimalMin("0.00")
    private BigDecimal pricing;

    @Size(max = 10)
    private String currency;

    @Size(max = 40)
    private String billingCycle;

    @Size(max = 255)
    private String stripePriceId;

    private Map<String, Object> features;
    private List<String> highlights;
    private Boolean recommended;
    private Boolean active;
    private Boolean isFree;
    private Integer displayOrder;

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPricing() {
        return pricing;
    }

    public String getCurrency() {
        return currency;
    }

    public String getBillingCycle() {
        return billingCycle;
    }

    public String getStripePriceId() {
        return stripePriceId;
    }

    public Map<String, Object> getFeatures() {
        return features;
    }

    public List<String> getHighlights() {
        return highlights;
    }

    public Boolean getRecommended() {
        return recommended;
    }

    public Boolean getActive() {
        return active;
    }

    public Boolean getIsFree() {
        return isFree;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPricing(BigDecimal pricing) {
        this.pricing = pricing;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setBillingCycle(String billingCycle) {
        this.billingCycle = billingCycle;
    }

    public void setStripePriceId(String stripePriceId) {
        this.stripePriceId = stripePriceId;
    }

    public void setFeatures(Map<String, Object> features) {
        this.features = features;
    }

    public void setHighlights(List<String> highlights) {
        this.highlights = highlights;
    }

    public void setRecommended(Boolean recommended) {
        this.recommended = recommended;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public void setIsFree(Boolean isFree) {
        this.isFree = isFree;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}
