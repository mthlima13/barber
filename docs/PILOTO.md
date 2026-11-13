# BarberFlow — Plano do Piloto (Fase 5, seção 6)

Roteiro para o piloto com **uma barbearia real**. O objetivo não é "estar
pronto" — é **aprender**: o que a barbearia usa, o que ignora e o que
trava. Todo aprendizado vira issue no GitHub (template `Feedback do
piloto`).

## 1. Setup com a barbearia (sessão de ~1h, presencial ou chamada)

1. Deploy no ar (ver `PRODUCAO.md`) e smoke + E2E verdes
2. Bootstrap do gestor real (senha forte, trocada no primeiro acesso)
3. Cadastras junto com o gestor, **na tela dele**:
   - serviços com nome/duração/preço que a barbearia já usa (não inventar)
   - profissionais e expediente real
   - WhatsApp do gestor em `/gestor/configuracoes`
4. Cliente de teste marcado com nome "`TESTE`" nas reservas de ensaio
5. Primeira reserva feita JUNTO (gestor na mão no mouse): catálogo →
   horário → link seguro recebido no WhatsApp (ou log, se envio simulado)
6. Mostrar os três caminhos do gestor: agenda do dia, fila de espera,
   notificações (outbox) — e o avise-me do lado do cliente

## 2. O que observar (2 semanas)

### Persona gestor
- Confirma/cancela pelo painel quantas vezes por dia? (hipótese: o painel
  vira o "checklist de abertura" do dia)
- Configura o próprio WhatsApp sozinho? O card de Outbox com falha é
  entendido sem explicação?
- A fila de espera é usada no caos real (cliente ligando pedindo encaixe)?

### Persona barbeiro (papel BARBER)
- A agenda restrita ("minha agenda") é suficiente para o dia a dia?
- Conflito de horário no papel (bloqueio manual vs. reserva online)?

### Persona cliente
- O link seguro é entendido? Alguém tenta "criar conta"?
- O aviso de lembrete 24h reduz no-show? (comparar reservas antes/depois)
- Avise-me: quem entra na fila confirma a oferta em <5min?

## 3. Hipóteses com critério de sucesso

| Hipótese | Métrica | Sucesso |
|---|---|---|
| Gestor usa o painel no dia a dia | acessos/semana | >= 5 dias |
| Reserva online substitui telefone | reservas web / total | >= 30% |
| Fila recupera horário perdido | ofertas aceitas / ofertas | >= 30% |
| Lembrete reduz no-show | no-show semana 2 vs. semana 1 | queda |
| Notificações chegam e são lidas | outbox FAILED | ~ 0 |

Fonte das métricas: painel do gestor (cartões do dia), `/actuator/prometheus`
(`barberflow_appointments_total`, `barberflow_waitlist_offers_*`,
`barberflow_outbox_*`) e o `GET /api/v1/dashboard/outbox`.

## 4. Limitações conhecidas do MVP (confirmar se incomodam)

- Entrega real de WhatsApp depende das credenciais da Meta
  (`WHATSAPP_ENABLED=true`); sem elas o envio é simulado em log
- Rate limit **em memória**: válido para instância única (trocar por Redis
  se escalar horizontalmente)
- Reservas não têm depósito/sinal nem cobrança — só agenda + notificação
- Sem relatório além do dia (resumo do painel); histórico fica no banco
- Sem PWA/app: o acesso é pelo navegador (link seguro funciona no celular)

Cada item vira pergunta direta no feedback: "isso te bloqueou alguma vez?"

## 5. Rotina de feedback

- **Diário (5min)**: gestor manda áudio/mensagem WhatsApp com o que
  atrapalhou — registrar issue no mesmo dia (template de feedback)
- **Fim da semana 1**: revisar métricas da seção 3 + issues abertas;
  classificar cada issue em `bloqueia` / `incomoda` / `desejo`
- **Retrospectiva (fim da semana 2)**: continuar o piloto, ajustar ou
  fechar — decidir a Fase 6 com dados, não com achismo

Regra de ouro: **nada é "reclamação"** — toda fricção é dado. Bug real
(repro, print, o que esperava) segue o template normal de issue; fricção
de uso segue `Feedback do piloto`.
