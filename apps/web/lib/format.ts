import type { Slot } from "@/lib/api";

/** Formata "2026-11-14T12:00:00Z" como "12:00" no fuso informado. */
export function slotTime(iso: string, timeZone: string): string {
  return new Intl.DateTimeFormat("pt-BR", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone,
  }).format(new Date(iso));
}

/** Intervalo legivel do slot no fuso da barbearia. */
export function slotRange(slot: Slot, timeZone: string): string {
  return `${slotTime(slot.startAt, timeZone)} - ${slotTime(slot.endAt, timeZone)}`;
}

/** Formata instant ISO como data completa (ex.: "14/11/2026 (sabado)"). */
export function fullDate(iso: string, timeZone: string): string {
  const date = new Date(iso);
  const formatted = new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    timeZone,
  }).format(date);
  const weekday = new Intl.DateTimeFormat("pt-BR", {
    weekday: "long",
    timeZone,
  }).format(date);
  return `${formatted} (${weekday})`;
}

/** Instant ISO com hora (ex.: "14/11/2026 as 09:00") no fuso informado. */
export function dateTime(iso: string, timeZone: string): string {
  const formatted = new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone,
  }).format(new Date(iso));
  return formatted;
}

/** Preco em reais a partir de centavos. */
export function price(priceCents: number): string {
  return (priceCents / 100).toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL",
  });
}

/** Data local (fuso da barbearia) como YYYY-MM-DD para o input date. */
export function isoDateLocal(iso: string, timeZone: string): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    timeZone,
  }).format(new Date(iso));
  return parts; // en-CA => YYYY-MM-DD
}

/** Hoje (fuso da barbearia) como YYYY-MM-DD, limite minimo do input date. */
export function todayIn(timeZone: string): string {
  return isoDateLocal(new Date().toISOString(), timeZone);
}

/** Rotulo amigavel do status da reserva (maquina de estados da API). */
export function statusLabel(status: string): string {
  switch (status) {
    case "PENDING":
      return "Pendente";
    case "CONFIRMED":
      return "Confirmada";
    case "COMPLETED":
      return "Concluida";
    case "CANCELLED":
      return "Cancelada";
    case "NO_SHOW":
      return "Nao compareceu";
    default:
      return status;
  }
}
