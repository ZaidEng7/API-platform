-- Optimistic locking: SubscriptionTimeoutJob and confirmPayment()/cancel()
-- can all race on the same AWAITING_PAYMENT row from independent
-- transactions. Without a version check, whichever transaction commits
-- last silently overwrites the other's status (JPA's default save() issues
-- an unconditional UPDATE ... WHERE id = ?). Defaulting existing rows to 0
-- matches Hibernate's own @Version initial value.
ALTER TABLE subscriptions ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
