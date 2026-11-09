"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { animate, motion } from "motion/react";
import { RefreshCw, Search, Upload, X } from "lucide-react";
import { Overlay } from "@/components/overlay";
import { PageHeader } from "@/components/page-header";
import { ServiceDetails, STATUS } from "@/components/service-dialog";
import { ServiceIcon } from "@/components/service-icon";
import { Badge, Button, Card, Overline } from "@/components/ui";
import type { ServiceRecord } from "@/lib/db";
import { SEARCH_EVENT } from "@/lib/dom";
import { branding, CATEGORY_LABELS, serviceType, type ServiceCategory } from "@/lib/service-types";
import type { ServiceSummary } from "@/lib/services/summaries";
import { cn } from "@/lib/utils";

const REFRESH_MS = 60_000;
const CATEGORY_ORDER: ServiceCategory[] = ["infrastructure", "network", "security", "media", "games", "development"];

type View = "tous" | "en-ligne" | "alertes";
const VIEWS: { id: View; label: string }[] = [
  { id: "tous", label: "Tous" },
  { id: "en-ligne", label: "En ligne" },
  { id: "alertes", label: "Alertes" },
];

export function ServicesDashboard({ services, userName }: { services: ServiceRecord[]; userName: string }) {
  const [summaries, setSummaries] = useState<Record<string, ServiceSummary | undefined>>({});
  const [refreshing, setRefreshing] = useState<Set<string>>(new Set());
  const [query, setQuery] = useState("");
  const [openId, setOpenId] = useState<string | null>(null);
  const [liftedId, setLiftedId] = useState<string | null>(null);
  const [origin, setOrigin] = useState<DOMRect | null>(null);
  const searchInput = useRef<HTMLInputElement>(null);
  const params = useSearchParams();
  const view: View = (VIEWS.find((item) => item.id === params.get("vue"))?.id ?? "tous") as View;

  const refreshOne = useCallback(async (service: ServiceRecord) => {
    setRefreshing((current) => new Set(current).add(service.id));
    let summary: ServiceSummary;
    try {
      const response = await fetch(`/api/services/${service.id}/summary`, { cache: "no-store" });
      summary = response.ok ? ((await response.json()) as ServiceSummary) : { status: "offline", error: "Injoignable" };
    } catch {
      summary = { status: "offline", error: "Injoignable" };
    }
    setSummaries((current) => ({ ...current, [service.id]: summary }));
    setRefreshing((current) => {
      const next = new Set(current);
      next.delete(service.id);
      return next;
    });
  }, []);

  const refreshAll = useCallback(() => Promise.all(services.map(refreshOne)), [services, refreshOne]);

  // Polling: a first refresh right after mount, then every minute.
  useEffect(() => {
    const first = setTimeout(refreshAll, 0);
    const timer = setInterval(refreshAll, REFRESH_MS);
    return () => {
      clearTimeout(first);
      clearInterval(timer);
    };
  }, [refreshAll]);

  // Search: "/" shortcut, the mobile nav's search button, and ?recherche=1 from another page.
  useEffect(() => {
    const focus = () => {
      window.scrollTo({ top: 0, behavior: "smooth" });
      searchInput.current?.focus({ preventScroll: true });
    };
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (event.key === "/" && !target?.closest("input, textarea, [contenteditable]")) {
        event.preventDefault();
        focus();
      }
    };
    window.addEventListener(SEARCH_EVENT, focus);
    window.addEventListener("keydown", onKey);
    if (new URLSearchParams(window.location.search).has("recherche")) setTimeout(focus, 50);
    return () => {
      window.removeEventListener(SEARCH_EVENT, focus);
      window.removeEventListener("keydown", onKey);
    };
  }, []);

  const setView = (next: View) => {
    const url = new URL(window.location.href);
    if (next === "tous") url.searchParams.delete("vue");
    else url.searchParams.set("vue", next);
    url.searchParams.delete("recherche");
    window.history.replaceState(null, "", url);
  };

  const visible = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return services.filter((service) => {
      const status = summaries[service.id]?.status;
      if (view === "en-ligne" && status !== "online") return false;
      if (view === "alertes" && status !== "offline" && status !== "auth") return false;
      if (!needle) return true;
      const type = serviceType(service.type);
      return [service.label, service.type, type?.name ?? "", service.url].some((value) => value.toLowerCase().includes(needle));
    });
  }, [services, summaries, view, query]);

  const grouped = useMemo(() => {
    const groups = new Map<ServiceCategory, ServiceRecord[]>();
    for (const service of visible) {
      const category = serviceType(service.type)?.category ?? "infrastructure";
      groups.set(category, [...(groups.get(category) ?? []), service]);
    }
    return CATEGORY_ORDER.filter((category) => groups.has(category)).map((category) => ({ category, services: groups.get(category)! }));
  }, [visible]);

  const values = Object.values(summaries).filter(Boolean) as ServiceSummary[];
  const online = values.filter((summary) => summary.status === "online").length;
  const offline = values.filter((summary) => summary.status === "offline").length;
  const authIssues = values.filter((summary) => summary.status === "auth").length;
  const hour = new Date().getHours();
  const greeting = hour < 6 ? "Bonne nuit" : hour < 18 ? "Bonjour" : "Bonsoir";

  const openService = (service: ServiceRecord, element: HTMLElement) => {
    setOrigin(element.getBoundingClientRect());
    setOpenId(service.id);
    setLiftedId(service.id);
  };
  const closeService = useCallback(() => setOpenId(null), []);
  const openService_ = services.find((service) => service.id === (openId ?? liftedId));
  let cardIndex = 0;

  return (
    <div className="flex flex-col gap-7">
      <PageHeader
        overline="Tableau de bord"
        title={`${greeting}, ${userName.split(" ")[0]}`}
        actions={
          services.length > 0 && (
            <Button variant="outline" size="sm" onClick={refreshAll} disabled={refreshing.size > 0}>
              <RefreshCw className={cn(refreshing.size > 0 && "animate-spin")} />
              Actualiser
            </Button>
          )
        }
      />

      {services.length === 0 ? (
        <Card className="enter flex flex-col items-start gap-3 p-8">
          <h2 className="text-[1.15rem] font-semibold">Aucun service pour l&apos;instant</h2>
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
          {/* Stat strip: one card, four columns separated by hairlines; each figure filters the list. */}
          <Card className="enter grid grid-cols-2 divide-border/70 overflow-hidden md:grid-cols-4 md:divide-x">
            <Stat label="Services" value={services.length} onClick={() => setView("tous")} active={view === "tous"} />
            <Stat label="En ligne" value={online} tone="success" onClick={() => setView("en-ligne")} active={view === "en-ligne"} />
            <Stat label="Hors ligne" value={offline} tone={offline > 0 ? "destructive" : undefined} onClick={() => setView("alertes")} active={view === "alertes"} />
            <Stat label="Identifiants" value={authIssues} tone={authIssues > 0 ? "warning" : undefined} onClick={() => setView("alertes")} active={view === "alertes"} />
          </Card>

          {/* Toolbar: search and view switch. */}
          <div className="enter flex flex-col gap-3 sm:flex-row sm:items-center" style={{ "--i": 1 } as React.CSSProperties}>
            <label className="group relative flex-1">
              <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground transition-colors group-focus-within:text-primary" />
              <input
                ref={searchInput}
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                onKeyDown={(event) => event.key === "Escape" && (setQuery(""), event.currentTarget.blur())}
                placeholder="Rechercher un service"
                aria-label="Rechercher un service"
                className="h-[2.57rem] w-full rounded-lg border border-input bg-input/20 pr-16 pl-9 text-[1rem] backdrop-blur-[4px] outline-none transition-[border-color,box-shadow] placeholder:text-muted-foreground focus:border-primary/70 focus:ring-2 focus:ring-primary/25 sm:text-[0.93rem]"
              />
              {query ? (
                <button onClick={() => setQuery("")} className="absolute top-1/2 right-2 flex size-7 -translate-y-1/2 items-center justify-center rounded-md text-muted-foreground hover:bg-muted/60 hover:text-foreground" aria-label="Effacer la recherche">
                  <X className="size-4" />
                </button>
              ) : (
                <kbd className="data pointer-events-none absolute top-1/2 right-2.5 hidden -translate-y-1/2 rounded border border-border px-1.5 text-[0.75rem] text-muted-foreground sm:block">/</kbd>
              )}
            </label>
            <div role="tablist" aria-label="Filtrer" className="flex h-[2.57rem] shrink-0 items-center gap-0.5 rounded-lg border border-border/70 bg-surface/40 p-1 backdrop-blur-md">
              {VIEWS.map((item) => (
                <button
                  key={item.id}
                  role="tab"
                  aria-selected={view === item.id}
                  onClick={() => setView(item.id)}
                  className={cn(
                    "relative flex h-full flex-1 items-center justify-center gap-1.5 rounded-md px-3 text-[0.93rem] font-medium transition-colors sm:flex-none",
                    view === item.id ? "text-foreground" : "text-muted-foreground hover:text-foreground"
                  )}
                >
                  {view === item.id && <motion.span layoutId="view-pill" className="absolute inset-0 rounded-md bg-muted shadow-xs" transition={{ type: "spring", duration: 0.25, bounce: 0.1 }} />}
                  <span className="relative">{item.label}</span>
                  {item.id === "alertes" && offline + authIssues > 0 && (
                    <span className="data relative rounded-full bg-destructive/15 px-1.5 text-[0.72rem] text-destructive">{offline + authIssues}</span>
                  )}
                </button>
              ))}
            </div>
          </div>

          {grouped.length === 0 && (
            <div className="fade-in flex flex-col items-center gap-1 py-16 text-center">
              <p className="font-medium">{view === "alertes" && !query ? "Tout fonctionne" : "Aucun service trouvé"}</p>
              <p className="text-[0.93rem] text-muted-foreground">
                {view === "alertes" && !query ? "Aucun service hors ligne ni identifiant refusé." : "Essaie un autre nom ou un autre filtre."}
              </p>
            </div>
          )}

          {grouped.map(({ category, services: items }) => (
            <section key={category} id={category} className="flex flex-col gap-3">
              <div className="flex items-baseline gap-2 px-0.5">
                <Overline>{CATEGORY_LABELS[category]}</Overline>
                <span className="data text-[0.75rem] text-muted-foreground/70">{items.length}</span>
              </div>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3 min-[1700px]:grid-cols-4">
                {items.map((service) => (
                  <ServiceCard
                    key={service.id}
                    index={cardIndex++}
                    service={service}
                    summary={summaries[service.id]}
                    lifted={liftedId === service.id}
                    onOpen={openService}
                  />
                ))}
              </div>
            </section>
          ))}
        </>
      )}

      <Overlay
        open={openId !== null}
        onClose={closeService}
        onExited={() => setLiftedId(null)}
        origin={origin}
        label={openService_?.label ?? "Service"}
      >
        {openService_ && (
          <ServiceDetails
            service={openService_}
            summary={summaries[openService_.id]}
            refreshing={refreshing.has(openService_.id)}
            onRefresh={() => refreshOne(openService_)}
            onDeleted={closeService}
          />
        )}
      </Overlay>
    </div>
  );
}

