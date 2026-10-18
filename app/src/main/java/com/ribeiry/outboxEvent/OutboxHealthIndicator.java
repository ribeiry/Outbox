package com.ribeiry.outboxEvent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

@Component
public class OutboxHealthIndicator implements HealthIndicator {

    private final int pendingThreshold;
    private final OutboxEventsRepository repository;

    public OutboxHealthIndicator(
            OutboxEventsRepository repository,
            @Value("${outbox.pending-threshold:10}") int pendingThreshold) {
        this.repository = repository;
        this.pendingThreshold = pendingThreshold;
    }

    @Override
    public Health health() {
        long pending = repository.findByStatus(OutboxEvents.Status.PENDING).size();
        long failed  = repository.findByStatus(OutboxEvents.Status.FAILED).size();

        if (pending > pendingThreshold) {
            return Health.down()
                    .withDetail("pending", pending)
                    .withDetail("failed", failed)
                    .withDetail("reason", "Backlog de eventos PENDING acima do limite (" + pendingThreshold + ")")
                    .build();
        }

        if (failed > 0) {
            return new Health.Builder(new Status("WARN"))
                    .withDetail("pending", pending)
                    .withDetail("failed", failed)
                    .withDetail("reason", "Existem eventos com status FAILED")
                    .build();
        }

        return Health.up()
                .withDetail("pending", pending)
                .withDetail("failed", failed)
                .build();
    }
}
