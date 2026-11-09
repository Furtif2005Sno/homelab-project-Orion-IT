"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AlertTriangle, ArrowLeft, ExternalLink, RefreshCw, Search } from "lucide-react";
import { PageHeader } from "@/components/page-header";
import { ActionBar, ActionsProvider, RowActions } from "@/components/service-actions";
import { ServiceIcon } from "@/components/service-icon";
import { Badge, Button, Card, Overline, type Tone } from "@/components/ui";
import type { ServiceRecord } from "@/lib/db";
import { branding } from "@/lib/service-types";
import type { DashRow, DashSection, ServiceDashboard } from "@/lib/services/dashboards";
import { cn } from "@/lib/utils";

const REFRESH_MS = 30_000;

type State =
  | { phase: "loading" }
  | { phase: "ready"; dashboard: ServiceDashboard; checkedAt: string }
  | { phase: "error"; message: string; kind?: string };

export function ServiceDashboardView({ service, brand: initialBrand }: { service: ServiceRecord; brand?: string }) {
  const [state, setState] = useState<State>({ phase: "loading" });
  const [refreshing, setRefreshing] = useState(false);
  const brand = branding(service.type, initialBrand ?? (state.phase === "ready" && /forgejo/i.test(state.dashboard.subtitle ?? "") ? "forgejo" : undefined));

  const load = useCallback(async () => {
    setRefreshing(true);
    try {
      const response = await fetch(`/api/services/${service.id}/dashboard`, { cache: "no-store" });
      const body = await response.json();
      if (response.ok) setState({ phase: "ready", dashboard: body.dashboard, checkedAt: body.checkedAt });
      else setState((current) => (current.phase === "ready" ? current : { phase: "error", message: body.error ?? "Erreur", kind: body.kind }));
    } catch {
      setState((current) => (current.phase === "ready" ? current : { phase: "error", message: "Le tableau de bord n'a pas répondu." }));
    }
    setRefreshing(false);
  }, [service.id]);

  useEffect(() => {
    const first = setTimeout(load, 0);
    const timer = setInterval(load, REFRESH_MS);
    return () => {
      clearTimeout(first);
      clearInterval(timer);
    };
  }, [load]);

  const dashboard = state.phase === "ready" ? state.dashboard : null;
  const full = dashboard?.sections.filter((section) => section.kind === "stats" || section.kind === "chart") ?? [];
  const rest = dashboard?.sections.filter((section) => section.kind !== "stats" && section.kind !== "chart") ?? [];

  return (
    <ActionsProvider serviceId={service.id} onDone={load}>
    <div className="flex flex-col gap-6">
      <Link href="/" className="inline-flex w-fit items-center gap-1.5 text-[0.857rem] text-muted-foreground transition-colors hover:text-foreground">
        <ArrowLeft className="size-4" />
        Tableau de bord
      </Link>

      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex min-w-0 items-center gap-4">
          <ServiceIcon icon={brand.icon} iconOnDark={brand.iconOnDark} color={brand.color} label={service.label} size="lg" />
          <PageHeader overline={brand.name} title={service.label} description={dashboard?.subtitle} />
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <Button variant="outline" size="sm" onClick={load} disabled={refreshing}>
            <RefreshCw className={cn(refreshing && "animate-spin")} />
            Actualiser
          </Button>
          <a href={service.url} target="_blank" rel="noreferrer">
            <Button size="sm">
              <ExternalLink />
              Ouvrir
            </Button>
          </a>
        </div>
      </div>

      {dashboard?.actions && dashboard.actions.length > 0 && (
        <div className="enter">
          <ActionBar actions={dashboard.actions} />
        </div>
      )}

      {state.phase === "loading" && <DashboardSkeleton />}

      {state.phase === "error" && (
        <Card className="enter flex flex-col items-start gap-3 p-6">
          <div className="flex items-center gap-2 text-destructive">
            <AlertTriangle className="size-5" />
            <h2 className="text-[1.05rem] font-semibold">{state.kind === "auth" ? "Identifiants refusés" : "Données indisponibles"}</h2>
          </div>
          <p className="text-[0.93rem] text-muted-foreground">{state.message}</p>
          <Button variant="outline" size="sm" onClick={load}>
            <RefreshCw />
            Réessayer
          </Button>
        </Card>
      )}

      {dashboard && (
        <>
          {full.map((section, index) => (
            <div key={`full-${index}`} className="enter" style={{ "--i": index } as React.CSSProperties}>
              <Section section={section} />
            </div>
          ))}
          {rest.length > 0 && (
            <div className="grid grid-cols-1 items-start gap-4 xl:grid-cols-2">
              {rest.map((section, index) => (
                <div key={`rest-${index}`} className={cn("enter min-w-0", wide(section) && "xl:col-span-2")} style={{ "--i": full.length + index } as React.CSSProperties}>
                  <Section section={section} />
                </div>
              ))}
            </div>
          )}
          {dashboard.sections.length === 0 && <p className="text-[0.93rem] text-muted-foreground">Ce service n&apos;expose pas de données détaillées.</p>}
        </>
      )}
    </div>
    </ActionsProvider>
  );
}

