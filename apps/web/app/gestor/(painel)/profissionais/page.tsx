"use client";

import { useCallback, useEffect, useState } from "react";
import {
  createBarber,
  deleteBarber,
  getBarberHours,
  listBarbers,
  putBarberHours,
  updateBarber,
  ApiError,
  type BarberAdmin,
  type BusinessHour,
} from "@/lib/api";
import { getToken } from "@/lib/auth";

const WEEKDAYS = [
  { value: 0, label: "Domingo" },
  { value: 1, label: "Segunda" },
  { value: 2, label: "Terca" },
  { value: 3, label: "Quarta" },
  { value: 4, label: "Quinta" },
  { value: 5, label: "Sexta" },
  { value: 6, label: "Sabado" },
];

/** CRUD de profissionais + expediente (spec, secoes 6 e 7). */
export default function ProfissionaisPage() {
  const [items, setItems] = useState<BarberAdmin[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [hoursFor, setHoursFor] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);

  const token = getToken();

  const load = useCallback(async () => {
    if (!token) return;
    try {
      setItems(await listBarbers(token));
    } catch (e) {
      handleError(e);
      setItems([]);
    }
  }, [token]);

  function handleError(e: unknown) {
    if (e instanceof ApiError && e.status === 401) {
      window.location.href = "/gestor/login";
      return;
    }
    setError(e instanceof ApiError ? e.message : "Erro inesperado");
  }

  useEffect(() => {
    void load();
  }, [load]);

  async function onCreate(event: React.FormEvent) {
    event.preventDefault();
    if (!token || name.trim().length < 2) return;
    setBusy(true);
    setError(null);
    try {
      await createBarber(name.trim(), token);
      setName("");
      await load();
    } catch (e) {
      handleError(e);
    } finally {
      setBusy(false);
    }
  }

  async function onToggleActive(barber: BarberAdmin) {
    if (!token) return;
    setBusy(true);
    setError(null);
    try {
      await updateBarber(barber.id, { active: !barber.active }, token);
      await load();
    } catch (e) {
      handleError(e);
    } finally {
      setBusy(false);
    }
  }

  async function onRename(barber: BarberAdmin) {
    const displayName = window.prompt("Novo nome:", barber.displayName);
    if (!displayName || displayName.trim().length < 2 || !token) return;
    setBusy(true);
    setError(null);
    try {
      await updateBarber(barber.id, { displayName: displayName.trim() }, token);
      await load();
    } catch (e) {
      handleError(e);
    } finally {
      setBusy(false);
    }
  }

  async function onDelete(barber: BarberAdmin) {
    if (!window.confirm(`Desativar o profissional "${barber.displayName}"?`)) {
      return;
    }
    if (!token) return;
    setBusy(true);
    setError(null);
    try {
      await deleteBarber(barber.id, token);
      await load();
    } catch (e) {
      handleError(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <h1 className="text-xl font-bold">Profissionais</h1>

      {error && (
        <p className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700" role="alert">
          {error}
        </p>
      )}

      <form
        className="mt-4 flex gap-2 rounded-lg bg-white p-4 shadow"
        onSubmit={onCreate}
      >
        <input
          required
          maxLength={120}
          placeholder="Nome exibido (ex.: Joao - Barbeiro)"
          className="w-full rounded-lg border border-slate-300 p-2 text-sm"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <button
          type="submit"
          className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700 disabled:opacity-40"
          disabled={busy}
        >
          Criar
        </button>
      </form>

      {items === null ? (
        <p className="mt-6 text-sm text-slate-500">Carregando...</p>
      ) : (
        <ul className="mt-4 grid gap-2">
          {items.map((barber) => (
            <li key={barber.id} className="rounded-lg bg-white p-4 shadow">
              <div className="flex flex-wrap items-center gap-3">
                <div>
                  <p className="font-semibold">
                    {barber.displayName}
                    {!barber.active && (
                      <span className="ml-2 rounded bg-slate-200 px-2 py-0.5 text-xs text-slate-600">
                        inativo
                      </span>
                    )}
                  </p>
                </div>
                <span className="ml-auto flex flex-wrap gap-2">
                  <button
                    type="button"
                    className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50 disabled:opacity-40"
                    disabled={busy}
                    onClick={() => void onRename(barber)}
                  >
                    Renomear
                  </button>
                  <button
                    type="button"
                    className={`rounded-lg px-3 py-1.5 text-sm font-semibold disabled:opacity-40 ${
                      hoursFor === barber.id
                        ? "bg-slate-200 text-slate-800"
                        : "border border-slate-300 hover:bg-slate-50"
                    }`}
                    disabled={busy}
                    onClick={() =>
                      setHoursFor(hoursFor === barber.id ? null : barber.id)
                    }
                  >
                    Expediente
                  </button>
                  <button
                    type="button"
                    className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50 disabled:opacity-40"
                    disabled={busy}
                    onClick={() => void onToggleActive(barber)}
                  >
                    {barber.active ? "Desativar" : "Ativar"}
                  </button>
                  <button
                    type="button"
                    className="rounded-lg border border-red-300 px-3 py-1.5 text-sm text-red-700 hover:bg-red-50 disabled:opacity-40"
                    disabled={busy}
                    onClick={() => void onDelete(barber)}
                  >
                    Excluir
                  </button>
                </span>
              </div>
              {hoursFor === barber.id && (
                <HoursEditor
                  barberId={barber.id}
                  onDone={(message) => {
                    setHoursFor(null);
                    setError(message);
                  }}
                />
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function HoursEditor({
  barberId,
  onDone,
}: {
  barberId: string;
  onDone: (error: string | null) => void;
}) {
  const [rows, setRows] = useState<Record<number, { on: boolean; start: string; end: string }>>(
    Object.fromEntries(
      WEEKDAYS.map((d) => [d.value, { on: false, start: "09:00", end: "18:00" }]),
    ),
  );
  const [busy, setBusy] = useState(false);
  const token = getToken();

  useEffect(() => {
    (async () => {
      if (!token) return;
      try {
        const hours = await getBarberHours(barberId, token);
        setRows((current) => {
          const next = { ...current };
          for (const hour of hours) {
            next[hour.weekday] = {
              on: true,
              start: hour.startLocal.slice(0, 5),
              end: hour.endLocal.slice(0, 5),
            };
          }
          return next;
        });
      } catch {
        // Sem expediente definido: mantem o padrao em branco
      }
    })();
  }, [barberId, token]);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!token) return;
    setBusy(true);
    try {
      const hours: BusinessHour[] = WEEKDAYS.filter((d) => rows[d.value].on).map(
        (d) => ({
          weekday: d.value,
          startLocal: rows[d.value].start,
          endLocal: rows[d.value].end,
        }),
      );
      await putBarberHours(barberId, hours, token);
      onDone(null);
    } catch (e) {
      onDone(e instanceof ApiError ? e.message : "Erro ao salvar expediente");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="mt-4 border-t border-slate-200 pt-4" onSubmit={submit}>
      <h3 className="text-sm font-semibold">Expediente semanal</h3>
      <div className="mt-2 grid gap-1">
        {WEEKDAYS.map((day) => (
          <label
            key={day.value}
            className="flex items-center gap-2 text-sm"
          >
            <input
              type="checkbox"
              checked={rows[day.value].on}
              onChange={(e) =>
                setRows((current) => ({
                  ...current,
                  [day.value]: { ...current[day.value], on: e.target.checked },
                }))
              }
            />
            <span className="w-20">{day.label}</span>
            <input
              type="time"
              className="rounded-lg border border-slate-300 p-1.5 text-sm"
              value={rows[day.value].start}
              disabled={!rows[day.value].on}
              onChange={(e) =>
                setRows((current) => ({
                  ...current,
                  [day.value]: { ...current[day.value], start: e.target.value },
                }))
              }
            />
            <span className="text-slate-500">as</span>
            <input
              type="time"
              className="rounded-lg border border-slate-300 p-1.5 text-sm"
              value={rows[day.value].end}
              disabled={!rows[day.value].on}
              onChange={(e) =>
                setRows((current) => ({
                  ...current,
                  [day.value]: { ...current[day.value], end: e.target.value },
                }))
              }
            />
          </label>
        ))}
      </div>
      <button
        type="submit"
        className="mt-3 rounded-lg bg-green-700 px-4 py-2 text-sm font-semibold text-white hover:bg-green-600 disabled:opacity-40"
        disabled={busy}
      >
        Salvar expediente
      </button>
    </form>
  );
}
