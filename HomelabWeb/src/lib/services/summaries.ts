import "server-only";
import type { ServiceWithSecrets } from "../db";
import { baseUrls, isReachable, rawFetch, request, requestAt, ServiceHttpError, type HttpResponse } from "./http";

// Live service data, ported from the mobile app so both show the same figures:
// - home services follow HomeViewModel.fetchInstanceSummary (value, "/ total", label),
// - media services follow MediaArrRepository.*CardPreview (headline + metrics),
// - `details` carries the extra figures shown in the service popup.

export type ServiceStatus = "online" | "offline" | "auth";

export interface ServiceDetail {
  label: string;
  value: string;
  tone?: "success" | "warning" | "destructive";
}

export interface ServiceMetric {
  label: string;
  value: string;
}

export interface ServiceSummary {
  status: ServiceStatus;
  /** Home card figure (app InstanceSummary). */
  value?: string;
  sub?: string;
  label?: string;
  /** Media card (app MediaArrCardPreview). */
  headline?: string;
  metrics?: ServiceMetric[];
  details?: ServiceDetail[];
  /** Branding variant detected live (Forgejo answers its own API). */
  brand?: "forgejo";
  latencyMs?: number;
  error?: string;
  checkedAt?: string;
}

type Metric = Omit<ServiceSummary, "status" | "latencyMs" | "error" | "checkedAt">;
type Adapter = (service: ServiceWithSecrets) => Promise<Metric>;
export type Json = Record<string, unknown>;

// ---- Small helpers ------------------------------------------------------------------------

export const fmt = (value: number) => new Intl.NumberFormat("fr-FR").format(value);
export const asArray = (value: unknown): unknown[] => (Array.isArray(value) ? value : []);
export const asObject = (value: unknown): Json => (value && typeof value === "object" && !Array.isArray(value) ? (value as Json) : {});
export const num = (value: unknown): number => (typeof value === "number" ? value : Number(value) || 0);
export const str = (value: unknown): string => (typeof value === "string" ? value.trim() : typeof value === "number" ? String(value) : "");
export const firstNonBlank = (...values: unknown[]) => values.map(str).find((value) => value.length > 0);
export const capitalize = (value: string) => value.charAt(0).toUpperCase() + value.slice(1);

/** Same as the app's speedLabel: binary units. */
export function speed(bytesPerSecond: number) {
  const units = ["o/s", "Ko/s", "Mo/s", "Go/s"];
  let value = bytesPerSecond;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toFixed(value < 10 && unit > 0 ? 1 : 0)} ${units[unit]}`;
}

export const json = (response: HttpResponse): unknown => {
  try {
    return response.json();
  } catch {
    return null;
  }
};
export const get = async (service: ServiceWithSecrets, path: string, headers?: Record<string, string>) => json(await request(service, path, { headers }));
/** Lenient call, like the app's runCatching: null on any failure except refused credentials. */
export async function tryGet(service: ServiceWithSecrets, path: string, headers?: Record<string, string>): Promise<unknown> {
  try {
    return await get(service, path, headers);
  } catch (error) {
    if (error instanceof ServiceHttpError && (error.status === 401 || error.status === 403)) throw error;
    return null;
  }
}
export async function firstAvailable(service: ServiceWithSecrets, paths: string[]): Promise<HttpResponse | null> {
  for (const path of paths) {
    try {
      return await request(service, path);
    } catch (error) {
      if (error instanceof ServiceHttpError && (error.status === 401 || error.status === 403)) throw error;
    }
  }
  return null;
}

// Session-based services: sessions cached in memory per service id, renewed on rejection.
const sessions = new Map<string, string>();

// ---- Logins -------------------------------------------------------------------------------

async function qbittorrentCookie(service: ServiceWithSecrets, base: string, force = false): Promise<string> {
  const cached = sessions.get(service.id);
  if (cached && !force) return cached;
  const response = await rawFetch(service, `${base}/api/v2/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded", Referer: base },
    body: new URLSearchParams({ username: service.secrets.username ?? "", password: service.secrets.password ?? "" }).toString(),
  });
  // qBittorrent < 5 names the cookie SID, 5.x names it QBT_SID_<port>.
  const cookie = response.headers.getSetCookie().map((value) => value.split(";")[0]).find((value) => /^(QBT_)?SID(_\d+)?=/.test(value));
  if (!cookie) throw new ServiceHttpError(401, "qBittorrent login failed");
  sessions.set(service.id, cookie);
  return cookie;
}

