import Link from "next/link";

/** 404: rota inexistente (barbearia/reserva/URL errada). */
export default function NotFound() {
  return (
    <main className="mx-auto flex min-h-screen max-w-lg flex-col items-center justify-center p-6 text-center">
      <p className="text-6xl font-black text-slate-300">404</p>
      <h1 className="mt-2 text-xl font-bold">Pagina nao encontrada</h1>
      <p className="mt-2 text-sm text-slate-500">
        O endereco que voce abriu nao existe ou a barbearia nao esta mais
        disponivel. Confira o link recebido por WhatsApp.
      </p>
      <Link
        href="/"
        className="mt-6 rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700"
      >
        Ir para o inicio
      </Link>
    </main>
  );
}
