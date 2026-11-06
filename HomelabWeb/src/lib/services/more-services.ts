import "server-only";
import { createHash, randomBytes } from "node:crypto";
import { Agent, WebSocket as UndiciWebSocket } from "undici";
import type { ServiceWithSecrets } from "../db";
import { registerActions, ActionError, type ActionRunner } from "./actions";
import type { DashSection, ServiceDashboard } from "./dashboards";
import { baseUrls, rawFetch, request, requestAt, ServiceHttpError, type RequestOptions } from "./http";
import { asArray, asObject, firstNonBlank, fmt, get, json, num, registerSummaryAdapters, str, tryGet, type Json, type ServiceSummary } from "./summaries";

// The other services of the app (HomeViewModel summaries, their dashboard screens and actions):
// AdGuard Home, Technitium, Nginx Proxy Manager, Healthchecks, Proxmox, Plex, Jellystat, Wakapi,
// PatchMon, Linux Update, Crafty, Pterodactyl, Calagopus, Dockhand, DockMon, Komodo, Maltrail,
// Pangolin, TrueNAS.

type Metric = Omit<ServiceSummary, "status" | "latencyMs" | "error" | "checkedAt">;
type Builder = (service: ServiceWithSecrets) => Promise<ServiceDashboard>;
type Tone = "success" | "warning" | "destructive" | "info";

const sections = (...items: (DashSection | null | false | undefined)[]) => items.filter((item): item is DashSection => Boolean(item));
const pct = (value: number) => `${value.toFixed(value < 10 ? 1 : 0)} %`;
const bytes = (value: number) => {
  const units = ["o", "Ko", "Mo", "Go", "To", "Po"];
  let size = value;
  let unit = 0;
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024;
    unit++;
  }
  return `${size.toFixed(size < 10 && unit > 0 ? 1 : 0)} ${units[unit]}`;
};
const duration = (seconds: number) => {
  if (!Number.isFinite(seconds) || seconds <= 0) return "—";
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  return days > 0 ? `${days} j ${hours} h` : hours > 0 ? `${hours} h ${minutes} min` : `${minutes} min`;
};
/** Same as the app's formatHours. */
const hoursLabel = (value: number) => {
  if (value > 0 && value < 1) {
    const minutes = Math.floor(value * 60);
    return minutes <= 0 ? "<1m" : `${minutes}m`;
  }
  return value >= 100 ? `${value.toFixed(0)}h` : value >= 10 ? `${value.toFixed(1)}h` : `${value.toFixed(2)}h`;
};
const date = (value: unknown) => {
  const raw = str(value);
  if (!raw) return undefined;
  const parsed = new Date(/^\d+$/.test(raw) ? Number(raw) * (raw.length <= 10 ? 1000 : 1) : raw);
  return Number.isNaN(parsed.getTime()) ? raw : parsed.toLocaleString("fr-FR", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
};
const ranking = (rows: unknown): { label: string; value: number }[] =>
  asArray(rows).flatMap((row) => {
    const entry = asObject(row);
    if ("name" in entry || "domain" in entry) return [{ label: str(entry.name ?? entry.domain), value: num(entry.hits ?? entry.count) }];
    return Object.entries(entry).map(([label, value]) => ({ label, value: num(value) }));
  });
const postJson = (s: ServiceWithSecrets, path: string, body: unknown, options: RequestOptions = {}) =>
  request(s, path, { ...options, method: "POST", headers: { "Content-Type": "application/json", ...options.headers }, body: JSON.stringify(body) });

const sessions = new Map<string, string>();

/** Runs `call` with a cached session; on 401/403 logs in again once. */
async function withSession<T>(s: ServiceWithSecrets, login: (base: string) => Promise<string>, call: (base: string, session: string) => Promise<T>, initial?: string): Promise<T> {
  let lastError: unknown = null;
  for (const base of baseUrls(s)) {
    for (const force of [false, true]) {
      try {
        let session = force ? "" : (sessions.get(s.id) ?? initial ?? "");
        if (!session) {
          session = await login(base);
          sessions.set(s.id, session);
        }
        return await call(base, session);
      } catch (error) {
        lastError = error;
        const authError = error instanceof ServiceHttpError && (error.status === 401 || error.status === 403 || error.status === 400);
        if (!authError || force) break;
        sessions.delete(s.id);
      }
    }
  }
  throw lastError ?? new Error("unreachable");
}
const needCredentials = (s: ServiceWithSecrets) => {
  if (!s.secrets.username || !s.secrets.password) throw new ServiceHttpError(401, "credentials missing");
  return { username: s.secrets.username, password: s.secrets.password };
};

// ---- AdGuard Home ---------------------------------------------------------------------------

const adguardDashboard: Builder = async (s) => {
  const [status, stats, filtering] = await Promise.all([get(s, "/control/status"), get(s, "/control/stats"), tryGet(s, "/control/filtering/status")]);
  const st = asObject(status);
  const data = asObject(stats);
  const queries = num(data.num_dns_queries);
  const blocked = num(data.num_blocked_filtering);
  const hourly = asArray(data.dns_queries).map(num);
  const blockedHourly = asArray(data.blocked_filtering).map(num);
  const now = Date.now();
  const point = (values: number[]) => values.map((value, index) => [now - (values.length - 1 - index) * 3600_000, value] as [number, number]);
  const filters = asArray(asObject(filtering).filters) as Json[];
  const enabled = st.protection_enabled === true;
  return {
    subtitle: str(st.version) ? `AdGuard Home ${str(st.version)}` : undefined,
    actions: enabled
      ? [
          { id: "protection_off", label: "Pause 5 min", params: { minutes: "5" } },
          { id: "protection_off", label: "Pause 30 min", params: { minutes: "30" } },
          { id: "protection_off", label: "Désactiver la protection", tone: "destructive", confirm: "Désactiver la protection sans limite de durée ?" },
        ]
      : [{ id: "protection_on", label: "Activer la protection", tone: "primary" }],
    sections: sections(
      {
        kind: "stats",
        items: [
          { label: "Protection", value: enabled ? "Activée" : "Désactivée", tone: enabled ? "success" : "warning" },
          { label: "Requêtes", value: fmt(queries) },
          { label: "Bloquées", value: fmt(blocked) },
          { label: "Taux de blocage", value: queries ? pct((blocked / queries) * 100) : "—" },
          { label: "Navigation sécurisée", value: fmt(num(data.num_replaced_safebrowsing)) },
          { label: "Contrôle parental", value: fmt(num(data.num_replaced_parental)) },
          { label: "Temps moyen", value: `${(num(data.avg_processing_time) * 1000).toFixed(1)} ms` },
          { label: "Listes de filtrage", value: fmt(filters.filter((filter) => filter.enabled !== false).length) },
        ],
      },
      hourly.length > 1 && {
        kind: "chart",
        title: "Requêtes par heure",
        series: [
          { name: "Total", points: point(hourly) },
          ...(blockedHourly.length === hourly.length ? [{ name: "Bloquées", points: point(blockedHourly) }] : []),
        ],
      },
      { kind: "ranking", title: "Domaines les plus demandés", items: ranking(data.top_queried_domains), empty: "Aucune donnée" },
      { kind: "ranking", title: "Domaines les plus bloqués", items: ranking(data.top_blocked_domains), empty: "Aucune donnée" },
      { kind: "ranking", title: "Clients les plus actifs", items: ranking(data.top_clients), empty: "Aucune donnée" },
      filters.length > 0 && {
        kind: "rows",
        title: "Listes de filtrage",
        items: filters.map((filter) => ({
          title: str(filter.name) || str(filter.url),
          subtitle: `${fmt(num(filter.rules_count))} règles · ${date(filter.last_updated) ?? "jamais mise à jour"}`,
          badge: filter.enabled === false ? { label: "Désactivée" } : { label: "Active", tone: "success" as const },
        })),
      }
    ),
  };
};

const adguardActions: Record<string, ActionRunner> = {
  protection_on: async (s) => (await postJson(s, "/control/protection", { enabled: true }), "Protection activée"),
  protection_off: async (s, p) => {
    const minutes = Number(p.minutes ?? 0);
    await postJson(s, "/control/protection", minutes > 0 ? { enabled: false, duration: minutes * 60_000 } : { enabled: false });
    return minutes > 0 ? `Protection en pause ${minutes} min` : "Protection désactivée";
  },
};

// ---- Technitium DNS -------------------------------------------------------------------------

async function technitium<T>(s: ServiceWithSecrets, path: string, read: (body: Json) => T): Promise<T> {
  const login = async (base: string) => {
    const { username, password } = needCredentials(s);
    const body = asObject(json(await requestAt(s, base, `/api/user/login?user=${encodeURIComponent(username)}&pass=${encodeURIComponent(password)}&includeInfo=false`, { noAuth: true })));
    if (str(body.status) !== "ok" || !str(body.token)) throw new ServiceHttpError(401, "Technitium login failed");
    return str(body.token);
  };
  const stored = s.secrets.apiKey || s.secrets.token || undefined;
  return withSession(
    s,
    login,
    async (base, token) => {
      const body = asObject(json(await requestAt(s, base, `${path}${path.includes("?") ? "&" : "?"}token=${encodeURIComponent(token)}`, { noAuth: true })));
      if (str(body.status) === "invalid-token") throw new ServiceHttpError(401, "Technitium token expired");
      if (str(body.status) !== "ok") throw new Error(str(body.errorMessage) || "Technitium error");
      return read(asObject(body.response));
    },
    stored
  );
}

const technitiumDashboard: Builder = async (s) => {
  const data = await technitium(s, "/api/dashboard/stats/get?type=LastDay&utc=true", (response) => response);
  const stats = asObject(data.stats);
  const chart = asObject(data.mainChartData);
  const labels = asArray(chart.labels).map(str);
  const series = (asArray(chart.datasets) as Json[])
    .filter((dataset) => ["Total", "Blocked"].includes(str(dataset.label)))
    .map((dataset) => ({
      name: str(dataset.label) === "Total" ? "Total" : "Bloquées",
      points: asArray(dataset.data).map((value, index) => [new Date(labels[index]).getTime(), num(value)] as [number, number]).filter(([time]) => Number.isFinite(time)),
    }));
  const total = num(stats.totalQueries);
  return {
    subtitle: "Technitium DNS",
    actions: [
      { id: "disable_blocking", label: "Pause 5 min", params: { minutes: "5" } },
      { id: "disable_blocking", label: "Pause 30 min", params: { minutes: "30" } },
      { id: "update_blocklists", label: "Mettre à jour les listes" },
    ],
    sections: sections(
      {
        kind: "stats",
        items: [
          { label: "Requêtes (24 h)", value: fmt(total) },
          { label: "Bloquées", value: fmt(num(stats.totalBlocked)) },
          { label: "Taux de blocage", value: total ? pct((num(stats.totalBlocked) / total) * 100) : "—" },
          { label: "En cache", value: fmt(num(stats.totalCached)) },
          { label: "Récursives", value: fmt(num(stats.totalRecursive)) },
          { label: "NXDOMAIN", value: fmt(num(stats.totalNxDomain)) },
          { label: "Clients", value: fmt(num(stats.totalClients)) },
          { label: "Zones", value: fmt(num(stats.zones)) },
          { label: "Entrées en cache", value: fmt(num(stats.cachedEntries)) },
          { label: "Zones bloquées", value: fmt(num(stats.blockedZones) + num(stats.blockListZones)) },
        ],
      },
      series.length > 0 && series[0].points.length > 1 && { kind: "chart", title: "Requêtes, dernières 24 h", series },
      { kind: "ranking", title: "Domaines les plus demandés", items: ranking(data.topDomains), empty: "Aucune donnée" },
      { kind: "ranking", title: "Domaines les plus bloqués", items: ranking(data.topBlockedDomains), empty: "Aucune donnée" },
      { kind: "ranking", title: "Clients les plus actifs", items: ranking(data.topClients), empty: "Aucune donnée" }
    ),
  };
};

const technitiumActions: Record<string, ActionRunner> = {
  disable_blocking: async (s, p) => {
    const minutes = Number(p.minutes ?? 5);
    await technitium(s, `/api/settings/temporaryDisableBlocking?minutes=${minutes}`, () => null);
    return `Blocage en pause ${minutes} min`;
  },
  update_blocklists: async (s) => (await technitium(s, "/api/settings/forceUpdateBlockLists", () => null), "Mise à jour des listes lancée"),
};

// ---- Nginx Proxy Manager ----------------------------------------------------------------------

async function npm<T>(s: ServiceWithSecrets, path: string, options: RequestOptions = {}): Promise<T> {
  const login = async (base: string) => {
    const { username, password } = needCredentials(s);
    const response = await rawFetch(s, `${base}/api/tokens`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ identity: username, secret: password }) });
    if (!response.ok) throw new ServiceHttpError(401, "NPM login failed");
    const text = await response.text();
    let token = "";
    try {
      token = str(asObject(JSON.parse(text)).token);
    } catch {
      // NPMplus can put the token in a cookie only.
    }
    if (!token) token = response.headers.getSetCookie().map((cookie) => /token=([^;]+)/.exec(cookie)?.[1]).find(Boolean) ?? "";
    if (!token) throw new ServiceHttpError(401, "NPM login failed");
    return token;
  };
  return withSession(
    s,
    login,
    async (base, token) => json(await requestAt(s, base, path, { ...options, noAuth: true, headers: { Authorization: `Bearer ${token}`, Cookie: `token=${token}`, ...options.headers } })) as T,
    s.secrets.token || undefined
  );
}