/** qBittorrent request with the session cookie, renewed once when rejected (403). */
export async function qbittorrentRequest(service: ServiceWithSecrets, path: string, init: { method?: string; body?: string } = {}): Promise<HttpResponse> {
  let lastError: unknown = null;
  for (const base of baseUrls(service)) {
    for (const force of [false, true]) {
      try {
        const cookie = await qbittorrentCookie(service, base, force);
        return await requestAt(service, base, path, {
          noAuth: true,
          method: init.method,
          body: init.body,
          headers: { Cookie: cookie, Referer: base, ...(init.body ? { "Content-Type": "application/x-www-form-urlencoded" } : {}) },
        });
      } catch (error) {
        lastError = error;
        if (!(error instanceof ServiceHttpError && error.status === 403)) break;
      }
    }
  }
  throw lastError ?? new Error("qBittorrent unreachable");
}

export const qbittorrentGet = async (service: ServiceWithSecrets, path: string): Promise<unknown> => json(await qbittorrentRequest(service, path));

export async function ampSession(service: ServiceWithSecrets, base: string, force = false): Promise<string> {
  const cached = sessions.get(service.id);
  if (cached && !force) return cached;
  const login = await requestAt(service, base, "/API/Core/Login", {
    method: "POST",
    noAuth: true,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: service.secrets.username ?? "", password: service.secrets.password ?? "", token: "", rememberMe: false }),
  });
  const result = login.json<{ success?: boolean; sessionID?: string }>();
  if (!result.success || !result.sessionID) throw new ServiceHttpError(401, "AMP login failed");
  sessions.set(service.id, result.sessionID);
  return result.sessionID;
}

/** Pi-hole v6 session (POST /api/auth), reused until rejected; the app stores the password in piholePassword. */
export async function piholeSid(service: ServiceWithSecrets, base: string, force = false): Promise<string | null> {
  const cached = sessions.get(service.id);
  if (cached && !force) return cached;
  const secret = service.secrets.piholePassword || service.secrets.token;
  if (!secret) return null;
  const response = await requestAt(service, base, "/api/auth", {
    method: "POST",
    noAuth: true,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ password: secret }),
  });
  const sid = str(asObject(asObject(json(response)).session).sid);
  if (!sid) throw new ServiceHttpError(401, "Pi-hole login failed");
  sessions.set(service.id, sid);
  return sid;
}

/** Beszel (PocketBase) token: the stored one first, then a fresh login with the stored credentials. */
export async function beszelToken(service: ServiceWithSecrets, base: string, force = false): Promise<string> {
  const cached = sessions.get(service.id) ?? service.secrets.token ?? "";
  if (cached && !force) return cached;
  const { username, password } = service.secrets;
  if (!username || !password) throw new ServiceHttpError(401, "Beszel credentials missing");
  const response = await requestAt(service, base, "/api/collections/users/auth-with-password", {
    method: "POST",
    noAuth: true,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ identity: username, password }),
  }).catch((error) => {
    // PocketBase answers a bad login with 400.
    if (error instanceof ServiceHttpError && error.status === 400) throw new ServiceHttpError(401, "Beszel login failed");
    throw error;
  });
  const token = str(asObject(json(response)).token);
  if (!token) throw new ServiceHttpError(401, "Beszel login failed");
  sessions.set(service.id, token);
  return token;
}

// ---- Adapters -----------------------------------------------------------------------------

