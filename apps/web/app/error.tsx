"use client";

/** 500: erro de renderizacao/ rede no cliente (App Router). */
export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <main className="mx-auto flex min-h-screen max-w-lg flex-col items-center justify-center p-6 text-center">
      <p className="text-6xl font-black text-slate-300">500</p>
      <h1 className="mt-2 text-xl font-bold">Algo deu errado</h1>
      <p className="mt-2 text-sm text-slate-500">
        Nao foi possivel carregar esta pagina. Tente de novo; se o problema
        persistir, aguarde alguns minutos.
      </p>
      {error.digest && (
        <p className="mt-2 font-mono text-xs text-slate-400">
          Ref: {error.digest}
        </p>
      )}
      <button
        type="button"
        onClick={reset}
        className="mt-6 rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700"
      >
        Tentar novamente
      </button>
    </main>
  );
}
