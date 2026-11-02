# ADR-001 — Parallel Run

## Status
`Proposto`

## Contexto

Durante migrações de sistemas críticos — troca de banco de dados, substituição de um serviço de mensageria, refatoração de um componente de processamento assíncrono — existe o risco de o novo componente se comportar de forma diferente do atual sem que isso seja detectado antes do cutover.

O problema clássico é o de "dois commits":

- Promovemos o novo componente para produção.
- Ele processa eventos de forma diferente do esperado.
- O impacto já chegou ao usuário antes de ser detectado.

O padrão **Parallel Run** resolve isso ao executar o novo componente em modo *shadow*: ele processa os mesmos inputs que o atual, mas seus outputs são descartados ou apenas logados para comparação. O sistema atual permanece a fonte da verdade até que confiança suficiente seja estabelecida.

---

## Decisão

Adotar o padrão **Parallel Run** (também chamado de *Shadow Mode*) para validar novos componentes antes de qualquer cutover em produção, especialmente em:

- Substituição de pollers de outbox
- Migração de banco de dados (H2 → PostgreSQL)
- Troca de publisher Kafka
- Refatoração de regras de negócio em serviços transacionais

---

## Consequências

**Positivas**
- Zero impacto ao usuário durante a fase de validação
- Divergências detectadas antes do cutover
- Rollback trivial: basta desligar o shadow
- Permite medir performance real do novo componente com tráfego real

**Negativas**
- Consumo de recursos dobrado durante a janela de validação
- Requer cuidado com side effects do shadow (escrita em banco, publicação no Kafka)
- Aumenta complexidade temporária do código

---

## Anatomia do padrão

```
                        ┌─────────────────┐
                        │     Router      │
         Request ──────►│                 │
                        └────────┬────────┘
                                 │
                   ┌─────────────┴──────────────┐
                   ▼                            ▼
         ┌─────────────────┐          ┌─────────────────┐
         │    Primary      │          │     Shadow      │
         │  (sistema atual)│          │  (sistema novo) │
         └────────┬────────┘          └────────┬────────┘
                  │                            │
                  ▼                            ▼
             Response ao                  Comparator
              usuário                         │
                                          Log/Alert de
                                          divergências
```

| Componente   | Responsabilidade                                               |
|--------------|----------------------------------------------------------------|
| **Router**   | Direciona o input para ambos os sistemas                       |
| **Primary**  | Sistema atual — produz a resposta canônica                     |
| **Shadow**   | Sistema novo — executa mas não retorna ao caller               |
| **Comparator** | Compara os outputs, loga divergências, emite métricas        |

---

## Variações

### 1. Shadow Assíncrono (recomendado)

O shadow roda em thread separada. Não bloqueia o primary. Aceita latência de comparação.

```
Request ──► Primary ──► Response imediata ao usuário
     │
     └──► Fila interna ──► Shadow (async) ──► Comparator ──► Log
```

**Quando usar:** substituição de poller, de publisher, ou de qualquer componente de background.

---

### 2. Mirroring Síncrono

Ambos rodam no mesmo thread. O resultado é comparado antes de retornar.

```
Request ──► Primary  ──► resultado A ──┐
       └──► Shadow   ──► resultado B ──┴──► Comparator ──► Response A
```

**Quando usar:** pipelines de transformação de dados onde a latência dobrada é aceitável em ambiente de staging.

---

### 3. Traffic Shadowing via Proxy (nível de infraestrutura)

O proxy (Envoy, nginx mirror) copia o tráfego HTTP real para o novo serviço. O novo serviço nem sabe que está em shadow mode.

```
Cliente ──► Proxy ──► Primary  ──► Response
                 └──► Shadow   ──► /dev/null
```

**Quando usar:** migração de serviço HTTP completo, sem mudança no código da aplicação.

---

## Exemplo de implementação — Spring Boot

### Cenário: substituição do `OutboxPoller`

#### 1. Interface comum

```java
public interface OutboxPollerPort {
    List<OutboxEvents> pollAndPublish();
}
```

---

#### 2. Primary (implementação atual)

```java
@Component("primaryPoller")
public class OutboxPoller implements OutboxPollerPort {

    private final OutboxEventsRepository repository;
    private final KafkaPublisher publisher;

    public OutboxPoller(OutboxEventsRepository repository, KafkaPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @Override
    public List<OutboxEvents> pollAndPublish() {
        List<OutboxEvents> pending = repository.findByStatus(OutboxEvents.Status.PENDING);
        pending.forEach(evento -> {
            publisher.send("pedidos-eventos", evento.getAggregateId(), evento.getPayload());
            evento.setStatus(OutboxEvents.Status.PROCESSED);
            repository.save(evento);
        });
        return pending;
    }
}
```

---

#### 3. Shadow (novo candidato)

```java
@Component("shadowPoller")
public class NewOutboxPoller implements OutboxPollerPort {

    private final OutboxEventsRepository repository;
    private final KafkaPublisher publisher;
    private static final String SHADOW_TOPIC = "pedidos-eventos-shadow";

    public NewOutboxPoller(OutboxEventsRepository repository, KafkaPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @Override
    public List<OutboxEvents> pollAndPublish() {
        List<OutboxEvents> pending = repository.findByStatus(OutboxEvents.Status.PENDING);
        pending.forEach(evento -> {
            // publica em tópico de shadow — nunca no tópico real
            publisher.send(SHADOW_TOPIC, evento.getAggregateId(), evento.getPayload());
            // não persiste alteração de status no banco
        });
        return pending;
    }
}
```

