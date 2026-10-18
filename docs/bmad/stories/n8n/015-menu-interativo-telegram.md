# Story 015: Menu interativo no Telegram (inline keyboard via n8n)

**Como** usuário do bot no Telegram
**Quero** navegar pelas funcionalidades clicando em botões
**Para** não precisar decorar comandos (`/status`, `/ativos`, `/resumo`, etc.)

### Contexto

Hoje o usuário digita comandos via texto. Um menu interativo com botões (inline keyboard) reduz fricção, especialmente para usuários novos ou em mobile.

Toda a lógica de UI fica no **n8n** (não na API Go) — o bot Telegram envia botões que disparam callbacks, o n8n captura o `callback_query` e chama o endpoint correto da API.

### Critérios de Aceitação

- [ ] AC1: Comando `/menu` (ou `/start`) exibe teclado inline com botões:
  - 📊 Status
  - 📁 Ativos
  - 📈 Performance
  - 📅 Resumo Semanal
  - 📅 Resumo Mensal
  - 🎯 Benchmark
  - 🥧 Alocação por Setor
  - 🔔 Alertas
- [ ] AC2: Clique em botão dispara chamada correspondente à API e retorna a mensagem formatada
- [ ] AC3: Após resposta, botão "🔙 Voltar ao menu" permite retornar sem redigitar `/menu`
- [ ] AC4: Submenu de **Alertas** oferece:
  - 📋 Listar alertas
  - ➕ Criar alerta (via wizard de texto — fora do escopo desta story, apenas botão)
  - ✅ Verificar disparados
- [ ] AC5: Fluxo funciona para múltiplos usuários simultâneos (n8n usa `chat_id` do callback)
- [ ] AC6: Documentação em `docs/n8n-menu.md` com print do workflow

### Fluxo n8n (alto nível)

```
Telegram Trigger (message /menu)
  ↓
Send Message with inline_keyboard
  ↓
Telegram Trigger (callback_query)
  ↓
Switch por callback_data
  ↓
HTTP Request → API Go (com X-API-Key do usuário)
  ↓
Format response (Markdown)
  ↓
Edit Message ou Send Message com botão "Voltar"
```

### Payload de exemplo (Telegram Bot API)

```json
{
  "chat_id": 123456,
  "text": "Escolha uma opção:",
  "reply_markup": {
    "inline_keyboard": [
      [{"text": "📊 Status", "callback_data": "status"}],
      [{"text": "📁 Ativos", "callback_data": "assets"}],
      [{"text": "📈 Performance", "callback_data": "performance"}]
    ]
  }
}
```

### Edge Cases
- Usuário não cadastrado clica em botão → n8n detecta ausência de api_key e responde "Registre-se com /start"
- Callback expirou (mensagem antiga) → resposta genérica "Sessão expirada, envie /menu"
- API Go retorna erro → mostrar mensagem amigável, botão "Voltar"

### Fora de escopo
- Wizard de criação de alerta com múltiplos passos (story separada)
- Persistência de "estado da conversa" no n8n (usar callback_data stateless)
- Menu de configurações (renomear ativo, alterar setor via botão)
- Traduções (PT-BR apenas)

### Notas
- **Nenhuma alteração na API Go** — story é 100% n8n
- Requer mapeamento entre `telegram_id` do usuário e sua `api_key` (já existe via `POST /users`)
- n8n precisa armazenar api_keys por chat_id (variável de workflow ou lookup na API — decidir no design)
- Exportar workflow do n8n como JSON e versionar em `docs/n8n/menu-workflow.json`

### Estimativa
- Média — sem código Go, mas workflow n8n com múltiplos nós e testes manuais no Telegram