const ADAPTERS: Partial<Record<string, Adapter>> = {
  // ---------- Home services (HomeViewModel.fetchInstanceSummary) ----------

  portainer: async (s) => {
    const endpoints = asArray(await get(s, "/api/endpoints")) as Json[];
    let running = 0;
    let total = 0;
    const details: ServiceDetail[] = [];
    for (const endpoint of endpoints) {
      if (endpoint.Id === undefined) continue;
      const containers = asArray(await tryGet(s, `/api/endpoints/${endpoint.Id}/docker/containers/json?all=1`)) as Json[];
      const up = containers.filter((c) => c.State === "running" || str(c.Status).includes("Up")).length;
      total += containers.length;
      running += up;
      details.push({ label: str(endpoint.Name) || `Environnement ${endpoint.Id}`, value: `${up} / ${containers.length}`, tone: up === containers.length ? "success" : "warning" });
    }
    return { value: `${running}`, sub: `/ ${total}`, label: "Conteneurs", details };
  },

  pihole: async (s) => {
    let lastError: unknown = null;
    for (const base of baseUrls(s)) {
      // Pi-hole v6: session id in X-FTL-SID, renewed once when rejected.
      if (s.secrets.piholeAuthMode !== "LEGACY") {
        for (const force of [false, true]) {
          try {
            const sid = await piholeSid(s, base, force);
            const stats = asObject(json(await requestAt(s, base, "/api/stats/summary", { noAuth: true, headers: sid ? { "X-FTL-SID": sid } : {} })));
            const queries = asObject(stats.queries);
            const gravity = asObject(stats.gravity);
            const total = num(queries.total);
            return {
              value: fmt(total),
              label: "Requêtes",
              details: [
                { label: "Requêtes bloquées", value: fmt(num(queries.blocked)) },
                { label: "Taux de blocage", value: `${num(queries.percent_blocked).toFixed(1)} %` },
                { label: "Domaines uniques", value: fmt(num(queries.unique_domains)) },
                { label: "Domaines bloqués (listes)", value: fmt(num(gravity.domains_being_blocked)) },
                { label: "Transférées", value: fmt(num(queries.forwarded)) },
                { label: "En cache", value: fmt(num(queries.cached)) },
              ],
            };
          } catch (error) {
            lastError = error;
            if (!(error instanceof ServiceHttpError && error.status === 401) || force) break;
          }
        }
      }
      // Pi-hole v5: password as the legacy auth parameter.
      const secret = s.secrets.piholePassword || s.secrets.token;
      if (secret) {
        try {
          const legacy = asObject(json(await requestAt(s, base, `/admin/api.php?summaryRaw&auth=${encodeURIComponent(secret)}`, { noAuth: true })));
          if (Object.keys(legacy).length) {
            return {
              value: fmt(num(legacy.dns_queries_today)),
              label: "Requêtes",
              details: [
                { label: "Requêtes bloquées", value: fmt(num(legacy.ads_blocked_today)) },
                { label: "Taux de blocage", value: `${num(legacy.ads_percentage_today).toFixed(1)} %` },
                { label: "Domaines uniques", value: fmt(num(legacy.unique_domains)) },
                { label: "Domaines bloqués (listes)", value: fmt(num(legacy.domains_being_blocked)) },
              ],
            };
          }
        } catch (error) {
          lastError ??= error;
        }
      }
    }
    throw lastError ?? new ServiceHttpError(401, "Pi-hole credentials missing");
  },

  beszel: async (s) => {
    const path = "/api/collections/systems/records?sort=-updated&perPage=50&fields=id,name,host,status,info.cpu,info.mp,info.dp,info.efs";
    let lastError: unknown = null;
    for (const base of baseUrls(s)) {
      for (const force of [false, true]) {
        try {
          const token = await beszelToken(s, base, force);
          const items = asArray(asObject(json(await requestAt(s, base, path, { noAuth: true, headers: { Authorization: `Bearer ${token}` } }))).items) as Json[];
          // PocketBase can return an empty list for an expired token instead of a 401: retry once.
          if (items.length === 0 && !force && s.secrets.username && s.secrets.password) continue;
          const systems = items.sort((a, b) => str(a.name).toLowerCase().localeCompare(str(b.name).toLowerCase()));
          const online = systems.filter((system) => system.status === "up").length;
          return {
            value: `${online}`,
            sub: `/ ${systems.length}`,
            label: "Systèmes",
            details: systems.map((system) => {
              const info = asObject(system.info);
              const up = system.status === "up";
              const usage = up
                ? [`CPU ${Math.round(num(info.cpu))} %`, `RAM ${Math.round(num(info.mp))} %`, `Disque ${Math.round(num(info.dp))} %`].join(" · ")
                : str(system.status) || "hors ligne";
              return { label: str(system.name) || str(system.host), value: usage, tone: up ? undefined : ("destructive" as const) };
            }),
          };
        } catch (error) {
          lastError = error;
          const authError = error instanceof ServiceHttpError && [400, 401, 403].includes(error.status);
          if (!authError || force) break;
        }
      }
    }
    throw lastError ?? new Error("Beszel unreachable");
  },

  adguard_home: async (s) => {
    const stats = asObject(await get(s, "/control/stats"));
    const queries = num(stats.num_dns_queries);
    const blocked = num(stats.num_blocked_filtering);
    return {
      value: fmt(queries),
      label: "Requêtes",
      details: [
        { label: "Bloquées", value: fmt(blocked) },
        { label: "Taux de blocage", value: queries ? `${((blocked / queries) * 100).toFixed(1)} %` : "—" },
        { label: "Temps de traitement moyen", value: `${(num(stats.avg_processing_time) * 1000).toFixed(1)} ms` },
      ],
    };
  },

  gitea: async (s) => {
    // Same as the app: count every page of the user's repositories.
    let total = 0;
    for (let page = 1; page <= 50; page++) {
      const repos = asArray(await get(s, `/api/v1/user/repos?page=${page}&limit=100`));
      total += repos.length;
      if (repos.length < 100) break;
    }
    const forgejo = (await tryGet(s, "/api/forgejo/v1/version")) !== null;
    const version = str(asObject(await tryGet(s, "/api/v1/version")).version);
    return {
      value: `${total}`,
      label: "Dépôts",
      brand: forgejo ? "forgejo" : undefined,
      details: version ? [{ label: forgejo ? "Forgejo" : "Gitea", value: version }] : [],
    };
  },

  uptime_kuma: async (s) => {
    const metrics = (await request(s, "/metrics", { headers: { Accept: "text/plain" } })).text;
    const monitors = new Map<string, number>();
    for (const line of metrics.split("\n")) {
      if (!line.startsWith("monitor_status{")) continue;
      const name = /monitor_name="([^"]*)"/.exec(line)?.[1] ?? line;
      monitors.set(name, Number(line.trim().split(/\s+/).pop()));
    }
    const up = [...monitors.values()].filter((value) => value === 1).length;
    const down = [...monitors.entries()].filter(([, value]) => value !== 1);
    const details: ServiceDetail[] = down.length
      ? down.slice(0, 12).map(([name, value]) => ({ label: name, value: value === 2 ? "En attente" : value === 3 ? "Maintenance" : "Hors ligne", tone: value === 0 ? ("destructive" as const) : ("warning" as const) }))
      : [{ label: "Moniteurs", value: "Tous en ligne", tone: "success" }];
    return { value: `${up}`, sub: `/ ${monitors.size}`, label: "Moniteurs", details };
  },

  healthchecks: async (s) => {
    const checks = asArray(asObject(await get(s, "/api/v3/checks/")).checks) as Json[];
    const up = checks.filter((check) => check.status === "up" || check.status === "grace").length;
    const failing = checks.filter((check) => check.status === "down");
    return {
      value: `${up}`,
      sub: `/ ${checks.length}`,
      label: "Vérifications",
      details: failing.slice(0, 10).map((check) => ({ label: str(check.name) || "Check", value: "En échec", tone: "destructive" as const })),
    };
  },

  peanut: async (s) => {
    const devices = asArray(await get(s, "/api/v1/devices?meta=true")) as Json[];
    const charges = devices.map((device) => Number(device["battery.charge"])).filter((value) => !Number.isNaN(value));
    const onBattery = devices.filter((device) => str(device["ups.status"]).split(" ").includes("OB")).length;
    const details: ServiceDetail[] = devices.map((device, index) => {
      const status = str(device["ups.status"]);
      const charge = Number(device["battery.charge"]);
      const runtime = Number(device["battery.runtime"]);
      const load = Number(device["ups.load"]);
      const onBat = status.split(" ").includes("OB");
      const parts = [
        Number.isNaN(charge) ? null : `${Math.round(charge)} %`,
        Number.isNaN(load) ? null : `charge ${Math.round(load)} %`,
        Number.isNaN(runtime) ? null : `${Math.round(runtime / 60)} min`,
        onBat ? "sur batterie" : status ? "secteur" : null,
      ].filter(Boolean);
      return {
        label: str(device["device.model"]) || str(device["ups.model"]) || str(device.name) || `Onduleur ${index + 1}`,
        value: parts.join(" · ") || "—",
        tone: onBat ? ("warning" as const) : undefined,
      };
    });
    return {
      value: charges.length ? `${Math.round(Math.min(...charges))}%` : `${devices.length}`,
      label: onBattery > 0 ? "Sur batterie" : "Batterie",
      details,
    };
  },

  safeline: async (s) => {
    const data = await safelineData(s);
    const details: ServiceDetail[] = [
      ...(data.version ? [{ label: "Version", value: data.version }] : []),
      ...(data.todayRequests !== null ? [{ label: "Requêtes aujourd'hui", value: fmt(data.todayRequests) }] : []),
      ...(data.todayVisitors !== null ? [{ label: "Visiteurs aujourd'hui", value: fmt(data.todayVisitors) }] : []),
      ...(data.attackIps !== null ? [{ label: "IP attaquantes", value: fmt(data.attackIps) }] : []),
      ...data.sites.slice(0, 10).map((site, index) => {
        const names = asArray(site.server_names).map(str).filter(Boolean);
        const mode = num(site.mode);
        return {
          label: str(site.title) || str(site.comment) || names[0] || `Site ${index + 1}`,
          value: mode === 0 ? "Protection" : mode === 1 ? "Observation" : mode === 2 ? "Maintenance" : "—",
          tone: mode === 0 ? ("success" as const) : ("warning" as const),
        };
      }),
    ];
    return data.blocked !== null
      ? { value: fmt(data.blocked), label: "Bloquées", details }
      : { value: `${data.sites.length}`, label: "Sites protégés", details };
  },

  amp: async (s) => {
    for (const base of baseUrls(s)) {
      for (const force of [false, true]) {
        const session = await ampSession(s, base, force);
        const response = await requestAt(s, base, "/API/ADSModule/GetInstances", {
          method: "POST",
          noAuth: true,
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ SESSIONID: session }),
        });
        const data = json(response);
        if (!Array.isArray(data)) {
          if (!force) continue; // "Unauthorized Access" object: renew the session once.
          throw new ServiceHttpError(401, "AMP session rejected");
        }
        const instances = data
          .flatMap((target) => asArray(asObject(target).AvailableInstances))
          .map((instance) => asObject(instance))
          .filter((instance) => str(instance.Module).toUpperCase() !== "ADS");
        const isUp = (instance: Json) => Boolean(instance.Running) && num(instance.AppState) === 20 && !instance.Suspended;
        const running = instances.filter(isUp).length;
        const details: ServiceDetail[] = instances.map((instance) => {
          const up = isUp(instance);
          const users = asObject(asObject(instance.Metrics)["Active Users"]);
          const players = users.RawValue !== undefined ? ` · ${num(users.RawValue)}${users.MaxValue ? `/${num(users.MaxValue)}` : ""} joueurs` : "";
          return {
            label: str(instance.FriendlyName) || str(instance.InstanceName) || "Instance",
            value: (up ? "En ligne" : instance.Running ? "En transition" : "Arrêté") + players,
            tone: up ? ("success" as const) : undefined,
          };
        });
        return { value: `${running}`, sub: `/ ${instances.length}`, label: "En ligne", details };
      }
    }
    throw new Error("unreachable");
  },

  proxmox: async (s) => {
    const nodes = asArray(asObject(await get(s, "/api2/json/nodes")).data) as Json[];
    let running = 0;
    let total = 0;
    const details: ServiceDetail[] = [];
    for (const node of nodes.filter((n) => n.status === "online")) {
      const name = str(node.node);
      const vms = asArray(asObject(await tryGet(s, `/api2/json/nodes/${name}/qemu`)).data) as Json[];
      const lxcs = asArray(asObject(await tryGet(s, `/api2/json/nodes/${name}/lxc`)).data) as Json[];
      const up = [...vms, ...lxcs].filter((guest) => guest.status === "running").length;
      running += up;
      total += vms.length + lxcs.length;
      details.push({
        label: name,
        value: `${up} / ${vms.length + lxcs.length} · CPU ${Math.round(num(node.cpu) * 100)} % · RAM ${node.maxmem ? Math.round((num(node.mem) / num(node.maxmem)) * 100) : 0} %`,
      });
    }
    for (const node of nodes.filter((n) => n.status !== "online")) details.push({ label: str(node.node), value: "Hors ligne", tone: "destructive" });
    return { value: `${running}`, sub: `/ ${total}`, label: "Invités actifs", details };
  },

  plex: async (s) => {
    const sections = asObject(asObject(await get(s, "/library/sections")).MediaContainer);
    const directories = asArray(sections.Directory) as Json[];
    let totalItems = 0;
    const details: ServiceDetail[] = [];
    for (const directory of directories) {
      const container = asObject(asObject(await tryGet(s, `/library/sections/${str(directory.key)}/all?X-Plex-Container-Start=0&X-Plex-Container-Size=0`)).MediaContainer);
      const size = num(container.totalSize ?? container.size);
      totalItems += size;
      details.push({ label: str(directory.title), value: fmt(size) });
    }
    return { value: fmt(totalItems), label: "Éléments totaux", details };
  },

  nginx_proxy_manager: async (s) => {
    const report = asObject(await get(s, "/api/reports/hosts"));
    const proxy = num(report.proxy);
    const total = proxy + num(report.redirection) + num(report.stream) + num(report.dead);
    return { value: `${proxy}`, sub: `/ ${total}`, label: "Hôtes" };
  },

  // ---------- Media services (MediaArrRepository card previews) ----------

  qbittorrent: async (s) => {
    const info = asObject(await qbittorrentGet(s, "/api/v2/transfer/info"));
    const dht = num(info.dht_nodes);
    const details: ServiceDetail[] = [];
    try {
      const torrents = asArray(await qbittorrentGet(s, "/api/v2/torrents/info?filter=active"));
      details.push({ label: "Torrents actifs", value: fmt(torrents.length) });
    } catch {
      // The transfer figures are enough.
    }
    return {
      headline: str(info.connection_status) || undefined,
      metrics: [
        { label: "Réception", value: speed(num(info.dl_info_speed)) },
        { label: "Envoi", value: speed(num(info.up_info_speed)) },
        { label: "Nœuds DHT", value: `${dht}` },
      ],
      details,
    };
  },

  radarr: (s) => arrPreview(s, "v3"),
  sonarr: (s) => arrPreview(s, "v3"),
  lidarr: (s) => arrPreview(s, "v1"),

  jellyseerr: async (s) => {
    const status = asObject(await get(s, "/api/v1/status"));
    const requests = asObject(await get(s, "/api/v1/request?take=20&skip=0&sort=added&filter=all"));
    const results = asArray(requests.results) as Json[];
    let pending = 0;
    let available = 0;
    for (const row of results) {
      const value = typeof row.status === "number" ? String(row.status) : str(row.status).toLowerCase();
      if (value === "1" || value === "pending") pending++;
      if (value === "5" || value === "available") available++;
    }
    const total = num(requests.totalResults ?? asObject(requests.pageInfo).results ?? requests.total ?? results.length);
    return {
      headline: firstNonBlank(str(status.version) && `v${str(status.version)}`, status.commitTag),
      metrics: [
        { label: "Demandes", value: `${total}` },
        { label: "En attente", value: `${pending}` },
        { label: "Disponibles", value: `${available}` },
      ],
    };
  },

  prowlarr: async (s) => {
    const [status, indexers, health, apps] = await Promise.all([
      get(s, "/api/v1/system/status"),
      get(s, "/api/v1/indexer"),
      get(s, "/api/v1/health"),
      get(s, "/api/v1/applications"),
    ]);
    const unhealthy =
      (asArray(indexers) as Json[]).filter((indexer) => /error|down|unhealthy/.test(str(indexer.status).toLowerCase())).length + asArray(health).length;
    return {
      headline: versionBranch(asObject(status)),
      metrics: [
        { label: "Indexeurs", value: `${asArray(indexers).length}` },
        { label: "Applis", value: `${asArray(apps).length}` },
        { label: "Problèmes", value: `${unhealthy}` },
      ],
      details: (asArray(health) as Json[]).map((row) => ({ label: str(row.source) || "Santé", value: str(row.message), tone: "warning" as const })),
    };
  },

  autobrr: async (s) => {
    const config = asObject(await tryGet(s, "/api/config"));
    const filters = asArray(await get(s, "/api/filters")) as Json[];
    const stats = asObject(await tryGet(s, "/api/release/stats"));
    const networks = (asArray(await tryGet(s, "/api/irc")) as Json[]).filter((network) => network.enabled !== false);
    const enabledFilters = filters.filter((filter) => filter.enabled === true).length;
    const healthy = networks.filter((network) => network.healthy === true).length;
    const version = str(config.version).replace(/^v/, "");
    return {
      headline: version ? `v${version}` : undefined,
      metrics: [
        { label: "Filtres", value: `${enabledFilters}/${filters.length}` },
        { label: "Approuvées", value: fmt(num(stats.push_approved_count)) },
        { label: "IRC", value: `${healthy}/${networks.length}` },
      ],
      details: [
        { label: "Releases filtrées", value: fmt(num(stats.filtered_count)) },
        { label: "Rejetées", value: fmt(num(stats.push_rejected_count)) },
        ...networks.map((network) => ({
          label: str(network.name) || "Réseau IRC",
          value: network.healthy ? "Connecté" : "Déconnecté",
          tone: network.healthy ? ("success" as const) : ("destructive" as const),
        })),
      ],
    };
  },

  bazarr: async (s) => {
    const [status, badges, health] = await Promise.all([get(s, "/api/system/status"), get(s, "/api/badges"), get(s, "/api/system/health")]);
    const badgeObj = asObject(badges);
    const statusObj = asObject(status);
    const badgeMetrics = ["wanted", "missing", "providers"].filter((key) => key in badgeObj).map((key) => ({ label: capitalize(key), value: str(badgeObj[key]) }));
    const healthRows = asArray(asObject(health).data ?? health);
    return {
      headline: firstNonBlank(
        str(statusObj.version) && `v${str(statusObj.version)}`,
        str(asObject(statusObj.data).bazarr_version) && `v${str(asObject(statusObj.data).bazarr_version)}`,
        str(asObject(statusObj.data).version) && `v${str(asObject(statusObj.data).version)}`
      ),
      metrics: badgeMetrics.length ? badgeMetrics : [{ label: "Santé", value: `${healthRows.length}` }],
      details: [
        ...(badgeObj.episodes !== undefined ? [{ label: "Épisodes sans sous-titres", value: fmt(num(badgeObj.episodes)) }] : []),
        ...(badgeObj.movies !== undefined ? [{ label: "Films sans sous-titres", value: fmt(num(badgeObj.movies)) }] : []),
      ],
    };
  },

  gluetun: async (s) => {
    const vpnResponse = await firstAvailable(s, ["/v1/vpn/status", "/v1/openvpn/status"]);
    const vpn = vpnResponse ? asObject(json(vpnResponse)) : {};
    const ipResponse = await request(s, "/v1/publicip/ip");
    const ipObj = asObject(json(ipResponse));
    const ipRaw = ipResponse.text.trim().startsWith("{") ? "" : ipResponse.text.trim();
    const forwardedResponse = await firstAvailable(s, ["/v1/portforward", "/v1/openvpn/portforwarded"]);
    const forwardedObj = forwardedResponse ? asObject(json(forwardedResponse)) : {};
    const forwardedRaw = forwardedResponse && !forwardedResponse.text.trim().startsWith("{") ? forwardedResponse.text.trim() : "";

    const status = firstNonBlank(vpn.status, asObject(vpn.openvpn).status) ?? "Inconnu";
    const provider = firstNonBlank(vpn.provider, asObject(vpn.vpn).provider, asObject(vpn.openvpn).provider);
    const server = firstNonBlank(vpn.server_name, asObject(vpn.openvpn).server_name);
    const publicIp = firstNonBlank(ipObj.public_ip, ipObj.ip, ipRaw) ?? "N/A";
    const ports = asArray(forwardedObj.ports).map(num).filter((port) => port > 0);
    const single = firstNonBlank(forwardedObj.port, forwardedObj.port_forwarded, forwardedRaw);
    const forwarded = ports.length ? ports.join(", ") : single && single !== "0" ? single : "N/A";
    const location = [str(ipObj.city), str(ipObj.region), str(ipObj.country)].filter(Boolean).join(", ");
    return {
      headline: firstNonBlank(provider, server, status),
      metrics: [
        { label: "Statut", value: status },
        { label: "IP publique", value: publicIp },
        { label: "Port redirigé", value: forwarded },
      ],
      details: [
        ...(location ? [{ label: "Localisation", value: location }] : []),
        ...(str(ipObj.organization) ? [{ label: "Fournisseur", value: str(ipObj.organization) }] : []),
        ...(server ? [{ label: "Serveur", value: server }] : []),
      ],
    };
  },

  flaresolverr: async (s) => {
    const health = asObject(await get(s, "/health"));
    const sessionsObj = asObject(
      json(await request(s, "/v1", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ cmd: "sessions.list" }) }))
    );
    return {
      headline: firstNonBlank(str(health.version) && `v${str(health.version)}`, health.message, health.msg),
      metrics: [
        { label: "Sessions", value: `${asArray(sessionsObj.sessions).length}` },
        { label: "Statut", value: str(health.status) || "inconnu" },
      ],
    };
  },
};

