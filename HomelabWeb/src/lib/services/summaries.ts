import "server-only";
import type { ServiceWithSecrets } from "../db";
import { baseUrls, isReachable, rawFetch, request, requestAt, ServiceHttpError } from "./http";

export type ServiceStatus = "online" | "offline" | "auth";

/** A service's headline numbers, like the mobile app's home cards. */
export interface ServiceSummary {
  status: ServiceStatus;
  value?: string;
  sub?: string;
  label?: string;
  latencyMs?: number;
  error?: string;
  /** Extra rows shown in the service popup. `tone` colors the value (status-like rows). */
  details?: ServiceDetail[];
  checkedAt?: string;
}

export interface ServiceDetail {
  label: string;
  value: string;
  tone?: "success" | "warning" | "destructive";
}

type Metric = Omit<ServiceSummary, "status" | "latencyMs" | "error" | "checkedAt">;
type Adapter = (service: ServiceWithSecrets) => Promise<Metric>;

const fmt = (value: number) => new Intl.NumberFormat("fr-FR").format(value);
const speed = (bytesPerSecond: number) => {
  const units = ["o/s", "Ko/s", "Mo/s", "Go/s"];
  let value = bytesPerSecond;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toFixed(value < 10 && unit > 0 ? 1 : 0)} ${units[unit]}`;
};
const asArray = (value: unknown): unknown[] => (Array.isArray(value) ? value : []);
const num = (value: unknown): number => (typeof value === "number" ? value : Number(value) || 0);

// Session-based services: sessions cached in memory per service id, renewed on expiry.
const sessions = new Map<string, string>();

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

async function ampSession(service: ServiceWithSecrets, base: string, force = false): Promise<string> {
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

const ADAPTERS: Partial<Record<string, Adapter>> = {
  radarr: async (s) => ({ value: fmt(asArray((await request(s, "/api/v3/movie")).json()).length), label: "films" }),
  sonarr: async (s) => ({ value: fmt(asArray((await request(s, "/api/v3/series")).json()).length), label: "séries" }),
  lidarr: async (s) => ({ value: fmt(asArray((await request(s, "/api/v1/artist")).json()).length), label: "artistes" }),
  prowlarr: async (s) => ({ value: fmt(asArray((await request(s, "/api/v1/indexer")).json()).length), label: "indexeurs" }),
  bazarr: async (s) => {
    const badges = (await request(s, "/api/badges")).json<Record<string, number>>();
    return { value: fmt(num(badges.episodes) + num(badges.movies)), label: "sous-titres manquants" };
  },
  jellyseerr: async (s) => {
    const counts = (await request(s, "/api/v1/request/count")).json<{ pending?: number; total?: number }>();
    return { value: fmt(num(counts.pending)), sub: `/ ${fmt(num(counts.total))}`, label: "demandes en attente" };
  },
  autobrr: async (s) => {
    const stats = (await request(s, "/api/release/stats")).json<{ push_approved_count?: number }>();
    return { value: fmt(num(stats.push_approved_count)), label: "releases envoyées" };
  },
  gluetun: async (s) => {
    let status = "running";
    for (const path of ["/v1/vpn/status", "/v1/openvpn/status"]) {
      try {
        status = (await request(s, path)).json<{ status?: string }>().status ?? status;
        break;
      } catch {
        // Older Gluetun only has the OpenVPN route.
      }
    }
    const ip = (await request(s, "/v1/publicip/ip")).json<{ public_ip?: string; country?: string }>();
    const details: ServiceDetail[] = [
      { label: "Tunnel", value: status === "running" ? "Actif" : status, tone: status === "running" ? "success" : "warning" },
      ...(ip.public_ip ? [{ label: "IP publique", value: ip.public_ip }] : []),
      ...(ip.country ? [{ label: "Pays", value: ip.country }] : []),
    ];
    try {
      const port = (await request(s, "/v1/portforward")).json<{ port?: number }>().port;
      if (port) details.push({ label: "Port redirigé", value: String(port) });
    } catch {
      // Port forwarding is optional.
    }
    return { value: ip.country ?? ip.public_ip ?? "—", label: status === "running" ? "VPN actif" : `VPN ${status}`, details };
  },
  qbittorrent: async (s) => {
    for (const base of baseUrls(s)) {
      for (const force of [false, true]) {
        try {
          const cookie = await qbittorrentCookie(s, base, force);
          const info = (await requestAt(s, base, "/api/v2/transfer/info", { noAuth: true, headers: { Cookie: cookie } })).json<{
            dl_info_speed?: number;
            up_info_speed?: number;
          }>();
          const details: ServiceDetail[] = [
            { label: "Réception", value: speed(num(info.dl_info_speed)) },
            { label: "Envoi", value: speed(num(info.up_info_speed)) },
          ];
          try {
            const torrents = asArray(
              (await requestAt(s, base, "/api/v2/torrents/info?filter=active", { noAuth: true, headers: { Cookie: cookie } })).json()
            );
            details.push({ label: "Torrents actifs", value: fmt(torrents.length) });
          } catch {
            // The transfer numbers are enough.
          }
          return { value: `↓ ${speed(num(info.dl_info_speed))}`, sub: `↑ ${speed(num(info.up_info_speed))}`, label: "transfert", details };
        } catch (error) {
          if (!(error instanceof ServiceHttpError && error.status === 403) || force) throw error;
        }
      }
    }
    throw new Error("unreachable");
  },
  peanut: async (s) => {
    const devices = asArray((await request(s, "/api/v1/devices")).json()) as Record<string, unknown>[];
    const charges = devices.map((device) => Number(device["battery.charge"])).filter((value) => !Number.isNaN(value));
    const onBattery = devices.some((device) => String(device["ups.status"] ?? "").split(" ").includes("OB"));
    const details: ServiceDetail[] = devices.map((device, index) => {
      const status = String(device["ups.status"] ?? "");
      const charge = Number(device["battery.charge"]);
      const name = String(device["device.model"] ?? device["ups.model"] ?? device.name ?? `Onduleur ${index + 1}`);
      const onBat = status.split(" ").includes("OB");
      return {
        label: name,
        value: `${Number.isNaN(charge) ? "—" : `${Math.round(charge)} %`}${onBat ? " · sur batterie" : status ? " · secteur" : ""}`,
        tone: onBat ? "warning" : undefined,
      };
    });
    return charges.length
      ? { details, value: `${Math.round(Math.min(...charges))} %`, label: onBattery ? "sur batterie" : "batterie" }
      : { value: fmt(devices.length), label: "onduleurs", details };
  },
  safeline: async (s) => {
    const attack = (await request(s, "/api/stat/basic/attack")).json<{ data?: { intercept?: { value?: number }[] } }>();
    const blocked = asArray(attack.data?.intercept).reduce<number>((sum, node) => sum + num((node as { value?: number }).value), 0);
    return { value: fmt(blocked), label: "requêtes bloquées" };
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
        const data = response.json<unknown>();
        if (!Array.isArray(data)) {
          if (!force) continue; // "Unauthorized Access" object: renew the session once.
          throw new ServiceHttpError(401, "AMP session rejected");
        }
        const instances = data
          .flatMap((target) => asArray((target as { AvailableInstances?: unknown[] }).AvailableInstances))
          .map((instance) => instance as { Module?: string; Running?: boolean; AppState?: number; InstanceName?: string; FriendlyName?: string })
          .filter((instance) => instance.Module !== "ADS");
        const running = instances.filter((instance) => instance.Running && instance.AppState === 20).length;
        const details: ServiceDetail[] = instances.map((instance) => {
          const up = Boolean(instance.Running && instance.AppState === 20);
          return { label: instance.FriendlyName ?? instance.InstanceName ?? instance.Module ?? "Instance", value: up ? "En ligne" : "Arrêté", tone: up ? "success" : undefined };
        });
        return { value: fmt(running), sub: `/ ${fmt(instances.length)}`, label: "serveurs en ligne", details };
      }
    }
    throw new Error("unreachable");
  },
  uptime_kuma: async (s) => {
    const metrics = (await request(s, "/metrics", { headers: { Accept: "text/plain" } })).text;
    const statuses = metrics.split("\n").filter((line) => line.startsWith("monitor_status{"));
    const up = statuses.filter((line) => line.trim().endsWith(" 1")).length;
    const down = statuses
      .filter((line) => !line.trim().endsWith(" 1"))
      .map((line) => /monitor_name="([^"]*)"/.exec(line)?.[1])
      .filter((name): name is string => Boolean(name));
    const details: ServiceDetail[] = down.length
      ? down.slice(0, 8).map((name) => ({ label: name, value: "Hors ligne", tone: "destructive" as const }))
      : [{ label: "Sondes", value: "Toutes en ligne", tone: "success" }];
    return { value: fmt(up), sub: `/ ${fmt(statuses.length)}`, label: "sondes en ligne", details };
  },
  healthchecks: async (s) => {
    const checks = asArray((await request(s, "/api/v3/checks/")).json<{ checks?: unknown[] }>().checks) as { status?: string }[];
    return { value: fmt(checks.filter((check) => check.status === "up").length), sub: `/ ${fmt(checks.length)}`, label: "checks OK" };
  },
  proxmox: async (s) => {
    const resources = asArray((await request(s, "/api2/json/cluster/resources?type=vm")).json<{ data?: unknown[] }>().data) as {
      status?: string;
      name?: string;
      vmid?: number;
      type?: string;
    }[];
    const details: ServiceDetail[] = resources.slice(0, 10).map((guest) => ({
      label: `${guest.name ?? guest.vmid ?? "?"}${guest.type === "lxc" ? " (LXC)" : ""}`,
      value: guest.status === "running" ? "Actif" : (guest.status ?? "—"),
      tone: guest.status === "running" ? ("success" as const) : undefined,
    }));
    return { details, value: fmt(resources.filter((guest) => guest.status === "running").length), sub: `/ ${fmt(resources.length)}`, label: "VM/LXC actives" };
  },
  portainer: async (s) => {
    const endpoints = asArray((await request(s, "/api/endpoints")).json()) as { Id?: number }[];
    let running = 0;
    let total = 0;
    for (const endpoint of endpoints) {
      if (endpoint.Id === undefined) continue;
      const containers = asArray((await request(s, `/api/endpoints/${endpoint.Id}/docker/containers/json?all=1`)).json()) as { State?: string }[];
      total += containers.length;
      running += containers.filter((container) => container.State === "running").length;
    }
    return { value: fmt(running), sub: `/ ${fmt(total)}`, label: "conteneurs" };
  },
  adguard_home: async (s) => {
    const stats = (await request(s, "/control/stats")).json<{ num_dns_queries?: number; num_blocked_filtering?: number }>();
    const queries = num(stats.num_dns_queries);
    const blocked = num(stats.num_blocked_filtering);
    const details: ServiceDetail[] = [
      { label: "Requêtes", value: fmt(queries) },
      { label: "Bloquées", value: fmt(blocked) },
      { label: "Taux de blocage", value: queries ? `${((blocked / queries) * 100).toFixed(1)} %` : "—" },
    ];
    return { details, value: fmt(num(stats.num_dns_queries)), sub: `${fmt(num(stats.num_blocked_filtering))} bloquées`, label: "requêtes DNS" };
  },
  gitea: async (s) => {
    const response = await request(s, "/api/v1/user/repos?limit=1");
    const total = Number(response.headers.get("x-total-count") ?? asArray(response.json()).length);
    return { value: fmt(total), label: "dépôts" };
  },
  plex: async (s) => {
    const sections = (await request(s, "/library/sections")).json<{ MediaContainer?: { size?: number } }>();
    return { value: fmt(num(sections.MediaContainer?.size)), label: "bibliothèques" };
  },
};

/** Live summary for one service. Services without an adapter only report reachability. */
export async function summarize(service: ServiceWithSecrets): Promise<ServiceSummary> {
  return { ...(await summarizeInner(service)), checkedAt: new Date().toISOString() };
}

async function summarizeInner(service: ServiceWithSecrets): Promise<ServiceSummary> {
  const started = Date.now();
  const adapter = ADAPTERS[service.type];
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
    // An adapter failure (unexpected payload, missing credentials) does not mean the service is down.
    if (adapter && (await isReachable(service))) {
      return { status: "online", error: "Données indisponibles", latencyMs: Date.now() - started };
    }
    return { status: "offline", error: "Injoignable", latencyMs: Date.now() - started };
  }
}
