"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  getAgenda,
  getOutbox,
  getSummary,
  listBarbers,
  listServices,
  updateAppointmentStatus,
  ApiError,
  DEFAULT_SLUG,
  type Appointment,
  type BarberAdmin,
  type DashboardSummary,
  type OutboxEvent,
  type ServiceAdmin,
} from "@/lib/api";
import { getToken } from "@/lib/auth";
import { price, statusLabel, todayIn } from "@/lib/format";

const TZ = "America/Sao_Paulo";
const MONTHS = [
  "jan", "fev", "mar", "abr", "mai", "jun",
  "jul", "ago", "set", "out", "nov", "dez",
];
const WEEKDAYS_SHORT = ["SEG", "TER", "QUA", "QUI", "SEX", "SAB", "DOM"];

/** Grade de 30 min: eixo das 08:00 as 20:00 (24 linhas de 48px). */
const DAY_START_MIN = 8 * 60;
const DAY_END_MIN = 20 * 60;
const SLOT = 30;
const ROW_H = 48;
const ROWS = (DAY_END_MIN - DAY_START_MIN) / SLOT;

/** Acoes por status (maquina de estados da API, secao 8) — tema escuro. */
const ACTIONS: Record<string, { status: string; label: string; style: string }[]> =
  {
    PENDING: [
      { status: "CONFIRMED", label: "Confirmar", style: "bg-emerald-600 hover:bg-emerald-500 text-white" },
      { status: "CANCELLED", label: "Cancelar", style: "border border-red-900 text-red-400 hover:bg-red-950/60" },
    ],
    CONFIRMED: [
      { status: "COMPLETED", label: "Concluir", style: "bg-emerald-600 hover:bg-emerald-500 text-white" },
      { status: "NO_SHOW", label: "Nao compareceu", style: "border border-zinc-700 text-zinc-300 hover:bg-zinc-800" },
      { status: "CANCELLED", label: "Cancelar", style: "border border-red-900 text-red-400 hover:bg-red-950/60" },
    ],
  };

/** Cor do cartao conforme o status (CONFIRMED em destaque dourado). */
function cardStyle(status: string): string {
  switch (status) {
    case "CONFIRMED":
      return "border border-amber-300 bg-amber-400 text-zinc-950 shadow-[0_0_20px_rgba(251,191,36,0.25)]";
    case "PENDING":
      return "border border-amber-500/40 bg-amber-400/10 text-amber-200";
    case "COMPLETED":
      return "border border-emerald-700 bg-emerald-500/10 text-emerald-300";
    case "NO_SHOW":
      return "border border-zinc-700 bg-zinc-800 text-zinc-400";
    default: // CANCELLED
      return "border border-zinc-800 bg-zinc-900 text-zinc-600 line-through";
  }
}

// ------------------------------------------------------------- utilidades

/** "2026-03-23" -> Date UTC (meio-dia evita deslocamento de fuso). */
function parseDate(dateStr: string): Date {
  return new Date(`${dateStr}T12:00:00Z`);
}

function toDateString(date: Date): string {
  return date.toISOString().slice(0, 10);
}

/** Segunda-feira da semana da data (semana comeca na segunda). */
function weekStartOf(dateStr: string): Date {
  const date = parseDate(dateStr);
  const offset = (date.getUTCDay() + 6) % 7;
  date.setUTCDate(date.getUTCDate() - offset);
  return date;
}

function addDays(date: Date, days: number): Date {
  const next = new Date(date);
  next.setUTCDate(next.getUTCDate() + days);
  return next;
}

/** "23 mar" do date "2026-03-23". */
function dayLabel(dateStr: string): string {
  const date = parseDate(dateStr);
  return `${date.getUTCDate()} ${MONTHS[date.getUTCMonth()]}`;
}

/** "23 mar - 29 mar 2026". */
function weekLabel(start: Date, end: Date): string {
  const a = dayLabel(toDateString(start));
  const b = dayLabel(toDateString(end));
  const year = end.getUTCFullYear();
  return `${a} - ${b} ${year}`;
}

/** Minutos desde a meia-noite do instant ISO, no fuso da barbearia. */
function minutesOfDay(iso: string): number {
  const parts = new Intl.DateTimeFormat("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: TZ,
  }).format(new Date(iso));
  const [h, m] = parts.split(":").map(Number);
  return h * 60 + m;
}

