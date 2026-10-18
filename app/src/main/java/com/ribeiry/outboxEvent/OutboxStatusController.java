package com.ribeiry.outboxEvent;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Outbox", description = "Monitoramento do Outbox de eventos")
@RestController
@RequestMapping("/outbox")
public class OutboxStatusController {

    private final OutboxEventsRepository repository;

    public OutboxStatusController(OutboxEventsRepository repository) {
        this.repository = repository;
    }

    @Operation(summary = "Status do Outbox", description = "Retorna a contagem de eventos por status (PENDING, PROCESSED, FAILED)")
    @ApiResponse(responseCode = "200", description = "Contagem retornada com sucesso")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Long>> status() {
        long pending   = repository.findByStatus(OutboxEvents.Status.PENDING).size();
        long processed = repository.findByStatus(OutboxEvents.Status.PROCESSED).size();
        long failed    = repository.findByStatus(OutboxEvents.Status.FAILED).size();

        return ResponseEntity.ok(Map.of(
                "pending",   pending,
                "processed", processed,
                "failed",    failed
        ));
    }
}
