# BMAD — Outbox Pattern Demo

Documentação de processo usando o **BMAD Method** (Breakthrough Method of Agile AI-driven Development).

## Como usar

Ative uma persona dizendo ao assistente:

| Comando | O que acontece |
|---------|----------------|
| `"atua como PM"` | Escreve/refina stories com ACs |
| `"atua como Architect"` | Propõe design técnico para uma story |
| `"atua como Dev"` | Implementa o design aprovado |
| `"atua como QA"` | Valida cobertura e ACs |

**Você decide** se avança para a próxima fase antes de qualquer código ser escrito.

## Fluxo

```
PM escreve story
       ↓
  Você aprova?
  ├── Não → ajusta escopo
  └── Sim → Architect propõe design
                   ↓
             Você aprova?
             ├── Não → ajusta design
             └── Sim → Dev implementa
                              ↓
                         QA valida
```

## Estrutura

```
docs/bmad/
├── README.md              ← este arquivo
├── product-brief.md       ← visão do produto
├── architecture.md        ← arquitetura atual
├── personas/
│   ├── pm.md
│   ├── architect.md
│   ├── dev.md
│   └── qa.md
└── stories/
    ├── done/              ← stories concluídas
    └── backlog/           ← aguardando aprovação/implementação
```

## Status geral

| ID | Story | Status |
|----|-------|--------|
| 001 | Setup inicial (Pedido + Outbox + Kafka) | ✅ Done |
| 002 | Testes unitários (Service + Poller + Controller) | ✅ Done |
| 003 | Retry de eventos FAILED | 📋 Backlog |
| 004 | ~~Migração para PostgreSQL~~ | ❌ Cancelado |
