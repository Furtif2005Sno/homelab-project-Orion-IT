"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Download, FileLock2, Upload } from "lucide-react";
import { Badge, Button, Card, Input, Overline } from "@/components/ui";
import { decryptBackup, type BackupEnvelope } from "@/lib/backup-format";
import { serviceType } from "@/lib/service-types";
import { cn } from "@/lib/utils";

export function BackupImportPanel() {
  const router = useRouter();
  const [file, setFile] = useState<File | null>(null);
  const [password, setPassword] = useState("");
  const [envelope, setEnvelope] = useState<BackupEnvelope | null>(null);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [mode, setMode] = useState<"merge" | "replace">("merge");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<string | null>(null);

  async function decrypt() {
    if (!file) return;
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      // Decrypted here, in the browser: the backup password never leaves the page.
      const data = await decryptBackup(new Uint8Array(await file.arrayBuffer()), password);
      setEnvelope(data);
      setSelected(new Set(data.services.map((_, index) => index)));
    } catch (cause) {
      setEnvelope(null);
      setError(cause instanceof Error ? cause.message : "Impossible de lire la sauvegarde.");
    } finally {
      setBusy(false);
    }
  }

  async function importSelected() {
    if (!envelope) return;
    setBusy(true);
    setError(null);
    try {
      const response = await fetch("/api/backup/import", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ mode, services: envelope.services.filter((_, index) => selected.has(index)) }),
      });
      const body = (await response.json()) as { added?: number; updated?: number; skipped?: string[]; error?: string };
      if (!response.ok) throw new Error(body.error ?? `HTTP ${response.status}`);
      const skipped = body.skipped?.length ? `, ${body.skipped.length} ignoré(s)` : "";
      setResult(`${body.added ?? 0} ajouté(s), ${body.updated ?? 0} mis à jour${skipped}.`);
      setEnvelope(null);
      setFile(null);
      setPassword("");
      router.refresh();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Import impossible.");
    } finally {
      setBusy(false);
    }
  }

  function toggle(index: number) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(index)) next.delete(index);
      else next.add(index);
      return next;
    });
  }

  return (
    <Card className="flex flex-col gap-4 p-5">
      <div className="flex items-start gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/14 text-primary">
          <Upload className="size-5" />
        </span>
        <div>
          <h2 className="text-[1.1rem] font-semibold">Importer une sauvegarde</h2>
          <p className="text-[0.93rem] text-muted-foreground">Le fichier .homelab exporté depuis l&apos;app (Réglages → Sauvegarde).</p>
        </div>
      </div>

      {!envelope ? (
        <form
          className="flex flex-col gap-3"
          onSubmit={(event) => {
            event.preventDefault();
            decrypt();
          }}
        >
          <label className="flex cursor-pointer items-center gap-3 rounded-lg border border-dashed border-border p-4 transition-colors hover:border-primary/40">
            <FileLock2 className="size-5 text-muted-foreground" />
            <span className={cn("truncate text-[0.93rem]", file ? "text-foreground" : "text-muted-foreground")}>
              {file ? file.name : "Choisir un fichier .homelab"}
            </span>
            <input type="file" accept=".homelab,application/octet-stream" className="sr-only" onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
          </label>
          <Input type="password" placeholder="Mot de passe de la sauvegarde" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="off" />
          <Button type="submit" disabled={!file || !password || busy} className="self-start">
            {busy ? "Déchiffrement…" : "Lire la sauvegarde"}
          </Button>
        </form>
      ) : (
        <div className="flex flex-col gap-4">
          <div className="flex flex-wrap items-center gap-2 text-[0.857rem] text-muted-foreground">
            <span>{envelope.services.length} service(s)</span>
            <span>·</span>
            <span className="font-mono">app {envelope.appVersion}</span>
            <span>·</span>
            <span className="font-mono">{new Date(envelope.exportedAt).toLocaleString("fr-FR")}</span>
          </div>
          <ul className="flex max-h-80 flex-col gap-1.5 overflow-y-auto pr-1">
            {envelope.services.map((entry, index) => {
              const type = serviceType(entry.type);
              return (
                <li key={`${entry.type}-${entry.url}-${index}`}>
                  <label className={cn("subtle-card flex cursor-pointer items-center gap-3 px-3 py-2", !type && "opacity-60")}>
                    <input type="checkbox" checked={selected.has(index)} onChange={() => toggle(index)} className="size-4 accent-[var(--primary)]" disabled={!type} />
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    {type && <img src={type.icon} alt="" className="size-5 object-contain" />}
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-[0.93rem] font-medium">{entry.label || type?.name}</span>
                      <span className="block truncate font-mono text-[0.786rem] text-muted-foreground">{entry.url}</span>
                    </span>
                    {type ? <Badge>{type.name}</Badge> : <Badge tone="warning">Inconnu</Badge>}
                  </label>
                </li>
              );
            })}
          </ul>
          <div className="flex rounded-lg bg-muted/60 p-1 text-[0.857rem]" role="radiogroup" aria-label="Mode d'import">
            {(["merge", "replace"] as const).map((value) => (
              <button
                key={value}
                type="button"
                role="radio"
                aria-checked={mode === value}
                onClick={() => setMode(value)}
                className={cn("pressable flex-1 rounded-md px-3 py-1.5 font-medium", mode === value ? "bg-surface text-foreground shadow-sm" : "text-muted-foreground")}
              >
                {value === "merge" ? "Fusionner (met à jour les existants)" : "Remplacer tout"}
              </button>
            ))}
          </div>
          <div className="flex flex-wrap gap-2">
            <Button onClick={importSelected} disabled={busy || selected.size === 0}>
              {busy ? "Import…" : `Importer ${selected.size} service(s)`}
            </Button>
            <Button variant="outline" onClick={() => setEnvelope(null)} disabled={busy}>
              Annuler
            </Button>
          </div>
        </div>
      )}

      {error && <p className="text-[0.93rem] text-destructive">{error}</p>}
      {result && <p className="text-[0.93rem] text-success">{result}</p>}
    </Card>
  );
}

