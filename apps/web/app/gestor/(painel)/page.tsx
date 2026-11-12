"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  getAgenda,
  listBarbers,
  listServices,
  updateAppointmentStatus,
  ApiError,
  type Appointment,
  type BarberAdmin,
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
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const token = getToken();

  const load = useCallback(async () => {
    if (!token) return;
    setError(null);
    try {
      const [agenda, serviceList, barberList] = await Promise.all([
        getAgenda(date, token, barberId || undefined),
        listServices(token),
        listBarbers(token),
      ]);
      setItems(agenda);
      setServices(serviceList);
      setBarbers(barberList);
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
