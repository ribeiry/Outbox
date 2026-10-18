# Story 017: Mapping telegram_id → api_key no n8n (multiusuário)

**Como** operador do bot
**Quero** que o n8n descubra qual `api_key` usar com base no `telegram_id` do remetente
**Para** que cada usuário do Telegram acesse apenas sua própria carteira

### Contexto

Hoje o n8n usa uma credencial fixa (uma api_key) para chamar a API — funciona só para 1 usuário. Se outro usuário mandar mensagem no bot, o n8n usaria a mesma api_key e acessaria carteira errada.

Decisão: manter mapping estático **dentro do próprio n8n** (Static Data ou nó Function com objeto literal), sem depender de novo endpoint na API.

**Trade-off aceito:** cada novo usuário exige edição manual do workflow. Aceitável enquanto a base é pequena. Evolução para lookup dinâmico via API fica como story futura se escalar.

### Critérios de Aceitação

- [ ] AC1: Workflow n8n contém nó **Function/Code** com mapping `{telegram_id: api_key}` no Static Data ou objeto literal
- [ ] AC2: Ao receber mensagem do Telegram, o workflow extrai `message.from.id`
- [ ] AC3: Lookup no mapping devolve a `api_key` correspondente
- [ ] AC4: `api_key` é injetada no header `X-API-Key` das chamadas HTTP subsequentes
- [ ] AC5: `telegram_id` não cadastrado → responde ao usuário: "Você não está cadastrado. Contato: @admin"
- [ ] AC6: Documentação em `docs/n8n/multiusuario.md` explicando como adicionar novo usuário
- [ ] AC7: Mapping NÃO commitado em código Go — fica apenas no export do workflow n8n

### Padrão do nó Function

```javascript
const mapping = {
  "123456789": "api-key-do-augusto",
  "987654321": "api-key-do-outro-usuario"
};

const telegramId = String($input.item.json.message.from.id);
const apiKey = mapping[telegramId];

if (!apiKey) {
  return [{
    json: {
      unauthorized: true,
      chat_id: telegramId,
      text: "❌ Você não está cadastrado. Contato: @admin"
    }
  }];
}

return [{
  json: {
    ...items[0].json,
    api_key: apiKey,
    chat_id: telegramId
  }
}];
```

Nós HTTP Request subsequentes usam `{{$json.api_key}}` no header `X-API-Key`.

### Fluxo

```
Telegram Trigger
  ↓
Function: lookup api_key por telegram_id
  ↓
IF unauthorized → Telegram Send (msg de erro) → END
  ↓
Switch por comando (/status, /ativos, etc.)
  ↓
HTTP Request → API Go (com X-API-Key)
  ↓
Format → Telegram Send
```

### Como adicionar novo usuário (runbook)

1. Usuário chama `POST /users` (via curl direto ou comando de cadastro no bot)
2. Você recebe a `api_key` de retorno
3. Abre o workflow no n8n → nó Function → adiciona linha no mapping
4. Salva e ativa

### Edge Cases
- `message.from.id` ausente (mensagem de canal, edição) → tratar como não autorizado
- Callback query (`callback_query.from.id`) → mesmo lookup, extrair de campo diferente (relevante para story 015)
- Mapping vazio → mensagem clara para primeiro usuário

### Fora de escopo
- Lookup dinâmico via endpoint (`GET /internal/users/by-telegram/:id`) — story futura se necessário
- Auto-cadastro pelo bot (usuário manda `/start` → workflow chama `POST /users` e atualiza mapping)
- Interface de administração de usuários

### Notas
- Mapping fica no n8n, **não no repositório** — evita commit acidental de api_keys
- Backup: exportar workflow como JSON periodicamente (sem credenciais)
- Se n8n for reprovisionado (perda do volume), mapping precisa ser refeito — documentar isso

### Dependências
- Story 016 (ajuste dos endpoints no n8n) — este mapping é usado por todos os workflows ajustados lá

### Estimativa
- Baixa — 1 nó Function reusável em todos os workflows + documentação