/** Total of a SafeLine statistics payload whose shape varies across versions (same as the app). */
function safelineTotal(element: unknown): number | null {
  if (typeof element === "number" || typeof element === "string") {
    const value = Number(element);
    return Number.isFinite(value) ? value : null;
  }
  if (Array.isArray(element)) {
    const values = element
      .map((item) => (typeof item === "object" && item ? asObject(item).value ?? asObject(item).count ?? asObject(item).total : item))
      .map(Number)
      .filter(Number.isFinite);
    return values.length ? values.reduce((a, b) => a + b, 0) : null;
  }
  const object = asObject(element);
  for (const key of ["total", "count", "value", "today"]) if (object[key] !== undefined && Number.isFinite(Number(object[key]))) return Number(object[key]);
  for (const key of ["data", "nodes", "items"]) if (object[key] !== undefined) return safelineTotal(object[key]);
  return null;
}

/**
 * SafeLine dashboard data (app SafelineRepository.getDashboard): the statistics routes differ across
 * versions and some refuse API tokens, so each figure tries the known routes, then falls back to the
 * attack events.
 */
export async function safelineData(s: ServiceWithSecrets) {
  const unwrap = async (path: string): Promise<unknown> => {
    const root = asObject(await tryGet(s, path));
    if (root.err !== undefined && root.err !== null && str(root.err) !== "") return null;
    return root.data ?? null;
  };
  const first = async (...paths: string[]) => {
    for (const path of paths) {
      const data = await unwrap(path);
      if (data !== null && data !== undefined) return data;
    }
    return null;
  };
  // Token check first, as the app does: /api/open/system must answer (throws on 401/403).
  const system = asObject(asObject(json(await request(s, "/api/open/system"))).data);
  // SafeLine API 2.0 (mgt API doc): today's figures from /api/stat/advance/*, since local midnight.
  const midnight = new Date();
  midnight.setHours(0, 0, 0, 0);
  const today = `begin_time=${Math.floor(midnight.getTime() / 1000)}&end_time=${Math.floor(Date.now() / 1000)}`;
  const [advanceAccess, advanceAttack] = await Promise.all([
    first(`/api/stat/advance/access?${today}`, "/api/stat/advance/access"),
    first(`/api/stat/advance/attack?${today}`, "/api/stat/advance/attack"),
  ]);
  const access = asObject(advanceAccess);
  const advance = asObject(advanceAttack);
  const interceptByKind = asObject(advance.intercept);
  const advanceBlocked = Object.values(interceptByKind).map(Number).filter(Number.isFinite);
  const [counts, requests, attack, intercepts, sitesRaw, eventsRaw] = await Promise.all([
    first("/api/open/dashboard/user/counts"),
    first("/api/dashboard/requests"),
    first("/api/stat/basic/attack", "/api/stat/basic/access"),
    first("/api/dashboard/intercepts"),
    first("/api/open/site"),
    first("/api/open/events?page=1&page_size=100", "/api/open/events?page=1&page_size=20"),
  ]);
  const arrayIn = (value: unknown) => (Array.isArray(value) ? value : asArray(asObject(value).data ?? asObject(value).nodes ?? asObject(value).items)) as Json[];
  const countsObj = asObject(counts);
  const attackObj = asObject(attack);
  const events = arrayIn(eventsRaw);
  const optional = (value: unknown) => (value === undefined || value === null ? null : safelineTotal(value));
  return {
    version: str(system.version),
    todayRequests: optional(access.access) ?? optional(countsObj.today_pv) ?? optional(requests),
    todayVisitors: optional(access.ip) ?? optional(countsObj.today_uv),
    todayIps: optional(access.ip) ?? optional(countsObj.today_ip),
    blocked: (advanceBlocked.length ? advanceBlocked.reduce((a, b) => a + b, 0) : null) ?? optional(attackObj.intercept) ?? optional(intercepts) ?? (events.length ? events.reduce((total, event) => total + num(event.deny_count), 0) : null),
    attackIps: optional(advance.attack_ip) ?? optional(attackObj.attack_ip) ?? (events.length ? new Set(events.map((event) => str(event.ip))).size : null),
    sites: arrayIn(sitesRaw),
    events: events.slice(0, 20),
  };
}

