"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import {
  createAppointment,
  getAvailability,
  getBarbers,
  getBarbershop,
  getServices,
  joinWaitlist,
  ApiError,
  type PublicBarber,
  type PublicBarbershop,
  type PublicService,
  type Slot,
} from "@/lib/api";
import { fullDate, maskPhone, price, slotRange, toE164, todayIn } from "@/lib/format";

/**
 * Fluxo de reserva normal (spec, secao 9.1): servico -> profissional ->
 * data/horario (availability; a confirmacao revalida no servidor) ->
 * dados do cliente -> POST com Idempotency-Key obrigatoria (linha 149).
 *
 * <p>Telefone: mascara enquanto digita e normalizacao E.164 (+55) no
 * envio — a API exige o formato internacional (spec, secao 9.1) e o
 * WhatsApp usa o mesmo numero na confirmacao/lembrete (Fase 4).</p>
 */
export default function BookingPage() {
  const params = useParams<{ slug: string }>();
  const slug = params.slug;

  const [barbershop, setBarbershop] = useState<PublicBarbershop | null>(null);
  const [services, setServices] = useState<PublicService[]>([]);
  const [barbers, setBarbers] = useState<PublicBarber[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [serviceId, setServiceId] = useState<string | null>(null);
  const [barberId, setBarberId] = useState<string | null>(null);
  const [date, setDate] = useState<string>("");
  const [slots, setSlots] = useState<Slot[] | null>(null);
  const [slotStart, setSlotStart] = useState<string | null>(null);
  const [availabilityLoading, setAvailabilityLoading] = useState(false);

  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [consent, setConsent] = useState(false);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // "Avise-me" (lista de espera publica, spec secao 10): aparece quando a
  // data escolhida nao tem horarios livres.
  const [waitlistTime, setWaitlistTime] = useState("");
  const [waitlistBusy, setWaitlistBusy] = useState(false);
  const [waitlistDone, setWaitlistDone] = useState(false);
  const [waitlistError, setWaitlistError] = useState<string | null>(null);
  const [booked, setBooked] = useState<{
    id: string;
    manageToken: string;
    startsAt: string;
    status: string;
    serviceName: string;
    servicePriceCents: number;
    barberName: string;
  } | null>(null);

  // Chave de idempotencia gerada uma vez por sessao de agendamento: retry
  // apos falha de rede NAO cria reserva duplicada (spec, linha 149).
  const idempotencyKeyRef = useRef<string>(
    globalThis.crypto?.randomUUID() ?? cryptoFallback(),
  );

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [shop, serviceList, barberList] = await Promise.all([
          getBarbershop(slug),
          getServices(slug),
          getBarbers(slug),
        ]);
        if (!cancelled) {
          setBarbershop(shop);
          setServices(serviceList);
          setBarbers(barberList);
        }
      } catch (e) {
        if (!cancelled) {
          setLoadError(e instanceof Error ? e.message : "Erro ao carregar");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [slug]);

  // Muda servico/profissional: slot escolhido deixa de ser valido e o
  // avise-me comeca de novo (a inscricao e por data/horario especifico).
  useEffect(() => {
    setSlotStart(null);
    setWaitlistTime("");
    setWaitlistDone(false);
    setWaitlistError(null);
  }, [serviceId, barberId, date]);

  const loadAvailability = useCallback(async () => {
    if (!serviceId || !barberId || !date) return;
    setAvailabilityLoading(true);
    setError(null);
    try {
      const availability = await getAvailability(slug, barberId, serviceId, date);
      setSlots(availability.slots);
    } catch (e) {
      setSlots([]);
      setError(e instanceof ApiError ? e.message : "Erro ao buscar horarios");
    } finally {
      setAvailabilityLoading(false);
    }
  }, [slug, serviceId, barberId, date]);

  // Horarios carregam sozinhos quando servico, profissional e data estao
  // escolhidos — sem botao extra (a escolha revalida no servidor).
  useEffect(() => {
    if (serviceId && barberId && date) {
      void loadAvailability();
    }
  }, [serviceId, barberId, date, loadAvailability]);

  /** Inscricao na lista de espera para o horario desejado (spec, secao 10). */
  async function submitWaitlist(event: React.FormEvent) {
    event.preventDefault();
    if (!serviceId || !barberId || !date) return;

    const trimmedName = name.trim();
    if (trimmedName.length < 3) {
      setWaitlistError("Informe seu nome completo");
      return;
    }
    const e164 = toE164(phone);
    if (!e164) {
      setWaitlistError("Telefone invalido (use DDD, ex.: 11 97000-0000)");
      return;
    }
    if (!consent) {
      setWaitlistError("E necessario aceitar a politica de privacidade");
      return;
    }
    const zone = barbershop?.timezone ?? "America/Sao_Paulo";
    const startsAt = zoneIso(date, waitlistTime, zone);
    if (!startsAt) {
      setWaitlistError("Escolha o horario desejado");
      return;
    }

    setWaitlistBusy(true);
    setWaitlistError(null);
    try {
      await joinWaitlist(slug, barberId, serviceId, startsAt, {
        name: trimmedName,
        phone: e164,
        consented: true,
      });
      setWaitlistDone(true);
    } catch (e) {
      // 409 = ja inscrito para este horario (cliente re-tentando)
      setWaitlistError(
        e instanceof ApiError ? e.message : "Erro ao entrar na lista de espera",
      );
    } finally {
      setWaitlistBusy(false);
    }
  }

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!serviceId || !barberId || !slotStart) return;

    const trimmedName = name.trim();
    if (trimmedName.length < 3) {
      setError("Informe seu nome completo");
      return;
    }
    const e164 = toE164(phone);
    if (!e164) {
      setError("Telefone invalido (use DDD, ex.: 11 97000-0000)");
      return;
    }
    if (!consent) {
      setError("E necessario aceitar a politica de privacidade");
      return;
    }

    const selectedService = services.find((s) => s.id === serviceId);
    const selectedBarber = barbers.find((b) => b.id === barberId);

    setSubmitting(true);
    setError(null);
    try {
      const appointment = await createAppointment(
        slug,
        {
          slug,
          barberId,
          serviceId,
          startsAt: slotStart,
          customer: { name: trimmedName, phone: e164, consented: true },
        },
        idempotencyKeyRef.current,
      );
      setBooked({
        id: appointment.id,
        manageToken: appointment.manageToken ?? "",
        startsAt: appointment.startsAt,
        status: appointment.status,
        serviceName: selectedService?.name ?? "Servico",
        servicePriceCents: selectedService?.priceCents ?? 0,
        barberName: selectedBarber?.displayName ?? "Profissional",
      });
    } catch (e) {
      // 409 (conflito) e erros de validacao permitem escolher outro slot;
      // a chave de idempotencia e preservada para retries idempotentes.
      setError(e instanceof ApiError ? e.message : "Erro ao agendar");
      if (e instanceof ApiError && e.status === 409) {
        setSlotStart(null);
        void loadAvailability();
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (loadError) {
    return (
      <main className="mx-auto max-w-lg p-6">
        <div className="rounded-lg bg-red-50 p-6 text-center text-red-700">
          <p className="font-semibold">Nao foi possivel carregar a barbearia</p>
          <p className="mt-1 text-sm">{loadError}</p>
        </div>
      </main>
    );
  }

  if (booked) {
    const manageUrl = `/reservas/${booked.id}?t=${booked.manageToken}`;
    const zone = barbershop?.timezone ?? "America/Sao_Paulo";
    return (
      <main className="mx-auto max-w-lg p-6">
        <div className="rounded-lg bg-white p-6 shadow">
          <h1 className="text-xl font-bold text-green-700">Reserva criada!</h1>
          <dl className="mt-4 space-y-1 text-sm">
            <div className="flex gap-2">
              <dt className="font-semibold">Quando:</dt>
              <dd>{fullDate(booked.startsAt, zone)}</dd>
            </div>
            <div className="flex gap-2">
              <dt className="font-semibold">Servico:</dt>
              <dd>
                {booked.serviceName} - {price(booked.servicePriceCents)}
              </dd>
            </div>
            <div className="flex gap-2">
              <dt className="font-semibold">Profissional:</dt>
              <dd>{booked.barberName}</dd>
            </div>
            <div className="flex gap-2">
              <dt className="font-semibold">Status:</dt>
              <dd>{booked.status === "PENDING" ? "Pendente" : booked.status}</dd>
            </div>
          </dl>
          <p className="mt-3 rounded-lg bg-green-50 p-3 text-xs text-green-800">
            Voce recebe a confirmacao pelo WhatsApp e um lembrete automatico
            24h antes do horario. Nao precisa fazer nada.
          </p>
          <Link
            href={manageUrl}
            className="mt-6 block rounded-lg bg-slate-900 px-4 py-3 text-center font-semibold text-white hover:bg-slate-700"
          >
            Gerenciar minha reserva
          </Link>
          <p className="mt-3 text-xs text-slate-500">
            Guarde este link: e por ele que voce cancela ou remarca sem conta.
          </p>
        </div>
      </main>
    );
  }

  const zone = barbershop?.timezone ?? "America/Sao_Paulo";
  const selectedService = services.find((s) => s.id === serviceId);
  const selectedBarber = barbers.find((b) => b.id === barberId);
  const canConfirm = Boolean(serviceId && barberId && slotStart && date);

  return (
    <main className="mx-auto max-w-lg p-6">
      <header className="mb-6">
        <h1 className="text-2xl font-bold">
          {barbershop ? barbershop.name : "Carregando..."}
        </h1>
        <p className="text-sm text-slate-600">Agende seu horario online</p>
      </header>

      {error && (
        <div className="mb-4 rounded-lg bg-red-50 p-3 text-sm text-red-700" role="alert">
          {error}
        </div>
      )}

      <section className="rounded-lg bg-white p-4 shadow">
        <h2 className="font-semibold">1. Servico</h2>
        <div className="mt-2 grid gap-2">
          {services.map((service) => (
            <label
              key={service.id}
              className={`flex cursor-pointer items-center justify-between rounded-lg border p-3 text-sm ${
                serviceId === service.id
                  ? "border-slate-900 bg-slate-50"
                  : "border-slate-200"
              }`}
            >
              <span>
                <input
                  type="radio"
                  name="service"
                  className="hidden"
                  value={service.id}
                  checked={serviceId === service.id}
                  onChange={() => setServiceId(service.id)}
                />
                <span className="font-medium">{service.name}</span>
                <span className="block text-xs text-slate-500">
                  {service.durationMinutes} min
                  {service.description ? ` - ${service.description}` : ""}
                </span>
              </span>
              <span className="font-semibold">{price(service.priceCents)}</span>
            </label>
          ))}
        </div>
      </section>

      <section className="mt-4 rounded-lg bg-white p-4 shadow">
        <h2 className="font-semibold">2. Profissional</h2>
        <div className="mt-2 grid gap-2 sm:grid-cols-2">
          {barbers.map((barber) => (
            <label
              key={barber.id}
              className={`cursor-pointer rounded-lg border p-3 text-sm ${
                barberId === barber.id
                  ? "border-slate-900 bg-slate-50"
                  : "border-slate-200"
              }`}
            >
              <input
                type="radio"
                name="barber"
                className="hidden"
                value={barber.id}
                checked={barberId === barber.id}
                onChange={() => setBarberId(barber.id)}
              />
              <span className="font-medium">{barber.displayName}</span>
            </label>
          ))}
        </div>
      </section>

      <section className="mt-4 rounded-lg bg-white p-4 shadow">
        <h2 className="font-semibold">3. Data e horario</h2>
        <div className="mt-2">
          <input
            type="date"
            className="rounded-lg border border-slate-300 p-2 text-sm"
            min={todayIn(zone)}
            value={date}
            onChange={(e) => {
              setDate(e.target.value);
              setSlots(null);
            }}
          />
        </div>
        {availabilityLoading && (
          <p className="mt-3 text-sm text-slate-500">Buscando horarios...</p>
        )}
        {slots && slots.length === 0 && !availabilityLoading && (
          <div className="mt-3 rounded-lg bg-amber-50 p-3">
            <p className="text-sm font-medium text-amber-800">
              Sem horarios livres nesta data.
            </p>
            {waitlistDone ? (
              <p className="mt-2 text-sm text-green-800">
                Pronto! Vamos te avisar pelo WhatsApp se uma vaga abrir
                {waitlistTime ? ` as ${waitlistTime}` : ""}.
              </p>
            ) : (
              <form className="mt-2 grid gap-2" onSubmit={submitWaitlist}>
                <p className="text-xs text-amber-800">
                  Quer que a gente avise se abrir uma vaga? Escolha o horario
                  desejado — se ele ficar livre, voce recebe o convite em
                  primeiro.
                </p>
                <input
                  type="time"
                  className="rounded-lg border border-amber-300 p-2 text-sm"
                  value={waitlistTime}
                  onChange={(e) => setWaitlistTime(e.target.value)}
                  required
                />
                <label className="text-xs">
                  <span className="font-medium">Nome</span>
                  <input
                    className="mt-1 w-full rounded-lg border border-amber-300 p-2 text-sm"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="Nome e sobrenome"
                  />
                </label>
                <label className="text-xs">
                  <span className="font-medium">Telefone (WhatsApp)</span>
                  <input
                    className="mt-1 w-full rounded-lg border border-amber-300 p-2 text-sm"
                    value={phone}
                    onChange={(e) => setPhone(maskPhone(e.target.value))}
                    placeholder="(11) 97000-0000"
                    inputMode="tel"
                  />
                </label>
                <label className="flex items-start gap-2 text-xs text-amber-900">
                  <input
                    type="checkbox"
                    className="mt-0.5"
                    checked={consent}
                    onChange={(e) => setConsent(e.target.checked)}
                  />
                  <span>
                    Aceito a{" "}
                    <a href="/privacidade" className="underline hover:text-amber-800">
                      politica de privacidade
                    </a>{" "}
                    e o contato por WhatsApp caso abra uma vaga.
                  </span>
                </label>
                {waitlistError && (
                  <p className="text-xs text-red-700" role="alert">
                    {waitlistError}
                  </p>
                )}
                <button
                  type="submit"
                  className="rounded-lg bg-amber-700 px-4 py-2 text-sm font-semibold text-white hover:bg-amber-600 disabled:opacity-40"
                  disabled={waitlistBusy}
                >
                  {waitlistBusy ? "Registrando..." : "Avise-me se abrir vaga"}
                </button>
              </form>
            )}
          </div>
        )}
        <div className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-4">
          {(slots ?? []).map((slot) => (
            <button
              key={slot.startAt}
              type="button"
              className={`rounded-lg border p-2 text-sm ${
                slotStart === slot.startAt
                  ? "border-slate-900 bg-slate-900 text-white"
                  : "border-slate-200 hover:border-slate-400"
              }`}
              onClick={() => setSlotStart(slot.startAt)}
            >
              {slotRange(slot, zone)}
            </button>
          ))}
        </div>
      </section>

      <section className="mt-4 rounded-lg bg-white p-4 shadow">
        <h2 className="font-semibold">4. Seus dados</h2>
        <form className="mt-2 grid gap-3" onSubmit={submit}>
          <label className="text-sm">
            <span className="font-medium">Nome</span>
            <input
              className="mt-1 w-full rounded-lg border border-slate-300 p-2 text-sm"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Nome e sobrenome"
            />
          </label>
          <label className="text-sm">
            <span className="font-medium">Telefone (WhatsApp)</span>
            <input
              className="mt-1 w-full rounded-lg border border-slate-300 p-2 text-sm"
              value={phone}
              onChange={(e) => setPhone(maskPhone(e.target.value))}
              placeholder="(11) 97000-0000"
              inputMode="tel"
            />
          </label>
          <label className="flex items-start gap-2 text-xs text-slate-600">
            <input
              type="checkbox"
              className="mt-0.5"
              checked={consent}
              onChange={(e) => setConsent(e.target.checked)}
            />
            <span>
              Aceito a{" "}
              <a href="/privacidade" className="underline hover:text-green-800">
                politica de privacidade
              </a>{" "}
              e o contato por WhatsApp sobre esta reserva (confirmacao e
              lembrete 24h antes).
            </span>
          </label>

          {/* Resumo da reserva: o cliente confirma vendo exatamente o que vai
              ser gravado (servico, profissional, horario e preco). */}
          {canConfirm && selectedService && selectedBarber && (
            <dl className="rounded-lg bg-slate-50 p-3 text-sm">
              <div className="flex gap-2">
                <dt className="font-semibold">Servico:</dt>
                <dd>
                  {selectedService.name} ({selectedService.durationMinutes} min)
                </dd>
              </div>
              <div className="flex gap-2">
                <dt className="font-semibold">Profissional:</dt>
                <dd>{selectedBarber.displayName}</dd>
              </div>
              <div className="flex gap-2">
                <dt className="font-semibold">Quando:</dt>
                <dd>{slotStart ? fullDate(slotStart, zone) : "-"}</dd>
              </div>
              <div className="flex gap-2">
                <dt className="font-semibold">Total:</dt>
                <dd className="font-semibold">{price(selectedService.priceCents)}</dd>
              </div>
            </dl>
          )}

          <button
            type="submit"
            className="rounded-lg bg-green-700 px-4 py-3 font-semibold text-white hover:bg-green-600 disabled:opacity-40"
            disabled={!canConfirm || submitting}
          >
            {submitting ? "Agendando..." : "Confirmar agendamento"}
          </button>
        </form>
      </section>
    </main>
  );
}

/**
 * ISO com o offset do timezone da barbearia na data informada
 * (ex.: "2026-11-15T10:00:00-03:00"), necessario porque a API espera
 * Instant (a API publica de disponibilidade tambem recebe esse formato).
 */
function zoneIso(date: string, time: string, timeZone: string): string | null {
  if (!time || !date) return null;
  // Interpreta a parelha como UTC e descobre como o timezone a renderiza:
  // a diferenca e o offset local (funciona com DST, quando houver).
  const naive = new Date(`${date}T${time}:00Z`);
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone,
    hour12: false,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).formatToParts(naive);
  const map: Record<string, string> = {};
  for (const part of parts) {
    map[part.type] = part.value;
  }
  const asUtc = Date.UTC(
    Number(map.year),
    Number(map.month) - 1,
    Number(map.day),
    Number(map.hour) % 24,
    Number(map.minute),
    Number(map.second),
  );
  const offsetMinutes = Math.round((asUtc - naive.getTime()) / 60000);
  const sign = offsetMinutes < 0 ? "-" : "+";
  const abs = Math.abs(offsetMinutes);
  const hh = String(Math.floor(abs / 60)).padStart(2, "0");
  const mm = String(abs % 60).padStart(2, "0");
  return `${date}T${time}:00${sign}${hh}:${mm}`;
}

function cryptoFallback(): string {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