const npmSummary = async (s: ServiceWithSecrets): Promise<Metric> => {
  const report = asObject(await npm(s, "/api/reports/hosts"));
  const proxy = num(report.proxy);
  return { value: `${proxy}`, sub: `/ ${proxy + num(report.redirection) + num(report.stream) + num(report.dead)}`, label: "Hôtes" };
};

const npmDashboard: Builder = async (s) => {
  const [proxies, redirections, streams, certificates] = await Promise.all([
    npm<unknown>(s, "/api/nginx/proxy-hosts?expand=certificate"),
    npm<unknown>(s, "/api/nginx/redirection-hosts"),
    npm<unknown>(s, "/api/nginx/streams"),
    npm<unknown>(s, "/api/nginx/certificates"),
  ]);
  const proxyList = asArray(proxies) as Json[];
  const certs = asArray(certificates) as Json[];
  const soon = Date.now() + 14 * 86400_000;
  const expiring = certs.filter((cert) => new Date(str(cert.expires_on)).getTime() < soon);
  return {
    sections: sections(
      {
        kind: "stats",
        items: [
          { label: "Hôtes proxy", value: fmt(proxyList.length) },
          { label: "Actifs", value: fmt(proxyList.filter((host) => host.enabled !== false && host.enabled !== 0).length), tone: "success" },
          { label: "Redirections", value: fmt(asArray(redirections).length) },
          { label: "Flux", value: fmt(asArray(streams).length) },
          { label: "Certificats", value: fmt(certs.length) },
          { label: "Expirent sous 14 j", value: fmt(expiring.length), tone: expiring.length ? "warning" : undefined },
        ],
      },
      {
        kind: "rows",
        title: "Hôtes proxy",
        searchable: true,
        items: proxyList.map((host) => {
          const enabled = host.enabled !== false && host.enabled !== 0;
          const cert = asObject(host.certificate);
          return {
            title: asArray(host.domain_names).map(str).join(", "),
            subtitle: `${str(host.forward_scheme)}://${str(host.forward_host)}:${num(host.forward_port)}${str(cert.nice_name) ? ` · SSL ${str(cert.nice_name)}` : ""}`,
            badge: enabled ? { label: "Actif", tone: "success" as const } : { label: "Désactivé" },
            actions: [
              enabled
                ? { id: "host_disable", label: "Désactiver", tone: "destructive" as const, params: { id: str(host.id) }, confirm: "Désactiver cet hôte proxy ?" }
                : { id: "host_enable", label: "Activer", tone: "primary" as const, params: { id: str(host.id) } },
            ],
          };
        }),
      },
      {
        kind: "rows",
        title: "Certificats",
        items: certs
          .sort((a, b) => str(a.expires_on).localeCompare(str(b.expires_on)))
          .map((cert) => {
            const expires = new Date(str(cert.expires_on)).getTime();
            const days = Math.round((expires - Date.now()) / 86400_000);
            return {
              title: str(cert.nice_name) || asArray(cert.domain_names).map(str).join(", "),
              subtitle: `${str(cert.provider)} · expire le ${new Date(expires).toLocaleDateString("fr-FR")}`,
              badge: days < 0 ? { label: "Expiré", tone: "destructive" as const } : days < 14 ? { label: `${days} j`, tone: "warning" as const } : { label: `${days} j`, tone: "success" as const },
              actions: str(cert.provider) === "letsencrypt" ? [{ id: "cert_renew", label: "Renouveler", params: { id: str(cert.id) } }] : undefined,
            };
          }),
      }
    ),
  };
};

const npmActions: Record<string, ActionRunner> = {
  host_enable: async (s, p) => (await npm(s, `/api/nginx/proxy-hosts/${encodeURIComponent(p.id)}/enable`, { method: "POST" }), "Hôte activé"),
  host_disable: async (s, p) => (await npm(s, `/api/nginx/proxy-hosts/${encodeURIComponent(p.id)}/disable`, { method: "POST" }), "Hôte désactivé"),
  cert_renew: async (s, p) => (await npm(s, `/api/nginx/certificates/${encodeURIComponent(p.id)}/renew`, { method: "POST" }), "Renouvellement lancé"),
};

// ---- Healthchecks -----------------------------------------------------------------------------

const HC_STATUS: Record<string, { label: string; tone?: Tone }> = {
  up: { label: "OK", tone: "success" },
  grace: { label: "Délai de grâce", tone: "warning" },
  down: { label: "En échec", tone: "destructive" },
  paused: { label: "En pause" },
  new: { label: "Nouveau", tone: "info" },
  started: { label: "Démarré", tone: "info" },
};

