# BarberFlow — Roteiro de captura (screenshots/GIF para o portfolio)

Como preparar o estado e capturar as telas que contam a historia do
sistema em 6 imagens. Depois de capturar, salve em `docs/img/` e
descomente o bloco "Screenshots" no README.

Preparo:

```bash
docker compose -f infra/compose.yaml up --build
# bootstrap do gestor (curl no README) + cadastro de 2 servicos,
# 2 profissionais e expediente 09:00-19:00 no painel
```

Dica de navegador: `F12` -> toggle device toolbar (view de celular ~390px)
para as telas publicas; view desktop para o painel.

## 1. Reserva publica (desktop, view de celular)

- `/` redireciona para `/b/barbearia-piloto`
- Capturar: catalogo -> escolha de servico/profissional -> grade de
  horarios -> formulario do cliente (telefone com mascara)
- **Estado que vende**: deixe o dia cheio e capture a tela mostrando o
  botao/formulario **"Avise-me"** (a lista de espera aparece quando
  acabam os slots) — e o diferencial do projeto

## 2. Confirmacao da reserva (mesma view)

- Tela de resumo antes de confirmar -> confirmar -> tela final com o
  aviso do **link seguro** ("guarde este link")
- Se WhatsApp estiver desligado, o aviso de mensagem aparece no log da
  API — pode citar na legenda

## 3. Link seguro no celular

- Abra `/reservas/{id}?t=...` (o link da tela anterior) no view de celular
- Capturar: selo de status colorido + data completa + **aviso do lembrete
  24h** + botoes remarcar/cancelar
- Bônus: capturar a grade de remarcacao (busca de horarios -> confirmar)

## 4. Painel do gestor — agenda do dia

- `/gestor` com 3-4 reservas do dia (1 confirmada, 1 pendente, 1 fila)
- Capturar: **cartoes-resumo** no topo (reservas, previsao de receita,
  fila, outbox) + lista com nome do cliente e acoes por status

## 5. Fila de espera com oferta ativa

- Crie uma inscricao "Avise-me" para um slot cheio e, pelo painel,
  cancele a reserva que ocupa o slot -> a fila mostra o cliente com
  **oferta ativa (timer de 5 min)**
- Alternativa: `GET /gestor/fila` com cliente resolvido (nome/telefone)

## 6. Painel — notificacoes (outbox)

- Rola ate a secao **"Notificacoes (outbox)"**: eventos SENT (verde),
  PENDING (ambar) e, se quiser demonstrar resiliencia, um FAILED
  (vermelho com o erro) — em dev basta derrubar o WhatsApp
  (`WHATSAPP_ENABLED=false` nao gera FAILED; FAILED so aparece com
  entrega real falhando)

## GIF opcional (hero do README)

Grave um GIF de ~15s: catalogo -> horario -> reserva -> painel mostrando
a reserva nova (ferramentas: ScreenToGif no Windows, gifski/Capture no
macOS). Limite: < 5 MB. Nome sugerido: `docs/img/demo.gif`.
