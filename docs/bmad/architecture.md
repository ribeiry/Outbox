# Architecture — Outbox Pattern Demo

## Visão Geral

```
Cliente HTTP
     ↓
PedidoController  (POST /pedidos, GET /pedidos/{id})
     ↓
PedidoService  (@Transactional)
     ├── PedidoRepository.save(pedido)
     └── OutboxEventsRepository.save(evento {PENDING})
                         ↓
                   OutboxPoller (@Scheduled 5s)
                         ├── busca PENDING
                         ├── KafkaPublisher.publish("pedidos-eventos", key, payload)
                         └── atualiza status → PROCESSED | FAILED
```

## Estrutura de pacotes

```
app/src/main/java/com/ribeiry/
├── Main.java                          ← entrypoint Spring Boot
├── config/
│   └── SecurityConfig.java            ← Spring Security (permitAll + H2 console)
├── domain/
│   ├── Pedido.java                    ← entidade JPA (id, cpf, veiculo, valor, status, criadoEm)
│   └── PedidoRepository.java          ← JpaRepository<Pedido, UUID>
├── outboxEvent/
│   ├── OutboxEvents.java              ← entidade JPA (id, aggregateId, eventType, payload, status, criadoEm)
│   ├── OutboxEventsRepository.java    ← JpaRepository + findByStatus(Status)
│   └── OutboxPoller.java              ← @Scheduled(fixedDelay=5000) — publica PENDING no Kafka
├── pedido/
│   ├── PedidoController.java          ← REST: POST /pedidos, GET /pedidos/{id}
│   └── PedidoService.java             ← salva pedido + evento na mesma @Transactional
└── kafka/
    ├── KafkaConfig.java               ← ProducerFactory + KafkaTemplate beans
    └── KafkaPublisher.java            ← wrapper send(topic, key, payload)
```

## Modelo de dados (H2)

```sql
pedidos (
  id          UUID PRIMARY KEY,
  cliente_cpf VARCHAR,
  veiculo     VARCHAR,
  valor       DECIMAL,
  status      VARCHAR  -- CRIADO | APROVADO | CANCELADO
  criado_em   TIMESTAMP
)

outbox_events (
  id           UUID PRIMARY KEY,
  aggregate_id VARCHAR,
  event_type   VARCHAR,   -- ex: PEDIDO_CRIADO
  payload      TEXT,      -- JSON serializado
  status       VARCHAR,   -- PENDING | PROCESSED | FAILED
  criado_em    TIMESTAMP
)
```

## Contratos de API

### POST /pedidos
**Request:**
```json
{ "cpf": "123.456.789-00", "veiculo": "Honda Civic", "valor": 120000.00 }
```
**Response 201:**
```json
{
  "id": "uuid",
  "clienteCpf": "123.456.789-00",
  "veiculo": "Honda Civic",
  "valor": 120000.00,
  "status": "CRIADO",
  "criadoEm": "2025-10-12T19:00:00"
}
```

### GET /pedidos/{id}
**Response 200:** mesmo schema acima  
**Response 404:** não encontrado

## Payload do evento Kafka

```json
{
  "pedidoID": "uuid",
  "clienteCpf": "123.456.789-00",
  "veiculo": "Honda Civic",
  "valor": 120000.00,
  "status": "CRIADO",
  "criadoEm": "2025-10-12T19:00:00"
}
```

Tópico: `pedidos-eventos`  
Key: `pedido.id` (string UUID)

## Decisões técnicas

| Decisão | Motivo |
|---|---|
| H2 in-memory em dev | Sem dependência externa para rodar; schema recriado a cada start |
| KRaft mode no Kafka | Sem ZooKeeper; config mais simples para demo local |
| `@Scheduled fixedDelay` | Mais simples que fixedRate — espera o ciclo anterior terminar |
| Spring Security `permitAll` | Demo; H2 console exige CSRF off + frameOptions disabled |
| `ddl-auto=create-drop` | Schema gerenciado pelo Hibernate; adequado para demo |

## Gaps conhecidos

- Sem retry automático de eventos `FAILED`
- Sem idempotência no poller (se reiniciar no meio de um ciclo, pode tentar republicar)
- Sem persistência durável (H2 perde dados no restart)
- Sem testes automatizados
