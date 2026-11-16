"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { clearSession, getToken, getUser, type Me } from "@/lib/auth";

/** Icones inline (stroke currentColor) da navegacao lateral. */
function Icon({ name }: { name: string }) {
  const common = {
    width: 18,
    height: 18,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 1.8,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
  };
  switch (name) {
    case "calendar":
      return (
        <svg {...common}>
          <rect x="3" y="5" width="18" height="16" rx="2" />
          <path d="M16 3v4M8 3v4M3 11h18" />
        </svg>
      );
    case "bell":
      return (
        <svg {...common}>
          <path d="M6 9a6 6 0 1 1 12 0c0 5 2 6 2 6H4s2-1 2-6" />
          <path d="M10 20a2 2 0 0 0 4 0" />
        </svg>
      );
    case "scissors":
      return (
        <svg {...common}>
          <circle cx="6" cy="6" r="3" />
          <circle cx="6" cy="18" r="3" />
          <path d="M20 4 8.5 15.5M8.5 8.5 20 20" />
        </svg>
      );
    case "user":
      return (
        <svg {...common}>
          <circle cx="12" cy="8" r="4" />
          <path d="M4 21c0-4 4-6 8-6s8 2 8 6" />
        </svg>
      );
    case "settings":
      return (
        <svg {...common}>
          <circle cx="12" cy="12" r="3" />
          <path d="M19 12a7 7 0 0 0-.1-1.2l2-1.6-2-3.4-2.4 1a7 7 0 0 0-2-1.2L14 3h-4l-.5 2.6a7 7 0 0 0-2 1.2l-2.4-1-2 3.4 2 1.6a7 7 0 0 0 0 2.4l-2 1.6 2 3.4 2.4-1a7 7 0 0 0 2 1.2L10 21h4l.5-2.6a7 7 0 0 0 2-1.2l2.4 1 2-3.4-2-1.6c.1-.4.1-.8.1-1.2Z" />
        </svg>
      );
    case "logout":
      return (
        <svg {...common}>
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
          <path d="M16 17l5-5-5-5M21 12H9" />
        </svg>
      );
    default:
      return null;
  }
}

/**
 * Guard do painel com tema escuro: sidebar fixa (desktop) com navegacao
 * das secoes do dashboard (spec, secao 6) e topo compacto no mobile.
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
    return (
      <main className="min-h-screen bg-zinc-950 p-6 text-sm text-zinc-500">
        Carregando...
      </main>
    );
  }

  const isBarber = user?.role === "BARBER";
  const navItems = isBarber
    ? [{ href: "/gestor/minha-agenda", label: "Minha agenda", icon: "calendar" }]
    : [
        { href: "/gestor", label: "Agenda", icon: "calendar" },
        { href: "/gestor/fila", label: "Fila de espera", icon: "bell" },
        { href: "/gestor/servicos", label: "Servicos", icon: "scissors" },
        { href: "/gestor/profissionais", label: "Profissionais", icon: "user" },
        { href: "/gestor/configuracoes", label: "Configuracoes", icon: "settings" },
      ];

  const isActive = (href: string) =>
    href === "/gestor" ? pathname === href : pathname.startsWith(href);

  function logout() {
    clearSession();
    router.replace("/gestor/login");
  }

  return (
    <div className="min-h-screen bg-zinc-950 text-zinc-100">
      {/* Sidebar fixa (desktop) */}
      <aside className="fixed inset-y-0 left-0 z-40 hidden w-60 flex-col border-r border-zinc-800 bg-[#0a0a0a] lg:flex">
        <div className="border-b border-zinc-800 px-6 py-5">
          <p className="text-base font-bold tracking-tight text-amber-400">
            BarberFlow
          </p>
          <p className="mt-0.5 text-xs text-zinc-500">
            {user?.name ?? "Gestor"}
          </p>
        </div>
        <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
          {navItems.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={`flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${
                isActive(item.href)
                  ? "bg-amber-400/10 text-amber-300 ring-1 ring-amber-400/30"
                  : "text-zinc-400 hover:bg-zinc-900 hover:text-zinc-100"
              }`}
            >
              <Icon name={item.icon} />
              {item.label}
            </Link>
          ))}
        </nav>
        <div className="border-t border-zinc-800 p-3">
          <button
            type="button"
            onClick={logout}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm text-zinc-400 hover:bg-zinc-900 hover:text-zinc-100"
          >
            <Icon name="logout" />
            Sair
          </button>
        </div>
      </aside>

      {/* Topo compacto (mobile) */}
      <header className="sticky top-0 z-40 flex items-center gap-3 border-b border-zinc-800 bg-[#0a0a0a] px-4 py-3 lg:hidden">
        <p className="font-bold text-amber-400">BarberFlow</p>
        <nav className="ml-auto flex items-center gap-1 overflow-x-auto text-xs">
          {navItems.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={`whitespace-nowrap rounded-md px-2.5 py-1.5 ${
                isActive(item.href)
                  ? "bg-amber-400/10 text-amber-300"
                  : "text-zinc-400 hover:bg-zinc-900"
              }`}
            >
              {item.label}
            </Link>
          ))}
          <button
            type="button"
            onClick={logout}
            className="whitespace-nowrap rounded-md px-2.5 py-1.5 text-zinc-400 hover:bg-zinc-900"
          >
            Sair
          </button>
        </nav>
      </header>

      <main className="lg:pl-60">
        <div className="p-5 sm:p-8">{children}</div>
      </main>
    </div>
  );
}
