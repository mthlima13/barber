# BarberFlow — WhatsApp Cloud API em produção

Como colocar a entrega **real** de mensagens no ar (o código já envia; o
que falta é conta + templates aprovados na Meta).

## 1. Credenciais (Meta Business / WhatsApp Cloud API)

1. App no [Meta for Developers](https://developers.facebook.com) com
   produto **WhatsApp** adicionado
2. Número de telefone comercial com o `WHATSAPP_PHONE_NUMBER_ID`
   (numérico, no painel da Meta)
3. Token de acesso de sistema com permissão
   `whatsapp_business_messaging` → `WHATSAPP_ACCESS_TOKEN`
   (token permanente, não o temporário do painel de testes)
4. `WHATSAPP_APP_SECRET` (Assinatura do webhook, no painel do app) e
   `WHATSAPP_VERIFY_TOKEN` (valor que **você escolhe** — a API valida o
   handshake do webhook contra ele)
5. `.env` de produção com `WHATSAPP_ENABLED=true` e as 4 variáveis acima
6. Webhook: URL `https://<api>/api/v1/webhooks/whatsapp`, assinatura
   verificada por `X-Hub-Signature-256` (já implementado, com dedupe)

## 2. Templates a aprovar na Meta (obrigatório)

**Toda mensagem deste sistema é iniciada pela barbearia** (reserva,
lembrete, fila). Fora da janela de atendimento de 24h do cliente, a Meta
**só aceita mensagem template** — texto livre falha com erro 131030. Por
isso o envio usa mensagem template por padrão
(`WHATSAPP_TEMPLATE_LANGUAGE=pt_BR`; vazio volta para texto livre, que só
funciona dentro da janela).

Cada template: categoria **UTILITY**, idioma **pt_BR**, corpo com **um
único parâmetro** `{{1}}`. O sistema envia o texto renderizado como
`{{1}}` — o nome do template deriva do tipo do evento (letras minúsculas):

| Nome do template (criar exatamente assim) | Quando chega |
|---|---|
| `appointment_created` | Reserva criada pelo cliente (site ou fila) |
| `appointment_cancelled` | Reserva cancelada (cliente ou gestor) |
| `appointment_rescheduled` | Remarcação pelo link seguro |
| `appointment_confirmed` | Gestor confirmou a reserva no painel |
| `appointment_reminder` | Lembrete automático 24h antes |
| `appointment_expired` | PENDING expirou sem confirmação (job) |
| `waitlist_slot_available` | Vaga aberta ofertada à fila (5 min) |
| `manager_appointment_created` | Gestor: reserva nova |
| `manager_waitlist_joined` | Gestor: inscrição na fila |
| `manager_appointment_cancelled` | Gestor: cancelamento do cliente |
| `manager_appointment_rescheduled` | Gestor: remarcação do cliente |

No corpo de cada template, use `{{1}}` e cadastre um texto de exemplo
(a Meta exige exemplo de variável): cole a mensagem correspondente da
funcionalidade, ex. para `appointment_created`:

> Ola, Maria! Sua reserva esta marcada para 14/11 as 10:00. Se precisar
> remarcar ou cancelar, use o link seguro que voce recebeu.

Dica: as mensagens renderizadas são exatamente as do
`MessageTemplates.java` — copie de lá para os exemplos da Meta.

## 3. Variáveis de ambiente

| Variável | Uso |
|---|---|
| `WHATSAPP_ENABLED` | `true` liga a entrega real (HTTP); `false` loga e marca SENT |
| `WHATSAPP_PHONE_NUMBER_ID` / `WHATSAPP_ACCESS_TOKEN` | credenciais da Cloud API |
| `WHATSAPP_APP_SECRET` / `WHATSAPP_VERIFY_TOKEN` | validação do webhook |
| `WHATSAPP_TEMPLATE_LANGUAGE` | padrão `pt_BR`; vazio = texto livre (só na janela 24h) |
| `WHATSAPP_POLL_INTERVAL_MILLIS` | ciclo do worker (padrão 15000) |
| `REMINDERS_ENABLED` / `REMINDERS_WINDOW_HOURS` | lembrete 24h |
| `APPOINTMENT_EXPIRY_ENABLED` / `APPOINTMENT_EXPIRY_PENDING_TTL_HOURS` | expiração de PENDING |

## 4. Operação

- Eventos ficam na outbox do painel do gestor: SENT (verde), PENDING
  (âmbar), **FAILED** (vermelho, com o erro da última tentativa) e
  SKIPPED (cinza — mensagem suprimida porque perdeu a razão de existir,
  ex.: lembrete de reserva que o cliente cancelou antes da entrega)
- Backoff exponencial (5 tentativas) antes de FAILED; o webhook de status
  correlaciona entrega/leitura pelo `messageId` persistido
- Limites de taxa da Meta (número comercial): acompanhe erros `131047` /
  `131048` no painel da Meta; o worker respeita o retry interno
