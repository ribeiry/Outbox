# Story 010: Dead Letter Queue

## Status

❌ **Cancelada — absorvida pela Story 003**

O comportamento de DEAD_LETTER foi implementado diretamente na Story 003: após `maxTentativas` falhas de publicação no Kafka, o evento é marcado como `DEAD_LETTER` no banco. Sem necessidade de tópico Kafka separado.
