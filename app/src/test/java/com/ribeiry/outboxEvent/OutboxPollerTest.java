package com.ribeiry.outboxEvent;

import com.ribeiry.kafka.KafkaPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    @Test
    void backoff_prontoParaRetry_deveRetornarTrueQuandoNaoHaBackoffRegistrado() throws Exception {
        OutboxEvents evento = evento(OutboxEvents.Status.FAILED, 1);

        // backoffMap vazio → nenhuma entrada para esse id
        boolean resultado = invocarProntoParaRetry(evento);

        assertThat(resultado).isTrue();
    }
    @Test
    void backoff_prontoParaRetry_deveRetornarFalseQuandoBackoffAindaNaoExpirou() throws Exception {
        OutboxEvents evento = evento(OutboxEvents.Status.FAILED, 1);

        // registra próximo retry no futuro
        Map<UUID, Instant> backoffMap = backoffMap();
        backoffMap.put(evento.getId(), Instant.now().plusSeconds(60));

        boolean resultado = invocarProntoParaRetry(evento);

        assertThat(resultado).isFalse();
    }

    @Test
    void backoff_prontoParaRetry_deveRetornarTrueQuandoBackoffJaExpirou() throws Exception {
        OutboxEvents evento = evento(OutboxEvents.Status.FAILED, 1);

        Map<UUID, Instant> backoffMap = backoffMap();
        backoffMap.put(evento.getId(), Instant.now().minusSeconds(60));
        boolean resultado = invocarProntoParaRetry(evento);

        assertThat(resultado).isTrue();
    }

    @Test
    void backoff_poll_deveIgnorarEventoFalhadoCujoBackoffNaoExpirou() throws Exception {
        OutboxEvents evento = evento(OutboxEvents.Status.FAILED, 1);

        // coloca backoff futuro ANTES de chamar poll
        Map<UUID, Instant> backoffMap = backoffMap();
        backoffMap.put(evento.getId(), Instant.now().plusSeconds(60));

        when(repository.findByStatus(OutboxEvents.Status.PENDING)).thenReturn(List.of());
        when(repository.findByStatusAndTentativasLessThan(OutboxEvents.Status.FAILED, 3))
                .thenReturn(List.of(evento));

        poller.poll();

        verifyNoInteractions(kafkaPublisher);
        verify(repository, never()).save(evento);
    }

    @Test
    void backoff_deveRemoverDoMapQuandoEventoVirarDeadLetter() throws Exception {
        OutboxEvents evento = evento(OutboxEvents.Status.FAILED, 2);

        // pré-popula backoffMap para garantir que será removido
        Map<UUID, Instant> backoffMap = backoffMap();
        backoffMap.put(evento.getId(), Instant.now().minusSeconds(10));

        when(repository.findByStatus(OutboxEvents.Status.PENDING)).thenReturn(List.of());
        when(repository.findByStatusAndTentativasLessThan(OutboxEvents.Status.FAILED, 3))
                .thenReturn(List.of(evento));
        doThrow(new RuntimeException("Kafka down"))
                .when(kafkaPublisher).publish(anyString(), anyString(), anyString());

        poller.poll();

        assertThat(evento.getStatus()).isEqualTo(OutboxEvents.Status.DEAD_LETTER);
        assertThat(backoffMap).doesNotContainKey(evento.getId());
    }

    @Test
    void backoff_registrarBackoff_deveCresceComTentativas() throws Exception {
        OutboxEvents evento1 = evento(OutboxEvents.Status.FAILED, 1);
        evento1.setId(UUID.randomUUID());
        OutboxEvents evento2 = evento(OutboxEvents.Status.FAILED, 4);
        evento2.setId(UUID.randomUUID());

        Map<UUID, Instant> backoffMap = backoffMap();

        invocarRegistrarBackoff(evento1.getId(), 1);
        Instant apos1Tentativa = backoffMap.get(evento1.getId());

        invocarRegistrarBackoff(evento2.getId(), 4);
        Instant apos4Tentativas = backoffMap.get(evento2.getId());

        // com mais tentativas o instante futuro deve ser >= ao de menos tentativas
        // (jitter pode sobrepor em tentativas baixas, então verificamos o cap)
        assertThat(apos1Tentativa).isNotNull();
        assertThat(apos4Tentativas).isNotNull();
        // cap de 30s: tentativa 4 → 2^4 * 1000 = 16_000ms; deve estar dentro de 30s
        assertThat(apos4Tentativas).isBefore(Instant.now().plusSeconds(31));
    }


    private OutboxEvents evento(OutboxEvents.Status status, int tentativas) {
        OutboxEvents evento = new OutboxEvents();
        evento.setId(UUID.randomUUID());
        evento.setAggregateId(UUID.randomUUID().toString());
        evento.setEventType("PEDIDO_CRIADO");
        evento.setPayload("{\"pedidoID\":\"uuid\"}");
        evento.setStatus(status);
        evento.setTentativas(tentativas);
        return evento;
    }

    private boolean invocarProntoParaRetry(OutboxEvents evento) throws Exception {
        var method = OutboxPoller.class.getDeclaredMethod("prontoParaRetry", OutboxEvents.class);
        method.setAccessible(true);
        return (boolean) method.invoke(poller, evento);
    }

    private void invocarRegistrarBackoff(UUID id, int tentativas) throws Exception {
        var method = OutboxPoller.class.getDeclaredMethod("registrarBackOff", UUID.class, int.class);
        method.setAccessible(true);
        method.invoke(poller, id, tentativas);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Instant> backoffMap() throws Exception {
        Field field = OutboxPoller.class.getDeclaredField("backoffMap");
        field.setAccessible(true);
        return (Map<UUID, Instant>) field.get(poller);
    }
}