/** Long lists (torrents, containers, clients) get the full width. */
const wide = (section: DashSection) => section.kind === "rows" && section.items.length > 14;

function Section({ section }: { section: DashSection }) {
  switch (section.kind) {
    case "stats":
      return <StatsSection items={section.items} title={section.title} />;
    case "rows":
      return <RowsSection title={section.title} items={section.items} empty={section.empty} searchable={section.searchable} />;
    case "ranking":
      return <RankingSection title={section.title} items={section.items} empty={section.empty} />;
    case "chart":
      return <ChartSection title={section.title} unit={section.unit} series={section.series} />;
    case "notes":
      return (
        <Card className="flex flex-col gap-2.5 p-4">
          <div className="flex items-center gap-2">
            <AlertTriangle className={cn("size-4", toneText(section.tone))} />
            <h2 className="text-[1.05rem] font-semibold">{section.title}</h2>
          </div>
          <ul className="flex flex-col gap-1.5 text-[0.93rem]">
            {section.items.map((item, index) => (
              <li key={index} className="rounded-md bg-muted/40 px-3 py-2">
                {item}
              </li>
            ))}
          </ul>
        </Card>
      );
    case "facts":
      return (
        <Card className="p-4">
          <h2 className="mb-2 text-[1.05rem] font-semibold">{section.title}</h2>
          <dl className="divide-y divide-border/70">
            {section.items.map((item) => (
              <div key={item.label} className="flex items-center justify-between gap-4 py-2">
                <dt className="text-[0.93rem] text-muted-foreground">{item.label}</dt>
                <dd className="data min-w-0 truncate text-right text-[0.85rem]">{item.value}</dd>
              </div>
            ))}
          </dl>
        </Card>
      );
  }
}

const toneText = (tone?: Tone) =>
  tone === "success" ? "text-success" : tone === "warning" ? "text-warning" : tone === "destructive" ? "text-destructive" : tone === "info" ? "text-info" : undefined;

function StatsSection({ title, items }: { title?: string; items: { label: string; value: string; sub?: string; tone?: Tone }[] }) {
  return (
    <section className="flex flex-col gap-2">
      {title && <Overline>{title}</Overline>}
      <Card className="grid grid-cols-2 overflow-hidden sm:grid-cols-3 lg:grid-cols-4 2xl:grid-cols-6">
        {items.map((item) => (
          <div key={item.label} className="border-b border-r border-border/50 px-4 py-3.5">
            <p className="truncate text-[0.857rem] text-muted-foreground">{item.label}</p>
            <p className={cn("tabular mt-1 truncate text-[1.3rem] leading-tight font-semibold tracking-[-0.02em]", toneText(item.tone))}>{item.value}</p>
            {item.sub && <p className="truncate text-[0.786rem] text-muted-foreground">{item.sub}</p>}
          </div>
        ))}
      </Card>
    </section>
  );
}

const PAGE = 10;

