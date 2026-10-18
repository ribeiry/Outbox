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
}
