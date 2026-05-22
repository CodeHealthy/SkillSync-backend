package app.SkillSync.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Document(collection = "subscriptions")
public class SubscriptionPlan {

    @Id
    private String id;

    private String code;
    private String name;
    private BigDecimal pricing;
    private String currency;
    private String billingCycle;
    private String stripePriceId;
    private SubscriptionFeatures features;
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

    public Boolean getIsFree() {
        return isFree;
    }
}