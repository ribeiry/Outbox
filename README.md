# Outbox Pattern Demo

Demonstração do padrão **Transactional Outbox** com Spring Boot 4.1, Kafka e Java 26.

## O Problema

Publicar um evento Kafka dentro de uma transação de banco cria um problema de dois commits:

```
❌ Sem Outbox
repository.save(pedido)          → banco OK
kafkaPublisher.publish(evento)   → Kafka OK (ou falha?)
                                    ↑ inconsistência possível
```

Se o banco confirmar mas o Kafka falhar (ou vice-versa), o sistema fica inconsistente.

## A Solução

```
✅ Com Outbox
@Transactional
  repository.save(pedido)         ┐
  outboxRepository.save(evento)   ┘ mesma transação → atômico

OutboxPoller (@Scheduled)
  busca eventos PENDING
  publica no Kafka
  marca como PROCESSED
```

Pedido e evento são gravados na **mesma transação**. O poller publica de forma assíncrona.

## Fluxo completo

```
POST /pedidos
      ↓
PedidoService (@Transactional)
      ├── salva Pedido (status: CRIADO)
      └── salva OutboxEvent (status: PENDING)
                    ↓
            OutboxPoller (@Scheduled 5s)
                    ├── marca PROCESSING   → salva no banco
                    ├── publica no Kafka
                    └── marca PROCESSED    → salva no banco

            OutboxRecovery (@Scheduled)
                    └── eventos presos em PROCESSING > 15min → volta para PENDING
```

## Stack

| Componente | Tecnologia |
|---|---|
| API | Java 26 + Spring Boot 4.1 |
| Banco (dev) | H2 in-memory |
| Mensageria | Apache Kafka 3.7 (KRaft) |
| Poller | `@Scheduled` com Virtual Threads |
| Documentação | Swagger / OpenAPI 3 |
| Monitoramento | Spring Boot Actuator |

## Pré-requisitos

- Java 26
- Maven 3.9+
- Docker (para o Kafka)

## Como rodar

### 1. Subir o Kafka

```bash
docker compose -f infra/docker-compose.yaml up -d
```

### 2. Subir a aplicação

```bash
cd app
JAVA_HOME=/path/to/jdk-26 mvn spring-boot:run
```

### 3. Criar um pedido

```bash
curl -X POST http://localhost:8080/pedidos \
  -H "Content-Type: application/json" \
  -d '{"cpf":"123.456.789-00","veiculo":"Honda Civic","valor":120000.00}'
```

### 4. Consultar um pedido

```bash
curl http://localhost:8080/pedidos/{id}
```

### 5. Verificar status do Outbox

```bash
curl http://localhost:8080/outbox/status
```

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/pedidos` | Cria pedido e registra evento no Outbox |
| `GET` | `/pedidos/{id}` | Busca pedido por ID |
| `GET` | `/outbox/status` | Contagem de eventos por status |
| `GET` | `/actuator/health` | Saúde geral + OutboxHealthIndicator |
| `GET` | `/actuator/health/liveness` | Liveness probe |
| `GET` | `/actuator/health/readiness` | Readiness probe |
| `GET` | `/swagger-ui.html` | Documentação interativa |
| `GET` | `/h2-console` | Console do banco H2 |

## Ciclo de vida dos eventos

```
PENDING → PROCESSING → PROCESSED
                ↓ (falha Kafka)
             FAILED → PROCESSING → PROCESSED
                ↓ (max tentativas)
           DEAD_LETTER

PROCESSING → PENDING  (recovery: preso > 15 min)
```

## Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Endereço do broker Kafka |
| `DB_URL` | `jdbc:h2:mem:outboxdb` | URL do banco |
| `DB_USERNAME` | `sa` | Usuário do banco |
| `DB_PASSWORD` | *(vazio)* | Senha do banco |
| `OUTBOX_PENDING_THRESHOLD` | `10` | Limite de PENDING para health DOWN |
| `OUTBOX_MAX_TENTATIVAS` | `3` | Máximo de retries antes de DEAD_LETTER |
| `OUTBOX_MAX_THREADS` | `10` | Threads do poller (não usado atualmente) |

## Testes

```bash
cd app
JAVA_HOME=/path/to/jdk-26 mvn test
```

**14 testes** cobrindo:
- `PedidoService` — criação de pedido + evento, falha de serialização
- `OutboxPoller` — PENDING, FAILED, DEAD_LETTER, idempotência, recovery
- `PedidoController` — POST 201, GET 200, GET 404

## Stress Test (JMeter)

```bash
# Com a app e Kafka rodando
jmeter -n -t tests/jmeter/outbox-stress-test.jmx \
       -l tests/jmeter/results/stress-result.csv
```

**Resultado** (45 usuários, 5 minutos, pool=45):

| Métrica | Valor |
|---|---|
| Total requests | ~6.8M |
| Throughput | ~22.878 req/s |
| Latência média | 1ms |
| Erro | 0,00% |

> **Observação:** H2 in-memory não é adequado para produção. O gargalo identificado foi o connection pool — ajuste `spring.datasource.hikari.maximum-pool-size` conforme o número de threads concorrentes.

## Estrutura do projeto

```
.
├── app/
│   ├── src/main/java/com/ribeiry/
│   │   ├── config/          # SecurityConfig, OpenApiConfig, SchedulerConfig
│   │   ├── domain/          # Pedido, PedidoRepository
│   │   ├── kafka/           # KafkaConfig, KafkaPublisher
│   │   ├── outboxEvent/     # OutboxEvents, OutboxPoller, OutboxHealthIndicator, OutboxStatusController
│   │   └── pedido/          # PedidoController, PedidoService
│   └── src/test/
├── infra/
│   └── docker-compose.yaml  # Kafka KRaft
├── tests/
│   └── jmeter/              # Stress test
└── docs/
    └── bmad/                # Documentação de processo (BMAD Method)
```
