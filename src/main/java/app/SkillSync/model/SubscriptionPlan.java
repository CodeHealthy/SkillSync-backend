package app.SkillSync.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.List;

@Document(collection = "subscriptions")
public class SubscriptionPlan {

    @Id
    private String id;

    private String code;
    private String name;
    private String description;
    private BigDecimal pricing;
    private String currency;
    private String billingCycle;
    private String stripePriceId;
    private SubscriptionFeatures features;
    private List<String> highlights;
    private Boolean recommended;
    private Boolean active;
    private Integer displayOrder;
    private Boolean isFree;
    private String created;
    private String updated;

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public Boolean getIsFree() {
        return isFree;
    }

    public void setId(String id) {
        this.id = id;
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

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public void setIsFree(Boolean free) {
        isFree = free;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    public void setUpdated(String updated) {
        this.updated = updated;
    }
}