> **Regra crítica:** o shadow nunca escreve no banco de produção nem publica no tópico real do Kafka.

---

#### 4. Comparator

```java
@Component
public class PollerComparator {

    private static final Logger log = LoggerFactory.getLogger(PollerComparator.class);

    public void compare(List<OutboxEvents> primaryResult, List<OutboxEvents> shadowResult) {
        Set<String> primaryIds = primaryResult.stream()
                .map(OutboxEvents::getAggregateId)
                .collect(Collectors.toSet());

        Set<String> shadowIds = shadowResult.stream()
                .map(OutboxEvents::getAggregateId)
                .collect(Collectors.toSet());

        SetView<String> onlyInPrimary = Sets.difference(primaryIds, shadowIds);
        SetView<String> onlyInShadow  = Sets.difference(shadowIds, primaryIds);

        if (!onlyInPrimary.isEmpty()) {
            log.warn("[PARALLEL-RUN] Eventos no primary ausentes no shadow: {}", onlyInPrimary);
        }
        if (!onlyInShadow.isEmpty()) {
            log.warn("[PARALLEL-RUN] Eventos no shadow ausentes no primary: {}", onlyInShadow);
        }
        if (onlyInPrimary.isEmpty() && onlyInShadow.isEmpty()) {
            log.info("[PARALLEL-RUN] ✓ Outputs idênticos. Tamanho={}", primaryIds.size());
        }
    }

    public void shadowFailed(Exception e) {
        log.error("[PARALLEL-RUN] Shadow falhou silenciosamente: {}", e.getMessage());
    }
}
```

---

#### 5. Orquestrador do Parallel Run

```java
@Component
@ConditionalOnProperty(name = "outbox.parallel-run.enabled", havingValue = "true")
public class ParallelRunScheduler {

    private static final Logger log = LoggerFactory.getLogger(ParallelRunScheduler.class);

    private final OutboxPoller    primary;
    private final NewOutboxPoller shadow;
    private final PollerComparator comparator;

    public ParallelRunScheduler(
            OutboxPoller primary,
            NewOutboxPoller shadow,
            PollerComparator comparator) {
        this.primary    = primary;
        this.shadow     = shadow;
        this.comparator = comparator;
    }

    @Scheduled(fixedDelay = 5000)
    public void run() {
        // Primary roda de forma síncrona e canônica
        List<OutboxEvents> primaryResult = primary.pollAndPublish();

        // Shadow roda de forma assíncrona — falha silenciosa
        CompletableFuture.runAsync(() -> {
            try {
                List<OutboxEvents> shadowResult = shadow.pollAndPublish();
                comparator.compare(primaryResult, shadowResult);
            } catch (Exception e) {
                comparator.shadowFailed(e);
            }
        });
    }
}
```

---

#### 6. Feature flag via `application.properties`

```properties
# Habilita o parallel run (shadow mode)
outbox.parallel-run.enabled=true
```

Para fazer o cutover: desabilitar o primary, promover o shadow a primary, remover o orquestrador.

---

## Critérios de saída (cutover checklist)

| Critério | Threshold |
|---|---|
| Taxa de divergência entre primary e shadow | < 0,1% por 48 horas |
| Latência do shadow vs primary | ≤ primary + 20% |
| Zero panics/erros não esperados no shadow | janela de 48 horas |
| Tópico Kafka de shadow consumido e validado | ✅ |
| Rollback manual testado (desligar shadow sem impacto) | ✅ |
| Equipe alinhada na data de cutover | ✅ |

---

## Riscos e mitigações

| Risco | Mitigação |
|---|---|
| Shadow tem side effects (escreve no banco real) | Usar schema separado ou flag `dryRun = true` no shadow |
| Shadow publica no tópico real do Kafka | Usar tópico dedicado `*-shadow` para o candidato |
| Comparação de timestamps gera falso positivo | Normalizar campos não-determinísticos antes de comparar |
| Shadow consome CPU/memória em excesso | Throttle: rodar shadow apenas em X% dos ciclos |
| Shadow lento bloqueia o primary | Shadow **sempre** assíncrono — `CompletableFuture.runAsync` |

---

## Relação com outros padrões

```
Strangler Fig ──► usa Parallel Run para validar antes do cutover do monolito
Feature Flag  ──► habilita/desabilita o shadow por ambiente (dev/staging/prod)
Blue/Green    ──► Parallel Run valida o "Green" antes de rotear tráfego real
Canary        ──► X% do tráfego vai para o novo; Parallel Run valida 0% primeiro
```

---

## Referências

- Martin Fowler — [Parallel Change](https://martinfowler.com/bliki/ParallelChange.html)
- Sam Newman — *Building Microservices*, Cap. 8 (Decomposing the Database)
- [Feature Toggles (Pete Hodgson)](https://martinfowler.com/articles/feature-toggles.html)
