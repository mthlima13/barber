"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { clearSession, getToken, getUser, type Me } from "@/lib/auth";

/**
 * Guard do painel: sem token valida -> redireciona para o login. Nav com
 * as secoes do dashboard (spec, secao 6) e logout.
 */
export default function PainelLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  const router = useRouter();
  const pathname = usePathname();
  const [ready, setReady] = useState(false);
  const [user, setUser] = useState<Me | null>(null);

  useEffect(() => {
    const token = getToken();
    if (!token) {
      router.replace("/gestor/login");
      return;
    }
    setUser(getUser());
    setReady(true);
  }, [router]);

  if (!ready) {
    return <main className="p-6 text-sm text-slate-500">Carregando...</main>;
  }

  const isBarber = user?.role === "BARBER";
  const navItems = isBarber
    ? [{ href: "/gestor/minha-agenda", label: "Minha agenda" }]
    : [
        { href: "/gestor", label: "Agenda" },
        { href: "/gestor/servicos", label: "Servicos" },
        { href: "/gestor/profissionais", label: "Profissionais" },
        { href: "/gestor/fila", label: "Fila de espera" },
        { href: "/gestor/configuracoes", label: "Configuracoes" },
      ];

  return (
    <div className="min-h-screen">
      <header className="bg-slate-900 text-white">
        <div className="mx-auto flex max-w-4xl flex-wrap items-center gap-4 p-4">
          <span className="font-bold">BarberFlow</span>
          <nav className="flex flex-wrap gap-2 text-sm">
            {navItems.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className={`rounded-lg px-3 py-1.5 ${
                  pathname === item.href
                    ? "bg-white text-slate-900"
                    : "hover:bg-slate-700"
                }`}
              >
                {item.label}
              </Link>
            ))}
          </nav>
          <span className="ml-auto text-xs text-slate-300">
            {user?.name ?? "Gestor"}
            <button
              type="button"
              className="ml-3 underline hover:text-white"
              onClick={() => {
                clearSession();
                router.replace("/gestor/login");
              }}
            >
              Sair
            </button>
          </span>
        </div>
      </header>
      <main className="mx-auto max-w-4xl p-6">{children}</main>
    </div>
  );
}
