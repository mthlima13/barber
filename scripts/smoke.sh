#!/usr/bin/env bash
# BarberFlow — smoke test de staging/producao (spec, Fase 5 e linha 286).
# Uso: BASE_URL=https://api.exemplo.com WEB_URL=https://app.exemplo.com ./scripts/smoke.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
WEB_URL="${WEB_URL:-http://localhost:3000}"
SLUG="${SLUG:-barbearia-piloto}"

fail() { echo "FALHOU: $1" >&2; exit 1; }

echo "==> API: ${BASE_URL}"
echo "==> Web: ${WEB_URL}"

# 1. Healthcheck (orquestracao/rollout)
curl -fsS "${BASE_URL}/actuator/health" > /dev/null || fail "healthcheck da API"
echo "[ok] /actuator/health"

# 2. Catalogo publico da barbearia
curl -fsS "${BASE_URL}/api/v1/public/barbershops/${SLUG}" > /dev/null \
  || fail "catalogo publico (${SLUG})"
curl -fsS "${BASE_URL}/api/v1/public/barbershops/${SLUG}/services" > /dev/null \
  || fail "servicos publicos"
curl -fsS "${BASE_URL}/api/v1/public/barbershops/${SLUG}/barbers" > /dev/null \
  || fail "profissionais publicos"
echo "[ok] catalogo publico"

# 3. Headers de seguranca (spec, linha 216)
HEADERS=$(curl -sSI "${BASE_URL}/api/v1/public/barbershops/${SLUG}")
echo "${HEADERS}" | grep -qi "x-content-type-options: *nosniff" \
  || fail "header X-Content-Type-Options ausente"
echo "${HEADERS}" | grep -qi "x-frame-options: *deny" \
  || fail "header X-Frame-Options ausente"
echo "${HEADERS}" | grep -qi "content-security-policy:" \
  || fail "header Content-Security-Policy ausente"
echo "[ok] headers de seguranca"

# 4. Login com credencial errada NAO autentica (401)
STATUS=$(curl -sS -o /dev/null -w '%{http_code}' \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke-invalido@exemplo.com","password":"errada"}' \
  "${BASE_URL}/api/v1/auth/login")
[ "${STATUS}" = "401" ] || fail "login invalido deveria dar 401, veio ${STATUS}"
echo "[ok] login invalido rejeitado (401)"

# 5. Endpoint do cliente sem token: acesso negado
STATUS=$(curl -sS -o /dev/null -w '%{http_code}' "${BASE_URL}/api/v1/me")
[ "${STATUS}" = "401" ] || fail "endpoint autenticado deveria dar 401, veio ${STATUS}"
echo "[ok] rota autenticada exige token (401)"

# 6. Frontend no ar
curl -fsS "${WEB_URL}/privacidade" > /dev/null || fail "pagina de privacidade"
echo "[ok] frontend (${WEB_URL})"

echo ""
echo "SMOKE OK"
