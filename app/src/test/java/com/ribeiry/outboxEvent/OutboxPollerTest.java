package com.ribeiry.outboxEvent;

import com.ribeiry.kafka.KafkaPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

    @Mock
    private OutboxEventsRepository repository;

    @Mock
    private KafkaPublisher kafkaPublisher;

    private OutboxPoller poller;

    @BeforeEach
    void setup() {
        poller = new OutboxPoller(repository, kafkaPublisher, 3);
        ReflectionTestUtils.setField(poller, "Timereprocessing", 15);
    }

    @Test
    void poll_devePublicarEventoPendenteEMarcarComoProcessado() {
        OutboxEvents evento = evento(OutboxEvents.Status.PENDING, 0);
        when(repository.findByStatus(OutboxEvents.Status.PENDING)).thenReturn(List.of(evento));
        when(repository.findByStatusAndTentativasLessThan(OutboxEvents.Status.FAILED, 3)).thenReturn(List.of());

        poller.poll();

        verify(kafkaPublisher).publish("pedidos-eventos", evento.getAggregateId(), evento.getPayload());
        assertThat(evento.getStatus()).isEqualTo(OutboxEvents.Status.PROCESSED);
        verify(repository, times(2)).save(evento);
    }

    @Test
    void poll_deveMarcaFalhadoQuandoKafkaLancarExcecao() {
        OutboxEvents evento = evento(OutboxEvents.Status.PENDING, 0);
        when(repository.findByStatus(OutboxEvents.Status.PENDING)).thenReturn(List.of(evento));
        when(repository.findByStatusAndTentativasLessThan(OutboxEvents.Status.FAILED, 3)).thenReturn(List.of());
        doThrow(new RuntimeException("Kafka indisponível"))
                .when(kafkaPublisher).publish(anyString(), anyString(), anyString());

        poller.poll();

        assertThat(evento.getStatus()).isEqualTo(OutboxEvents.Status.FAILED);
        assertThat(evento.getTentativas()).isEqualTo(1);
        verify(repository, times(2)).save(evento);
    }

    @Test
    void poll_naoDeveProcessarNadaQuandoNaoHaEventosPendentes() {
        when(repository.findByStatus(OutboxEvents.Status.PENDING)).thenReturn(List.of());
        when(repository.findByStatusAndTentativasLessThan(OutboxEvents.Status.FAILED, 3)).thenReturn(List.of());

        poller.poll();

        verifyNoInteractions(kafkaPublisher);
        verify(repository, never()).save(any());
    }

    @Test
    void poll_deveReprocessarEventoFalhadoElegivelEMarcarComoProcessado() {
        OutboxEvents evento = evento(OutboxEvents.Status.FAILED, 1);
        when(repository.findByStatus(OutboxEvents.Status.PENDING)).thenReturn(List.of());
        when(repository.findByStatusAndTentativasLessThan(OutboxEvents.Status.FAILED, 3)).thenReturn(List.of(evento));

        poller.poll();

        verify(kafkaPublisher).publish("pedidos-eventos", evento.getAggregateId(), evento.getPayload());
        assertThat(evento.getStatus()).isEqualTo(OutboxEvents.Status.PROCESSED);
    }

    @Test
    void poll_deveIncrementarTentativasQuandoRetryFalhar() {
        OutboxEvents evento = evento(OutboxEvents.Status.FAILED, 1);
        when(repository.findByStatus(OutboxEvents.Status.PENDING)).thenReturn(List.of());
        when(repository.findByStatusAndTentativasLessThan(OutboxEvents.Status.FAILED, 3)).thenReturn(List.of(evento));
        doThrow(new RuntimeException("Kafka indisponível"))
                .when(kafkaPublisher).publish(anyString(), anyString(), anyString());

        poller.poll();

        assertThat(evento.getStatus()).isEqualTo(OutboxEvents.Status.FAILED);
        assertThat(evento.getTentativas()).isEqualTo(2);
    }

    @Test
    void poll_deveMarcarDeadLetterAoAtingirMaxTentativas() {
        OutboxEvents evento = evento(OutboxEvents.Status.FAILED, 2);
        when(repository.findByStatus(OutboxEvents.Status.PENDING)).thenReturn(List.of());
        when(repository.findByStatusAndTentativasLessThan(OutboxEvents.Status.FAILED, 3)).thenReturn(List.of(evento));
        doThrow(new RuntimeException("Kafka indisponível"))
                .when(kafkaPublisher).publish(anyString(), anyString(), anyString());

        poller.poll();

        assertThat(evento.getStatus()).isEqualTo(OutboxEvents.Status.DEAD_LETTER);
        assertThat(evento.getTentativas()).isEqualTo(3);
    }

    // --- idempotencia ---

    @Test
    void poll_devePularEventoQuandoBancoFalharAoSalvarProcessing() {
        OutboxEvents evento = evento(OutboxEvents.Status.PENDING, 0);
        when(repository.findByStatus(OutboxEvents.Status.PENDING)).thenReturn(List.of(evento));
        when(repository.findByStatusAndTentativasLessThan(OutboxEvents.Status.FAILED, 3)).thenReturn(List.of());
        doThrow(new RuntimeException("DB indisponível")).when(repository).save(evento);

        poller.poll();

        verifyNoInteractions(kafkaPublisher);
        assertThat(evento.getStatus()).isEqualTo(OutboxEvents.Status.FAILED);
    }

    // --- reprocessing ---

    @Test
    void reprocessing_deveResetarEventoProcessingTravatoParaPending() {
        OutboxEvents evento = evento(OutboxEvents.Status.PROCESSING, 0);
        when(repository.findByStatusAndCriadoEmBefore(eq(OutboxEvents.Status.PROCESSING), any()))
                .thenReturn(List.of(evento));

        poller.reprocessingSchedule();

        assertThat(evento.getStatus()).isEqualTo(OutboxEvents.Status.PENDING);
        verify(repository).save(evento);
    }

    @Test
    void reprocessing_naoDeveResetarNadaQuandoNaoHaEventosTravados() {
        when(repository.findByStatusAndCriadoEmBefore(eq(OutboxEvents.Status.PROCESSING), any()))
                .thenReturn(List.of());

        poller.reprocessingSchedule();

        verify(repository, never()).save(any());
    }

    private OutboxEvents evento(OutboxEvents.Status status, int tentativas) {
        OutboxEvents evento = new OutboxEvents();
        evento.setAggregateId(UUID.randomUUID().toString());
        evento.setEventType("PEDIDO_CRIADO");
        evento.setPayload("{\"pedidoID\":\"uuid\"}");
        evento.setStatus(status);
        evento.setTentativas(tentativas);
        return evento;
    }
}
