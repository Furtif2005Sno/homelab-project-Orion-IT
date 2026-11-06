"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ArrowUpRight, RefreshCw, Upload } from "lucide-react";
import { PageHeader } from "@/components/page-header";
import { Badge, Button, Card, Overline, type Tone } from "@/components/ui";
import type { ServiceRecord } from "@/lib/db";
import { CATEGORY_LABELS, serviceType, type ServiceCategory, type ServiceTypeInfo } from "@/lib/service-types";
import type { ServiceSummary } from "@/lib/services/summaries";
import { cn } from "@/lib/utils";

const REFRESH_MS = 60_000;
const CATEGORY_ORDER: ServiceCategory[] = ["infrastructure", "network", "security", "media", "games", "development"];

const STATUS: Record<ServiceSummary["status"], { label: string; tone: Tone }> = {
  online: { label: "En ligne", tone: "success" },
  offline: { label: "Hors ligne", tone: "destructive" },
  auth: { label: "Identifiants", tone: "warning" },
};

export function ServicesDashboard({ services, userName }: { services: ServiceRecord[]; userName: string }) {
  const [summaries, setSummaries] = useState<Record<string, ServiceSummary | undefined>>({});
  const [refreshing, setRefreshing] = useState(false);

  const refresh = useCallback(async () => {
    setRefreshing(true);
    await Promise.all(
      services.map(async (service) => {
        try {
          const response = await fetch(`/api/services/${service.id}/summary`, { cache: "no-store" });
          const summary = response.ok ? ((await response.json()) as ServiceSummary) : { status: "offline" as const, error: "Injoignable" };
          setSummaries((current) => ({ ...current, [service.id]: summary }));
        } catch {
          setSummaries((current) => ({ ...current, [service.id]: { status: "offline", error: "Injoignable" } }));
        }
      })
    );
    setRefreshing(false);
  }, [services]);

  // Polling: a first refresh right after mount, then every minute.
  useEffect(() => {
    const first = setTimeout(refresh, 0);
    const timer = setInterval(refresh, REFRESH_MS);
    return () => {
      clearTimeout(first);
      clearInterval(timer);
    };
  }, [refresh]);

  const grouped = useMemo(() => {
    const groups = new Map<ServiceCategory, ServiceRecord[]>();
    for (const service of services) {
      const category = serviceType(service.type)?.category ?? "infrastructure";
      groups.set(category, [...(groups.get(category) ?? []), service]);
    }
    return CATEGORY_ORDER.filter((category) => groups.has(category)).map((category) => ({ category, services: groups.get(category)! }));
  }, [services]);

  const values = Object.values(summaries).filter(Boolean) as ServiceSummary[];
  const online = values.filter((summary) => summary.status === "online").length;
  const offline = values.filter((summary) => summary.status === "offline").length;
  const authIssues = values.filter((summary) => summary.status === "auth").length;
  const hour = new Date().getHours();
  const greeting = hour < 6 ? "Bonne nuit" : hour < 18 ? "Bonjour" : "Bonsoir";

  return (
    <div className="flex flex-col gap-8">
      <PageHeader
        overline="Tableau de bord"
        title={`${greeting}, ${userName.split(" ")[0]}`}
        actions={
          services.length > 0 && (
            <Button variant="outline" size="sm" onClick={refresh} disabled={refreshing}>
              <RefreshCw className={cn(refreshing && "animate-spin")} />
              Actualiser
            </Button>
          )
        }
      />

      {services.length === 0 ? (
        <Card className="flex flex-col items-start gap-3 p-8">
          <h2 className="text-[1.1rem] font-semibold">Aucun service pour l&apos;instant</h2>
          <p className="max-w-prose text-[0.93rem] text-muted-foreground">
            Exporte une sauvegarde depuis l&apos;app mobile (Réglages → Sauvegarde), puis importe-la ici pour tout retrouver.
          </p>
          <Link href="/backup" className="mt-2">
            <Button>
              <Upload />
              Importer une sauvegarde
            </Button>
          </Link>
        </Card>
      ) : (
        <>
          {/* Arcane-style stat strip: one card, four columns separated by hairlines. */}
          <Card className="grid grid-cols-2 divide-border/70 md:grid-cols-4 md:divide-x">
            <Stat label="Services" value={services.length} />
            <Stat label="En ligne" value={online} tone="success" />
            <Stat label="Hors ligne" value={offline} tone={offline > 0 ? "destructive" : undefined} />
            <Stat label="Identifiants" value={authIssues} tone={authIssues > 0 ? "warning" : undefined} />
          </Card>

          {grouped.map(({ category, services: items }) => (
            <section key={category} id={category} className="flex flex-col gap-3">
              <div className="flex items-baseline gap-2 px-0.5">
                <Overline>{CATEGORY_LABELS[category]}</Overline>
                <span className="font-mono text-[0.786rem] text-muted-foreground/70 tabular">{items.length}</span>
              </div>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
                {items.map((service) => (
                  <ServiceCard key={service.id} service={service} summary={summaries[service.id]} />
                ))}
              </div>
            </section>
          ))}
        </>
      )}
    </div>
  );
}

