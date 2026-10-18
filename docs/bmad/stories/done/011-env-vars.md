# Story 011: Configuração via variáveis de ambiente

**Como** DevOps
**Quero** configurar a aplicação via variáveis de ambiente
**Para** rodar o serviço em container sem modificar `application.properties`

## Critérios de Aceitação

- [x] AC1: `KAFKA_BOOTSTRAP_SERVERS` configura o broker Kafka
- [x] AC2: `DB_URL` configura a URL do banco
- [x] AC3: `DB_USERNAME` e `DB_PASSWORD` configuram as credenciais
- [x] AC4: `outbox.pending-threshold` (ou default `10`) configura o limite do health indicator
- [x] AC5: Valores padrão mantêm o comportamento atual sem nenhuma variável definida

## Status

✅ **Done**
