"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import {
  createAppointment,
  getAvailability,
  getBarbers,
  getBarbershop,
  getServices,
  ApiError,
  type PublicBarber,
  type PublicBarbershop,
  type PublicService,
  type Slot,
} from "@/lib/api";
import { price, slotRange, todayIn } from "@/lib/format";

/**
 * Fluxo de reserva normal (spec, secao 9.1): servico -> profissional ->
 * data/horario (availability; a confirmacao revalida no servidor) ->
 * dados do cliente -> POST com Idempotency-Key obrigatoria (linha 149).
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
  const [booked, setBooked] = useState<{
    id: string;
    manageToken: string;
    startsAt: string;
    status: string;
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

  // Muda servico/profissional: slot escolhido deixa de ser valido.
  useEffect(() => {
    setSlotStart(null);
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

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!serviceId || !barberId || !slotStart) return;

    const trimmedName = name.trim();
    const trimmedPhone = phone.trim();
    if (trimmedName.length < 3) {
      setError("Informe seu nome completo");
      return;
    }
    if (!/^\+?\d{10,15}$/.test(trimmedPhone.replace(/\D/g, ""))) {
      setError("Telefone invalido (use DDD, ex.: 11970000000)");
      return;
    }
    if (!consent) {
      setError("E necessario aceitar a politica de privacidade");
      return;
    }

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
          customer: { name: trimmedName, phone: trimmedPhone, consented: true },
        },
        idempotencyKeyRef.current,
      );
      setBooked({
        id: appointment.id,
        manageToken: appointment.manageToken ?? "",
        startsAt: appointment.startsAt,
        status: appointment.status,
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
    return (
      <main className="mx-auto max-w-lg p-6">
        <div className="rounded-lg bg-white p-6 shadow">
          <h1 className="text-xl font-bold text-green-700">Reserva criada!</h1>
          <p className="mt-2 text-sm text-slate-600">
            Seu horario esta reservado. A confirmacao pode ser feita pela
            barbearia; voce recebe o protocolo abaixo.
          </p>
          <dl className="mt-4 space-y-1 text-sm">
            <div className="flex gap-2">
              <dt className="font-semibold">Protocolo:</dt>
              <dd className="font-mono">{booked.id}</dd>
            </div>
            <div className="flex gap-2">
              <dt className="font-semibold">Status:</dt>
              <dd>{booked.status === "PENDING" ? "Pendente" : booked.status}</dd>
            </div>
          </dl>
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

  const selectedService = services.find((s) => s.id === serviceId);
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
        <div className="mt-2 flex items-center gap-3">
          <input
            type="date"
            className="rounded-lg border border-slate-300 p-2 text-sm"
            min={todayIn(barbershop?.timezone ?? "America/Sao_Paulo")}
            value={date}
            onChange={(e) => {
              setDate(e.target.value);
              setSlots(null);
            }}
          />
          <button
            type="button"
            className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700 disabled:opacity-40"
            disabled={!serviceId || !barberId || !date || availabilityLoading}
            onClick={() => void loadAvailability()}
          >
            {availabilityLoading ? "Buscando..." : "Ver horarios"}
          </button>
        </div>
        {slots && slots.length === 0 && (
          <p className="mt-3 text-sm text-slate-500">
            Sem horarios livres nesta data. Tente outro dia.
          </p>
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
              {slotRange(slot, barbershop?.timezone ?? "America/Sao_Paulo")}
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
              onChange={(e) => setPhone(e.target.value)}
              placeholder="11970000000"
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
              e o contato por WhatsApp sobre esta reserva.
            </span>
          </label>
          <button
            type="submit"
            className="rounded-lg bg-green-700 px-4 py-3 font-semibold text-white hover:bg-green-600 disabled:opacity-40"
            disabled={!canConfirm || submitting}
          >
            {submitting ? "Agendando..." : "Confirmar agendamento"}
          </button>
          {selectedService && (
            <p className="text-center text-xs text-slate-500">
              {selectedService.name} - {price(selectedService.priceCents)}
            </p>
          )}
        </form>
      </section>
    </main>
  );
}

function cryptoFallback(): string {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