/** "11:45" a partir de minutos desde a meia-noite. */
function hourLabel(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

// ------------------------------------------------------------------ pagina

/**
 * Agenda SEMANAL do gestor (7 colunas x eixo de 30 min, cartoes das
 * reservas posicionados pela hora): filtro por profissional, navegacao
 * de semana e transicoes de status no cartao (spec, secao 6).
 */
export default function AgendaPage() {
  const [weekAnchor, setWeekAnchor] = useState(() =>
    todayIn(TZ),
  );
  const [barberId, setBarberId] = useState<string>("");
  const [days, setDays] = useState<Record<string, Appointment[]> | null>(null);
  const [services, setServices] = useState<ServiceAdmin[]>([]);
  const [barbers, setBarbers] = useState<BarberAdmin[]>([]);
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [outbox, setOutbox] = useState<OutboxEvent[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);

  const token = getToken();

  const weekStart = useMemo(() => weekStartOf(weekAnchor), [weekAnchor]);
  const weekDates = useMemo(
    () =>
      Array.from({ length: 7 }, (_, i) => toDateString(addDays(weekStart, i))),
    [weekStart],
  );
  const today = todayIn(TZ);

  const load = useCallback(async () => {
    if (!token) return;
    setError(null);
    try {
      const [serviceList, barberList, summaryToday, events, ...perDay] =
        await Promise.all([
          listServices(token),
          listBarbers(token),
          getSummary(today, token).catch(() => null),
          getOutbox(token).catch(() => null),
          ...weekDates.map((date) => getAgenda(date, token)),
        ]);
      const byDay: Record<string, Appointment[]> = {};
      weekDates.forEach((date, index) => {
        byDay[date] = perDay[index];
      });
      setServices(serviceList);
      setBarbers(barberList);
      setSummary(summaryToday);
      setOutbox(events);
      setDays(byDay);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        window.location.href = "/gestor/login";
        return;
      }
      setError(e instanceof ApiError ? e.message : "Erro ao carregar agenda");
      setDays({});
    }
  }, [token, weekDates, today]);

  useEffect(() => {
    void load();
  }, [load]);

  /** Reservas do filtro atual, indexadas por dia da semana. */
  const filteredDays = useMemo(() => {
    const byDay: Record<string, Appointment[]> = {};
    for (const date of weekDates) {
      byDay[date] = (days?.[date] ?? []).filter(
        (item) => !barberId || item.barberId === barberId,
      );
    }
    return byDay;
  }, [days, weekDates, barberId]);

  /** Contagem semanal por profissional (chips, sem canceladas). */
  const countByBarber = useMemo(() => {
    const counts = new Map<string, number>();
    for (const date of weekDates) {
      for (const item of days?.[date] ?? []) {
        if (item.status === "CANCELLED") continue;
        counts.set(item.barberId, (counts.get(item.barberId) ?? 0) + 1);
      }
    }
    return counts;
  }, [days, weekDates]);

  const serviceById = useMemo(
    () => new Map(services.map((s) => [s.id, s])),
    [services],
  );
  const barberById = useMemo(
    () => new Map(barbers.map((b) => [b.id, b])),
    [barbers],
  );

  async function changeStatus(appointment: Appointment, status: string) {
    if (!token) return;
    setBusyId(appointment.id);
    setError(null);
    try {
      const updated = await updateAppointmentStatus(
        appointment.id,
        status,
        token,
      );
      setDays((current) => {
        if (!current) return current;
        const dateKey = Object.keys(current).find((date) =>
          (current[date] ?? []).some((item) => item.id === updated.id),
        );
        if (!dateKey) return current;
        return {
          ...current,
          [dateKey]: current[dateKey].map((item) =>
            item.id === updated.id ? updated : item,
          ),
        };
      });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Erro ao atualizar status");
    } finally {
      setBusyId(null);
      setOpenMenuId(null);
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-bold tracking-tight">Agenda</h1>
      <p className="mt-1 text-sm text-zinc-500">Gerir reservas e horarios</p>

      {error && (
        <p
          className="mt-4 rounded-lg border border-red-900 bg-red-950/60 p-3 text-sm text-red-400"
          role="alert"
        >
          {error}
        </p>
      )}

      {/* Chips de filtro por profissional (Geral + cada um com contagem) */}
      <div className="mt-5 flex flex-wrap items-center gap-2">
        <FilterChip
          label="Geral"
          count={
            Array.from(countByBarber.values()).reduce((a, b) => a + b, 0)
          }
          active={barberId === ""}
          onClick={() => setBarberId("")}
        />
        {barbers
          .filter((barber) => barber.active)
          .map((barber) => (
            <FilterChip
              key={barber.id}
              label={barber.displayName}
              count={countByBarber.get(barber.id) ?? 0}
              active={barberId === barber.id}
              onClick={() => setBarberId(barber.id)}
            />
          ))}
      </div>

      {/* Barra de navegacao da semana */}
      <div className="mt-4 flex flex-wrap items-center gap-2 rounded-xl border border-zinc-800 bg-[#0e0e0e] p-2">
        <button
          type="button"
          aria-label="Semana anterior"
          className="rounded-lg border border-zinc-700 p-2 text-zinc-300 hover:bg-zinc-800"
          onClick={() =>
            setWeekAnchor(toDateString(addDays(parseDate(weekAnchor), -7)))
          }
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="m15 18-6-6 6-6" /></svg>
        </button>
        <button
          type="button"
          aria-label="Proxima semana"
          className="rounded-lg border border-zinc-700 p-2 text-zinc-300 hover:bg-zinc-800"
          onClick={() =>
            setWeekAnchor(toDateString(addDays(parseDate(weekAnchor), 7)))
          }
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="m9 18 6-6-6-6" /></svg>
        </button>
        <button
          type="button"
          className="rounded-lg border border-zinc-700 px-3 py-2 text-sm text-zinc-300 hover:bg-zinc-800"
          onClick={() => setWeekAnchor(today)}
        >
          Hoje
        </button>
        <span className="ml-2 text-sm font-semibold text-zinc-200">
          {weekLabel(weekStart, addDays(weekStart, 6))}
        </span>
      </div>

      {/* Grade semanal */}
      {days === null ? (
        <p className="mt-6 text-sm text-zinc-500">Carregando...</p>
      ) : (
        <div className="mt-4 overflow-hidden rounded-xl border border-zinc-800 bg-[#0e0e0e]">
          <div className="max-h-[70vh] overflow-auto">
            <div className="min-w-[880px]">
              {/* Cabecalho dos dias (fixo no scroll) */}
              <div className="sticky top-0 z-20 flex border-b border-zinc-800 bg-[#0e0e0e]">
                <div className="w-14 shrink-0" />
                {weekDates.map((date) => {
                  const count = filteredDays[date].filter(
                    (item) => item.status !== "CANCELLED",
                  ).length;
                  const isToday = date === today;
                  return (
                    <div
                      key={date}
                      className="flex-1 border-l border-zinc-800 py-3 text-center"
                    >
                      <p className="text-[11px] font-medium tracking-wider text-zinc-500">
                        {WEEKDAYS_SHORT[
                          (parseDate(date).getUTCDay() + 6) % 7
                        ]}
                      </p>
                      <div className="mt-1 flex items-center justify-center">
                        <span
                          className={`flex h-9 w-9 items-center justify-center rounded-full text-base font-bold ${
                            isToday
                              ? "bg-amber-400 text-zinc-950"
                              : "text-zinc-200"
                          }`}
                        >
                          {parseDate(date).getUTCDate()}
                        </span>
                      </div>
                      {count > 0 && (
                        <span className="mt-1.5 inline-block rounded-full bg-amber-400/15 px-2 text-xs font-semibold text-amber-300">
                          {count}
                        </span>
                      )}
                    </div>
                  );
                })}
              </div>

              {/* Corpo: eixo de horas + colunas */}
              <div className="flex">
                <div className="w-14 shrink-0">
                  {Array.from({ length: ROWS }, (_, slot) => (
                    <div
                      key={slot}
                      style={{ height: ROW_H }}
                      className="flex items-start justify-end pr-2 pt-1 text-[11px] text-zinc-500"
                    >
                      {slot % 2 === 0
                        ? hourLabel(DAY_START_MIN + slot * SLOT)
                        : ""}
                    </div>
                  ))}
                </div>
                <div className="flex flex-1">
                  {weekDates.map((date) => (
                    <DayColumn
                      key={date}
                      date={date}
                      items={filteredDays[date]}
                      barberName={(item) =>
                        barberById.get(item.barberId)?.displayName ?? ""
                      }
                      serviceName={(item) =>
                        serviceById.get(item.serviceId)?.name ?? "Servico"
                      }
                      openMenuId={openMenuId}
                      onToggleMenu={(id) =>
                        setOpenMenuId((current) => (current === id ? null : id))
                      }
                      busyId={busyId}
                      onAction={changeStatus}
                    />
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Resumo e outbox preservados (recursos do painel, agora abaixo da grade) */}
      {summary && (
        <details className="mt-6 rounded-xl border border-zinc-800 bg-[#0e0e0e] p-4">
          <summary className="cursor-pointer text-sm font-semibold text-zinc-200">
            Resumo de hoje
          </summary>
          <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-5">
            <SummaryCard
              label="Reservas"
              value={String(summary.appointments.total)}
              hint={`${summary.appointments.confirmed} confirmadas, ${summary.appointments.pending} pendentes`}
            />
            <SummaryCard
              label="Previsao"
              value={price(summary.appointments.expectedRevenueCents)}
              hint="pendentes + confirmadas + concluidas"
            />
            <SummaryCard
              label="Fila de espera"
              value={String(summary.waitlist.waiting + summary.waitlist.offered)}
              hint={`${summary.waitlist.waiting} aguardando`}
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
        </details>
      )}

      {outbox && outbox.length > 0 && (
        <details className="mt-3 rounded-xl border border-zinc-800 bg-[#0e0e0e] p-4">
          <summary className="cursor-pointer text-sm font-semibold text-zinc-200">
            Notificacoes (outbox)
          </summary>
          <p className="mt-2 text-xs text-zinc-500">
            {outbox.filter((e) => e.status === "SENT").length} enviadas, {" "}
            {outbox.filter((e) => e.status === "PENDING").length} pendentes, {" "}
            {outbox.filter((e) => e.status === "FAILED").length} com falha.
          </p>
          <ul className="mt-2 grid gap-1">
            {outbox.slice(0, 15).map((event) => (
              <OutboxRow key={event.id} event={event} />
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}

/** Chip de filtro com contagem (estilo da referencia: selecionado dourado). */
function FilterChip({
  label,
  count,
  active,
  onClick,
}: {
  label: string;
  count: number;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex items-center gap-2 rounded-lg border px-3.5 py-2 text-sm font-medium transition-colors ${
        active
          ? "border-amber-400 bg-amber-400 text-zinc-950"
          : "border-zinc-700 bg-[#0e0e0e] text-zinc-300 hover:border-zinc-500"
      }`}
    >
      {label}
      <span
        className={`rounded-full px-1.5 py-0.5 text-[11px] font-semibold ${
          active ? "bg-zinc-950/15 text-zinc-950" : "bg-amber-400/15 text-amber-300"
        }`}
      >
        {count}
      </span>
    </button>
  );
}

/** Coluna do dia: slots de 30 min clicaveis + cartoes por hora real. */
function DayColumn({
  date,
  items,
  barberName,
  serviceName,
  openMenuId,
  onToggleMenu,
  busyId,
  onAction,
}: {
  date: string;
  items: Appointment[];
  barberName: (item: Appointment) => string;
  serviceName: (item: Appointment) => string;
  openMenuId: string | null;
  onToggleMenu: (id: string) => void;
  busyId: string | null;
  onAction: (item: Appointment, status: string) => void;
}) {
  /** Cartao sobrepoe o slot? (esconde o "+ Nova Marcacao") */
  function overlaps(startMin: number, endMin: number): boolean {
    return items.some((item) => {
      const start = Math.max(minutesOfDay(item.startsAt), DAY_START_MIN);
      const end = Math.min(minutesOfDay(item.endsAt), DAY_END_MIN);
      return start < endMin && end > startMin;
    });
  }

  return (
    <div
      className="relative flex-1 border-l border-zinc-800"
      style={{ height: ROWS * ROW_H }}
    >
      {/* Linhas de slot (fundo + "+ Nova Marcacao" nos livres) */}
      {Array.from({ length: ROWS }, (_, slot) => {
        const startMin = DAY_START_MIN + slot * SLOT;
        return (
          <div
            key={slot}
            style={{ height: ROW_H }}
            className="absolute inset-x-0 border-b border-zinc-800/60"
          >
            {!overlaps(startMin, startMin + SLOT) && (
              <Link
                href={`/b/${DEFAULT_SLUG}`}
                className="absolute inset-x-1 top-1 text-center text-[11px] text-amber-500/60 hover:text-amber-300"
                title="Abrir pagina publica de reserva"
              >
                + Nova Marcacao
              </Link>
            )}
          </div>
        );
      })}

      {/* Cartoes das reservas posicionados pela hora */}
      {items.map((item) => {
        const start = Math.max(minutesOfDay(item.startsAt), DAY_START_MIN);
        const end = Math.min(minutesOfDay(item.endsAt), DAY_END_MIN);
        const top = ((start - DAY_START_MIN) / SLOT) * ROW_H + 2;
        const height = Math.max(((end - start) / SLOT) * ROW_H - 4, 24);
        const menuOpen = openMenuId === item.id;
        const showBarber = barberName(item) !== "";
        return (
          <div key={item.id}>
            <button
              type="button"
              onClick={() => onToggleMenu(item.id)}
              style={{ top, height }}
              className={`absolute inset-x-1 z-10 overflow-hidden rounded-lg px-2 py-1 text-left transition-shadow ${cardStyle(
                item.status,
              )}`}
              title={`${statusLabel(item.status)} — clique para gerir`}
            >
              <p className="truncate text-xs font-bold leading-tight">
                {item.customerName ?? "Cliente"}
              </p>
              <p className="truncate text-[10px] opacity-80">
                {hourLabel(start)} - {hourLabel(end)}
                {showBarber && ` · ${barberName(item)}`}
              </p>
              {height >= 68 && (
                <p className="truncate text-[10px] opacity-70">
                  {serviceName(item)} · {price(item.priceCents)}
                </p>
              )}
            </button>

            {/* Menu de transicoes de status (ancorado no cartao) */}
            {menuOpen && (
              <div
                style={{ top: top + height + 4 }}
                className="absolute inset-x-1 z-30 grid gap-1 rounded-lg border border-zinc-700 bg-[#181818] p-2 shadow-xl"
              >
                <p className="px-1 text-[10px] font-semibold uppercase tracking-wider text-zinc-500">
                  {statusLabel(item.status)}
                </p>
                {(ACTIONS[item.status] ?? []).map((action) => (
                  <button
                    key={action.status}
                    type="button"
                    className={`rounded-md px-2 py-1.5 text-xs font-semibold disabled:opacity-40 ${action.style}`}
                    disabled={busyId === item.id}
                    onClick={() => onAction(item, action.status)}
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
      className={`rounded-lg p-3 ${
        alert
          ? "border border-red-900 bg-red-950/40"
          : "border border-zinc-800 bg-[#141414]"
      }`}
    >
      <p className="text-xs font-medium text-zinc-500">{label}</p>
      <p className="mt-1 text-lg font-bold text-zinc-100">{value}</p>
      <p className={`text-xs ${alert ? "text-red-400" : "text-zinc-500"}`}>
        {hint}
      </p>
    </div>
  );
}

/** Linha da outbox no painel (falha destacada com o erro da ultima tentativa). */
function OutboxRow({ event }: { event: OutboxEvent }) {
  const badge =
    event.status === "SENT"
      ? "bg-emerald-400/15 text-emerald-300"
      : event.status === "FAILED"
        ? "bg-red-500/15 text-red-400"
        : event.status === "SKIPPED"
          ? "bg-zinc-700 text-zinc-300"
          : "bg-amber-400/15 text-amber-300";
  return (
    <li
      className={`flex flex-wrap items-center gap-2 rounded-lg border p-2 text-xs ${
        event.status === "FAILED"
          ? "border-red-900 bg-red-950/40"
          : "border-zinc-800 bg-[#141414]"
      }`}
    >
      <span className={`rounded-full px-2 py-0.5 font-semibold ${badge}`}>
        {event.status}
      </span>
      <span className="font-medium text-zinc-200">{event.eventType}</span>
      <span className="text-zinc-500">
        {new Intl.DateTimeFormat("pt-BR", {
          day: "2-digit",
          month: "2-digit",
          hour: "2-digit",
          minute: "2-digit",
          timeZone: "America/Sao_Paulo",
        }).format(new Date(event.createdAt))}
      </span>
      {event.attemptCount > 1 && (
        <span className="text-zinc-600">({event.attemptCount} tentativas)</span>
      )}
      {event.lastError && (
        <span className="ml-auto max-w-full truncate text-red-400" title={event.lastError}>
          {event.lastError}
        </span>
      )}
    </li>
  );
}
