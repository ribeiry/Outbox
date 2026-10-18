# Story 001: Setup inicial — Pedido + Outbox + Kafka

**Como** desenvolvedor
**Quero** criar um pedido via API e ter o evento `PEDIDO_CRIADO` publicado no Kafka de forma confiável
**Para** demonstrar o padrão Transactional Outbox em uma aplicação Spring Boot

## Critérios de Aceitação

- [x] AC1: `POST /pedidos` cria um `Pedido` com status `CRIADO` e retorna 201
- [x] AC2: Na mesma transação, um `OutboxEvent` com status `PENDING` é gravado
- [x] AC3: O `OutboxPoller` publica o evento no tópico `pedidos-eventos` a cada 5s
- [x] AC4: Após publicação bem-sucedida, o evento muda para `PROCESSED`
- [x] AC5: Se o Kafka falhar, o evento muda para `FAILED`
- [x] AC6: `GET /pedidos/{id}` retorna o pedido ou 404

## Status

✅ **Done** — implementado, sem cobertura de testes automatizados
