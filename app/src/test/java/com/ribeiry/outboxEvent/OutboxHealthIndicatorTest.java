package com.ribeiry.outboxEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxHealthIndicatorTest {

    @Mock
    private OutboxEventsRepository repository;

    private OutboxHealthIndicator indicator;

    @BeforeEach
    void setup() {
        indicator = new OutboxHealthIndicator(repository, 10);
    }

    @Test
    void health_deveRetornarUpQuandoSemEventosPendentesEFailed() {
        when(repository.findByStatus(OutboxEvents.Status.PENDING)).thenReturn(Collections.emptyList());
        when(repository.findByStatus(OutboxEvents.Status.FAILED)).thenReturn(Collections.emptyList());

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void health_deveRetornarUpQuandoPendingNoLimite() {
        List<OutboxEvents> pending = Collections.nCopies(10, new OutboxEvents());
        when(repository.findByStatus(OutboxEvents.Status.PENDING)).thenReturn(pending);
        when(repository.findByStatus(OutboxEvents.Status.FAILED)).thenReturn(Collections.emptyList());

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void health_deveRetornarWarnQuandoHaEventosFailed() {
        when(repository.findByStatus(OutboxEvents.Status.PENDING)).thenReturn(Collections.emptyList());
        when(repository.findByStatus(OutboxEvents.Status.FAILED)).thenReturn(List.of(new OutboxEvents()));

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("WARN");
    }

    @Test
    void health_deveRetornarDownQuandoPendingAcimaDoLimite() {
        List<OutboxEvents> pending = Collections.nCopies(11, new OutboxEvents());
        when(repository.findByStatus(OutboxEvents.Status.PENDING)).thenReturn(pending);
        when(repository.findByStatus(OutboxEvents.Status.FAILED)).thenReturn(Collections.emptyList());

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void health_deveIncluirDetalhesDePendingEFailed() {
        when(repository.findByStatus(OutboxEvents.Status.PENDING)).thenReturn(List.of(new OutboxEvents()));
        when(repository.findByStatus(OutboxEvents.Status.FAILED)).thenReturn(Collections.emptyList());

        Health health = indicator.health();

        assertThat(health.getDetails()).containsKey("pending");
        assertThat(health.getDetails()).containsKey("failed");
    }

    @Test
    void health_downDeveIncluirMotivoNoDetalhe() {
        List<OutboxEvents> pending = Collections.nCopies(11, new OutboxEvents());
        when(repository.findByStatus(OutboxEvents.Status.PENDING)).thenReturn(pending);
        when(repository.findByStatus(OutboxEvents.Status.FAILED)).thenReturn(Collections.emptyList());

        Health health = indicator.health();

        assertThat(health.getDetails()).containsKey("reason");
    }
}
