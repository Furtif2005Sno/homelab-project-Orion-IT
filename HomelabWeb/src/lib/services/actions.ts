import "server-only";
import type { ServiceWithSecrets } from "../db";
import { baseUrls, request, requestAt, ServiceHttpError } from "./http";
import { ampSession, asArray, asObject, json, piholeSid, qbittorrentRequest, str } from "./summaries";

// Service actions, ported from the app (MediaArrRepository.runAction, PortainerRepository,
// PiholeRepository.setBlocking, AmpRepository.runAction, UnifiRepository.authorizeGuest…).

export interface DashAction {
  id: string;
  label: string;
  params?: Record<string, string>;
  tone?: "primary" | "destructive";
  /** Ask before running (destructive or heavy actions). */
  confirm?: string;
}

type Params = Record<string, string>;
type Runner = (service: ServiceWithSecrets, params: Params) => Promise<string>;

const form = (values: Record<string, string>) => new URLSearchParams(values).toString();
const requireParam = (params: Params, key: string) => {
  const value = params[key];
  if (!value) throw new ActionError(`Paramètre manquant : ${key}`);
  return value;
};

export class ActionError extends Error {}

// ---- qBittorrent ------------------------------------------------------------------------------

/** qBittorrent 5 renamed pause/resume to stop/start: try the app's route, then the new one. */
async function qbit(service: ServiceWithSecrets, paths: string[], body?: string) {
  let lastError: unknown = null;
  for (const path of paths) {
    try {
      await qbittorrentRequest(service, path, { method: "POST", body: body ?? "" });
      return;
    } catch (error) {
      lastError = error;
      if (!(error instanceof ServiceHttpError && error.status === 404)) throw error;
    }
  }
  throw lastError;
}

const hashes = (params: Params) => form({ hashes: params.hash ?? "all" });

const QBIT: Record<string, Runner> = {
  pause: async (s, p) => (await qbit(s, ["/api/v2/torrents/pause", "/api/v2/torrents/stop"], hashes(p)), p.hash ? "Torrent mis en pause" : "Tous les torrents sont en pause"),
  resume: async (s, p) => (await qbit(s, ["/api/v2/torrents/resume", "/api/v2/torrents/start"], hashes(p)), p.hash ? "Torrent repris" : "Tous les torrents ont repris"),
  recheck: async (s, p) => (await qbit(s, ["/api/v2/torrents/recheck"], hashes(p)), "Vérification lancée"),
  reannounce: async (s, p) => (await qbit(s, ["/api/v2/torrents/reannounce"], hashes(p)), "Annonce relancée"),
  toggle_alt_speed: async (s) => (await qbit(s, ["/api/v2/transfer/toggleSpeedLimitsMode"]), "Limite de vitesse alternative basculée"),
  delete: async (s, p) => (await qbit(s, ["/api/v2/torrents/delete"], form({ hashes: requireParam(p, "hash"), deleteFiles: "false" })), "Torrent supprimé"),
  delete_data: async (s, p) => (await qbit(s, ["/api/v2/torrents/delete"], form({ hashes: requireParam(p, "hash"), deleteFiles: "true" })), "Torrent et fichiers supprimés"),
};

// ---- Servarr commands -------------------------------------------------------------------------

/** POST /api/vX/command, trying the app's candidate names in order (they differ across versions). */
async function arrCommand(service: ServiceWithSecrets, api: "v1" | "v3", candidates: string[]) {
  let lastError: unknown = null;
  for (const name of candidates) {
    try {
      await request(service, `/api/${api}/command`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ name }) });
      return name;
    } catch (error) {
      lastError = error;
      if (error instanceof ServiceHttpError && (error.status === 401 || error.status === 403)) throw error;
    }
  }
  throw lastError ?? new ActionError("Commande refusée");
}

const arr = (api: "v1" | "v3", commands: Record<string, [string[], string]>): Record<string, Runner> =>
  Object.fromEntries(Object.entries(commands).map(([id, [candidates, message]]) => [id, async (s: ServiceWithSecrets) => (await arrCommand(s, api, candidates), message)]));

