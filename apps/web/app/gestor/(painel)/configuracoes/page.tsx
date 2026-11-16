"use client";

import { useCallback, useEffect, useState } from "react";
import { getSettings, updateSettings, ApiError } from "@/lib/api";
import { getToken } from "@/lib/auth";
import { maskPhone, toE164 } from "@/lib/format";

/**
 * Configuracoes da barbearia (gestor): o WhatsApp informado aqui recebe as
 * notificacoes de negocio via outbox — reserva nova e inscricao na fila.
 * Deixar vazio e salvar desliga as notificacoes.
 */
export default function ConfiguracoesPage() {
  const [phone, setPhone] = useState("");
  const [loaded, setLoaded] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const token = getToken();

  const load = useCallback(async () => {
    if (!token) return;
    try {
      const settings = await getSettings(token);
      setPhone(settings.managerPhone ?? "");
      setLoaded(true);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        window.location.href = "/gestor/login";
        return;
      }
      setError(e instanceof ApiError ? e.message : "Erro ao carregar configuracoes");
    }
  }, [token]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!token) return;
    setError(null);
    setMessage(null);

    // Vazio = desligar (null); preenchido = E.164 obrigatorio
    const trimmed = phone.trim();
    let e164: string | null = null;
    if (trimmed.length > 0) {
      e164 = toE164(trimmed);
      if (!e164) {
        setError("Telefone invalido (use DDD, ex.: 11 97000-0000)");
        return;
      }
    }

    setBusy(true);
    try {
      const saved = await updateSettings(e164, token);
      setPhone(saved.managerPhone ?? "");
      setMessage(
        saved.managerPhone
          ? `Notificacoes ligadas para ${saved.managerPhone}`
          : "Notificacoes desligadas",
      );
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro ao salvar configuracoes");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <h1 className="text-xl font-bold">Configuracoes</h1>
      <p className="mt-1 text-sm text-zinc-400">
        Notificacoes de negocio para o WhatsApp da barbearia (entregues com o
        mesmo worker da fila de mensagens; cliente continua recebendo as
        mensagens dele normalmente).
      </p>

      {loaded ? (
        <form className="mt-6 max-w-md" onSubmit={onSubmit}>
          <label className="text-sm">
            <span className="font-medium">WhatsApp do gestor</span>
            <input
              className="mt-1 w-full rounded-lg border border-zinc-700 bg-zinc-900 p-2 text-sm text-zinc-100"
              value={phone}
              onChange={(e) => setPhone(maskPhone(e.target.value))}
              placeholder="(11) 97000-0000"
              inputMode="tel"
            />
            <span className="mt-1 block text-xs text-zinc-400">
              Deixe vazio para desligar as notificacoes.
            </span>
          </label>
          <button
            type="submit"
            className="mt-4 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-500 disabled:opacity-40"
            disabled={busy}
          >
            {busy ? "Salvando..." : "Salvar"}
          </button>
          {message && (
            <p className="mt-3 rounded-lg border border-emerald-900 bg-emerald-950/40 p-3 text-sm text-emerald-300">
              {message}
            </p>
          )}
          {error && (
            <p className="mt-3 rounded-lg border border-red-900 bg-red-950/60 p-3 text-sm text-red-400" role="alert">
              {error}
            </p>
          )}
          <p className="mt-4 text-xs text-zinc-400">
            Voce sera avisado quando uma reserva nova entrar (inclusive pela
            lista de espera) e quando alguem se inscrever na fila.
          </p>
        </form>
      ) : (
        <p className="mt-6 text-sm text-zinc-400">Carregando...</p>
      )}
    </div>
  );
}
