"use client";

import { useCallback, useEffect, useState } from "react";
import {
  createService,
  deleteService,
  listServices,
  updateService,
  ApiError,
  type ServiceAdmin,
} from "@/lib/api";
import { getToken } from "@/lib/auth";
import { price } from "@/lib/format";

/** CRUD de servicos do painel (spec, secao 6). */
export default function ServicosPage() {
  const [items, setItems] = useState<ServiceAdmin[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<string | null>(null);

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [duration, setDuration] = useState("30");
  const [priceReais, setPriceReais] = useState("50");
  const [busy, setBusy] = useState(false);

  const token = getToken();

  const load = useCallback(async () => {
    if (!token) return;
    try {
      setItems(await listServices(token));
    } catch (e) {
      handleError(e);
      setItems([]);
    }
  }, [token]);

  function handleError(e: unknown) {
    if (e instanceof ApiError && e.status === 401) {
      window.location.href = "/gestor/login";
      return;
    }
    setError(e instanceof ApiError ? e.message : "Erro inesperado");
  }

  useEffect(() => {
    void load();
  }, [load]);

  async function onCreate(event: React.FormEvent) {
    event.preventDefault();
    if (!token) return;
    setBusy(true);
    setError(null);
    try {
      await createService(
        {
          name: name.trim(),
          description: description.trim() || undefined,
          durationMinutes: Number(duration),
          priceCents: Math.round(Number(priceReais.replace(",", ".")) * 100),
        },
        token,
      );
      setName("");
      setDescription("");
      setDuration("30");
      setPriceReais("50");
      await load();
    } catch (e) {
      handleError(e);
    } finally {
      setBusy(false);
    }
  }

  async function onToggleActive(service: ServiceAdmin) {
    if (!token) return;
    setBusy(true);
    setError(null);
    try {
      await updateService(service.id, { active: !service.active }, token);
      await load();
    } catch (e) {
      handleError(e);
    } finally {
      setBusy(false);
    }
  }

  async function onDelete(service: ServiceAdmin) {
    if (
      !window.confirm(`Desativar o servico "${service.name}"? (soft delete)`)
    ) {
      return;
    }
    if (!token) return;
    setBusy(true);
    setError(null);
    try {
      await deleteService(service.id, token);
      await load();
    } catch (e) {
      handleError(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <h1 className="text-xl font-bold">Servicos</h1>

      {error && (
        <p className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-700" role="alert">
          {error}
        </p>
      )}

      <form
        className="mt-4 grid gap-2 rounded-lg bg-white p-4 shadow sm:grid-cols-4"
        onSubmit={onCreate}
      >
        <input
          required
          maxLength={120}
          placeholder="Nome do servico"
          className="rounded-lg border border-slate-300 p-2 text-sm sm:col-span-2"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <input
          required
          type="number"
          min={1}
          max={480}
          placeholder="Duracao (min)"
          className="rounded-lg border border-slate-300 p-2 text-sm"
          value={duration}
          onChange={(e) => setDuration(e.target.value)}
        />
        <input
          required
          type="text"
          inputMode="decimal"
          placeholder="Preco (R$)"
          className="rounded-lg border border-slate-300 p-2 text-sm"
          value={priceReais}
          onChange={(e) => setPriceReais(e.target.value)}
        />
        <input
          maxLength={500}
          placeholder="Descricao (opcional)"
          className="rounded-lg border border-slate-300 p-2 text-sm sm:col-span-3"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
        <button
          type="submit"
          className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-700 disabled:opacity-40"
          disabled={busy}
        >
          Criar servico
        </button>
      </form>

      {items === null ? (
        <p className="mt-6 text-sm text-slate-500">Carregando...</p>
      ) : (
        <ul className="mt-4 grid gap-2">
          {items.map((service) =>
            editing === service.id ? (
              <li key={service.id} className="rounded-lg bg-white p-4 shadow">
                <EditServiceForm
                  service={service}
                  onDone={(message) => {
                    setEditing(null);
                    setError(message);
                    void load();
                  }}
                />
              </li>
            ) : (
              <li
                key={service.id}
                className="flex flex-wrap items-center gap-3 rounded-lg bg-white p-4 shadow"
              >
                <div className="min-w-48">
                  <p className="font-semibold">
                    {service.name}
                    {!service.active && (
                      <span className="ml-2 rounded bg-slate-200 px-2 py-0.5 text-xs text-slate-600">
                        inativo
                      </span>
                    )}
                  </p>
                  <p className="text-xs text-slate-500">
                    {service.durationMinutes} min - {price(service.priceCents)}
                    {service.description ? ` - ${service.description}` : ""}
                  </p>
                </div>
                <span className="ml-auto flex gap-2">
                  <button
                    type="button"
                    className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50 disabled:opacity-40"
                    disabled={busy}
                    onClick={() => setEditing(service.id)}
                  >
                    Editar
                  </button>
                  <button
                    type="button"
                    className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50 disabled:opacity-40"
                    disabled={busy}
                    onClick={() => void onToggleActive(service)}
                  >
                    {service.active ? "Desativar" : "Ativar"}
                  </button>
                  <button
                    type="button"
                    className="rounded-lg border border-red-300 px-3 py-1.5 text-sm text-red-700 hover:bg-red-50 disabled:opacity-40"
                    disabled={busy}
                    onClick={() => void onDelete(service)}
                  >
                    Excluir
                  </button>
                </span>
              </li>
            ),
          )}
        </ul>
      )}
    </div>
  );
}

function EditServiceForm({
  service,
  onDone,
}: {
  service: ServiceAdmin;
  onDone: (error: string | null) => void;
}) {
  const [name, setName] = useState(service.name);
  const [description, setDescription] = useState(service.description ?? "");
  const [duration, setDuration] = useState(String(service.durationMinutes));
  const [priceReais, setPriceReais] = useState(
    (service.priceCents / 100).toFixed(2),
  );
  const [busy, setBusy] = useState(false);
  const token = getToken();

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!token) return;
    setBusy(true);
    try {
      await updateService(
        service.id,
        {
          name: name.trim(),
          description: description.trim(),
          durationMinutes: Number(duration),
          priceCents: Math.round(Number(priceReais.replace(",", ".")) * 100),
        },
        token,
      );
      onDone(null);
    } catch (e) {
      onDone(e instanceof ApiError ? e.message : "Erro ao salvar");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="grid gap-2 sm:grid-cols-4" onSubmit={submit}>
      <input
        required
        maxLength={120}
        className="rounded-lg border border-slate-300 p-2 text-sm sm:col-span-2"
        value={name}
        onChange={(e) => setName(e.target.value)}
      />
      <input
        required
        type="number"
        min={1}
        max={480}
        className="rounded-lg border border-slate-300 p-2 text-sm"
        value={duration}
        onChange={(e) => setDuration(e.target.value)}
      />
      <input
        required
        type="text"
        inputMode="decimal"
        className="rounded-lg border border-slate-300 p-2 text-sm"
        value={priceReais}
        onChange={(e) => setPriceReais(e.target.value)}
      />
      <input
        maxLength={500}
        placeholder="Descricao"
        className="rounded-lg border border-slate-300 p-2 text-sm sm:col-span-3"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
      />
      <button
        type="submit"
        className="rounded-lg bg-green-700 px-4 py-2 text-sm font-semibold text-white hover:bg-green-600 disabled:opacity-40"
        disabled={busy}
      >
        Salvar
      </button>
    </form>
  );
}