const RADARR = arr("v3", {
  search_missing: [["MissingMoviesSearch", "MoviesSearch", "MovieSearch"], "Recherche des films manquants lancée"],
  rss_sync: [["RssSync", "RSSSync"], "Synchronisation RSS lancée"],
  refresh: [["RefreshMovie", "RefreshMovies", "RefreshMonitoredDownloads"], "Actualisation lancée"],
  rescan: [["RescanFolders", "RescanMovie", "RescanMovieFiles"], "Analyse des dossiers lancée"],
  downloaded_scan: [["DownloadedMoviesScan", "CheckForFinishedDownload"], "Analyse des téléchargements lancée"],
  health_check: [["HealthCheck", "CheckHealth"], "Contrôle de santé lancé"],
});
const SONARR = arr("v3", {
  search_missing: [["MissingEpisodeSearch", "SeriesSearch", "EpisodeSearch"], "Recherche des épisodes manquants lancée"],
  rss_sync: [["RssSync", "RSSSync"], "Synchronisation RSS lancée"],
  refresh: [["RefreshSeries", "RefreshMonitoredDownloads"], "Actualisation lancée"],
  rescan: [["RescanSeries", "RescanSeriesPaths", "RescanFolders"], "Analyse des dossiers lancée"],
  downloaded_scan: [["DownloadedEpisodesScan", "CheckForFinishedDownload"], "Analyse des téléchargements lancée"],
  health_check: [["HealthCheck", "CheckHealth"], "Contrôle de santé lancé"],
});
const LIDARR = arr("v1", {
  search_missing: [["MissingAlbumSearch", "AlbumSearch", "ArtistSearch"], "Recherche des albums manquants lancée"],
  rss_sync: [["RssSync", "RSSSync"], "Synchronisation RSS lancée"],
  refresh: [["RefreshArtist", "RefreshMonitoredDownloads"], "Actualisation lancée"],
  rescan: [["RescanFolders", "RescanArtist"], "Analyse des dossiers lancée"],
  downloaded_scan: [["DownloadedAlbumsScan", "CheckForFinishedDownload"], "Analyse des téléchargements lancée"],
  health_check: [["HealthCheck", "CheckHealth"], "Contrôle de santé lancé"],
});
const PROWLARR: Record<string, Runner> = {
  test_indexers: async (s) => (await request(s, "/api/v1/indexer/testall", { method: "POST", headers: { "Content-Type": "application/json" }, body: "{}" }), "Test des indexeurs lancé"),
  ...arr("v1", {
    sync_apps: [["ApplicationIndexerSync", "ApplicationSync", "IndexerSync"], "Synchronisation des applications lancée"],
    health_check: [["HealthCheck", "CheckHealth"], "Contrôle de santé lancé"],
  }),
};

export const ARR_TOOLBAR: DashAction[] = [
  { id: "search_missing", label: "Rechercher les manquants", tone: "primary" },
  { id: "rss_sync", label: "Synchro RSS" },
  { id: "refresh", label: "Actualiser la bibliothèque" },
  { id: "rescan", label: "Analyser les dossiers" },
  { id: "downloaded_scan", label: "Analyser les téléchargements" },
  { id: "health_check", label: "Contrôle de santé" },
];

// ---- Seerr ------------------------------------------------------------------------------------

async function seerrJob(service: ServiceWithSecrets, keywords: string[]) {
  const jobs = asArray(json(await request(service, "/api/v1/settings/jobs"))).map(asObject);
  const job = jobs.find((candidate) => keywords.some((keyword) => `${str(candidate.id)} ${str(candidate.name)}`.toLowerCase().includes(keyword)));
  if (!job) throw new ActionError("Tâche introuvable sur ce serveur");
  await request(service, `/api/v1/settings/jobs/${encodeURIComponent(str(job.id))}/run`, { method: "POST" });
  return str(job.name) || str(job.id);
}

const SEERR: Record<string, Runner> = {
  approve: async (s, p) => (await request(s, `/api/v1/request/${encodeURIComponent(requireParam(p, "id"))}/approve`, { method: "POST" }), "Demande approuvée"),
  decline: async (s, p) => (await request(s, `/api/v1/request/${encodeURIComponent(requireParam(p, "id"))}/decline`, { method: "POST" }), "Demande refusée"),
  recent_scan: async (s) => `Lancé : ${await seerrJob(s, ["recently", "added", "new"])}`,
  full_scan: async (s) => `Lancé : ${await seerrJob(s, ["full", "library", "scan"])}`,
};

// ---- autobrr, Gluetun, FlareSolverr -----------------------------------------------------------

const AUTOBRR: Record<string, Runner> = {
  filter_enable: async (s, p) => (
    await request(s, `/api/filters/${encodeURIComponent(requireParam(p, "id"))}/enabled`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ enabled: true }) }), "Filtre activé"
  ),
  filter_disable: async (s, p) => (
    await request(s, `/api/filters/${encodeURIComponent(requireParam(p, "id"))}/enabled`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ enabled: false }) }), "Filtre désactivé"
  ),
};

const GLUETUN: Record<string, Runner> = {
  restart_vpn: async (s) => {
    const put = (status: string) => request(s, "/v1/vpn/status", { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ status }) });
    try {
      await put("stopped");
      await put("running");
    } catch (error) {
      // Older Gluetun only has the OpenVPN route.
      if (!(error instanceof ServiceHttpError && error.status === 404)) throw error;
      const legacy = (status: string) => request(s, "/v1/openvpn/status", { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ status }) });
      await legacy("stopped");
      await legacy("running");
    }
    return "Tunnel VPN redémarré";
  },
};

