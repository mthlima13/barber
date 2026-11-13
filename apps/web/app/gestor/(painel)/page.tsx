"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  getAgenda,
  getSummary,
  listBarbers,
  listServices,
  updateAppointmentStatus,
  ApiError,
  type Appointment,
  type BarberAdmin,
  type DashboardSummary,
  type ServiceAdmin,
} from "@/lib/api";
import { getToken } from "@/lib/auth";
import { dateTime, price, statusLabel, todayIn } from "@/lib/format";

/** Acoes disponiveis por status (maquina de estados da API, secao 8). */
const ACTIONS: Record<string, { status: string; label: string; style: string }[]> =
  {
    PENDING: [
      {
        status: "CONFIRMED",
        label: "Confirmar",
        style: "bg-green-700 hover:bg-green-600 text-white",
      },
      {
        status: "CANCELLED",
        label: "Cancelar",
        style: "border border-red-300 text-red-700 hover:bg-red-50",
      },
    ],
    CONFIRMED: [
      {
        status: "COMPLETED",
        label: "Concluir",
        style: "bg-green-700 hover:bg-green-600 text-white",
      },
      {
        status: "NO_SHOW",
        label: "Nao compareceu",
        style: "border border-slate-300 hover:bg-slate-50",
      },
      {
        status: "CANCELLED",
        label: "Cancelar",
        style: "border border-red-300 text-red-700 hover:bg-red-50",
      },
    ],
  };

/** Agenda do dia do gestor (spec, secao 6): lista por data e transicoes. */
export default function AgendaPage() {
  const [date, setDate] = useState(() =>
    todayIn("America/Sao_Paulo"),
  );
  const [barberId, setBarberId] = useState<string>("");
  const [items, setItems] = useState<Appointment[] | null>(null);
  const [services, setServices] = useState<ServiceAdmin[]>([]);
  const [barbers, setBarbers] = useState<BarberAdmin[]>([]);
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const token = getToken();

  const load = useCallback(async () => {
    if (!token) return;
    setError(null);
    try {
      const [agenda, serviceList, barberList, daySummary] = await Promise.all([
        getAgenda(date, token, barberId || undefined),
        listServices(token),
        listBarbers(token),
        getSummary(date, token).catch(() => null),
      ]);
      setItems(agenda);
      setServices(serviceList);
      setBarbers(barberList);
      setSummary(daySummary);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        window.location.href = "/gestor/login";
        return;
      }
      setError(e instanceof ApiError ? e.message : "Erro ao carregar agenda");
      setItems([]);
    }
  }, [token, date, barberId]);

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

  async function changeStatus(appointmentId: string, status: string) {
    if (!token) return;
    setBusyId(appointmentId);
    setError(null);
    try {
      const updated = await updateAppointmentStatus(appointmentId, status, token);
      setItems((current) =>
        (current ?? []).map((item) => (item.id === updated.id ? updated : item)),
      );
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro ao atualizar status");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div>
      <div className="flex flex-wrap items-end gap-3">
        <h1 className="text-xl font-bold">Agenda</h1>
        <label className="text-sm">
          <span className="mr-2 font-medium">Data</span>
          <input
            type="date"
            className="rounded-lg border border-slate-300 p-2 text-sm"
            value={date}
            onChange={(e) => setDate(e.target.value)}
          />
        </label>
        <label className="text-sm">
          <span className="mr-2 font-medium">Profissional</span>
          <select
            className="rounded-lg border border-slate-300 p-2 text-sm"
            value={barberId}
            onChange={(e) => setBarberId(e.target.value)}
          >
            <option value="">Todos</option>
            {barbers.map((barber) => (
              <option key={barber.id} value={barber.id}>
                {barber.displayName}
              </option>
            ))}
          </select>
        </label>
      </div>

      {error && (
        <p className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700" role="alert">
          {error}
        </p>
      )}

      {summary && (
        <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-5">
          <SummaryCard
            label="Reservas do dia"
            value={String(summary.appointments.total)}
            hint={`${summary.appointments.confirmed} confirmadas, ${summary.appointments.pending} pendentes`}
          />
          <SummaryCard
            label="Previsao do dia"
            value={price(summary.appointments.expectedRevenueCents)}
            hint="pendentes + confirmadas + concluidas"
          />
          <SummaryCard
            label="Fila de espera"
            value={String(summary.waitlist.waiting + summary.waitlist.offered)}
            hint={`${summary.waitlist.waiting} aguardando, ${summary.waitlist.offered} com oferta`}
          />
          <SummaryCard
            label="Outbox"
            value={String(summary.outbox.pending)}
            hint={
              summary.outbox.failed > 0
                ? `${summary.outbox.failed} com falha — verificar`
                : "sem falhas"
            }
            alert={summary.outbox.failed > 0}
          />
          <SummaryCard
            label="Concluidas"
            value={String(summary.appointments.completed)}
            hint={`${summary.appointments.noShow} nao compareceu`}
          />
        </div>
      )}

      {items === null ? (
        <p className="mt-6 text-sm text-slate-500">Carregando...</p>
      ) : items.length === 0 ? (
        <p className="mt-6 text-sm text-slate-500">
          Nenhuma reserva nesta data.
        </p>
      ) : (
        <ul className="mt-4 grid gap-2">
          {items.map((item) => (
            <li
              key={item.id}
              className="flex flex-wrap items-center gap-3 rounded-lg bg-white p-4 shadow"
            >
              <div className="min-w-48">
                <p className="font-semibold">
                  {dateTime(item.startsAt, "America/Sao_Paulo")}
                </p>
                <p className="text-xs text-slate-500">
                  {serviceById.get(item.serviceId)?.name ?? "Servico"} -{" "}
                  {barberById.get(item.barberId)?.displayName ?? "Profissional"}{" "}
                  - {price(item.priceCents)}
                </p>
                <p className="font-mono text-[10px] text-slate-400">{item.id}</p>
              </div>
              <span
                className={`rounded-full px-3 py-1 text-xs font-semibold ${
                  item.status === "PENDING"
                    ? "bg-amber-100 text-amber-800"
                    : item.status === "CONFIRMED"
                      ? "bg-blue-100 text-blue-800"
                      : item.status === "COMPLETED"
                        ? "bg-green-100 text-green-800"
                        : "bg-slate-200 text-slate-700"
                }`}
              >
                {statusLabel(item.status)}
              </span>
              <span className="ml-auto flex flex-wrap gap-2">
                {(ACTIONS[item.status] ?? []).map((action) => (
                  <button
                    key={action.status}
                    type="button"
                    className={`rounded-lg px-3 py-1.5 text-sm font-semibold disabled:opacity-40 ${action.style}`}
                    disabled={busyId === item.id}
                    onClick={() => void changeStatus(item.id, action.status)}
                  >
                    {action.label}
                  </button>
                ))}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** Cartao do resumo do dia (GET /api/v1/dashboard/summary). */
function SummaryCard({
  label,
  value,
  hint,
  alert = false,
}: {
  label: string;
  value: string;
  hint: string;
  alert?: boolean;
}) {
  return (
    <div
      className={`rounded-lg p-3 shadow-sm ${
        alert ? "bg-red-50" : "bg-white"
      } border ${alert ? "border-red-200" : "border-slate-200"}`}
    >
      <p className="text-xs font-medium text-slate-500">{label}</p>
      <p className="mt-1 text-lg font-bold text-slate-800">{value}</p>
      <p className={`text-xs ${alert ? "text-red-700" : "text-slate-500"}`}>
        {hint}
      </p>
    </div>
  );
}