export function BackupExportPanel({ serviceCount }: { serviceCount: number }) {
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const mismatch = confirm.length > 0 && password !== confirm;

  async function exportBackup(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const response = await fetch("/api/backup/export", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ password }),
      });
      if (!response.ok) throw new Error(((await response.json()) as { error?: string }).error ?? `HTTP ${response.status}`);
      const blob = await response.blob();
      const name = /filename="([^"]+)"/.exec(response.headers.get("Content-Disposition") ?? "")?.[1] ?? "homelab_backup.homelab";
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = name;
      link.click();
      URL.revokeObjectURL(url);
      setPassword("");
      setConfirm("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Export impossible.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className="flex flex-col gap-4 p-5">
      <div className="flex items-start gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-info/12 text-info">
          <Download className="size-5" />
        </span>
        <div>
          <h2 className="text-[1.1rem] font-semibold">Exporter vers l&apos;app</h2>
          <p className="text-[0.93rem] text-muted-foreground">Un fichier .homelab chiffré, importable depuis l&apos;app mobile.</p>
        </div>
      </div>
      <form className="flex flex-col gap-3" onSubmit={exportBackup}>
        <Overline>{serviceCount} service(s) exporté(s)</Overline>
        <Input type="password" placeholder="Mot de passe (8 caractères minimum)" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="new-password" />
        <Input type="password" placeholder="Confirmer le mot de passe" value={confirm} onChange={(event) => setConfirm(event.target.value)} autoComplete="new-password" />
        {mismatch && <p className="text-[0.857rem] text-destructive">Les mots de passe ne correspondent pas.</p>}
        <Button type="submit" variant="outline" disabled={busy || serviceCount === 0 || password.length < 8 || password !== confirm} className="self-start">
          {busy ? "Chiffrement…" : "Télécharger la sauvegarde"}
        </Button>
        {error && <p className="text-[0.93rem] text-destructive">{error}</p>}
      </form>
    </Card>
  );
}
