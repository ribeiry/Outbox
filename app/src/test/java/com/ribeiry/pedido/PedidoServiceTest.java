package com.ribeiry.pedido;

import com.ribeiry.domain.Pedido;
import com.ribeiry.domain.PedidoRepository;
import com.ribeiry.outboxEvent.OutboxEvents;
import com.ribeiry.outboxEvent.OutboxEventsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private OutboxEventsRepository outboxEventsRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PedidoService pedidoService;

    @Test
    void save_deveSalvarPedidoEEvento() throws Exception {
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            p.setStatus(Pedido.Status.CRIADO);
            p.setCriadoEm(LocalDateTime.now());
            return p;
        });
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"pedidoID\":\"uuid\"}");
        when(outboxEventsRepository.save(any(OutboxEvents.class))).thenAnswer(inv -> inv.getArgument(0));

        Pedido resultado = pedidoService.save("123.456.789-00", "Honda Civic", BigDecimal.valueOf(120000));

        verify(pedidoRepository).save(any(Pedido.class));

        ArgumentCaptor<OutboxEvents> captor = ArgumentCaptor.forClass(OutboxEvents.class);
        verify(outboxEventsRepository).save(captor.capture());

        OutboxEvents evento = captor.getValue();
        assertThat(evento.getEventType()).isEqualTo("PEDIDO_CRIADO");
        assertThat(evento.getAggregateId()).isEqualTo(resultado.getId().toString());
        assertThat(evento.getPayload()).isEqualTo("{\"pedidoID\":\"uuid\"}");
    }

    @Test
    void save_deveRetornarPedidoComCamposCorretos() throws Exception {
        UUID id = UUID.randomUUID();
        LocalDateTime agora = LocalDateTime.now();

        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            p.setId(id);
            p.setStatus(Pedido.Status.CRIADO);
            p.setCriadoEm(agora);
            return p;
        });
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Pedido resultado = pedidoService.save("111.222.333-44", "Toyota Corolla", BigDecimal.valueOf(95000));

        assertThat(resultado.getId()).isEqualTo(id);
        assertThat(resultado.getClienteCpf()).isEqualTo("111.222.333-44");
        assertThat(resultado.getVeiculo()).isEqualTo("Toyota Corolla");
        assertThat(resultado.getValor()).isEqualByComparingTo(BigDecimal.valueOf(95000));
        assertThat(resultado.getStatus()).isEqualTo(Pedido.Status.CRIADO);
    }

    @Test
    void save_deveLancarExcecaoQuandoSerializacaoFalhar() throws Exception {
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            p.setStatus(Pedido.Status.CRIADO);
            p.setCriadoEm(LocalDateTime.now());
            return p;
        });
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("serialization error"));

        assertThatThrownBy(() -> pedidoService.save("123.456.789-00", "Honda Civic", BigDecimal.valueOf(120000)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Erro ao tentar deserializar o objeto pedido");

        verify(outboxEventsRepository, never()).save(any());
    }

    @Test
    void findById_deveRetornarPedidoQuandoExistir() {
        UUID id = UUID.randomUUID();
        Pedido pedido = new Pedido();
        pedido.setId(id);
        pedido.setClienteCpf("123.456.789-00");
        when(pedidoRepository.findById(id)).thenReturn(Optional.of(pedido));

        Optional<Pedido> resultado = pedidoService.findById(id);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getId()).isEqualTo(id);
        assertThat(resultado.get().getClienteCpf()).isEqualTo("123.456.789-00");
    }

    @Test
    void findById_deveRetornarVazioQuandoNaoExistir() {
        UUID id = UUID.randomUUID();
        when(pedidoRepository.findById(id)).thenReturn(Optional.empty());

        Optional<Pedido> resultado = pedidoService.findById(id);

        assertThat(resultado).isEmpty();
    }

    @Test
    void pedidoEventPayload_from_deveMappearTodosOsCampos() {
        UUID id = UUID.randomUUID();
        LocalDateTime agora = LocalDateTime.now();
        Pedido pedido = new Pedido();
        pedido.setId(id);
        pedido.setClienteCpf("987.654.321-00");
        pedido.setVeiculo("Ford Ka");
        pedido.setValor(BigDecimal.valueOf(45000));
        pedido.setStatus(Pedido.Status.CRIADO);
        pedido.setCriadoEm(agora);

        PedidoService.PedidoEventPayload payload = PedidoService.PedidoEventPayload.from(pedido);

        assertThat(payload.pedidoID()).isEqualTo(id);
        assertThat(payload.clienteCpf()).isEqualTo("987.654.321-00");
        assertThat(payload.veiculo()).isEqualTo("Ford Ka");
        assertThat(payload.valor()).isEqualByComparingTo(BigDecimal.valueOf(45000));
        assertThat(payload.status()).isEqualTo("CRIADO");
        assertThat(payload.criadoEm()).isEqualTo(agora);
    }
}