function Stat({ label, value, tone }: { label: string; value: number; tone?: "success" | "destructive" | "warning" }) {
  return (
    <div className="px-5 py-4">
      <p className="text-[0.857rem] text-muted-foreground">{label}</p>
      <p
        className={cn(
          "tabular mt-1 font-mono text-[1.85rem] leading-none font-semibold tracking-[-0.03em]",
          tone === "success" && "text-success",
          tone === "destructive" && "text-destructive",
          tone === "warning" && "text-warning"
        )}
      >
        {value}
      </p>
    </div>
  );
}

function ServiceIcon({ type, label }: { type?: ServiceTypeInfo; label: string }) {
  const [failed, setFailed] = useState(false);
  const color = type?.color ?? "#a1a1a1";
  return (
    <span
      className="flex size-10 shrink-0 items-center justify-center rounded-lg ring-1 ring-inset ring-foreground/5"
      style={{ backgroundColor: `color-mix(in oklab, ${color} 12%, transparent)` }}
    >
      {type && !failed ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={type.icon}
          alt=""
          className="size-6 object-contain"
          loading="lazy"
          onError={() => setFailed(true)}
          // An image that already failed before hydration never fires onError: check on mount.
          ref={(element) => {
            if (element?.complete && element.naturalWidth === 0) setFailed(true);
          }}
        />
      ) : (
        <span className="font-mono text-[0.93rem] font-semibold" style={{ color }}>
          {label.slice(0, 1).toUpperCase()}
        </span>
      )}
    </span>
  );
}

function ServiceCard({ service, summary }: { service: ServiceRecord; summary?: ServiceSummary }) {
  const type = serviceType(service.type);
  const status = summary ? STATUS[summary.status] : null;
  return (
    <a href={service.url} target="_blank" rel="noreferrer" className="group block focus-visible:outline-none">
      <Card interactive className="flex h-full flex-col gap-5 p-4 group-focus-visible:border-primary/40">
        <div className="flex items-center gap-3">
          <ServiceIcon type={type} label={service.label} />
          <div className="min-w-0 flex-1">
            <h3 className="truncate text-[1rem] leading-snug font-semibold">{service.label}</h3>
            <p className="truncate text-[0.857rem] text-muted-foreground">{type?.name ?? service.type}</p>
          </div>
          <ArrowUpRight className="size-4 shrink-0 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" />
        </div>

        <div className="mt-auto flex items-end justify-between gap-3">
          <div className="min-w-0">
            {summary?.value ? (
              <>
                {/* Service numbers in the service's own color, like the app's home cards. */}
                <p className="tabular truncate font-mono text-[1.35rem] leading-tight font-semibold" style={{ color: type?.color }}>
                  {summary.value}
                  {summary.sub && <span className="ml-1.5 text-[0.857rem] font-normal text-muted-foreground">{summary.sub}</span>}
                </p>
                {summary.label && <p className="mt-0.5 text-[0.857rem] text-muted-foreground">{summary.label}</p>}
              </>
            ) : (
              <p className="text-[0.857rem] text-muted-foreground">{summary ? (summary.error ?? "Accessible") : "Vérification…"}</p>
            )}
          </div>
          <div className="flex shrink-0 flex-col items-end gap-1.5">
            {status ? <Badge tone={status.tone} dot pulse={summary?.status === "online"}>{status.label}</Badge> : <Badge dot>…</Badge>}
            {summary?.latencyMs !== undefined && summary.status !== "offline" && (
              <span className="font-mono text-[0.75rem] text-muted-foreground/80 tabular">{summary.latencyMs} ms</span>
            )}
          </div>
        </div>
      </Card>
    </a>
  );
}