function RowsSection({ title, items, empty, searchable }: { title: string; items: DashRow[]; empty?: string; searchable?: boolean }) {
  const [query, setQuery] = useState("");
  const [expanded, setExpanded] = useState(false);
  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    if (!needle) return items;
    return items.filter((item) => [item.title, item.subtitle, item.meta, item.badge?.label].some((value) => value?.toLowerCase().includes(needle)));
  }, [items, query]);
  const visible = expanded || query ? filtered : filtered.slice(0, PAGE);

  return (
    <Card className="flex flex-col">
      <div className="flex items-center justify-between gap-3 px-4 pt-4 pb-3">
        <h2 className="truncate text-[1.05rem] font-semibold">
          {title}
          <span className="data ml-2 text-[0.786rem] font-normal text-muted-foreground">{items.length}</span>
        </h2>
        {searchable && items.length > 6 && (
          <label className="relative w-44 shrink-0">
            <Search className="pointer-events-none absolute z-10 top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-muted-foreground" />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Filtrer"
              aria-label={`Filtrer ${title}`}
              className="h-8 w-full rounded-md border border-input bg-input/20 pr-2 pl-8 text-[0.857rem] outline-none focus:border-primary/70 focus:ring-2 focus:ring-primary/25"
            />
          </label>
        )}
      </div>
      {filtered.length === 0 ? (
        <p className="px-4 pb-4 text-[0.93rem] text-muted-foreground">{query ? "Aucun résultat" : (empty ?? "Rien à afficher")}</p>
      ) : (
        <ul className="divide-y divide-border/60 border-t border-border/60">
          {visible.map((item, index) => (
            <li key={`${item.title}-${index}`} className="flex flex-col gap-2 px-4 py-2.5 transition-colors hover:bg-muted/20">
              <div className="flex items-center gap-3">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-[0.93rem] font-medium" title={item.title}>
                    {item.title}
                  </p>
                  {item.subtitle && (
                    <p className="truncate text-[0.786rem] text-muted-foreground" title={item.subtitle}>
                      {item.subtitle}
                    </p>
                  )}
                </div>
                {item.gauges && (
                  <div className="hidden shrink-0 gap-3 md:flex">
                    {item.gauges.map((gauge) => (
                      <Gauge key={gauge.label} label={gauge.label} value={gauge.value} />
                    ))}
                  </div>
                )}
                <div className="flex shrink-0 flex-col items-end gap-1">
                  {item.badge && <Badge tone={item.badge.tone ?? "neutral"}>{item.badge.label}</Badge>}
                  {item.meta && <span className="data text-[0.75rem] text-muted-foreground">{item.meta}</span>}
                </div>
                {item.actions && item.actions.length > 0 && <RowActions actions={item.actions} />}
              </div>
              {item.gauges && (
                <div className="flex gap-3 md:hidden">
                  {item.gauges.map((gauge) => (
                    <Gauge key={gauge.label} label={gauge.label} value={gauge.value} />
                  ))}
                </div>
              )}
              {item.progress !== undefined && (
                <div className="flex items-center gap-2">
                  <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
                    <div className="h-full rounded-full bg-primary transition-[width] duration-500" style={{ width: `${Math.round(item.progress * 100)}%` }} />
                  </div>
                  <span className="data w-10 text-right text-[0.75rem] text-muted-foreground">{Math.round(item.progress * 100)} %</span>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
      {!query && filtered.length > PAGE && (
        <button onClick={() => setExpanded((value) => !value)} className="border-t border-border/60 px-4 py-2.5 text-[0.857rem] font-medium text-muted-foreground transition-colors hover:text-foreground">
          {expanded ? "Réduire" : `Afficher tout (${filtered.length})`}
        </button>
      )}
    </Card>
  );
}

/** Small usage gauge (CPU, RAM, disk): neutral below 75 %, warning below 90 %, then destructive. */
function Gauge({ label, value }: { label: string; value: number }) {
  const clamped = Math.max(0, Math.min(100, value));
  return (
    <div className="w-20" title={`${label} ${clamped.toFixed(1)} %`}>
      <div className="flex justify-between text-[0.72rem] text-muted-foreground">
        <span>{label}</span>
        <span className="data">{Math.round(clamped)} %</span>
      </div>
      <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-muted">
        <div
          className={cn("h-full rounded-full transition-[width] duration-500", clamped >= 90 ? "bg-destructive" : clamped >= 75 ? "bg-warning" : "bg-foreground/55")}
          style={{ width: `${clamped}%` }}
        />
      </div>
    </div>
  );
}

function RankingSection({ title, items, empty }: { title: string; items: { label: string; value: number; display?: string }[]; empty?: string }) {
  const max = Math.max(1, ...items.map((item) => item.value));
  return (
    <Card className="p-4">
      <h2 className="mb-3 text-[1.05rem] font-semibold">{title}</h2>
      {items.length === 0 ? (
        <p className="text-[0.93rem] text-muted-foreground">{empty ?? "Aucune donnée"}</p>
      ) : (
        <ol className="flex flex-col gap-1">
          {items.slice(0, 10).map((item, index) => (
            <li key={`${item.label}-${index}`} className="group relative flex items-center gap-3 overflow-hidden rounded-md px-2.5 py-1.5" title={`${item.label} : ${item.display ?? item.value}`}>
              {/* Bar sits behind the label, anchored at the start (magnitude, one hue). */}
              <span className="absolute inset-y-0 left-0 rounded-md bg-primary/14 transition-[width] duration-500 group-hover:bg-primary/22" style={{ width: `${(item.value / max) * 100}%` }} />
              <span className="relative min-w-0 flex-1 truncate text-[0.857rem]">{item.label}</span>
              <span className="data relative shrink-0 text-[0.786rem] text-muted-foreground">{item.display ?? new Intl.NumberFormat("fr-FR").format(item.value)}</span>
            </li>
          ))}
        </ol>
      )}
    </Card>
  );
}

// ---- Line chart -------------------------------------------------------------------------------

/** Two validated series colors (dataviz check, dark and light): blue then Orion orange. */
const SERIES = ["var(--series-1)", "var(--series-2)"];

function ChartSection({ title, unit, series }: { title: string; unit?: string; series: { name: string; points: [number, number][] }[] }) {
  // More than two series: small multiples, one series each (no generated hues).
  if (series.length > 2) {
    return (
      <section className="flex flex-col gap-2">
        <Overline>{title}</Overline>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          {series.map((single) => (
            <LineChart key={single.name} title={single.name} unit={unit} series={[single]} compact />
          ))}
        </div>
      </section>
    );
  }
  return <LineChart title={title} unit={unit} series={series} />;
}

function LineChart({ title, unit = "", series, compact = false }: { title: string; unit?: string; series: { name: string; points: [number, number][] }[]; compact?: boolean }) {
  const ref = useRef<SVGSVGElement>(null);
  const box = useRef<HTMLDivElement>(null);
  const [hover, setHover] = useState<number | null>(null);
  // Drawn at the container's real pixel width, so text and strokes keep their size.
  const [width, setWidth] = useState(800);
  useEffect(() => {
    const element = box.current;
    if (!element) return;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.max(240, Math.round(entry.contentRect.width))));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  const height = compact ? 150 : 220;
  const pad = { top: 12, right: 12, bottom: 22, left: 44 };
  const all = series.flatMap((s) => s.points);
  const times = all.map(([t]) => t);
  const minT = Math.min(...times);
  const maxT = Math.max(...times);
  const maxV = niceMax(Math.max(1, ...all.map(([, v]) => v)));
  const x = (t: number) => pad.left + ((t - minT) / Math.max(1, maxT - minT)) * (width - pad.left - pad.right);
  const y = (v: number) => pad.top + (1 - v / maxV) * (height - pad.top - pad.bottom);
  const base = series[0]?.points ?? [];
  const format = (v: number) => `${new Intl.NumberFormat("fr-FR", { maximumFractionDigits: v < 10 ? 1 : 0 }).format(v)}${unit}`;
  const timeLabel = (t: number) =>
    new Date(t).toLocaleString("fr-FR", maxT - minT > 3 * 86400_000 ? { day: "numeric", month: "short" } : { hour: "2-digit", minute: "2-digit" });

  const onMove = (event: React.PointerEvent<SVGSVGElement>) => {
    const svg = ref.current;
    if (!svg || base.length === 0) return;
    const rect = svg.getBoundingClientRect();
    const t = minT + ((event.clientX - rect.left - pad.left) / (width - pad.left - pad.right)) * (maxT - minT);
    let nearest = 0;
    base.forEach(([time], index) => {
      if (Math.abs(time - t) < Math.abs(base[nearest][0] - t)) nearest = index;
    });
    setHover(nearest);
  };

  const ticks = [0, 0.5, 1].map((ratio) => maxV * ratio);
  const hovered = hover !== null ? base[hover] : null;

  return (
    <Card className="p-4">
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-[1.05rem] font-semibold">{title}</h2>
        {series.length > 1 && (
          <div className="flex items-center gap-3 text-[0.786rem] text-muted-foreground">
            {series.map((s, index) => (
              <span key={s.name} className="inline-flex items-center gap-1.5">
                <span className="h-0.5 w-3.5 rounded-full" style={{ backgroundColor: SERIES[index] }} />
                {s.name}
              </span>
            ))}
          </div>
        )}
      </div>
      <div ref={box} className="relative">
        <svg
          ref={ref}
          viewBox={`0 0 ${width} ${height}`}
          className="block touch-none select-none"
          width={width}
          height={height}
          role="img"
          aria-label={`${title} : ${series.map((s) => `${s.name}, dernière valeur ${format(s.points.at(-1)?.[1] ?? 0)}`).join(" ; ")}`}
          onPointerMove={onMove}
          onPointerLeave={() => setHover(null)}
        >
          {ticks.map((tick) => (
            <g key={tick}>
              <line x1={pad.left} x2={width - pad.right} y1={y(tick)} y2={y(tick)} stroke="var(--border)" strokeWidth={1} />
              <text x={pad.left - 8} y={y(tick) + 4} textAnchor="end" className="fill-muted-foreground" fontSize={11}>
                {format(tick)}
              </text>
            </g>
          ))}
          <text x={pad.left} y={height - 4} className="fill-muted-foreground" fontSize={11}>
            {timeLabel(minT)}
          </text>
          <text x={width - pad.right} y={height - 4} textAnchor="end" className="fill-muted-foreground" fontSize={11}>
            {timeLabel(maxT)}
          </text>
          {series.map((s, index) => {
            const color = series.length === 1 ? "var(--primary)" : SERIES[index];
            const d = s.points.map(([t, v], i) => `${i ? "L" : "M"}${x(t).toFixed(1)},${y(v).toFixed(1)}`).join("");
            return (
              <g key={s.name}>
                {series.length === 1 && <path d={`${d}L${x(s.points.at(-1)![0])},${y(0)}L${x(s.points[0][0])},${y(0)}Z`} fill={color} opacity={0.1} />}
                <path d={d} fill="none" stroke={color} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
              </g>
            );
          })}
          {hovered && (
            <g>
              <line x1={x(hovered[0])} x2={x(hovered[0])} y1={pad.top} y2={height - pad.bottom} stroke="var(--muted-foreground)" strokeDasharray="3 3" strokeWidth={1} />
              {series.map((s, index) => {
                const point = s.points[hover!];
                return point ? <circle key={s.name} cx={x(point[0])} cy={y(point[1])} r={4.5} fill={series.length === 1 ? "var(--primary)" : SERIES[index]} stroke="var(--background)" strokeWidth={2} /> : null;
              })}
            </g>
          )}
        </svg>
        {hovered && (
          <div
            className="pointer-events-none absolute top-1 z-10 rounded-md border border-border bg-popover/95 px-2.5 py-1.5 text-[0.786rem] shadow-md backdrop-blur-md"
            style={{ left: `${(x(hovered[0]) / width) * 100}%`, transform: x(hovered[0]) > width * 0.6 ? "translateX(calc(-100% - 10px))" : "translateX(10px)" }}
          >
            <p className="text-muted-foreground">{new Date(hovered[0]).toLocaleString("fr-FR", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })}</p>
            {series.map((s, index) => (
              <p key={s.name} className="flex items-center gap-1.5">
                <span className="size-2 rounded-full" style={{ backgroundColor: series.length === 1 ? "var(--primary)" : SERIES[index] }} />
                <span className="text-muted-foreground">{s.name}</span>
                <span className="data ml-auto pl-3 font-medium">{format(s.points[hover!]?.[1] ?? 0)}</span>
              </p>
            ))}
          </div>
        )}
      </div>
    </Card>
  );
}

function niceMax(value: number) {
  const exponent = Math.pow(10, Math.floor(Math.log10(value)));
  const fraction = value / exponent;
  return (fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10) * exponent;
}

function DashboardSkeleton() {
  return (
    <div className="flex flex-col gap-4">
      <Card className="grid grid-cols-2 gap-4 p-4 sm:grid-cols-4">
        {Array.from({ length: 4 }, (_, index) => (
          <div key={index} className="flex flex-col gap-2">
            <span className="skeleton block h-3 w-20" />
            <span className="skeleton block h-6 w-14" />
          </div>
        ))}
      </Card>
      <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        {Array.from({ length: 2 }, (_, index) => (
          <Card key={index} className="flex flex-col gap-3 p-4">
            <span className="skeleton block h-4 w-32" />
            {Array.from({ length: 5 }, (_, row) => (
              <span key={row} className="skeleton block h-8 w-full" />
            ))}
          </Card>
        ))}
      </div>
    </div>
  );
}
