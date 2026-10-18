# Product Brief — Outbox Pattern Demo

## Visão do Produto

Demonstração do padrão **Transactional Outbox** aplicado a um serviço de pedidos de veículos. O objetivo é garantir que eventos de domínio sejam publicados no Kafka de forma confiável — sem risco de inconsistência entre o banco de dados e o broker — usando uma tabela `outbox_events` como buffer transacional.

## Problema resolvido

Sem o padrão Outbox, publicar um evento Kafka dentro de uma transação de banco cria um problema de dois commits: se o commit do banco funcionar mas o Kafka falhar (ou vice-versa), o sistema fica inconsistente. O Outbox resolve isso:

1. Banco e evento são gravados na mesma transação
2. Um poller assíncrono lê os eventos `PENDING` e os publica no Kafka
3. Se o Kafka falhar, o evento fica `FAILED` e pode ser reprocessado

## Contexto de domínio

Pedido de veículo criado por cliente (CPF), com valor e status. Ao ser criado, um evento `PEDIDO_CRIADO` deve chegar no tópico `pedidos-eventos` do Kafka.

## Stack

| Componente | Tecnologia |
|---|---|
| API | Java 26 + Spring Boot 4.1 |
| Banco (dev) | H2 in-memory |
| Mensageria | Apache Kafka 3.7 |
| Poller | `@Scheduled` Spring (`fixedDelay=5s`) |
| Segurança | Spring Security (sem auth — permitAll) |
| Infra | Docker Compose (Kafka KRaft mode) |

## Estado atual

| Componente | Status |
|---|---|
| Entidade `Pedido` | ✅ Implementado |
| Entidade `OutboxEvents` | ✅ Implementado |
| `PedidoService` (salva pedido + evento na mesma tx) | ✅ Implementado |
| `OutboxPoller` (publica PENDING → Kafka) | ✅ Implementado |
| `KafkaPublisher` + `KafkaConfig` | ✅ Implementado |
| `PedidoController` (POST + GET) | ✅ Implementado |
| `SecurityConfig` (permitAll) | ✅ Implementado |
| Testes unitários | ❌ Ausente |
| Retry de eventos FAILED | ❌ Ausente |
| Persistência durável (PostgreSQL) | ❌ Ausente |

## Restrições

- Pedido e OutboxEvent devem ser gravados na **mesma transação** (`@Transactional`)
- O poller não deve republicar eventos já `PROCESSED`
- O banco de dev é H2 in-memory; migrations via `ddl-auto=create-drop`
