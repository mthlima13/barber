"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  getBarberAgenda,
  listServices,
  updateBarberAppointmentStatus,
  ApiError,
  type Appointment,
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

/**
 * Minha agenda (papel BARBER, spec linha 361): somente as proprias
 * reservas do dia; a autorizacao por recurso fica na API.
 */
export default function MinhaAgendaPage() {
  const [date, setDate] = useState(() => todayIn("America/Sao_Paulo"));
  const [items, setItems] = useState<Appointment[] | null>(null);
  const [services, setServices] = useState<ServiceAdmin[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const token = getToken();

  const load = useCallback(async () => {
    if (!token) return;
    setError(null);
    try {
      const [agenda, serviceList] = await Promise.all([
        getBarberAgenda(date, token),
        listServices(token).catch(() => [] as ServiceAdmin[]),
      ]);
      setItems(agenda);
      setServices(serviceList);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        window.location.href = "/gestor/login";
        return;
      }
      if (e instanceof ApiError && e.status === 403) {
        setError("Esta conta nao tem perfil de profissional nesta barbearia");
        setItems([]);
        return;
      }
      setError(e instanceof ApiError ? e.message : "Erro ao carregar agenda");
      setItems([]);
    }
  }, [token, date]);

  useEffect(() => {
    void load();
  }, [load]);

  const serviceById = useMemo(
    () => new Map(services.map((s) => [s.id, s])),
    [services],
  );

  async function changeStatus(appointmentId: string, status: string) {
    if (!token) return;
    setBusyId(appointmentId);
    setError(null);
    try {
      const updated = await updateBarberAppointmentStatus(
        appointmentId,
        status,
        token,
      );
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
        <h1 className="text-xl font-bold">Minha agenda</h1>
        <label className="text-sm">
          <span className="mr-2 font-medium">Data</span>
          <input
            type="date"
            className="rounded-lg border border-slate-300 p-2 text-sm"
            value={date}
            onChange={(e) => setDate(e.target.value)}
          />
        </label>
      </div>
      {error && (
        <p className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}
      <div className="mt-4 space-y-3">
        {items === null && <p className="text-sm text-slate-500">Carregando...</p>}
        {items?.length === 0 && (
          <p className="text-sm text-slate-500">Sem reservas nesta data.</p>
        )}
        {items?.map((item) => {
          const actions = ACTIONS[item.status] ?? [];
          const service = item.serviceId ? serviceById.get(item.serviceId) : undefined;
          return (
            <div
              key={item.id}
              className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm"
            >
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-sm font-semibold text-slate-800">
                  {dateTime(item.startsAt, "America/Sao_Paulo")}
                </span>
                <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700">
                  {statusLabel(item.status)}
                </span>
                {service && (
                  <span className="text-sm text-slate-600">
                    {service.name} - {price(service.priceCents)}
                  </span>
                )}
              </div>
              {actions.length > 0 && (
                <div className="mt-3 flex flex-wrap gap-2">
                  {actions.map((action) => (
                    <button
                      key={action.status}
                      type="button"
                      disabled={busyId === item.id}
                      onClick={() => changeStatus(item.id, action.status)}
                      className={`rounded-lg px-3 py-1.5 text-xs font-semibold disabled:opacity-40 ${action.style}`}
                    >
                      {action.label}
                    </button>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
