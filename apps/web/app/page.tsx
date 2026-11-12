import { redirect } from "next/navigation";

/**
 * Entrada unica: redireciona para a pagina de agendamento da barbearia
 * padrao (MVP: uma barbearia por instancia — spec, secao 5).
 */
export default function Home() {
  redirect(`/b/${process.env.NEXT_PUBLIC_DEFAULT_SLUG ?? "barbearia-piloto"}`);
}
