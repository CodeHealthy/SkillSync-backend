package app.SkillSync.dto;

import app.SkillSync.model.SubscriptionFeatures;

import java.math.BigDecimal;
import java.util.List;

public class SubscriptionPlanResponse {

    private String id;
    private String code;
    private String name;
    private String description;
    private BigDecimal pricing;
    private String currency;
    private String billingCycle;
    private SubscriptionFeatures features;
    private List<String> highlights;
    private Boolean recommended;
    private Boolean active;
    private Boolean isFree;
    private Integer displayOrder;

    public String getId() {
        return id;
    }

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

    public SubscriptionFeatures getFeatures() {
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

    public void setId(String id) {
        this.id = id;
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

    public void setFeatures(SubscriptionFeatures features) {
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
