package com.ribeiry.outboxEvent;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventsTest {

    @Test
    void constructor_deveInicializarTentativasComZero() {
        OutboxEvents evento = new OutboxEvents();
        assertThat(evento.getTentativas()).isZero();
    }

    @Test
    void constructor_deveInicializarStatusComoNulo() {
        OutboxEvents evento = new OutboxEvents();
        assertThat(evento.getStatus()).isNull();
    }

    @Test
    void onCreate_deveDefinirStatusPendingECriadoEm() {
        OutboxEvents evento = new OutboxEvents();
        evento.onCreate();

        assertThat(evento.getStatus()).isEqualTo(OutboxEvents.Status.PENDING);
        assertThat(evento.getCriadoEm()).isNotNull();
        assertThat(evento.getCriadoEm()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void setters_devemPersistirValores() {
        UUID id = UUID.randomUUID();
        LocalDateTime agora = LocalDateTime.now();

        OutboxEvents evento = new OutboxEvents();
        evento.setId(id);
        evento.setAggregateId("agg-123");
        evento.setEventType("PEDIDO_CRIADO");
        evento.setPayload("{\"chave\":\"valor\"}");
        evento.setStatus(OutboxEvents.Status.PROCESSED);
        evento.setCriadoEm(agora);
        evento.setTentativas(3);

        assertThat(evento.getId()).isEqualTo(id);
        assertThat(evento.getAggregateId()).isEqualTo("agg-123");
        assertThat(evento.getEventType()).isEqualTo("PEDIDO_CRIADO");
        assertThat(evento.getPayload()).isEqualTo("{\"chave\":\"valor\"}");
        assertThat(evento.getStatus()).isEqualTo(OutboxEvents.Status.PROCESSED);
        assertThat(evento.getCriadoEm()).isEqualTo(agora);
        assertThat(evento.getTentativas()).isEqualTo(3);
    }

    @Test
    void status_deveConterTodosOsValoresEsperados() {
        assertThat(OutboxEvents.Status.values()).containsExactlyInAnyOrder(
                OutboxEvents.Status.PENDING,
                OutboxEvents.Status.PROCESSED,
                OutboxEvents.Status.FAILED,
                OutboxEvents.Status.DEAD_LETTER,
                OutboxEvents.Status.PROCESSING
        );
    }

    @Test
    void status_FAILED_deveSerDistintoDeProcessed() {
        assertThat(OutboxEvents.Status.FAILED).isNotEqualTo(OutboxEvents.Status.PROCESSED);
    }

    @Test
    void tentativas_deveAceitarIncremento() {
        OutboxEvents evento = new OutboxEvents();
        evento.setTentativas(evento.getTentativas() + 1);
        assertThat(evento.getTentativas()).isEqualTo(1);
    }

    @Test
    void onCreate_deveSobreescreverStatusAnterior() {
        OutboxEvents evento = new OutboxEvents();
        evento.setStatus(OutboxEvents.Status.FAILED);
        evento.onCreate();

        assertThat(evento.getStatus()).isEqualTo(OutboxEvents.Status.PENDING);
    }
}
