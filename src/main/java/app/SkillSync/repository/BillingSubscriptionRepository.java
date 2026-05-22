package app.SkillSync.repository;

import app.SkillSync.model.BillingSubscription;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface BillingSubscriptionRepository extends MongoRepository<BillingSubscription, String> {
    Optional<BillingSubscription> findByOrganizationId(String organizationId);
    Optional<BillingSubscription> findByStripeCustomerId(String stripeCustomerId);
    Optional<BillingSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);
}