const healthchecksDashboard: Builder = async (s) => {
  const checks = asArray(asObject(await get(s, "/api/v3/checks/")).checks) as Json[];
  const count = (status: string) => checks.filter((check) => check.status === status).length;
  return {
    sections: [
      {
        kind: "stats",
        items: [
          { label: "Vérifications", value: fmt(checks.length) },
          { label: "OK", value: fmt(count("up") + count("grace")), tone: "success" },
          { label: "En échec", value: fmt(count("down")), tone: count("down") ? "destructive" : undefined },
          { label: "En pause", value: fmt(count("paused")) },
        ],
      },
      {
        kind: "rows",
        title: "Vérifications",
        searchable: true,
        items: checks
          .sort((a, b) => Number(b.status === "down") - Number(a.status === "down") || str(a.name).localeCompare(str(b.name)))
          .map((check) => {
            const id = str(check.uuid) || str(check.unique_key);
            return {
              title: str(check.name) || "Sans nom",
              subtitle: [str(check.desc), str(check.schedule) || (num(check.timeout) ? `toutes les ${duration(num(check.timeout))}` : "")].filter(Boolean).join(" · "),
              meta: check.last_ping ? `dernier ping ${date(check.last_ping)}` : "jamais pingé",
              badge: HC_STATUS[str(check.status)] ?? { label: str(check.status) },
              actions: id
                ? [check.status === "paused" ? { id: "resume", label: "Reprendre", tone: "primary" as const, params: { id } } : { id: "pause", label: "Mettre en pause", params: { id } }]
                : undefined,
            };
          }),
      },
    ],
  };
};

const healthchecksActions: Record<string, ActionRunner> = {
  pause: async (s, p) => (await request(s, `/api/v3/checks/${encodeURIComponent(p.id)}/pause`, { method: "POST" }), "Vérification en pause"),
  resume: async (s, p) => (await request(s, `/api/v3/checks/${encodeURIComponent(p.id)}/resume`, { method: "POST" }), "Vérification reprise"),
};

// ---- Proxmox ----------------------------------------------------------------------------------

async function proxmox(s: ServiceWithSecrets, path: string, options: RequestOptions = {}): Promise<unknown> {
  if (s.secrets.apiKey) return asObject(json(await request(s, path, options))).data;
  // Password login: ticket cookie + CSRF token, renewed when rejected (the app's interceptor does the same).
  const login = async (base: string) => {
    const { username, password } = needCredentials(s);
    const user = username.includes("@") ? username : `${username}@pam`;
    const response = await requestAt(s, base, "/api2/json/access/ticket", {
      method: "POST",
      noAuth: true,
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ username: user, password }).toString(),
    });
    const data = asObject(asObject(json(response)).data);
    if (!str(data.ticket)) throw new ServiceHttpError(401, "Proxmox login failed");
    return `${str(data.ticket)}\n${str(data.CSRFPreventionToken)}`;
  };
  return withSession(s, login, async (base, session) => {
    const [ticket, csrf] = session.split("\n");
    const response = await requestAt(s, base, path, { ...options, noAuth: true, headers: { Cookie: `PVEAuthCookie=${ticket}`, ...(options.method && options.method !== "GET" ? { CSRFPreventionToken: csrf } : {}), ...options.headers } });
    return asObject(json(response)).data;
  });
}

const proxmoxSummary = async (s: ServiceWithSecrets): Promise<Metric> => {
  const nodes = asArray(await proxmox(s, "/api2/json/nodes")) as Json[];
  let running = 0;
  let total = 0;
  for (const node of nodes.filter((n) => n.status === "online")) {
    const guests = [...(asArray(await proxmox(s, `/api2/json/nodes/${str(node.node)}/qemu`).catch(() => [])) as Json[]), ...(asArray(await proxmox(s, `/api2/json/nodes/${str(node.node)}/lxc`).catch(() => [])) as Json[])];
    total += guests.length;
    running += guests.filter((guest) => guest.status === "running").length;
  }
  return { value: `${running}`, sub: `/ ${total}`, label: "Invités actifs" };
};

const proxmoxDashboard: Builder = async (s) => {
  const nodes = asArray(await proxmox(s, "/api2/json/nodes")) as Json[];
  const version = asObject(await proxmox(s, "/api2/json/version").catch(() => ({})));
  const guests: (Json & { __node: string; __kind: string })[] = [];
  const storages: Json[] = [];
  for (const node of nodes.filter((n) => n.status === "online")) {
    const name = str(node.node);
    for (const kind of ["qemu", "lxc"]) {
      for (const guest of asArray(await proxmox(s, `/api2/json/nodes/${name}/${kind}`).catch(() => [])) as Json[]) guests.push({ ...guest, __node: name, __kind: kind });
    }
    for (const storage of asArray(await proxmox(s, `/api2/json/nodes/${name}/storage`).catch(() => [])) as Json[]) storages.push({ ...storage, __node: name });
  }
  const running = guests.filter((guest) => guest.status === "running").length;
  return {
    subtitle: str(version.version) ? `Proxmox VE ${str(version.version)}` : undefined,
    sections: sections(
      {
        kind: "stats",
        items: [
          { label: "Nœuds", value: `${nodes.filter((n) => n.status === "online").length}/${nodes.length}` },
          { label: "VM", value: fmt(guests.filter((guest) => guest.__kind === "qemu").length) },
          { label: "Conteneurs LXC", value: fmt(guests.filter((guest) => guest.__kind === "lxc").length) },
          { label: "Invités actifs", value: fmt(running), tone: "success" },
        ],
      },
      {
        kind: "rows",
        title: "Nœuds",
        items: nodes.map((node) => {
          const online = node.status === "online";
          return {
            title: str(node.node),
            subtitle: online ? `uptime ${duration(num(node.uptime))} · ${bytes(num(node.mem))} / ${bytes(num(node.maxmem))}` : undefined,
            badge: online ? { label: "En ligne", tone: "success" as const } : { label: "Hors ligne", tone: "destructive" as const },
            gauges: online
              ? [
                  { label: "CPU", value: num(node.cpu) * 100 },
                  { label: "RAM", value: num(node.maxmem) ? (num(node.mem) / num(node.maxmem)) * 100 : 0 },
                  { label: "Disque", value: num(node.maxdisk) ? (num(node.disk) / num(node.maxdisk)) * 100 : 0 },
                ]
              : undefined,
          };
        }),
      },
      {
        kind: "rows",
        title: "Machines virtuelles et conteneurs",
        searchable: true,
        items: guests
          .sort((a, b) => Number(b.status === "running") - Number(a.status === "running") || num(a.vmid) - num(b.vmid))
          .map((guest) => {
            const up = guest.status === "running";
            const params = { node: guest.__node, kind: guest.__kind, vmid: str(guest.vmid) };
            return {
              title: `${num(guest.vmid)} · ${str(guest.name) || "sans nom"}`,
              subtitle: [guest.__kind === "lxc" ? "LXC" : "VM", guest.__node, up ? `uptime ${duration(num(guest.uptime))}` : null, num(guest.maxmem) ? `${bytes(num(guest.mem))} / ${bytes(num(guest.maxmem))}` : null].filter(Boolean).join(" · "),
              badge: up ? { label: "Actif", tone: "success" as const } : guest.status === "paused" ? { label: "En pause", tone: "warning" as const } : { label: "Arrêté" },
              gauges: up ? [{ label: "CPU", value: num(guest.cpu) * 100 }, { label: "RAM", value: num(guest.maxmem) ? (num(guest.mem) / num(guest.maxmem)) * 100 : 0 }] : undefined,
              actions: up
                ? [
                    { id: "shutdown", label: "Éteindre", params, confirm: `Éteindre ${str(guest.name)} proprement ?` },
                    { id: "reboot", label: "Redémarrer", params, confirm: `Redémarrer ${str(guest.name)} ?` },
                    { id: "stop", label: "Forcer l'arrêt", tone: "destructive" as const, params, confirm: `Forcer l'arrêt de ${str(guest.name)} ? Équivaut à couper le courant.` },
                  ]
                : [{ id: "start", label: "Démarrer", tone: "primary" as const, params }],
            };
          }),
      },
      storages.length > 0 && {
        kind: "rows",
        title: "Stockages",
        items: storages
          .filter((storage) => storage.active !== 0)
          .map((storage) => ({
            title: `${str(storage.storage)} (${str(storage.__node)})`,
            subtitle: `${str(storage.type)} · ${bytes(num(storage.used))} / ${bytes(num(storage.total))}`,
            progress: num(storage.total) ? num(storage.used) / num(storage.total) : undefined,
          })),
      }
    ),
  };
};

const proxmoxPower = (verb: string, message: string): ActionRunner => async (s, p) => {
  if (!["qemu", "lxc"].includes(p.kind)) throw new ActionError("Type d'invité inconnu");
  await proxmox(s, `/api2/json/nodes/${encodeURIComponent(p.node)}/${p.kind}/${encodeURIComponent(p.vmid)}/status/${verb}`, { method: "POST" });
  return message;
};
const proxmoxActions: Record<string, ActionRunner> = {
  start: proxmoxPower("start", "Démarrage demandé"),
  shutdown: proxmoxPower("shutdown", "Extinction demandée"),
  reboot: proxmoxPower("reboot", "Redémarrage demandé"),
  stop: proxmoxPower("stop", "Arrêt forcé demandé"),
};

