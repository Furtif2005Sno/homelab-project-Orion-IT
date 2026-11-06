"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Check, Copy, ExternalLink, RefreshCw, ShieldAlert, Trash2 } from "lucide-react";
import { ServiceIcon } from "@/components/service-icon";
import { Badge, Button, Overline, type Tone } from "@/components/ui";
import type { ServiceRecord } from "@/lib/db";
import { branding } from "@/lib/service-types";
import type { ServiceSummary } from "@/lib/services/summaries";
import { cn } from "@/lib/utils";

export const STATUS: Record<ServiceSummary["status"], { label: string; tone: Tone }> = {
  online: { label: "En ligne", tone: "success" },
  offline: { label: "Hors ligne", tone: "destructive" },
  auth: { label: "Identifiants", tone: "warning" },
};

const TONE_TEXT = { success: "text-success", warning: "text-warning", destructive: "text-destructive" } as const;

function since(iso?: string) {
  if (!iso) return null;
  const seconds = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
  if (seconds < 10) return "à l'instant";
  if (seconds < 60) return `il y a ${seconds} s`;
  return `il y a ${Math.round(seconds / 60)} min`;
}

/** Content of the service popup: live status, key figure, details, connection and actions. */
export function ServiceDetails({ service, summary, refreshing, onRefresh, onDeleted }: {
  service: ServiceRecord;
  summary?: ServiceSummary;
  refreshing: boolean;
  onRefresh: () => void;
  onDeleted: () => void;
}) {
  const router = useRouter();
  const brand = branding(service.type, summary?.brand);
  const status = summary ? STATUS[summary.status] : null;
  const [confirming, setConfirming] = useState(false);
  const [deleting, setDeleting] = useState(false);

  async function remove() {
    if (!confirming) return setConfirming(true);
    setDeleting(true);
    const response = await fetch(`/api/services/${service.id}`, { method: "DELETE" });
    setDeleting(false);
    if (response.ok) {
      onDeleted();
      router.refresh();
    }
  }

  return (
    <div className="flex flex-col gap-5 p-5 sm:p-6">
      {/* Header */}
      <div className="flex items-center gap-4 pr-8">
        <ServiceIcon icon={brand.icon} color={brand.color} label={service.label} size="lg" />
        <div className="min-w-0">
          <h2 className="truncate text-[1.15rem] leading-[1.25] font-semibold tracking-[-0.02em]">{service.label}</h2>
          <p className="truncate text-[0.857rem] text-muted-foreground">{summary?.headline ? `${brand.name} · ${summary.headline}` : brand.name}</p>
        </div>
      </div>

      {/* Status and key figure */}
      <div className="subtle-card flex items-end justify-between gap-4 p-4">
        <div className="min-w-0">
          {summary?.metrics?.length ? (
            <dl className="grid grid-cols-3 gap-4">
              {summary.metrics.map((metric, position) => (
                <div key={metric.label} className="min-w-0">
                  <dd className="data truncate text-[1.2rem] leading-tight font-semibold" style={position === 0 ? { color: brand.color } : undefined}>
                    {metric.value}
                  </dd>
                  <dt className="truncate text-[0.857rem] text-muted-foreground">{metric.label}</dt>
                </div>
              ))}
            </dl>
          ) : summary?.value ? (
            <>
              <p className="data truncate text-[1.75rem] leading-[1.1] font-semibold tracking-[-0.03em]" style={{ color: brand.color }}>
                {summary.value}
                {summary.sub && <span className="ml-2 text-[0.93rem] font-normal text-muted-foreground">{summary.sub}</span>}
              </p>
              {summary.label && <p className="mt-1 text-[0.857rem] text-muted-foreground">{summary.label}</p>}
            </>
          ) : summary ? (
            <p className="text-[0.93rem] text-muted-foreground">{summary.error ?? "Accessible"}</p>
          ) : (
            <span className="skeleton block h-7 w-28" />
          )}
        </div>
        <div className="flex shrink-0 flex-col items-end gap-1.5">
          {status ? <Badge tone={status.tone} dot pulse={summary?.status === "online"}>{status.label}</Badge> : <Badge dot>…</Badge>}
          <span className="data text-[0.75rem] text-muted-foreground">
            {summary?.latencyMs !== undefined && summary.status !== "offline" && `${summary.latencyMs} ms · `}
            {since(summary?.checkedAt) ?? "vérification…"}
          </span>
        </div>
      </div>

      {/* Details from the service */}
      {summary?.details && summary.details.length > 0 && (
        <section className="flex flex-col gap-2">
          <Overline>Détails</Overline>
          <dl className="divide-y divide-border/70 rounded-lg border border-border/70">
            {summary.details.map((detail, index) => (
              <div key={`${detail.label}-${index}`} className="enter flex items-center justify-between gap-4 px-3.5 py-2.5" style={{ "--i": index } as React.CSSProperties}>
                <dt className="min-w-0 truncate text-[0.93rem] text-muted-foreground">{detail.label}</dt>
                <dd className={cn("data shrink-0 text-[0.85rem]", detail.tone ? TONE_TEXT[detail.tone] : "text-foreground")}>{detail.value}</dd>
              </div>
            ))}
          </dl>
        </section>
      )}

      {/* Connection */}
      <section className="flex flex-col gap-2">
        <Overline>Connexion</Overline>
        <div className="flex flex-col gap-1.5">
          <UrlRow label="Adresse" url={service.url} />
          {service.fallbackUrl && <UrlRow label="Secours" url={service.fallbackUrl} />}
          {service.allowSelfSigned && (
            <p className="flex items-center gap-1.5 px-0.5 text-[0.857rem] text-muted-foreground">
              <ShieldAlert className="size-3.5 text-warning" />
              Certificat auto-signé accepté
            </p>
          )}
        </div>
      </section>

      {/* Actions */}
      <div className="flex flex-col-reverse gap-2 border-t border-border/70 pt-4 sm:flex-row sm:items-center">
        <Button variant="ghost" size="sm" onClick={remove} disabled={deleting} className={cn("text-destructive hover:bg-destructive/12", confirming && "bg-destructive/12")}>
          <Trash2 />
          {confirming ? "Confirmer la suppression" : "Supprimer"}
        </Button>
        <div className="flex gap-2 sm:ml-auto">
          <Button variant="outline" onClick={onRefresh} disabled={refreshing} className="flex-1 sm:flex-none">
            <RefreshCw className={cn(refreshing && "animate-spin")} />
            Actualiser
          </Button>
          <a href={service.url} target="_blank" rel="noreferrer" className="flex-1 sm:flex-none">
            <Button className="w-full">
              <ExternalLink />
              Ouvrir
            </Button>
          </a>
        </div>
      </div>
    </div>
  );
}

function UrlRow({ label, url }: { label: string; url: string }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 1400);
    } catch {
      // Clipboard unavailable (insecure context): nothing to do.
    }
  };
  return (
    <div className="flex items-center gap-3 rounded-lg border border-border/70 bg-background/30 py-1.5 pr-1.5 pl-3.5">
      <span className="w-16 shrink-0 text-[0.857rem] text-muted-foreground">{label}</span>
      <a href={url} target="_blank" rel="noreferrer" className="data min-w-0 flex-1 truncate text-[0.85rem] hover:text-primary">
        {url}
      </a>
      <button
        onClick={copy}
        className="pressable flex size-8 shrink-0 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted/60 hover:text-foreground"
        aria-label={`Copier l'adresse ${label.toLowerCase()}`}
      >
        {copied ? <Check className="size-4 text-success" /> : <Copy className="size-4" />}
      </button>
    </div>
  );
}
