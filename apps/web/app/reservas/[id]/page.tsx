"use client";

import { Suspense, useCallback, useEffect, useState } from "react";
import { useParams, useSearchParams } from "next/navigation";
import {
  cancelAppointment,
  getAppointment,
  getAvailability,
  rescheduleAppointment,
  ApiError,
  type Appointment,
  type Slot,
} from "@/lib/api";
import { DEFAULT_SLUG } from "@/lib/api";
import {
  dateTime,
  price,
  slotRange,
  statusLabel,
  todayIn,
} from "@/lib/format";

/**
 * Link seguro de gerenciamento (spec, secao 9.1): o token de gerenciamento
 * na query (`?t=...`) e a credencial — sem conta. Permite cancelar e
 * remarcar (spec, linhas 182-184).
 */
export default function ManagePage() {
  return (
    <Suspense fallback={<Loading />}>
      <ManageContent />
    </Suspense>
  );
}

function Loading() {
  return (
    <main className="mx-auto max-w-lg p-6">
      <p className="text-sm text-slate-500">Carregando reserva...</p>
    </main>
  );
}

function ManageContent() {
  const params = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const appointmentId = params.id;
  // Token de gerenciamento: credencial do link seguro.
  const token = searchParams.get("t") ?? "";

  const [appointment, setAppointment] = useState<Appointment | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [rescheduling, setRescheduling] = useState(false);
  const [date, setDate] = useState("");
  const [slots, setSlots] = useState<Slot[] | null>(null);
  const [newSlot, setNewSlot] = useState<string | null>(null);
  const [timezone, setTimezone] = useState("America/Sao_Paulo");

  const reload = useCallback(async () => {
    if (!token) {
      setError("Link invalido: token de gerenciamento ausente");
      return;
    }
    try {
      setAppointment(await getAppointment(appointmentId, token));
    } catch (e) {
      setError(
        e instanceof ApiError
          ? e.message
          : "Nao foi possivel carregar a reserva",
      );
    }
  }, [appointmentId, token]);

  useEffect(() => {
    void reload();
  }, [reload]);

  if (error) {
    return (
      <main className="mx-auto max-w-lg p-6">
        <div className="rounded-lg bg-red-50 p-6 text-center text-red-700">
          <p className="font-semibold">Erro</p>
          <p className="mt-1 text-sm">{error}</p>
        </div>
      </main>
    );
  }

  if (!appointment) {
    return <Loading />;
  }

  const canChange = appointment.status === "PENDING" || appointment.status === "CONFIRMED";

  async function onCancel() {
    if (!window.confirm("Cancelar esta reserva? Esta acao nao pode ser desfeita.")) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const updated = await cancelAppointment(appointmentId, token);
      setAppointment(updated);
      setMessage("Reserva cancelada.");
      setRescheduling(false);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro ao cancelar");
    } finally {
      setBusy(false);
    }
  }

  async function loadRescheduleSlots() {
    if (!date || !appointment) return;
    setBusy(true);
    setError(null);
    try {
      const availability = await getAvailability(
        DEFAULT_SLUG,
        appointment.barberId,
        appointment.serviceId,
        date,
      );
      setSlots(availability.slots);
      if (availability.timezone) setTimezone(availability.timezone);
    } catch (e) {
      setSlots([]);
      setError(e instanceof ApiError ? e.message : "Erro ao buscar horarios");
    } finally {
      setBusy(false);
    }
  }

  async function onReschedule() {
    if (!newSlot) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await rescheduleAppointment(appointmentId, token, newSlot);
      setAppointment(updated);
      setMessage("Reserva remarcada.");
      setRescheduling(false);
      setSlots(null);
      setNewSlot(null);
      setDate("");
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro ao remarcar");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="mx-auto max-w-lg p-6">
      <div className="rounded-lg bg-white p-6 shadow">
        <h1 className="text-xl font-bold">Minha reserva</h1>
        <p className="mt-1 text-xs text-slate-500">
          Protocolo <span className="font-mono">{appointment.id}</span>
        </p>

        <dl className="mt-4 grid gap-1 text-sm">
          <div className="flex gap-2">
            <dt className="font-semibold">Data e hora:</dt>
            <dd>{dateTime(appointment.startsAt, timezone)}</dd>
          </div>
          <div className="flex gap-2">
            <dt className="font-semibold">Duracao:</dt>
            <dd>{appointment.durationMinutes} min</dd>
          </div>
          <div className="flex gap-2">
            <dt className="font-semibold">Valor:</dt>
            <dd>{price(appointment.priceCents)}</dd>
          </div>
          <div className="flex gap-2">
            <dt className="font-semibold">Status:</dt>
            <dd>{statusLabel(appointment.status)}</dd>
          </div>
        </dl>

        {message && (
          <p className="mt-4 rounded-lg bg-green-50 p-3 text-sm text-green-700">
            {message}
          </p>
        )}

        {canChange ? (
          <div className="mt-6 grid gap-2">
            <button
              type="button"
              className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold hover:bg-slate-50 disabled:opacity-40"
              disabled={busy}
              onClick={() => setRescheduling((v) => !v)}
            >
              {rescheduling ? "Fechar remarcação" : "Remarcar"}
            </button>
            <button
              type="button"
              className="rounded-lg bg-red-700 px-4 py-2 text-sm font-semibold text-white hover:bg-red-600 disabled:opacity-40"
              disabled={busy}
              onClick={() => void onCancel()}
            >
              {busy ? "Aguarde..." : "Cancelar reserva"}
            </button>
          </div>
        ) : (
          <p className="mt-6 rounded-lg bg-slate-50 p-3 text-sm text-slate-600">
            Esta reserva nao pode mais ser alterada pelo link
            (status: {statusLabel(appointment.status)}).
          </p>
        )}

        {rescheduling && canChange && (
          <div className="mt-4 rounded-lg border border-slate-200 p-4">
            <h2 className="text-sm font-semibold">Novo horario</h2>
            <div className="mt-2 flex gap-2">
              <input
                type="date"
                className="rounded-lg border border-slate-300 p-2 text-sm"
                min={todayIn(timezone)}
                value={date}
                onChange={(e) => {
                  setDate(e.target.value);
                  setSlots(null);
                  setNewSlot(null);
                }}
              />
              <button
                type="button"
                className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700 disabled:opacity-40"
                disabled={!date || busy}
                onClick={() => void loadRescheduleSlots()}
              >
                Buscar
              </button>
            </div>
            {slots && slots.length === 0 && (
              <p className="mt-3 text-sm text-slate-500">
                Sem horarios livres nesta data.
              </p>
            )}
            <div className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-4">
              {(slots ?? []).map((slot) => (
                <button
                  key={slot.startAt}
                  type="button"
                  className={`rounded-lg border p-2 text-sm ${
                    newSlot === slot.startAt
                      ? "border-slate-900 bg-slate-900 text-white"
                      : "border-slate-200 hover:border-slate-400"
                  }`}
                  onClick={() => setNewSlot(slot.startAt)}
                >
                  {slotRange(slot, timezone)}
                </button>
              ))}
            </div>
            <button
              type="button"
              className="mt-3 w-full rounded-lg bg-green-700 px-4 py-2 text-sm font-semibold text-white hover:bg-green-600 disabled:opacity-40"
              disabled={!newSlot || busy}
              onClick={() => void onReschedule()}
            >
              Confirmar novo horario
            </button>
          </div>
        )}
      </div>
    </main>
  );
}