// ---- Plex -------------------------------------------------------------------------------------

const plexDashboard: Builder = async (s) => {
  const [identity, sectionsRaw, sessionsRaw, recentRaw] = await Promise.all([
    tryGet(s, "/identity"),
    get(s, "/library/sections"),
    tryGet(s, "/status/sessions"),
    tryGet(s, "/library/recentlyAdded?X-Plex-Container-Start=0&X-Plex-Container-Size=15"),
  ]);
  const libraries = asArray(asObject(asObject(sectionsRaw).MediaContainer).Directory) as Json[];
  const sizes = await Promise.all(libraries.map(async (library) => num(asObject(asObject(await tryGet(s, `/library/sections/${str(library.key)}/all?X-Plex-Container-Start=0&X-Plex-Container-Size=0`)).MediaContainer).totalSize)));
  const playing = asArray(asObject(asObject(sessionsRaw).MediaContainer).Metadata) as Json[];
  const recent = asArray(asObject(asObject(recentRaw).MediaContainer).Metadata) as Json[];
  const title = (item: Json) => (str(item.grandparentTitle) ? `${str(item.grandparentTitle)} · ${str(item.title)}` : str(item.title));
  return {
    subtitle: str(asObject(asObject(identity).MediaContainer).version) ? `Plex ${str(asObject(asObject(identity).MediaContainer).version)}` : undefined,
    sections: sections(
      {
        kind: "stats",
        items: [
          { label: "Éléments totaux", value: fmt(sizes.reduce((a, b) => a + b, 0)) },
          { label: "Bibliothèques", value: fmt(libraries.length) },
          { label: "Lectures en cours", value: fmt(playing.length), tone: playing.length ? "info" : undefined },
        ],
      },
      {
        kind: "rows",
        title: "Lectures en cours",
        empty: "Personne ne regarde en ce moment",
        items: playing.map((item) => ({
          title: title(item),
          subtitle: [str(asObject(item.User).title), str(asObject(item.Player).product), str(asObject(item.Player).state)].filter(Boolean).join(" · "),
          progress: num(item.duration) ? num(item.viewOffset) / num(item.duration) : undefined,
          badge: str(asObject(item.Session).location) === "lan" ? { label: "Local" } : { label: "Distant", tone: "info" as const },
        })),
      },
      { kind: "ranking", title: "Bibliothèques", items: libraries.map((library, index) => ({ label: str(library.title), value: sizes[index] })) },
      {
        kind: "rows",
        title: "Ajouts récents",
        items: recent.map((item) => ({ title: title(item), subtitle: [str(item.type) === "movie" ? "Film" : str(item.type) === "episode" ? "Épisode" : str(item.type), num(item.year) ? String(num(item.year)) : null].filter(Boolean).join(" · "), meta: date(item.addedAt) })),
      }
    ),
  };
};

// ---- Jellystat --------------------------------------------------------------------------------

async function jellystatViews(s: ServiceWithSecrets, days: number) {
  const raw = asObject(await get(s, `/stats/getViewsOverTime?days=${days}`));
  return (asArray(raw.stats) as Json[]).map((point) => {
    let views = 0;
    let minutes = 0;
    const perLibrary: Record<string, number> = {};
    for (const [key, value] of Object.entries(point)) {
      if (key.toLowerCase() === "key") continue;
      const metric = asObject(value);
      views += num(metric.count ?? metric.Count);
      const libraryMinutes = num(metric.duration ?? metric.Duration);
      minutes += libraryMinutes;
      perLibrary[key] = (perLibrary[key] ?? 0) + libraryMinutes;
    }
    return { day: str(point.Key ?? point.key), views, seconds: minutes * 60, perLibrary };
  });
}

const jellystatSummary = async (s: ServiceWithSecrets): Promise<Metric> => {
  const points = await jellystatViews(s, 7);
  return { value: hoursLabel(points.reduce((total, point) => total + point.seconds, 0) / 3600), label: "Temps regardé" };
};

const jellystatDashboard: Builder = async (s) => {
  const points = await jellystatViews(s, 30);
  const byType = asObject(await tryGet(s, "/stats/getViewsByLibraryType?days=30"));
  const libraries: Record<string, number> = {};
  for (const point of points) for (const [library, minutes] of Object.entries(point.perLibrary)) libraries[library] = (libraries[library] ?? 0) + minutes;
  const week = points.slice(-7);
  return {
    sections: sections(
      {
        kind: "stats",
        items: [
          { label: "Temps regardé (7 j)", value: hoursLabel(week.reduce((t, p) => t + p.seconds, 0) / 3600) },
          { label: "Temps regardé (30 j)", value: hoursLabel(points.reduce((t, p) => t + p.seconds, 0) / 3600) },
          { label: "Lectures (30 j)", value: fmt(points.reduce((t, p) => t + p.views, 0)) },
          { label: "Jours actifs", value: fmt(points.filter((p) => p.views > 0 || p.seconds > 0).length) },
          ...(byType.Movie !== undefined ? [{ label: "Films", value: fmt(num(byType.Movie)) }] : []),
          ...(byType.Series !== undefined ? [{ label: "Séries", value: fmt(num(byType.Series)) }] : []),
          ...(byType.Audio !== undefined ? [{ label: "Musique", value: fmt(num(byType.Audio)) }] : []),
        ],
      },
      points.length > 1 && {
        kind: "chart",
        title: "Heures regardées par jour",
        unit: " h",
        series: [{ name: "Heures", points: points.map((p) => [new Date(p.day).getTime(), p.seconds / 3600] as [number, number]).filter(([t]) => Number.isFinite(t)) }],
      },
      { kind: "ranking", title: "Bibliothèques (30 j)", items: Object.entries(libraries).map(([label, minutes]) => ({ label, value: minutes, display: hoursLabel(minutes / 60) })) }
    ),
  };
};

// ---- Wakapi -----------------------------------------------------------------------------------

async function wakapiSummary(s: ServiceWithSecrets, interval: string) {
  for (const path of [`/api/summary?interval=${interval}`, `/summary?interval=${interval}`]) {
    const data = await tryGet(s, path);
    if (data) return asObject(data);
  }
  throw new Error("Wakapi summary unavailable");
}
const wakapiSeconds = (summary: Json) => {
  const grand = asObject(summary.grand_total);
  if (grand.total_seconds !== undefined) return num(grand.total_seconds);
  return (asArray(summary.projects) as Json[]).reduce((total, item) => total + num(item.total ?? item.total_seconds), 0);
};
const wakapiItems = (summary: Json, key: string) =>
  (asArray(summary[key]) as Json[]).map((item) => ({ label: str(item.key ?? item.name), value: num(item.total ?? item.total_seconds), display: hoursLabel(num(item.total ?? item.total_seconds) / 3600) })).sort((a, b) => b.value - a.value);

const wakapiDashboard: Builder = async (s) => {
  const [today, week] = await Promise.all([wakapiSummary(s, "today"), wakapiSummary(s, "last_7_days").catch(() => ({}) as Json)]);
  return {
    sections: [
      {
        kind: "stats",
        items: [
          { label: "Aujourd'hui", value: hoursLabel(wakapiSeconds(today) / 3600) },
          { label: "7 derniers jours", value: hoursLabel(wakapiSeconds(week) / 3600) },
          { label: "Projets (7 j)", value: fmt(asArray(week.projects).length) },
        ],
      },
      { kind: "ranking", title: "Projets, 7 jours", items: wakapiItems(week, "projects"), empty: "Aucune activité" },
      { kind: "ranking", title: "Langages, 7 jours", items: wakapiItems(week, "languages"), empty: "Aucune activité" },
      { kind: "ranking", title: "Éditeurs, 7 jours", items: wakapiItems(week, "editors"), empty: "Aucune activité" },
    ],
  };
};

// ---- PatchMon, Linux Update ------------------------------------------------------------------

const patchmonDashboard: Builder = async (s) => {
  const hosts = asArray(asObject(await get(s, "/api/v1/api/hosts")).hosts) as Json[];
  const active = hosts.filter((host) => str(host.status).toLowerCase() === "active").length;
  const updates = hosts.reduce((total, host) => total + num(host.updates_count ?? host.outdated_packages ?? asObject(host.stats).outdated_packages), 0);
  return {
    sections: [
      {
        kind: "stats",
        items: [
          { label: "Hôtes", value: fmt(hosts.length) },
          { label: "Actifs", value: fmt(active), tone: "success" },
          { label: "Paquets à mettre à jour", value: fmt(updates), tone: updates ? "warning" : undefined },
        ],
      },
      {
        kind: "rows",
        title: "Hôtes",
        searchable: true,
        items: hosts.map((host) => ({
          title: firstNonBlank(host.friendly_name, host.hostname, host.name) ?? "Hôte",
          subtitle: [str(host.ip), firstNonBlank(host.os_type, host.os) ?? "", str(host.os_version)].filter(Boolean).join(" · "),
          meta: host.last_update ? date(host.last_update) : undefined,
          badge: str(host.status).toLowerCase() === "active" ? { label: "Actif", tone: "success" as const } : { label: str(host.status) || "—", tone: "warning" as const },
        })),
      },
    ],
  };
};

