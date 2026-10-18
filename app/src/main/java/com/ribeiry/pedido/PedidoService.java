package com.ribeiry.pedido;

import com.ribeiry.domain.Pedido;
import com.ribeiry.domain.PedidoRepository;
import com.ribeiry.outboxEvent.OutboxEvents;
import com.ribeiry.outboxEvent.OutboxEventsRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PedidoService {

    private final PedidoRepository repository;
    private OutboxEventsRepository outboxEventsRepository;
    private final ObjectMapper objectMapper;

    public record PedidoEventPayload (
            UUID pedidoID,
            String clienteCpf,
            String veiculo,
            BigDecimal valor,
            String status,
            LocalDateTime criadoEm
    ){
        public static PedidoEventPayload from(Pedido pedido){
            return new PedidoEventPayload(
                    pedido.getId(),
                    pedido.getClienteCpf(),
                    pedido.getVeiculo(),
                    pedido.getValor(),
                    pedido.getStatus().name(),
                    pedido.getCriadoEm()
            );
        }
    }

    public PedidoService(PedidoRepository repository, OutboxEventsRepository eventsRepository, ObjectMapper objectMapper){
        this.repository = repository;
        this.outboxEventsRepository = eventsRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Pedido save(String cpf, String veiculo, BigDecimal valor) {
        Pedido pedido = new Pedido();
        String payload;

        pedido.setClienteCpf(cpf);
        pedido.setVeiculo(veiculo);
        pedido.setValor(valor);

        repository.save(pedido);


        try {
            payload = objectMapper.writeValueAsString(PedidoEventPayload.from(pedido));
        }
        catch (Exception e){
            throw new RuntimeException("Erro ao tentar deserializar o objeto pedido",e);
        }
        OutboxEvents evento = new OutboxEvents();
        evento.setAggregateId(pedido.getId().toString());
        evento.setEventType("PEDIDO_CRIADO");
        evento.setPayload(payload);

        outboxEventsRepository.save(evento);
        return  pedido;
    }

    public Optional<Pedido> findById(UUID id){

        Optional<Pedido> pedido = repository.findById(id);

        return  pedido;
    }
}
