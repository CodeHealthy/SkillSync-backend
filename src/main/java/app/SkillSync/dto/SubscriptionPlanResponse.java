package app.SkillSync.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class SubscriptionPlanResponse {

    private String id;
    private String code;
    private String name;
    private String description;
    private BigDecimal pricing;
    private String currency;
    private String billingCycle;
    private Map<String, Object> features;
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
