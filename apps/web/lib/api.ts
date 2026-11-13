/**
 * Cliente da API BarberFlow (spec, secao 10): rotas publicas e rotas de
 * cliente autenticadas pelo token de gerenciamento (Bearer). Base URL
 * configuravel por NEXT_PUBLIC_API_BASE_URL (spec, linha 245).
 */
export const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

/** Barbearia padrao no MVP de instancia unica. */
export const DEFAULT_SLUG =
  process.env.NEXT_PUBLIC_DEFAULT_SLUG ?? "barbearia-piloto";

export type PublicBarbershop = {
  id: string;
  slug: string;
  name: string;
  timezone: string;
};

export type PublicService = {
  id: string;
  name: string;
  description: string | null;
  durationMinutes: number;
  priceCents: number;
};

export type PublicBarber = {
  id: string;
  displayName: string;
};

export type Slot = { startAt: string; endAt: string };

/** Inscricao na lista de espera publica (spec, secao 10). */
export type PublicWaitlistEntry = {
  id: string;
  status: string;
  manageToken: string | null;
};

/** Alerta por horario especifico quando a data esta cheia (spec, secao 10). */
export function joinWaitlist(
  slug: string,
  barberId: string,
  serviceId: string,
  startsAt: string,
  customer: { name: string; phone: string; consented: boolean },
) {
  return request<PublicWaitlistEntry>("POST", "/api/v1/public/waitlist", {
    json: { slug, barberId, serviceId, startsAt, customer },
  });
}
export type Availability = {
  date: string;
  timezone: string;
  barberId: string;
  serviceId: string;
  durationMinutes: number;
  slots: Slot[];
};

export type Appointment = {
  id: string;
  tenantId: string;
  barberId: string;
  customerId: string;
  serviceId: string;
  startsAt: string;
  endsAt: string;
  status: string;
  priceCents: number;
  durationMinutes: number;
  /** Somente na criacao (link seguro — spec, secao 9.1). */
  manageToken?: string | null;
};

/** Erro de API: problem+json (RFC 9457) ou fallback generico. */
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, detail: string) {
    super(detail);
    this.status = status;
  }
}

async function parseProblem(response: Response): Promise<never> {
  let detail = `Erro ${response.status}`;
  try {
    const body = (await response.json()) as { detail?: string; title?: string };
    detail = body.detail ?? body.title ?? detail;
  } catch {
    // corpo nao-JSON: mantem o detalhe generico
  }
  throw new ApiError(response.status, detail);
}

