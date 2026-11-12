"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { login, ApiError } from "@/lib/api";
import { saveSession } from "@/lib/auth";

/** Login do gestor (spec, secao 11): JWT salvo na sessao do browser. */
export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const session = await login(email.trim(), password);
      saveSession(session.accessToken, session.user);
      router.replace("/gestor");
    } catch (e) {
      setError(
        e instanceof ApiError
          ? e.status === 401
            ? "E-mail ou senha incorretos"
            : e.message
          : "Erro ao entrar",
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-sm flex-col justify-center p-6">
      <div className="rounded-lg bg-white p-6 shadow">
        <h1 className="text-xl font-bold">Painel do gestor</h1>
        <p className="mt-1 text-sm text-slate-600">
          Entre com sua conta da barbearia.
        </p>
        <form className="mt-4 grid gap-3" onSubmit={submit}>
          <label className="text-sm">
            <span className="font-medium">E-mail</span>
            <input
              type="email"
              required
              className="mt-1 w-full rounded-lg border border-slate-300 p-2 text-sm"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="gestor@barbearia.local"
            />
          </label>
          <label className="text-sm">
            <span className="font-medium">Senha</span>
            <input
              type="password"
              required
              className="mt-1 w-full rounded-lg border border-slate-300 p-2 text-sm"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </label>
          {error && (
            <p className="rounded-lg bg-red-50 p-3 text-sm text-red-700" role="alert">
              {error}
            </p>
          )}
          <button
            type="submit"
            className="rounded-lg bg-slate-900 px-4 py-3 font-semibold text-white hover:bg-slate-700 disabled:opacity-40"
            disabled={busy}
          >
            {busy ? "Entrando..." : "Entrar"}
          </button>
        </form>
        <p className="mt-4 text-center text-xs text-slate-500">
          Primeiro acesso? Use o bootstrap da API (ver README) e depois entre
          aqui. <Link href="/" className="underline">Voltar ao agendamento</Link>
        </p>
      </div>
    </main>
  );
}
