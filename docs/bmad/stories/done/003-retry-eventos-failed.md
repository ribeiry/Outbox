# Story 003: Retry de eventos FAILED

**Como** sistema
**Quero** reprocessar automaticamente eventos com status `FAILED`
**Para** garantir entrega eventual mesmo após falhas transientes no Kafka

## Critérios de Aceitação

- [x] AC1: Eventos com status `FAILED` são reprocessados pelo poller
- [x] AC2: Após N tentativas sem sucesso, o evento vai para `DEAD_LETTER` (não bloqueia o poller)
- [x] AC3: O número máximo de tentativas é configurável via `application.properties`
- [x] AC4: Cada tentativa incrementa um contador `tentativas` no evento

## Edge Cases
- N tentativas configurado como 0 → nunca reprocessa (comportamento atual)
- Kafka volta após ficar offline → eventos FAILED são processados no próximo ciclo

## Fora de escopo
- Dead letter queue no Kafka
- Notificação de falha por email/webhook

## Notas
- Adicionar campo `tentativas INT DEFAULT 0` na entidade `OutboxEvents`
- Adicionar enum `DEAD_LETTER` ao `OutboxEvents.Status`