export function versionBranch(status: Json): string | undefined {
  const parts = [str(status.version) && `v${str(status.version)}`, str(status.branch) && capitalize(str(status.branch))].filter(Boolean);
  return parts.length ? parts.join(" • ") : undefined;
}

/** Radarr / Sonarr (v3) and Lidarr (v1): queue, health issues, next 14 days. */
async function arrPreview(s: ServiceWithSecrets, api: "v1" | "v3"): Promise<Metric> {
  const day = (date: Date) => date.toISOString().slice(0, 10);
  const now = new Date();
  const end = new Date(now.getTime() + 14 * 24 * 3600 * 1000);
  const [status, queue, health, upcoming] = await Promise.all([
    get(s, `/api/${api}/system/status`),
    get(s, `/api/${api}/queue?page=1&pageSize=1&sortDirection=descending`),
    get(s, `/api/${api}/health`),
    get(s, `/api/${api}/calendar?start=${day(now)}&end=${day(end)}`),
  ]);
  const queueObj = asObject(queue);
  const queueTotal = num(queueObj.totalRecords ?? queueObj.recordsTotal ?? queueObj.total);
  return {
    headline: versionBranch(asObject(status)),
    metrics: [
      { label: "Téléchargements", value: `${queueTotal}` },
      { label: "Santé", value: `${asArray(health).length}` },
      { label: "À venir", value: `${asArray(upcoming).length}` },
    ],
    details: [
      ...(asArray(health) as Json[]).map((row) => ({ label: str(row.source) || "Santé", value: str(row.message), tone: "warning" as const })),
      ...(asArray(upcoming) as Json[]).slice(0, 8).map((item) => {
        const series = asObject(item.series);
        const title = str(series.title) ? `${str(series.title)} · S${num(item.seasonNumber)}E${num(item.episodeNumber)}` : str(item.title) || str(asObject(item.artist).artistName);
        const date = str(item.airDateUtc) || str(item.digitalRelease) || str(item.physicalRelease) || str(item.inCinemas) || str(item.releaseDate);
        return { label: title || "À venir", value: date ? new Date(date).toLocaleDateString("fr-FR", { day: "numeric", month: "short" }) : "—" };
      }),
    ],
  };
}