/** Figure that counts up to its value when it changes. */
function AnimatedNumber({ value }: { value: number }) {
  const element = useRef<HTMLSpanElement>(null);
  const previous = useRef(0);
  useEffect(() => {
    const node = element.current;
    if (!node) return;
    const controls = animate(previous.current, value, {
      duration: 0.7,
      ease: [0.23, 1, 0.32, 1],
      onUpdate: (latest) => {
        node.textContent = String(Math.round(latest));
      },
    });
    previous.current = value;
    return () => controls.stop();
  }, [value]);
  return <span ref={element}>0</span>;
}

function Stat({ label, value, tone, onClick, active }: {
  label: string;
  value: number;
  tone?: "success" | "destructive" | "warning";
  onClick: () => void;
  active: boolean;
}) {
  return (
    <button
      onClick={onClick}
      aria-pressed={active}
      className="group relative px-5 py-4 text-left transition-colors hover:bg-muted/25"
    >
      <p className="text-[0.857rem] text-muted-foreground transition-colors group-hover:text-foreground">{label}</p>
      <p
        className={cn(
          "tabular mt-1 text-[1.45rem] leading-[1.1] font-semibold tracking-[-0.03em] transition-colors sm:text-[1.75rem]",
          tone === "success" && "text-success",
          tone === "destructive" && "text-destructive",
          tone === "warning" && "text-warning"
        )}
      >
        <AnimatedNumber value={value} />
      </p>
    </button>
  );
}

