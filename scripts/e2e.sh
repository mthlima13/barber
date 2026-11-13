#!/usr/bin/env bash
# BarberFlow — teste E2E do fluxo de ponta a ponta (Parte 28): login do
# gestor, catalogo, disponibilidade, reserva publica com idempotencia,
# link seguro do cliente (consulta/remarcacao/cancelamento), avise-me e
# outbox do painel. Complementa o smoke.sh (so infra/health).
#
# Uso:
#   BASE_URL=http://localhost:8081 MANAGER_EMAIL=... MANAGER_PASSWORD=... ./scripts/e2e.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
SLUG="${SLUG:-barbearia-piloto}"
MANAGER_EMAIL="${MANAGER_EMAIL:-gestor@test.local}"
MANAGER_PASSWORD="${MANAGER_PASSWORD:-senha-segura-123}"

fail() { echo "FALHOU: $1" >&2; exit 1; }
# Primeiro valor string de um campo JSON de nivel superior (corpos simples)
json_str() { printf '%s' "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4; }

echo "==> API: ${BASE_URL}"
stamp=$(date +%s)
auth_post() { curl -fsS -X POST -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" "$@"; }
auth_put() { curl -fsS -X PUT -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" "$@"; }

# 1. Login do gestor
LOGIN=$(curl -fsS -H "Content-Type: application/json" \
  -d "{\"email\":\"${MANAGER_EMAIL}\",\"password\":\"${MANAGER_PASSWORD}\"}" \
  "${BASE_URL}/api/v1/auth/login") || fail "login do gestor"
TOKEN=$(json_str "$LOGIN" accessToken)
[ -n "${TOKEN}" ] || fail "login sem accessToken"
echo "[ok] login do gestor"

# 2. Catalogo: barbeiro + servico + expediente (+3 dias)
BARBER=$(auth_post -d "{\"displayName\":\"E2E ${stamp}\"}" \
  "${BASE_URL}/api/v1/dashboard/barbers") || fail "criar barbeiro"
BARBER_ID=$(json_str "$BARBER" id)
SERVICE=$(auth_post -d "{\"name\":\"Corte E2E ${stamp}\",\"durationMinutes\":30,\"priceCents\":5000}" \
  "${BASE_URL}/api/v1/dashboard/services") || fail "criar servico"
SERVICE_ID=$(json_str "$SERVICE" id)
[ -n "${BARBER_ID}" ] && [ -n "${SERVICE_ID}" ] || fail "catalogo sem ids"
auth_put -d "{\"serviceIds\":[\"${SERVICE_ID}\"]}" \
  "${BASE_URL}/api/v1/dashboard/barbers/${BARBER_ID}/services" > /dev/null \
  || fail "vincular servico ao barbeiro"
# GNU date (Linux) com fallback BSD/macOS
DATE=$(date -d "+3 days" +%Y-%m-%d 2>/dev/null || date -v+3d +%Y-%m-%d)
WEEKDAY=$(date -d "+3 days" +%w 2>/dev/null || date -v+3d +%w)
auth_put -d "[{\"weekday\":${WEEKDAY},\"startLocal\":\"09:00\",\"endLocal\":\"18:00\"}]" \
  "${BASE_URL}/api/v1/dashboard/barbers/${BARBER_ID}/hours" > /dev/null \
  || fail "expediente do barbeiro"
echo "[ok] barbeiro/servico/expediente (${DATE})"

# 3. Disponibilidade publica: dois slots para reservar e remarcar
AVAIL=$(curl -fsS \
  "${BASE_URL}/api/v1/public/barbershops/${SLUG}/availability?barberId=${BARBER_ID}&serviceId=${SERVICE_ID}&date=${DATE}") \
  || fail "disponibilidade publica"
SLOT1=$(printf '%s' "$AVAIL" | grep -o '"startAt":"[^"]*"' | head -1 | cut -d'"' -f4)
SLOT2=$(printf '%s' "$AVAIL" | grep -o '"startAt":"[^"]*"' | sed -n '2p' | cut -d'"' -f4)
[ -n "${SLOT1}" ] && [ -n "${SLOT2}" ] || fail "menos de 2 slots livres em ${DATE}"
echo "[ok] disponibilidade: ${SLOT1} / ${SLOT2}"

# 4. Reserva publica (com consentimento LGPD) — criacao e replay idempotente
BOOK_BODY="{\"slug\":\"${SLUG}\",\"barberId\":\"${BARBER_ID}\",\"serviceId\":\"${SERVICE_ID}\",\"startsAt\":\"${SLOT1}\",\"customer\":{\"name\":\"Cliente E2E\",\"phone\":\"+5511900000001\",\"consented\":true}}"
BOOK=$(curl -fsS -H "Content-Type: application/json" -H "Idempotency-Key: e2e-${stamp}" \
  -d "${BOOK_BODY}" "${BASE_URL}/api/v1/public/appointments") || fail "criar reserva"
APPT_ID=$(json_str "$BOOK" id)
MTOKEN=$(json_str "$BOOK" manageToken)
[ -n "${APPT_ID}" ] && [ -n "${MTOKEN}" ] || fail "reserva sem id/manageToken"
REPLAY=$(curl -fsS -H "Content-Type: application/json" -H "Idempotency-Key: e2e-${stamp}" \
  -d "${BOOK_BODY}" "${BASE_URL}/api/v1/public/appointments") || fail "replay da reserva"
[ "$(json_str "$REPLAY" id)" = "${APPT_ID}" ] || fail "replay deveria retornar a mesma reserva"
echo "[ok] reserva criada e idempotente (${APPT_ID})"

# 5. Link seguro: consulta e remarcacao
curl -fsS -H "Authorization: Bearer ${MTOKEN}" \
  "${BASE_URL}/api/v1/customer/appointments/${APPT_ID}" > /dev/null \
  || fail "consulta pelo link seguro"
curl -fsS -X POST -H "Authorization: Bearer ${MTOKEN}" -H "Content-Type: application/json" \
  -d "{\"startsAt\":\"${SLOT2}\"}" \
  "${BASE_URL}/api/v1/customer/appointments/${APPT_ID}/reschedule" > /dev/null \
  || fail "remarcacao pelo link seguro"
echo "[ok] link seguro: consulta e remarcacao"

# 6. Cancelamento pelo link seguro (libera o slot)
curl -fsS -X POST -H "Authorization: Bearer ${MTOKEN}" \
  "${BASE_URL}/api/v1/customer/appointments/${APPT_ID}/cancel" > /dev/null \
  || fail "cancelamento pelo link seguro"
echo "[ok] cancelamento pelo link seguro"

# 7. Avise-me (lista de espera) para o slot do profissional
WAITLIST=$(curl -fsS -H "Content-Type: application/json" \
  -d "{\"slug\":\"${SLUG}\",\"barberId\":\"${BARBER_ID}\",\"serviceId\":\"${SERVICE_ID}\",\"startsAt\":\"${SLOT2}\",\"customer\":{\"name\":\"Fila E2E\",\"phone\":\"+5511900000002\",\"consented\":true}}" \
  "${BASE_URL}/api/v1/public/waitlist") || fail "inscricao na lista de espera"
ENTRY_ID=$(json_str "$WAITLIST" id)
[ -n "${ENTRY_ID}" ] || fail "inscricao sem id"
echo "[ok] avise-me inscrito (${ENTRY_ID})"

# 8. Outbox do painel: eventos do fluxo registrados na mesma transacao
OUTBOX=$(curl -fsS -H "Authorization: Bearer ${TOKEN}" \
  "${BASE_URL}/api/v1/dashboard/outbox") || fail "outbox do painel"
printf '%s' "$OUTBOX" | grep -q "APPOINTMENT_CREATED" || fail "evento APPOINTMENT_CREATED ausente"
printf '%s' "$OUTBOX" | grep -q "APPOINTMENT_RESCHEDULED" || fail "evento APPOINTMENT_RESCHEDULED ausente"
printf '%s' "$OUTBOX" | grep -q "APPOINTMENT_CANCELLED" || fail "evento APPOINTMENT_CANCELLED ausente"
printf '%s' "$OUTBOX" | grep -q "MANAGER_WAITLIST_JOINED" || fail "evento MANAGER_WAITLIST_JOINED ausente"
echo "[ok] outbox com eventos do fluxo (criacao, remarcacao, cancelamento, fila)"

echo ""
echo "E2E OK"
