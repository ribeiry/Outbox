package com.ribeiry.outboxEvent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


public interface OutboxEventsRepository extends JpaRepository<OutboxEvents, UUID> {
    List<OutboxEvents> findByStatus(OutboxEvents.Status status);
    List<OutboxEvents> findByStatusAndTentativasLessThan(OutboxEvents.Status status, int maxTentativas);
    List<OutboxEvents> findByStatusAndCriadoEmBefore(OutboxEvents.Status status, LocalDateTime criadoEm);

}