const linuxUpdateDashboard: Builder = async (s) => {
  const [stats, systems] = await Promise.all([get(s, "/api/dashboard/stats"), tryGet(s, "/api/dashboard/systems")]);
  const st = asObject(stats);
  const list = asArray(asObject(systems).systems ?? systems) as Json[];
  return {
    actions: [{ id: "check_all", label: "Vérifier tous les systèmes", tone: "primary" }],
    sections: [
      {
        kind: "stats",
        items: [
          { label: "Systèmes", value: fmt(num(st.total)) },
          { label: "À jour", value: fmt(num(st.upToDate)), tone: "success" },
          { label: "Mises à jour dispo", value: fmt(num(st.needsUpdates ?? st.withUpdates)), tone: num(st.needsUpdates ?? st.withUpdates) ? "warning" : undefined },
          ...(st.unreachable !== undefined ? [{ label: "Injoignables", value: fmt(num(st.unreachable)), tone: num(st.unreachable) ? ("destructive" as const) : undefined }] : []),
          ...(st.totalUpdates !== undefined ? [{ label: "Paquets", value: fmt(num(st.totalUpdates)) }] : []),
        ],
      },
      {
        kind: "rows",
        title: "Systèmes",
        searchable: true,
        items: list.map((system) => {
          const updates = num(system.updateCount ?? system.updates ?? system.pendingUpdates);
          const id = str(system.id);
          return {
            title: firstNonBlank(system.name, system.hostname) ?? "Système",
            subtitle: [str(system.hostname), firstNonBlank(system.osName, system.os) ?? "", system.lastCheck ? `vérifié ${date(system.lastCheck)}` : null].filter(Boolean).join(" · "),
            badge: system.isReachable === false ? { label: "Injoignable", tone: "destructive" as const } : updates > 0 ? { label: `${updates} mises à jour`, tone: "warning" as const } : { label: "À jour", tone: "success" as const },
            actions: id
              ? [
                  { id: "check", label: "Vérifier", params: { id } },
                  ...(updates > 0 ? [{ id: "upgrade", label: "Mettre à jour", tone: "primary" as const, params: { id }, confirm: "Installer les mises à jour sur ce système ?" }] : []),
                  { id: "reboot", label: "Redémarrer", tone: "destructive" as const, params: { id }, confirm: "Redémarrer ce système ?" },
                ]
              : undefined,
          };
        }),
      },
    ],
  };
};

const linuxUpdateActions: Record<string, ActionRunner> = {
  check_all: async (s) => (await request(s, "/api/systems/check-all", { method: "POST" }), "Vérification lancée"),
  check: async (s, p) => (await request(s, `/api/systems/${encodeURIComponent(p.id)}/check`, { method: "POST" }), "Vérification lancée"),
  upgrade: async (s, p) => (await request(s, `/api/systems/${encodeURIComponent(p.id)}/upgrade`, { method: "POST" }), "Mise à jour lancée"),
  reboot: async (s, p) => (await request(s, `/api/systems/${encodeURIComponent(p.id)}/reboot`, { method: "POST" }), "Redémarrage demandé"),
};

// ---- Game panels: Crafty, Pterodactyl, Calagopus -----------------------------------------------

async function crafty(s: ServiceWithSecrets, path: string, options: RequestOptions = {}): Promise<unknown> {
  const login = async (base: string) => {
    const { username, password } = needCredentials(s);
    const response = await requestAt(s, base, "/api/v2/auth/login", { method: "POST", noAuth: true, headers: { "Content-Type": "application/json" }, body: JSON.stringify({ username, password }) });
    const token = str(asObject(asObject(json(response)).data).token);
    if (!token) throw new ServiceHttpError(401, "Crafty login failed");
    return token;
  };
  return withSession(s, login, async (base, token) => asObject(json(await requestAt(s, base, path, { ...options, noAuth: true, headers: { Authorization: `Bearer ${token}`, ...options.headers } }))).data, s.secrets.token || undefined);
}

const craftyServers = async (s: ServiceWithSecrets) => {
  const servers = asArray(await crafty(s, "/api/v2/servers")) as Json[];
  const stats = await Promise.all(servers.map((server) => crafty(s, `/api/v2/servers/${str(server.server_id)}/stats`).catch(() => ({}))));
  return servers.map((server, index) => ({ server, stats: asObject(stats[index]) }));
};

const craftyDashboard: Builder = async (s) => {
  const list = await craftyServers(s);
  return {
    sections: [
      {
        kind: "stats",
        items: [
          { label: "Serveurs", value: fmt(list.length) },
          { label: "Actifs", value: fmt(list.filter((item) => item.stats.running === true).length), tone: "success" },
          { label: "Joueurs", value: fmt(list.reduce((total, item) => total + num(item.stats.online), 0)) },
        ],
      },
      {
        kind: "rows",
        title: "Serveurs",
        items: list.map(({ server, stats }) => {
          const running = stats.running === true;
          const id = str(server.server_id);
          return {
            title: str(server.server_name),
            subtitle: [str(stats.version) || str(server.type), running ? `${num(stats.online)}/${num(stats.max)} joueurs` : null, running && stats.mem ? `${str(stats.mem)}` : null].filter(Boolean).join(" · "),
            badge: running ? { label: "En ligne", tone: "success" as const } : { label: "Arrêté" },
            gauges: running ? [{ label: "CPU", value: num(stats.cpu) }, { label: "RAM", value: num(stats.mem_percent) }] : undefined,
            actions: running
              ? [
                  { id: "restart_server", label: "Redémarrer", params: { id } },
                  { id: "stop_server", label: "Arrêter", tone: "destructive" as const, params: { id }, confirm: `Arrêter ${str(server.server_name)} ?` },
                ]
              : [{ id: "start_server", label: "Démarrer", tone: "primary" as const, params: { id } }],
          };
        }),
      },
    ],
  };
};

const craftyActions: Record<string, ActionRunner> = Object.fromEntries(
  (["start_server", "stop_server", "restart_server"] as const).map((verb) => [
    verb,
    (async (s, p) => (await crafty(s, `/api/v2/servers/${encodeURIComponent(p.id)}/action/${verb}`, { method: "POST" }), verb === "start_server" ? "Serveur démarré" : verb === "stop_server" ? "Serveur arrêté" : "Serveur redémarré")) as ActionRunner,
  ])
);

/** Pterodactyl and Calagopus share the client API shape. */
function panel(kind: "pterodactyl" | "calagopus") {
  const listPath = kind === "pterodactyl" ? "/api/client" : "/api/client/servers";
  const idOf = (server: Json) => (kind === "pterodactyl" ? str(server.identifier) : str(server.uuid_short ?? server.uuidShort ?? server.identifier));
  const load = async (s: ServiceWithSecrets) => {
    const raw = asObject(await get(s, listPath));
    const servers = (asArray(raw.data ?? raw.servers ?? raw) as Json[]).map((item) => asObject(item.attributes ?? item));
    const resources = await Promise.all(
      servers.map((server) => tryGet(s, `/api/client/servers/${encodeURIComponent(idOf(server))}/resources`).then((value) => asObject(asObject(value).attributes ?? value)))
    );
    return servers.map((server, index) => {
      const res = resources[index];
      const state = str(res.current_state ?? res.state) || str(server.status);
      return { server, res, state: server.is_suspended || server.suspended ? "suspended" : server.is_installing ? "installing" : state };
    });
  };
  const summary = async (s: ServiceWithSecrets): Promise<Metric> => {
    const list = await load(s);
    return { value: `${list.filter((item) => item.state === "running").length}`, sub: `/ ${list.length}`, label: "serveurs actifs" };
  };
  const dashboard: Builder = async (s) => {
    const list = await load(s);
    return {
      sections: [
        {
          kind: "stats",
          items: [
            { label: "Serveurs", value: fmt(list.length) },
            { label: "Actifs", value: fmt(list.filter((item) => item.state === "running").length), tone: "success" },
            { label: "Suspendus", value: fmt(list.filter((item) => item.state === "suspended").length) },
          ],
        },
        {
          kind: "rows",
          title: "Serveurs",
          items: list.map(({ server, res, state }) => {
            const usage = asObject(res.resources ?? res);
            const limits = asObject(server.limits);
            const running = state === "running";
            const params = { id: idOf(server) };
            return {
              title: str(server.name),
              subtitle: [str(server.node), running ? `uptime ${duration(num(usage.uptime) / 1000)}` : null, num(usage.memory_bytes) ? bytes(num(usage.memory_bytes)) : null].filter(Boolean).join(" · "),
              badge: running ? { label: "En ligne", tone: "success" as const } : state === "starting" ? { label: "Démarrage", tone: "info" as const } : state === "suspended" ? { label: "Suspendu", tone: "warning" as const } : { label: "Arrêté" },
              gauges: running
                ? [
                    { label: "CPU", value: num(limits.cpu) ? (num(usage.cpu_absolute) / num(limits.cpu)) * 100 : num(usage.cpu_absolute) },
                    { label: "RAM", value: num(limits.memory) ? (num(usage.memory_bytes) / (num(limits.memory) * 1024 * 1024)) * 100 : 0 },
                  ]
                : undefined,
              actions:
                state === "suspended"
                  ? undefined
                  : running
                    ? [
                        { id: "restart", label: "Redémarrer", params },
                        { id: "stop", label: "Arrêter", tone: "destructive" as const, params, confirm: `Arrêter ${str(server.name)} ?` },
                        { id: "kill", label: "Tuer", tone: "destructive" as const, params, confirm: `Tuer ${str(server.name)} ? Les données non sauvegardées seront perdues.` },
                      ]
                    : [{ id: "start", label: "Démarrer", tone: "primary" as const, params }],
            };
          }),
        },
      ],
    };
  };
  const actions: Record<string, ActionRunner> = Object.fromEntries(
    (["start", "stop", "restart", "kill"] as const).map((signal) => [
      signal,
      (async (s, p) => (await postJson(s, `/api/client/servers/${encodeURIComponent(p.id)}/power`, { signal }), { start: "Démarrage demandé", stop: "Arrêt demandé", restart: "Redémarrage demandé", kill: "Serveur tué" }[signal])) as ActionRunner,
    ])
  );
  return { summary, dashboard, actions };
}
const pterodactyl = panel("pterodactyl");
const calagopus = panel("calagopus");

