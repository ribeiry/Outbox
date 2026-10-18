# Story 016: Ajuste do JavaScript no n8n para novos endpoints e envelope de response

**Como** operador do sistema
**Quero** que os workflows do n8n consumam corretamente os novos endpoints e o envelope `{telegram_id, data|message|error}`
**Para** que as mensagens do Telegram exibam dados corretos após as recentes mudanças na API

### Contexto

A API evoluiu recentemente e vários pontos do n8n ficaram desatualizados:

**Novos endpoints não integrados no n8n:**
- `GET /portfolio/allocation` (alocação por setor)
- `GET /portfolio/benchmark?period=weekly|monthly`
- `GET /portfolio/period-summary?period=weekly|monthly`
- `POST /portfolio/simulate`
- `PATCH /portfolio/assets/:ticker/sector`
- `GET /alerts`, `POST /alerts`, `DELETE /alerts/:ticker`, `GET /alerts/check`

**Envelope de response mudou:**
- Antes: retorno direto (`[{...}]` ou `{...}`)
- Agora: `{"telegram_id": "...", "data": [...]}` ou `{"telegram_id": "...", "message": "..."}` ou `{"error": "..."}`

Todo código JavaScript nos nós Function/Code do n8n que fazia `items[0].json.ticker` etc. está quebrado — precisa acessar via `items[0].json.data.ticker`.

### Critérios de Aceitação

- [ ] AC1: Todos os workflows n8n existentes ajustados para ler `json.data` em vez de `json` direto
- [ ] AC2: Tratamento de erro: quando resposta tem `json.error`, o n8n envia mensagem de erro amigável ao Telegram
- [ ] AC3: `telegram_id` do envelope é usado como `chat_id` no nó Telegram (garantindo entrega ao usuário certo)
- [ ] AC4: Novos endpoints integrados como nós HTTP Request + Format:
  - Alocação por setor
  - Benchmark (semanal e mensal)
  - Resumo semanal e mensal
  - Alertas (listar, criar, deletar, verificar)
  - Simulação de operações
- [ ] AC5: Formatação Markdown das mensagens do Telegram preservada (`*negrito*`, listas com `•`, valores com `R$`)
- [ ] AC6: Workflows exportados em JSON e versionados em `docs/n8n/`
- [ ] AC7: Documento `docs/n8n/README.md` descrevendo cada workflow e mapeamento comando → endpoint

### Escopo dos workflows a revisar

| Workflow atual | Endpoint | Ajuste |
|---|---|---|
| `/status` | `GET /portfolio/summary` | Envelope + `data` |
| `/ativos` | `GET /portfolio/assets` | Envelope + `data` |
| `/resumo` | `GET /portfolio/summary?group_by=...` | Envelope + `data` |
| `/performance` | `GET /portfolio/performance` | Envelope + `data` (inclui campo `sector` novo) |
| **novo** `/semanal` | `GET /portfolio/period-summary?period=weekly` | Criar |
| **novo** `/mensal` | `GET /portfolio/period-summary?period=monthly` | Criar |
| **novo** `/benchmark` | `GET /portfolio/benchmark?period=weekly` | Criar |
| **novo** `/setores` | `GET /portfolio/allocation` | Criar |
| **novo** `/alertas` | `GET /alerts` | Criar |
| **novo** `/verificar` | `GET /alerts/check` | Criar |

### Padrão de tratamento de resposta (Code node)

```javascript
const response = items[0].json;

if (response.error) {
  return [{ json: {
    chat_id: /* fallback */,
    text: `❌ Erro: ${response.error}`
  }}];
}

const chatId = response.telegram_id;
const data = response.data;

// formatar mensagem a partir de `data`
return [{ json: { chat_id: chatId, text: formatMessage(data) } }];
```

### Edge Cases
- Response com `data: []` (vazio) → mensagem amigável ("Nenhum alerta ativo", "Carteira vazia")
- Response com `data: null` → tratar como vazio
- Timeout da API → mensagem "Serviço temporariamente indisponível"
- Ativo sem preço atual (quote indisponível) → indicar no texto

### Fora de escopo
- Menu interativo com inline_keyboard (story 015)
- Wizard de criação de alerta via múltiplas mensagens
- Suporte a múltiplos idiomas
- Cache de respostas no n8n

### Dependências
- Story 015 (menu interativo) pode reaproveitar os workflows ajustados aqui
- Story 013 (GET /users/me) útil para o n8n validar api_key antes de disparar comandos

### Notas
- Testar cada comando manualmente no Telegram após ajuste
- Exportar cada workflow via UI do n8n: `Workflows → ... → Download`
- Versionar exports em `docs/n8n/workflows/` (não commitar credentials)
- Considerar helper JavaScript compartilhado (`docs/n8n/helpers.js`) para lógica de envelope repetida

### Estimativa
- Alta — ~10 workflows a revisar/criar, testes manuais no Telegram, sem código Go
