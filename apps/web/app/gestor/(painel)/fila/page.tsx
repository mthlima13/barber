"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  listBarbers,
  listServices,
  listWaitlist,
  processWaitlist,
  ApiError,
  type BarberAdmin,
  type ServiceAdmin,
  type WaitlistEntry,
} from "@/lib/api";
import { getToken } from "@/lib/auth";
import { dateTime, slotTime } from "@/lib/format";

/** Lista de espera no painel (spec, secao 6): fila e processamento manual. */
export default function FilaPage() {
  const [items, setItems] = useState<WaitlistEntry[] | null>(null);
  const [services, setServices] = useState<ServiceAdmin[]>([]);
  const [barbers, setBarbers] = useState<BarberAdmin[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const token = getToken();

  const load = useCallback(async () => {
    if (!token) return;
    setError(null);
    try {
      const [entries, serviceList, barberList] = await Promise.all([
        listWaitlist(token),
        listServices(token),
        listBarbers(token),
      ]);
      setItems(entries);
      setServices(serviceList);
      setBarbers(barberList);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        window.location.href = "/gestor/login";
        return;
      }
      setError(e instanceof ApiError ? e.message : "Erro ao carregar fila");
      setItems([]);
    }
  }, [token]);

  useEffect(() => {
    void load();
  }, [load]);

  const serviceById = useMemo(
    () => new Map(services.map((s) => [s.id, s])),
    [services],
  );
  const barberById = useMemo(
    () => new Map(barbers.map((b) => [b.id, b])),
    [barbers],
  );

  async function onProcess() {
    if (!token) return;
    setBusy(true);
    setError(null);
    try {
      await processWaitlist(token);
      await load();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro ao processar fila");
    } finally {
      setBusy(false);
    }
  }

  const entryStatusLabel: Record<string, string> = {
    WAITING: "Aguardando",
    OFFERED: "Oferta enviada",
    FULFILLED: "Atendido",
    CANCELLED: "Cancelado",
    EXPIRED: "Oferta expirada",
  };

  return (
    <div>
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-xl font-bold">Fila de espera</h1>
        <button
          type="button"
          className="ml-auto rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700 disabled:opacity-40"
          disabled={busy}
          onClick={() => void onProcess()}
        >
          {busy ? "Processando..." : "Processar fila"}
        </button>
      </div>
      <p className="mt-1 text-xs text-slate-500">
        Expira ofertas vencidas (5 min) e oferece horarios livres ao primeiro
        da fila, em ordem de inscricao.
      </p>

      {error && (
        <p className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700" role="alert">
          {error}
        </p>
      )}

      {items === null ? (
        <p className="mt-6 text-sm text-slate-500">Carregando...</p>
      ) : items.length === 0 ? (
        <p className="mt-6 text-sm text-slate-500">Fila vazia.</p>
      ) : (
        <ul className="mt-4 grid gap-2">
          {items.map((entry) => (
            <li
              key={entry.id}
              className="flex flex-wrap items-center gap-3 rounded-lg bg-white p-4 shadow"
            >
              <div className="min-w-56">
                <p className="font-semibold">
                  {slotTime(entry.desiredStartAt, "America/Sao_Paulo")} -{" "}
                  {slotTime(entry.desiredEndAt, "America/Sao_Paulo")}
                </p>
                <p className="text-xs text-slate-500">
                  {serviceById.get(entry.serviceId)?.name ?? "Servico"} -{" "}
                  {barberById.get(entry.barberId)?.displayName ?? "Profissional"}
                  {" - "}
                  inscrito em {dateTime(entry.createdAt, "America/Sao_Paulo")}
                </p>
                {entry.expiresAt && (
                  <p className="text-xs text-amber-700">
                    Oferta expira em {dateTime(entry.expiresAt, "America/Sao_Paulo")}
                  </p>
                )}
              </div>
              <span
                className={`rounded-full px-3 py-1 text-xs font-semibold ${
                  entry.status === "WAITING"
                    ? "bg-amber-100 text-amber-800"
                    : entry.status === "OFFERED"
                      ? "bg-blue-100 text-blue-800"
                      : entry.status === "FULFILLED"
                        ? "bg-green-100 text-green-800"
                        : "bg-slate-200 text-slate-700"
                }`}
              >
                {entryStatusLabel[entry.status] ?? entry.status}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