// ---- Docker managers: Dockhand, DockMon, Komodo ----------------------------------------------

async function dockhand(s: ServiceWithSecrets, path: string, options: RequestOptions = {}): Promise<unknown> {
  const login = async (base: string) => {
    const { username, password } = needCredentials(s);
    for (const loginPath of ["/api/auth/login", "/api/auth/local/login", "/api/login"]) {
      for (const payload of [{ username, password }, { email: username, password }]) {
        const response = await rawFetch(s, base + loginPath, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) }).catch(() => null);
        if (!response?.ok) continue;
        const cookie = response.headers.getSetCookie().map((value) => value.split(";")[0]).join("; ");
        if (cookie) return cookie;
      }
    }
    throw new ServiceHttpError(401, "Dockhand login failed");
  };
  return withSession(s, login, async (base, cookie) => json(await requestAt(s, base, path, { ...options, noAuth: true, headers: { Cookie: cookie, ...options.headers } })), s.secrets.token || undefined);
}

const dockhandLoad = async (s: ServiceWithSecrets) => {
  const [stats, containers, stacks] = await Promise.all([dockhand(s, "/api/dashboard/stats"), dockhand(s, "/api/containers?all=true"), dockhand(s, "/api/stacks").catch(() => [])]);
  return { stats: asObject(asObject(stats).stats ?? stats), containers: asArray(asObject(containers).containers ?? containers) as Json[], stacks: asArray(asObject(stacks).stacks ?? stacks) as Json[] };
};

const dockhandDashboard: Builder = async (s) => {
  const { stats, containers, stacks } = await dockhandLoad(s);
  return {
    sections: sections(
      {
        kind: "stats",
        items: [
          { label: "Conteneurs", value: fmt(num(stats.totalContainers) || containers.length) },
          { label: "Actifs", value: fmt(num(stats.runningContainers) || containers.filter((c) => str(c.state ?? c.State) === "running").length), tone: "success" },
          ...(stats.totalImages !== undefined ? [{ label: "Images", value: fmt(num(stats.totalImages)) }] : []),
          ...(stats.totalVolumes !== undefined ? [{ label: "Volumes", value: fmt(num(stats.totalVolumes)) }] : []),
          { label: "Stacks", value: fmt(stacks.length) },
        ],
      },
      {
        kind: "rows",
        title: "Conteneurs",
        searchable: true,
        items: containers.map((container) => {
          const state = str(container.state ?? container.State);
          const id = str(container.id ?? container.Id);
          const name = str(container.name ?? asArray(container.Names)[0]).replace(/^\//, "");
          return {
            title: name,
            subtitle: [str(container.image ?? container.Image), str(container.status ?? container.Status)].filter(Boolean).join(" · "),
            badge: state === "running" ? { label: "Actif", tone: "success" as const } : { label: state === "exited" ? "Arrêté" : state || "—" },
            actions:
              state === "running"
                ? [
                    { id: "container_restart", label: "Redémarrer", params: { id } },
                    { id: "container_stop", label: "Arrêter", tone: "destructive" as const, params: { id }, confirm: `Arrêter ${name} ?` },
                  ]
                : [{ id: "container_start", label: "Démarrer", tone: "primary" as const, params: { id } }],
          };
        }),
      },
      stacks.length > 0 && {
        kind: "rows",
        title: "Stacks",
        items: stacks.map((stack) => {
          const name = str(stack.name);
          const running = /running|up/i.test(str(stack.status));
          return {
            title: name,
            subtitle: str(stack.status),
            actions: running
              ? [
                  { id: "stack_restart", label: "Redémarrer", params: { name } },
                  { id: "stack_stop", label: "Arrêter", tone: "destructive" as const, params: { name }, confirm: `Arrêter la stack ${name} ?` },
                ]
              : [{ id: "stack_start", label: "Démarrer", tone: "primary" as const, params: { name } }],
          };
        }),
      }
    ),
  };
};

const dockhandActions: Record<string, ActionRunner> = {
  container_start: async (s, p) => (await dockhand(s, `/api/containers/${encodeURIComponent(p.id)}/start`, { method: "POST" }), "Conteneur démarré"),
  container_stop: async (s, p) => (await dockhand(s, `/api/containers/${encodeURIComponent(p.id)}/stop`, { method: "POST" }), "Conteneur arrêté"),
  container_restart: async (s, p) => (await dockhand(s, `/api/containers/${encodeURIComponent(p.id)}/restart`, { method: "POST" }), "Conteneur redémarré"),
  stack_start: async (s, p) => (await dockhand(s, `/api/stacks/${encodeURIComponent(p.name)}/start`, { method: "POST" }), "Stack démarrée"),
  stack_stop: async (s, p) => (await dockhand(s, `/api/stacks/${encodeURIComponent(p.name)}/stop`, { method: "POST" }), "Stack arrêtée"),
  stack_restart: async (s, p) => (await dockhand(s, `/api/stacks/${encodeURIComponent(p.name)}/restart`, { method: "POST" }), "Stack redémarrée"),
};

const dockmonLoad = async (s: ServiceWithSecrets) => {
  const [hosts, containers] = await Promise.all([tryGet(s, "/api/hosts"), get(s, "/api/containers")]);
  return { hosts: asArray(asObject(hosts).hosts ?? hosts) as Json[], containers: asArray(asObject(containers).containers ?? containers) as Json[] };
};
const dockmonDashboard: Builder = async (s) => {
  const { hosts, containers } = await dockmonLoad(s);
  const running = containers.filter((c) => str(c.state ?? c.status) === "running").length;
  return {
    sections: [
      {
        kind: "stats",
        items: [
          { label: "Hôtes", value: fmt(hosts.length) },
          { label: "Conteneurs", value: fmt(containers.length) },
          { label: "Actifs", value: fmt(running), tone: "success" },
        ],
      },
      {
        kind: "rows",
        title: "Conteneurs",
        searchable: true,
        items: containers.map((container) => {
          const id = str(container.id ?? container.short_id);
          return {
            title: str(container.name),
            subtitle: [hosts.find((host) => str(host.id) === str(container.host_id))?.name, str(container.image)].filter(Boolean).join(" · "),
            badge: str(container.state ?? container.status) === "running" ? { label: "Actif", tone: "success" as const } : { label: str(container.state ?? container.status) || "—" },
            actions: id
              ? [
                  { id: "restart", label: "Redémarrer", params: { id, host: str(container.host_id) } },
                  { id: "update", label: "Mettre à jour", params: { id, host: str(container.host_id) }, confirm: `Mettre à jour l'image de ${str(container.name)} ?` },
                ]
              : undefined,
          };
        }),
      },
    ],
  };
};
const dockmonActions: Record<string, ActionRunner> = {
  restart: async (s, p) => (await request(s, `/api/containers/${encodeURIComponent(p.id)}/restart${p.host ? `?host_id=${encodeURIComponent(p.host)}` : ""}`, { method: "POST" }), "Conteneur redémarré"),
  update: async (s, p) => (await request(s, `/api/containers/${encodeURIComponent(p.id)}/update${p.host ? `?host_id=${encodeURIComponent(p.host)}` : ""}`, { method: "POST" }), "Mise à jour lancée"),
};

const komodoRead = async (s: ServiceWithSecrets, method: string, params: object = {}) => json(await postJson(s, `/read/${method}`, params));
const komodoDashboard: Builder = async (s) => {
  const [servers, containers, stacks, version] = await Promise.all([
    komodoRead(s, "GetServersSummary"),
    komodoRead(s, "GetDockerContainersSummary"),
    komodoRead(s, "ListStacks").catch(() => []),
    komodoRead(s, "GetVersion").catch(() => ({})),
  ]);
  const srv = asObject(servers);
  const ctn = asObject(containers);
  return {
    subtitle: str(asObject(version).version) ? `Komodo ${str(asObject(version).version)}` : undefined,
    sections: [
      {
        kind: "stats",
        items: [
          { label: "Serveurs", value: `${num(srv.healthy)}/${num(srv.total)}` },
          { label: "Conteneurs actifs", value: `${num(ctn.running)}/${num(ctn.total)}`, tone: "success" },
          { label: "Arrêtés", value: fmt(num(ctn.stopped) + num(ctn.exited)) },
          { label: "Unhealthy", value: fmt(num(ctn.unhealthy)), tone: num(ctn.unhealthy) ? "destructive" : undefined },
          { label: "Stacks", value: fmt(asArray(stacks).length) },
        ],
      },
      {
        kind: "rows",
        title: "Stacks",
        items: (asArray(stacks) as Json[]).map((stack) => {
          const info = asObject(stack.info);
          const state = str(info.state);
          const id = str(stack.id ?? stack.name);
          return {
            title: str(stack.name),
            subtitle: [str(info.server_id) && `serveur ${str(info.server_id)}`, str(info.status)].filter(Boolean).join(" · "),
            badge: state === "running" ? { label: "Active", tone: "success" as const } : state === "unhealthy" ? { label: "Unhealthy", tone: "destructive" as const } : { label: state || "—" },
            actions:
              state === "running"
                ? [
                    { id: "stack_restart", label: "Redémarrer", params: { stack: id } },
                    { id: "stack_deploy", label: "Redéployer", params: { stack: id }, confirm: `Redéployer ${str(stack.name)} ?` },
                    { id: "stack_stop", label: "Arrêter", tone: "destructive" as const, params: { stack: id }, confirm: `Arrêter ${str(stack.name)} ?` },
                  ]
                : [
                    { id: "stack_start", label: "Démarrer", tone: "primary" as const, params: { stack: id } },
                    { id: "stack_deploy", label: "Déployer", params: { stack: id } },
                  ],
          };
        }),
      },
    ],
  };
};
const komodoExec = (method: string, message: string): ActionRunner => async (s, p) => (await postJson(s, `/execute/${method}`, { stack: p.stack }), message);
const komodoActions: Record<string, ActionRunner> = {
  stack_start: komodoExec("StartStack", "Stack démarrée"),
  stack_stop: komodoExec("StopStack", "Stack arrêtée"),
  stack_restart: komodoExec("RestartStack", "Stack redémarrée"),
  stack_deploy: komodoExec("DeployStack", "Déploiement lancé"),
};

// ---- Maltrail ---------------------------------------------------------------------------------

const sha256 = (value: string) => createHash("sha256").update(value, "utf8").digest("hex");

async function maltrail(s: ServiceWithSecrets, path: string): Promise<string> {
  const login = async (base: string) => {
    const { username, password } = needCredentials(s);
    const nonce = randomBytes(8).toString("hex");
    const response = await rawFetch(s, `${base}/login`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ username, nonce, hash: sha256(sha256(password) + nonce) }).toString(),
    });
    const cookie = response.headers.getSetCookie().map((value) => value.split(";")[0]).join("; ");
    if (!response.ok || !cookie) throw new ServiceHttpError(401, "Maltrail login failed");
    return cookie;
  };
  const hasCredentials = Boolean(s.secrets.username && s.secrets.password);
  if (!hasCredentials) return (await request(s, path, { headers: { Accept: "*/*" } })).text;
  return withSession(s, login, async (base, cookie) => (await requestAt(s, base, path, { noAuth: true, headers: { Cookie: cookie, Accept: "*/*" } })).text, s.secrets.token || undefined);
}