// Adapters ported in other modules (more-services.ts) register here; they take precedence.
const EXTRA_ADAPTERS: Partial<Record<string, Adapter>> = {};
export function registerSummaryAdapters(adapters: Record<string, Adapter>) {
  Object.assign(EXTRA_ADAPTERS, adapters);
}

/** Live summary for one service. Services without an adapter only report reachability (like the app). */
export async function summarize(service: ServiceWithSecrets): Promise<ServiceSummary> {
  return { ...(await summarizeInner(service)), checkedAt: new Date().toISOString() };
}

async function summarizeInner(service: ServiceWithSecrets): Promise<ServiceSummary> {
  const started = Date.now();
  const adapter = EXTRA_ADAPTERS[service.type] ?? ADAPTERS[service.type];
  try {
    if (adapter) {
      const metric = await adapter(service);
      return { status: "online", ...metric, latencyMs: Date.now() - started };
    }
    if (await isReachable(service)) return { status: "online", latencyMs: Date.now() - started };
    return { status: "offline", error: "Injoignable", latencyMs: Date.now() - started };
  } catch (error) {
    if (error instanceof ServiceHttpError && (error.status === 401 || error.status === 403)) {
      return { status: "auth", error: "Identifiants refusés", latencyMs: Date.now() - started };
    }
    console.warn(`[summary] ${service.type} "${service.label}":`, error instanceof Error ? error.message : error);
    // An adapter failure (unexpected payload, proxy page) does not mean the service is down.
    if (adapter && (await isReachable(service))) {
      return { status: "online", error: "Données indisponibles", latencyMs: Date.now() - started };
    }
    return { status: "offline", error: "Injoignable", latencyMs: Date.now() - started };
  }
}