function ServiceCard({ service, summary, index, lifted, onOpen }: {
  service: ServiceRecord;
  summary?: ServiceSummary;
  index: number;
  lifted: boolean;
  onOpen: (service: ServiceRecord, element: HTMLElement) => void;
}) {
  const brand = branding(service.type, summary?.brand);
  const status = summary ? STATUS[summary.status] : null;
  return (
    <button
      type="button"
      onClick={(event) => onOpen(service, event.currentTarget)}
      aria-haspopup="dialog"
      className={cn("enter group block text-left focus-visible:outline-none", lifted && "invisible")}
      style={{ "--i": index + 2 } as React.CSSProperties}
    >
      <Card interactive className="flex h-full flex-col gap-4 p-4 group-focus-visible:border-primary/40 group-hover:-translate-y-0.5">
        <div className="flex items-start gap-3">
          <ServiceIcon icon={brand.icon} iconOnDark={brand.iconOnDark} color={brand.color} label={service.label} />
          <div className="min-w-0 flex-1">
            <h3 className="truncate text-[1.05rem] leading-snug font-semibold tracking-[-0.015em]">{service.label}</h3>
            <p className="truncate text-[0.857rem] text-muted-foreground">{summary?.headline ? `${brand.name} · ${summary.headline}` : brand.name}</p>
          </div>
          {status ? (
            <Badge tone={status.tone} dot pulse={summary?.status === "online"} className="fade-in shrink-0">
              {status.label}
            </Badge>
          ) : (
            <Badge dot className="shrink-0">…</Badge>
          )}
        </div>

        <div className="mt-auto flex items-end justify-between gap-3">
          <div className="min-w-0 flex-1">
            {summary?.metrics?.length ? (
              // Media services: the app's three-figure card.
              <dl key={summary.metrics.map((metric) => metric.value).join("|")} className="fade-in grid grid-cols-3 gap-2">
                {summary.metrics.slice(0, 3).map((metric, position) => (
                  <div key={metric.label} className="min-w-0">
                    <dd className="data truncate text-[1rem] leading-tight font-semibold" style={position === 0 ? { color: brand.color } : undefined}>
                      {metric.value}
                    </dd>
                    <dt className="truncate text-[0.786rem] text-muted-foreground">{metric.label}</dt>
                  </div>
                ))}
              </dl>
            ) : summary?.value ? (
              <div key={summary.value} className="fade-in">
                {/* Service numbers in the service's own color, like the app's home cards. */}
                <p className="data truncate text-[1.35rem] leading-tight font-semibold" style={{ color: brand.color }}>
                  {summary.value}
                  {summary.sub && <span className="ml-1.5 text-[0.857rem] font-normal text-muted-foreground">{summary.sub}</span>}
                </p>
                {summary.label && <p className="mt-0.5 text-[0.857rem] text-muted-foreground">{summary.label}</p>}
              </div>
            ) : summary ? (
              <p className="fade-in text-[0.857rem] text-muted-foreground">{summary.error ?? "En ligne"}</p>
            ) : (
              <div className="flex flex-col gap-1.5">
                <span className="skeleton block h-5 w-20" />
                <span className="skeleton block h-3 w-14" />
              </div>
            )}
          </div>
          {summary?.latencyMs !== undefined && summary.status !== "offline" && (
            <span className="data shrink-0 text-[0.75rem] text-muted-foreground/80">{summary.latencyMs} ms</span>
          )}
        </div>
      </Card>
    </button>
  );
}
