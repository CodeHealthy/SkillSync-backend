package app.SkillSync.repository;

import app.SkillSync.model.SubscriptionPlan;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SubscriptionPlanRepository extends MongoRepository<SubscriptionPlan, String> {

    Optional<SubscriptionPlan> findByCode(String code);

    Optional<SubscriptionPlan> findFirstByCodeIgnoreCase(String code);

    Optional<SubscriptionPlan> findFirstByNameIgnoreCase(String name);

    Optional<SubscriptionPlan> findByStripePriceId(String stripePriceId);

    Optional<SubscriptionPlan> findFirstByIsFreeTrue();
}
