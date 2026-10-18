# Story 002: Testes unitários

**Como** desenvolvedor
**Quero** ter cobertura de testes para `PedidoService`, `OutboxPoller` e `PedidoController`
**Para** garantir que o comportamento do Outbox está correto e evitar regressões

## Critérios de Aceitação

- [x] AC1: `PedidoService.save` — caminho feliz grava pedido + evento na mesma transação
- [x] AC2: `PedidoService.save` — falha na serialização lança `RuntimeException`
- [x] AC3: `OutboxPoller.poll` — eventos PENDING são publicados e marcados como PROCESSED
- [x] AC4: `OutboxPoller.poll` — falha no Kafka marca evento como FAILED
- [x] AC5: `OutboxPoller.poll` — eventos PROCESSED não são reprocessados
- [x] AC6: `PedidoController` POST — retorna 201 com body correto
- [x] AC7: `PedidoController` GET — retorna 200 para id existente, 404 para inexistente

## Fora de escopo
- Testes de integração com Kafka real (usar mock)
- Testes de `SecurityConfig`

## Notas
- Usar JUnit 5 + Mockito
- `mvn test` deve passar sem Kafka rodando (mocks)
