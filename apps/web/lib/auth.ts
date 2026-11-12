import type { Me } from "@/lib/api";

export type { Me };

/**
 * Sessao do gestor (MVP client-side): token JWT em localStorage e
 * anexado por Bearer em cada chamada ao dashboard. Na Fase 5 trocar por
 * cookie httpOnly com proxy server-side (spec, secao 11).
 */
const TOKEN_KEY = "barberflow.accessToken";
const USER_KEY = "barberflow.user";

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_KEY);
}

export function getUser(): Me | null {
  if (typeof window === "undefined") return null;
  const raw = window.localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as Me;
  } catch {
    return null;
  }
}

export function saveSession(accessToken: string, user: Me): void {
  window.localStorage.setItem(TOKEN_KEY, accessToken);
  window.localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearSession(): void {
  window.localStorage.removeItem(TOKEN_KEY);
  window.localStorage.removeItem(USER_KEY);
}