async function request<T>(
  method: string,
  path: string,
  options: {
    json?: unknown;
    token?: string;
    idempotencyKey?: string;
  } = {},
): Promise<T> {
  const headers: Record<string, string> = {};
  if (options.json !== undefined) headers["Content-Type"] = "application/json";
  if (options.token) headers["Authorization"] = `Bearer ${options.token}`;
  if (options.idempotencyKey) {
    headers["Idempotency-Key"] = options.idempotencyKey;
  }

  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: options.json === undefined ? undefined : JSON.stringify(options.json),
    cache: "no-store",
  });

  if (!response.ok) {
    await parseProblem(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

// ------------------------------------------------------------------ publico

export function getBarbershop(slug: string) {
  return request<PublicBarbershop>(
    "GET",
    `/api/v1/public/barbershops/${slug}`,
  );
}

export function getServices(slug: string) {
  return request<PublicService[]>(
    "GET",
    `/api/v1/public/barbershops/${slug}/services`,
  );
}

export function getBarbers(slug: string) {
  return request<PublicBarber[]>(
    "GET",
    `/api/v1/public/barbershops/${slug}/barbers`,
  );
}

export function getAvailability(
  slug: string,
  barberId: string,
  serviceId: string,
  date: string,
) {
  const query = new URLSearchParams({ barberId, serviceId, date });
  return request<Availability>(
    "GET",
    `/api/v1/public/barbershops/${slug}/availability?${query}`,
  );
}

export function createAppointment(
  slug: string,
  body: {
    slug: string;
    barberId: string;
    serviceId: string;
    startsAt: string;
    customer: { name: string; phone: string; consented: boolean };
  },
  idempotencyKey: string,
) {
  // Idempotencia obrigatoria (spec, linha 149): replay com a mesma chave
  // retorna a mesma reserva (200) sem duplicar.
  return request<Appointment>("POST", "/api/v1/public/appointments", {
    json: body,
    idempotencyKey,
  });
}

// ------------------------------------------------- cliente (link seguro)

export function getAppointment(appointmentId: string, token: string) {
  return request<Appointment>(
    "GET",
    `/api/v1/customer/appointments/${appointmentId}`,
    { token },
  );
}

export function cancelAppointment(appointmentId: string, token: string) {
  return request<Appointment>(
    "POST",
    `/api/v1/customer/appointments/${appointmentId}/cancel`,
    { token },
  );
}

export function rescheduleAppointment(
  appointmentId: string,
  token: string,
  startsAt: string,
) {
  return request<Appointment>(
    "POST",
    `/api/v1/customer/appointments/${appointmentId}/reschedule`,
    { json: { startsAt }, token },
  );
}

/** Oferta da lista de espera: aceita vaga com o token do caminho. */
export function acceptOffer(token: string) {
  // O token no caminho e a credencial; sem corpo (201 com a reserva).
  return request<Appointment>("POST", `/api/v1/customer/offers/${token}/accept`);
}

// ------------------------------------------------------------------ gestor

export type Me = {
  id: string;
  name: string;
  email: string;
  tenantId: string;
  role: string;
};

export type LoginResponse = {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: Me;
  tenantId: string;
  role: string;
};

export function login(email: string, password: string) {
  return request<LoginResponse>("POST", "/api/v1/auth/login", {
    json: { email, password },
  });
}

/** Reserva na agenda do gestor: nome do cliente resolvido? Nao — o payload
 * traz ids; a UI resolve nomes pelos catalogos de servicos/profissionais. */
export function getAgenda(date: string, authToken: string, barberId?: string) {
  const query = new URLSearchParams({ date });
  if (barberId) query.set("barberId", barberId);
  return request<Appointment[]>(
    "GET",
    `/api/v1/dashboard/appointments?${query}`,
    { token: authToken },
  );
}

export function updateAppointmentStatus(
  appointmentId: string,
  status: string,
  authToken: string,
) {
  return request<Appointment>(
    "PATCH",
    `/api/v1/dashboard/appointments/${appointmentId}/status`,
    { json: { status }, token: authToken },
  );
}

// ------------------------------------------------- dashboard: agenda BARBER

/** Agenda do PROPRIO profissional (papel BARBER — spec, linha 361). */
export function getBarberAgenda(date: string, authToken: string) {
  return request<Appointment[]>(
    "GET",
    `/api/v1/dashboard/barber/agenda?date=${date}`,
    { token: authToken },
  );
}

export function updateBarberAppointmentStatus(
  appointmentId: string,
  status: string,
  authToken: string,
) {
  return request<Appointment>(
    "PATCH",
    `/api/v1/dashboard/barber/appointments/${appointmentId}/status`,
    { json: { status }, token: authToken },
  );
}

/** Cria a conta de acesso do profissional (papel BARBER). */
export function createBarberCredentials(
  barberId: string,
  email: string,
  password: string,
  authToken: string,
) {
  return request<{ barberId: string; email: string; role: string }>(
    "POST",
    `/api/v1/dashboard/barbers/${barberId}/credentials`,
    { json: { email, password }, token: authToken },
  );
}

// --------------------------------------------------- dashboard: servicos

export type ServiceAdmin = {
  id: string;
  name: string;
  description: string | null;
  durationMinutes: number;
  priceCents: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export function listServices(authToken: string) {
  return request<ServiceAdmin[]>("GET", "/api/v1/dashboard/services", {
    token: authToken,
  });
}

export function createService(
  body: {
    name: string;
    description?: string;
    durationMinutes: number;
    priceCents: number;
  },
  authToken: string,
) {
  return request<ServiceAdmin>("POST", "/api/v1/dashboard/services", {
    json: body,
    token: authToken,
  });
}

export function updateService(
  serviceId: string,
  body: {
    name?: string;
    description?: string;
    durationMinutes?: number;
    priceCents?: number;
    active?: boolean;
  },
  authToken: string,
) {
  return request<ServiceAdmin>(
    "PATCH",
    `/api/v1/dashboard/services/${serviceId}`,
    { json: body, token: authToken },
  );
}

export function deleteService(serviceId: string, authToken: string) {
  return request<void>("DELETE", `/api/v1/dashboard/services/${serviceId}`, {
    token: authToken,
  });
}

// ------------------------------------------------ dashboard: profissionais

export type BarberAdmin = {
  id: string;
  userId: string | null;
  displayName: string;
  active: boolean;
  createdAt: string;
};

export function listBarbers(authToken: string) {
  return request<BarberAdmin[]>("GET", "/api/v1/dashboard/barbers", {
    token: authToken,
  });
}

export function createBarber(displayName: string, authToken: string) {
  return request<BarberAdmin>("POST", "/api/v1/dashboard/barbers", {
    json: { displayName },
    token: authToken,
  });
}

export function updateBarber(
  barberId: string,
  body: { displayName?: string; active?: boolean },
  authToken: string,
) {
  return request<BarberAdmin>("PATCH", `/api/v1/dashboard/barbers/${barberId}`, {
    json: body,
    token: authToken,
  });
}

export function deleteBarber(barberId: string, authToken: string) {
  return request<void>("DELETE", `/api/v1/dashboard/barbers/${barberId}`, {
    token: authToken,
  });
}

export type BusinessHour = {
  weekday: number;
  startLocal: string;
  endLocal: string;
};

export function getBarberHours(barberId: string, authToken: string) {
  return request<BusinessHour[]>(
    "GET",
    `/api/v1/dashboard/barbers/${barberId}/hours`,
    { token: authToken },
  );
}

export function putBarberHours(
  barberId: string,
  hours: BusinessHour[],
  authToken: string,
) {
  return request<BusinessHour[]>(
    "PUT",
    `/api/v1/dashboard/barbers/${barberId}/hours`,
    { json: hours, token: authToken },
  );
}

// ------------------------------------------- dashboard: lista de espera

export type WaitlistEntry = {
  id: string;
  barberId: string;
  serviceId: string;
  /** Cliente resolvido pela API (o painel nao recebe mais ids brutos). */
  customerName: string;
  customerPhone: string | null;
  desiredStartAt: string;
  desiredEndAt: string;
  status: string;
  createdAt: string;
  expiresAt: string | null;
  /** Oferta corrente da inscricao (nula enquanto WAITING/CANCELLED). */
  offer: {
    offerId: string;
    startsAt: string;
    endsAt: string;
    expiresAt: string | null;
  } | null;
};

export function listWaitlist(authToken: string) {
  return request<WaitlistEntry[]>("GET", "/api/v1/dashboard/waitlist", {
    token: authToken,
  });
}

/** Expira ofertas vencidas e oferece slots livres aos inscritos. */
export function processWaitlist(authToken: string) {
  return request<void>("POST", "/api/v1/dashboard/waitlist/process", {
    token: authToken,
  });
}

// -------------------------------------------------- dashboard: resumo

/** Cartoes resumo do painel (visao do dia em uma chamada). */
export type DashboardSummary = {
  date: string;
  appointments: {
    total: number;
    pending: number;
    confirmed: number;
    completed: number;
    noShow: number;
    cancelled: number;
    expectedRevenueCents: number;
  };
  waitlist: { waiting: number; offered: number; fulfilled: number; expired: number };
  outbox: { pending: number; failed: number };
};

/** Agregado do dia (sem data: hoje no fuso da barbearia). */
export function getSummary(date: string, authToken: string) {
  return request<DashboardSummary>(
    "GET",
    `/api/v1/dashboard/summary?date=${date}`,
    { token: authToken },
  );
}

// --------------------------------------------- dashboard: configuracoes

/** Configuracoes da barbearia: WhatsApp do gestor para notificacoes. */
export type Settings = { managerPhone: string | null };

export function getSettings(authToken: string) {
  return request<Settings>("GET", "/api/v1/dashboard/settings", {
    token: authToken,
  });
}

/** WhatsApp em E.164 ou null para desligar as notificacoes. */
export function updateSettings(
  managerPhone: string | null,
  authToken: string,
) {
  return request<Settings>("PATCH", "/api/v1/dashboard/settings", {
    json: { managerPhone },
    token: authToken,
  });
}