const flare = (s: ServiceWithSecrets, payload: object) => request(s, "/v1", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
const FLARESOLVERR: Record<string, Runner> = {
  create_session: async (s) => (await flare(s, { cmd: "sessions.create" }), "Session créée"),
  destroy_session: async (s, p) => (await flare(s, { cmd: "sessions.destroy", session: requireParam(p, "session") }), "Session fermée"),
};

// ---- Portainer --------------------------------------------------------------------------------

const container = (verb: "start" | "stop" | "restart", message: string): Runner => async (s, p) => {
  try {
    await request(s, `/api/endpoints/${encodeURIComponent(requireParam(p, "endpoint"))}/docker/containers/${encodeURIComponent(requireParam(p, "container"))}/${verb}`, { method: "POST" });
  } catch (error) {
    // Docker answers 304 when the container is already in that state: not an error.
    if (!(error instanceof ServiceHttpError && error.status === 304)) throw error;
  }
  return message;
};
const PORTAINER: Record<string, Runner> = {
  start: container("start", "Conteneur démarré"),
  stop: container("stop", "Conteneur arrêté"),
  restart: container("restart", "Conteneur redémarré"),
};

// ---- Pi-hole ----------------------------------------------------------------------------------

async function piholeBlocking(s: ServiceWithSecrets, blocking: boolean, timer?: number) {
  let lastError: unknown = null;
  for (const base of baseUrls(s)) {
    for (const force of [false, true]) {
      try {
        const sid = await piholeSid(s, base, force);
        await requestAt(s, base, "/api/dns/blocking", {
          method: "POST",
          noAuth: true,
          headers: { "Content-Type": "application/json", ...(sid ? { "X-FTL-SID": sid } : {}) },
          body: JSON.stringify({ blocking, timer: timer ?? null }),
        });
        return;
      } catch (error) {
        lastError = error;
        if (!(error instanceof ServiceHttpError && error.status === 401) || force) break;
      }
    }
  }
  throw lastError;
}
const PIHOLE: Record<string, Runner> = {
  enable: async (s) => (await piholeBlocking(s, true), "Blocage activé"),
  disable: async (s, p) => {
    const minutes = Number(p.minutes ?? 0);
    await piholeBlocking(s, false, minutes > 0 ? minutes * 60 : undefined);
    return minutes > 0 ? `Blocage désactivé pour ${minutes} min` : "Blocage désactivé";
  },
};

// ---- AMP --------------------------------------------------------------------------------------

async function ampCall(s: ServiceWithSecrets, method: string, body: Record<string, unknown>) {
  for (const base of baseUrls(s)) {
    for (const force of [false, true]) {
      const session = await ampSession(s, base, force);
      const response = await requestAt(s, base, `/API/${method}`, {
        method: "POST",
        noAuth: true,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ SESSIONID: session, ...body }),
      });
      const data = asObject(json(response));
      if (str(data.Title).toLowerCase().includes("unauthorized")) {
        if (!force) continue;
        throw new ServiceHttpError(401, "AMP session rejected");
      }
      return;
    }
  }
}
const amp = (verb: "Start" | "Stop" | "Restart", message: string): Runner => async (s, p) => (await ampCall(s, `ADSModule/${verb}Instance`, { InstanceName: requireParam(p, "instance") }), message);
const AMP: Record<string, Runner> = {
  start: amp("Start", "Instance démarrée"),
  stop: amp("Stop", "Instance arrêtée"),
  restart: amp("Restart", "Instance redémarrée"),
};

// ---- UniFi ------------------------------------------------------------------------------------

const UNIFI: Record<string, Runner> = {
  authorize_guest: async (s, p) => {
    const path = `sites/${encodeURIComponent(requireParam(p, "site"))}/clients/${encodeURIComponent(requireParam(p, "client"))}/actions`;
    const body = JSON.stringify({ action: "AUTHORIZE_GUEST_ACCESS", timeLimitMinutes: 120 });
    const headers = { "Content-Type": "application/json" };
    if (/api\.ui\.com|unifi\.ui\.com/i.test(s.url)) {
      await requestAt(s, "https://api.ui.com", `/v1/${path}`, { method: "POST", headers, body });
    } else {
      try {
        await request(s, `/proxy/network/integration/v1/${path}`, { method: "POST", headers, body });
      } catch (error) {
        if (error instanceof ServiceHttpError && (error.status === 401 || error.status === 403)) throw error;
        await request(s, `/v1/${path}`, { method: "POST", headers, body });
      }
    }
    return "Accès invité autorisé pour 2 h";
  },
};

// ---- Registry ---------------------------------------------------------------------------------

const RUNNERS: Partial<Record<string, Record<string, Runner>>> = {
  qbittorrent: QBIT,
  radarr: RADARR,
  sonarr: SONARR,
  lidarr: LIDARR,
  prowlarr: PROWLARR,
  jellyseerr: SEERR,
  autobrr: AUTOBRR,
  gluetun: GLUETUN,
  flaresolverr: FLARESOLVERR,
  portainer: PORTAINER,
  pihole: PIHOLE,
  amp: AMP,
  unifi_network: UNIFI,
};

/** Extra runners registered by other modules (services ported later). */
export function registerActions(type: string, runners: Record<string, Runner>) {
  RUNNERS[type] = { ...(RUNNERS[type] ?? {}), ...runners };
}

export async function runAction(service: ServiceWithSecrets, actionId: string, params: Params): Promise<string> {
  const runner = RUNNERS[service.type]?.[actionId];
  if (!runner) throw new ActionError("Action inconnue pour ce service");
  return runner(service, params);
}

export type { Runner as ActionRunner };
