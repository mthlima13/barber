import Link from "next/link";

export const metadata = {
  title: "Política de Privacidade | BarberFlow",
  description:
    "Como a barbearia coleta, usa e protege seus dados pessoais (LGPD).",
};

export default function PrivacidadePage() {
  return (
    <main className="mx-auto max-w-2xl px-4 py-10 text-slate-800">
      <Link
        href="/"
        className="text-sm text-green-700 underline hover:text-green-800"
      >
        &larr; Voltar ao agendamento
      </Link>
      <h1 className="mt-4 text-2xl font-bold">Política de Privacidade</h1>
      <p className="mt-1 text-sm text-slate-500">
        Versão 1.0 — em vigor desde a publicação. Em conformidade com a Lei
        Geral de Proteção de Dados (Lei nº 13.709/2018 — LGPD).
      </p>

      <section className="mt-8 space-y-3 text-sm leading-relaxed">
        <h2 className="text-lg font-semibold">1. Quem trata seus dados</h2>
        <p>
          A barbearia (controladora dos dados) utiliza o sistema BarberFlow
          para gerenciar reservas e avisos de horário. Dúvidas sobre seus dados
          podem ser feitas pelo canal de atendimento da barbearia ou pelo canal
          eletrônico descrito na seção 6.
        </p>

        <h2 className="text-lg font-semibold">2. Dados coletados e finalidade</h2>
        <p>Colecionamos o mínimo necessário para o serviço:</p>
        <ul className="list-disc space-y-1 pl-5">
          <li>
            <strong>Nome e telefone (WhatsApp)</strong> — para registrar,
            lembrar e reagendar sua reserva, e avisar sobre horários que você
            pediu para ser avisado (lista de espera).
          </li>
          <li>
            <strong>Data e hora das reservas</strong> — registro do negócio e
            organização da agenda.
          </li>
        </ul>
        <p>
          Não coletamos endereço, documentos, dados de pagamento por este
          sistema, nem dados sensíveis.
        </p>

        <h2 className="text-lg font-semibold">3. Base legal</h2>
        <ul className="list-disc space-y-1 pl-5">
          <li>
            <strong>Consentimento</strong> — contato por WhatsApp e inscrição na
            lista de espera: você aceita a política no formulário de agendamento
            e o consentimento fica registrado (data, origem e versão desta
            política).
          </li>
          <li>
            <strong>Execução de contrato</strong> — a reserva em si (nome e
            telefone para confirmar o horário).
          </li>
        </ul>

        <h2 className="text-lg font-semibold">4. Compartilhamento</h2>
        <p>
          Seus dados não são vendidos nem compartilhados com terceiros, exceto
          o provedor de envio de mensagens WhatsApp (Meta), que recebe apenas o
          necessário para entregar a notificação, e fornecedores de
          infraestrutura (hospedagem/banco de dados) contratados com
          obrigação de proteção.
        </p>

        <h2 className="text-lg font-semibold">5. Retenção</h2>
        <p>
          Mantemos seus dados enquanto houver reservas ativas ou recentes. Sem
          atividade identificável após o prazo de retenção definido pela
          barbearia, os dados são <strong>anonimizados</strong> de forma
          definitiva. Reservas históricas podem permanecer como registro do
          negócio, sem dados que identifiquem a pessoa.
        </p>

        <h2 className="text-lg font-semibold">6. Seus direitos</h2>
        <p>
          Você pode solicitar a qualquer momento, pelo canal da barbearia:
        </p>
        <ul className="list-disc space-y-1 pl-5">
          <li>Confirmação de que tratamos seus dados e acesso a eles;</li>
          <li>Correção de dados incompletos ou desatualizados;</li>
          <li>
            <strong>Exclusão</strong> — anonimização definitiva dos dados
            pessoais;
          </li>
          <li>Informação sobre compartilhamento e revogação do consentimento.</li>
        </ul>
        <p>
          O canal de autoatendimento é o <strong>link seguro da sua
          reserva</strong> (recebido no WhatsApp na confirmação): nele é possível
          consultar seus dados e solicitar a exclusão (anonimização) sem
          precisar falar com ninguém. O link não expõe seu telefone e permite
          apenas o gerenciamento da própria reserva.
        </p>

        <h2 className="text-lg font-semibold">7. Segurança</h2>
        <p>
          Usamos conexão cifrada (HTTPS), controle de acesso por perfil,
          tokens de uso limitado e logs sem conteúdo sensível. Nenhum sistema é
          infalível: se identificar algum problema, avise a barbearia.
        </p>
      </section>
    </main>
  );
}
