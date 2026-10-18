# Story 007: Health check do Outbox + Liveness/Readiness/Startup probes

**Como** operador
**Quero** endpoints de health check para monitorar o Outbox e as probes de disponibilidade
**Para** detectar backlog de eventos e integrar com orquestradores (Kubernetes)

## Critérios de Aceitação

- [x] AC1: `GET /actuator/health` retorna saúde geral com componente `outbox`
- [x] AC2: `GET /actuator/health/liveness` retorna status da liveness probe
- [x] AC3: `GET /actuator/health/readiness` retorna status da readiness probe
- [x] AC4: `OutboxHealthIndicator` retorna `UP` quando não há eventos PENDING/FAILED
- [x] AC5: `OutboxHealthIndicator` retorna `WARN` quando há eventos FAILED
- [x] AC6: `OutboxHealthIndicator` retorna `DOWN` quando PENDING > 10
- [x] AC7: `GET /outbox/status` retorna contagem de eventos por status

## Status

✅ **Done**
