# Story 009: Swagger / OpenAPI

**Como** desenvolvedor
**Quero** acessar `/swagger-ui.html` com todos os endpoints documentados
**Para** explorar a API sem precisar ler o código

## Critérios de Aceitação

- [x] AC1: `GET /swagger-ui.html` redireciona para a UI interativa
- [x] AC2: `GET /v3/api-docs` retorna a spec OpenAPI em JSON
- [x] AC3: Título da API é "Outbox Pattern Demo"
- [x] AC4: `PedidoController` documentado com `@Tag`, `@Operation` e `@ApiResponse`
- [x] AC5: `OutboxStatusController` documentado com `@Tag`, `@Operation` e `@ApiResponse`

## Status

✅ **Done**