const maltrailCounts = async (s: ServiceWithSecrets) => {
  const counts = asObject(JSON.parse(await maltrail(s, "/counts")));
  return Object.entries(counts)
    .map(([day, value]) => ({ time: /^\d+$/.test(day) ? Number(day) * 1000 : new Date(day).getTime(), value: num(value) }))
    .filter((entry) => Number.isFinite(entry.time))
    .sort((a, b) => a.time - b.time);
};

const maltrailSummary = async (s: ServiceWithSecrets): Promise<Metric> => {
  const counts = await maltrailCounts(s);
  const latest = counts.at(-1);
  return { value: fmt(latest?.value ?? 0), sub: latest ? new Date(latest.time).toLocaleDateString("fr-FR", { day: "numeric", month: "short" }) : undefined, label: "Détections" };
};

const maltrailDashboard: Builder = async (s) => {
  const counts = await maltrailCounts(s);
  const latest = counts.at(-1);
  const day = latest ? new Date(latest.time).toISOString().slice(0, 10) : new Date().toISOString().slice(0, 10);
  const raw = await maltrail(s, `/events?date=${day}`).catch(() => "");
  // Events are log lines: time sensor src_ip src_port dst_ip dst_port proto trail_type trail info reference.
  const events = raw
    .split("\n")
    .map((line) => line.match(/"[^"]*"|\S+/g)?.map((part) => part.replace(/^"|"$/g, "")) ?? [])
    .filter((parts) => parts.length >= 9)
    .slice(-60)
    .reverse();
  const trails: Record<string, number> = {};
  for (const parts of events) trails[parts[8]] = (trails[parts[8]] ?? 0) + 1;
  return {
    sections: sections(
      {
        kind: "stats",
        items: [
          { label: "Détections (dernier jour)", value: fmt(latest?.value ?? 0) },
          { label: "Jours enregistrés", value: fmt(counts.length) },
          { label: "Total", value: fmt(counts.reduce((total, entry) => total + entry.value, 0)) },
        ],
      },
      counts.length > 1 && { kind: "chart", title: "Détections par jour", series: [{ name: "Détections", points: counts.slice(-30).map((entry) => [entry.time, entry.value] as [number, number]) }] },
      { kind: "ranking", title: "Menaces les plus vues", items: Object.entries(trails).map(([label, value]) => ({ label, value })), empty: "Aucune détection" },
      {
        kind: "rows",
        title: "Détections récentes",
        searchable: true,
        empty: "Aucune détection",
        items: events.map((parts) => ({ title: parts[8], subtitle: `${parts[2]} → ${parts[4]}:${parts[5]} · ${parts[6]} · ${parts[9] ?? ""}`, meta: parts[0]?.split(" ")[1] ?? parts[0], badge: { label: parts[7] || "trail", tone: "warning" as const } })),
      }
    ),
  };
};

// ---- Pangolin ---------------------------------------------------------------------------------

const pangolinOrgs = async (s: ServiceWithSecrets): Promise<string[]> => {
  if (s.secrets.username) return [s.secrets.username];
  const orgs = asArray(asObject(asObject(await get(s, "/v1/orgs")).data).orgs ?? asObject(await get(s, "/v1/orgs")).data) as Json[];
  return orgs.map((org) => str(org.orgId ?? org.id)).filter(Boolean);
};
const pangolinList = async (s: ServiceWithSecrets, org: string, kind: string, key: string) => asArray(asObject(asObject(await tryGet(s, `/v1/org/${encodeURIComponent(org)}/${kind}`)).data)[key]) as Json[];

const pangolinSummary = async (s: ServiceWithSecrets): Promise<Metric> => {
  let sites = 0;
  let clients = 0;
  for (const org of await pangolinOrgs(s)) {
    sites += (await pangolinList(s, org, "sites", "sites")).length;
    clients += (await pangolinList(s, org, "clients", "clients")).length;
  }
  return { value: `${sites}`, sub: `/ ${clients}`, label: "sites / clients" };
};

const pangolinDashboard: Builder = async (s) => {
  const out: DashSection[] = [];
  let siteCount = 0;
  let resourceCount = 0;
  let clientCount = 0;
  for (const org of await pangolinOrgs(s)) {
    const [sites, resources, clients] = await Promise.all([pangolinList(s, org, "sites", "sites"), pangolinList(s, org, "resources", "resources"), pangolinList(s, org, "clients", "clients")]);
    siteCount += sites.length;
    resourceCount += resources.length;
    clientCount += clients.length;
    out.push(
      {
        kind: "rows",
        title: `Sites · ${org}`,
        items: sites.map((site) => ({ title: str(site.name), subtitle: [str(site.type), str(site.subnet)].filter(Boolean).join(" · "), badge: site.online ? { label: "En ligne", tone: "success" as const } : { label: "Hors ligne", tone: "destructive" as const } })),
      },
      {
        kind: "rows",
        title: `Ressources · ${org}`,
        searchable: true,
        items: resources.map((resource) => ({
          title: str(resource.name),
          subtitle: [str(resource.fullDomain ?? resource.domain), str(resource.protocol)].filter(Boolean).join(" · "),
          badge: resource.enabled === false ? { label: "Désactivée" } : resource.sso ? { label: "SSO", tone: "info" as const } : { label: "Publique" },
        })),
      }
    );
  }
  return {
    sections: [
      {
        kind: "stats",
        items: [
          { label: "Sites", value: fmt(siteCount) },
          { label: "Ressources", value: fmt(resourceCount) },
          { label: "Clients", value: fmt(clientCount) },
        ],
      },
      ...out,
    ],
  };
};

// ---- TrueNAS (JSON-RPC over WebSocket, like the app) ------------------------------------------

const insecureAgent = new Agent({ connect: { rejectUnauthorized: false } });

async function truenasCall(s: ServiceWithSecrets, calls: [string, unknown[]][]): Promise<unknown[]> {
  let lastError: unknown = null;
  for (const base of baseUrls(s)) {
    for (const path of ["/api/current", "/api/v25.04"]) {
      try {
        return await new Promise<unknown[]>((resolve, reject) => {
          const url = base.replace(/^http/, "ws") + path;
          const socket = new UndiciWebSocket(url, s.allowSelfSigned ? { dispatcher: insecureAgent } : undefined);
          const results: unknown[] = [];
          let id = 0;
          const timer = setTimeout(() => (socket.close(), reject(new Error("TrueNAS timeout"))), 10_000);
          const send = (method: string, params: unknown[]) => socket.send(JSON.stringify({ jsonrpc: "2.0", id: ++id, method, params }));
          socket.onopen = () => send("auth.login_with_api_key", [s.secrets.apiKey ?? ""]);
          socket.onerror = () => (clearTimeout(timer), reject(new Error("TrueNAS websocket error")));
          socket.onmessage = (event) => {
            const message = asObject(JSON.parse(String(event.data)));
            if (message.error) {
              clearTimeout(timer);
              socket.close();
              reject(num(asObject(message.error).code) === -32001 || id === 1 ? new ServiceHttpError(401, "TrueNAS auth") : new Error(str(asObject(message.error).message)));
              return;
            }
            if (id === 1) {
              if (message.result !== true) {
                clearTimeout(timer);
                socket.close();
                reject(new ServiceHttpError(401, "TrueNAS API key refused"));
                return;
              }
            } else {
              results.push(message.result);
            }
            const next = calls[id - 1];
            if (next) send(next[0], next[1]);
            else {
              clearTimeout(timer);
              socket.close();
              resolve(results);
            }
          };
        });
      } catch (error) {
        lastError = error;
        if (error instanceof ServiceHttpError) throw error;
      }
    }
  }
  throw lastError ?? new Error("TrueNAS unreachable");
}

const truenasLoad = async (s: ServiceWithSecrets) => {
  const [info, pools, apps, alerts, vms] = await truenasCall(s, [
    ["system.info", []],
    ["pool.query", []],
    ["app.query", []],
    ["alert.list", []],
    ["vm.query", []],
  ]);
  return { info: asObject(info), pools: asArray(pools) as Json[], apps: asArray(apps) as Json[], alerts: (asArray(alerts) as Json[]).filter((alert) => !alert.dismissed), vms: asArray(vms) as Json[] };
};

const truenasSummary = async (s: ServiceWithSecrets): Promise<Metric> => {
  const [pools] = await truenasCall(s, [["pool.query", []]]);
  const list = asArray(pools) as Json[];
  return { value: `${list.filter((pool) => pool.healthy === true).length}`, sub: `/ ${list.length}`, label: "Pools sains" };
};

const truenasDashboard: Builder = async (s) => {
  const { info, pools, apps, alerts, vms } = await truenasLoad(s);
  return {
    subtitle: [str(info.version), str(info.hostname)].filter(Boolean).join(" · "),
    sections: sections(
      {
        kind: "stats",
        items: [
          { label: "Pools sains", value: `${pools.filter((pool) => pool.healthy === true).length}/${pools.length}`, tone: pools.every((pool) => pool.healthy === true) ? "success" : "warning" },
          { label: "Applications", value: `${apps.filter((app) => str(app.state) === "RUNNING").length}/${apps.length}` },
          { label: "VM", value: fmt(vms.length) },
          { label: "Alertes", value: fmt(alerts.length), tone: alerts.length ? "warning" : undefined },
          { label: "Uptime", value: duration(num(info.uptime_seconds)) },
          { label: "Mémoire", value: bytes(num(info.physmem)) },
        ],
      },
      alerts.length > 0 && { kind: "notes", title: "Alertes", tone: "warning", items: alerts.slice(0, 8).map((alert) => str(alert.formatted) || str(alert.klass)) },
      {
        kind: "rows",
        title: "Pools",
        items: pools.map((pool) => {
          const topology = asObject(pool.topology);
          void topology;
          const size = num(pool.size);
          const allocated = num(pool.allocated);
          return {
            title: str(pool.name),
            subtitle: `${str(pool.status)} · ${bytes(allocated)} / ${bytes(size)}`,
            progress: size ? allocated / size : undefined,
            badge: pool.healthy === true ? { label: "Sain", tone: "success" as const } : { label: str(pool.status) || "Dégradé", tone: "destructive" as const },
          };
        }),
      },
      apps.length > 0 && {
        kind: "rows",
        title: "Applications",
        searchable: true,
        items: apps.map((app) => ({
          title: str(app.name),
          subtitle: [str(asObject(app.metadata).title), str(app.version)].filter(Boolean).join(" · "),
          badge: str(app.state) === "RUNNING" ? { label: "Active", tone: "success" as const } : { label: str(app.state).toLowerCase() || "—" },
        })),
      },
      vms.length > 0 && { kind: "rows", title: "Machines virtuelles", items: vms.map((vm) => ({ title: str(vm.name), badge: str(asObject(vm.status).state) === "RUNNING" ? { label: "Active", tone: "success" as const } : { label: "Arrêtée" } })) }
    ),
  };
};

// ---- Registration -----------------------------------------------------------------------------

registerSummaryAdapters({
  nginx_proxy_manager: npmSummary,
  proxmox: proxmoxSummary,
  jellystat: jellystatSummary,
  wakapi: async (s) => ({ value: hoursLabel(wakapiSeconds(await wakapiSummary(s, "today")) / 3600), label: "Temps d'aujourd'hui" }),
  patchmon: async (s) => {
    const hosts = asArray(asObject(await get(s, "/api/v1/api/hosts")).hosts) as Json[];
    return { value: `${hosts.filter((host) => str(host.status).toLowerCase() === "active").length}`, sub: `/ ${hosts.length}`, label: "Hôtes" };
  },
  linux_update: async (s) => {
    const stats = asObject(await get(s, "/api/dashboard/stats"));
    return { value: `${num(stats.upToDate)}`, sub: `/ ${num(stats.total)}`, label: "a jour" };
  },
  technitium: async (s) => {
    const stats = asObject((await technitium(s, "/api/dashboard/stats/get?type=LastDay&utc=true", (response) => response)).stats);
    return { value: fmt(num(stats.totalBlocked)), sub: `/ ${fmt(num(stats.totalQueries))}`, label: "Requêtes bloquées" };
  },
  crafty_controller: async (s) => {
    const list = await craftyServers(s);
    return { value: `${list.filter((item) => item.stats.running === true).length}`, sub: `/ ${list.length}`, label: "serveurs actifs" };
  },
  pterodactyl: pterodactyl.summary,
  calagopus: calagopus.summary,
  dockhand: async (s) => {
    const { stats, containers } = await dockhandLoad(s);
    return { value: `${num(stats.runningContainers) || containers.filter((c) => str(c.state ?? c.State) === "running").length}`, sub: `/ ${num(stats.totalContainers) || containers.length}`, label: "Conteneurs" };
  },
  dockmon: async (s) => {
    const { containers } = await dockmonLoad(s);
    return { value: `${containers.filter((c) => str(c.state ?? c.status) === "running").length}`, sub: `/ ${containers.length}`, label: "Conteneurs" };
  },
  komodo: async (s) => {
    const containers = asObject(await komodoRead(s, "GetDockerContainersSummary"));
    return { value: `${num(containers.running)}`, sub: `/ ${num(containers.total)}`, label: "Conteneurs" };
  },
  maltrail: maltrailSummary,
  pangolin: pangolinSummary,
  truenas: truenasSummary,
});

registerActions("adguard_home", adguardActions);
registerActions("technitium", technitiumActions);
registerActions("nginx_proxy_manager", npmActions);
registerActions("healthchecks", healthchecksActions);
registerActions("proxmox", proxmoxActions);
registerActions("linux_update", linuxUpdateActions);
registerActions("crafty_controller", craftyActions);
registerActions("pterodactyl", pterodactyl.actions);
registerActions("calagopus", calagopus.actions);
registerActions("dockhand", dockhandActions);
registerActions("dockmon", dockmonActions);
registerActions("komodo", komodoActions);

export const MORE_DASHBOARDS: Record<string, Builder> = {
  adguard_home: adguardDashboard,
  technitium: technitiumDashboard,
  nginx_proxy_manager: npmDashboard,
  healthchecks: healthchecksDashboard,
  proxmox: proxmoxDashboard,
  plex: plexDashboard,
  jellystat: jellystatDashboard,
  wakapi: wakapiDashboard,
  patchmon: patchmonDashboard,
  linux_update: linuxUpdateDashboard,
  crafty_controller: craftyDashboard,
  pterodactyl: pterodactyl.dashboard,
  calagopus: calagopus.dashboard,
  dockhand: dockhandDashboard,
  dockmon: dockmonDashboard,
  komodo: komodoDashboard,
  maltrail: maltrailDashboard,
  pangolin: pangolinDashboard,
  truenas: truenasDashboard,
};
