import "server-only";
import type { ServiceWithSecrets } from "../db";
import { baseUrls, request, requestAt, ServiceHttpError } from "./http";
import {
  ampSession,
  asArray,
  asObject,
  beszelToken,
  capitalize,
  firstAvailable,
  firstNonBlank,
  fmt,
  get,
  json,
  num,
  piholeSid,
  qbittorrentGet,
  qbittorrentRequest,
  speed,
  str,
  safelineData,
  summarize,
  tryGet,
  versionBranch,
  type Json,
} from "./summaries";
import { ARR_TOOLBAR, type DashAction } from "./actions";
import { MORE_DASHBOARDS } from "./more-services";

export type { DashAction };

// Full service dashboards, ported from the app's dashboard screens (their repositories'
// getDashboard / snapshot functions). One generic shape, rendered by one web component.

export type Tone = "success" | "warning" | "destructive" | "info";

export interface DashStat {
  label: string;
  value: string;
  sub?: string;
  tone?: Tone;
}

export interface DashRow {
  title: string;
  subtitle?: string;
  meta?: string;
  badge?: { label: string; tone?: Tone };
  /** 0..1 progress bar under the row. */
  progress?: number;
  /** Small labelled gauges (CPU, RAM…), 0..100. */
  gauges?: { label: string; value: number }[];
  actions?: DashAction[];
}

export type DashSection =
  | { kind: "stats"; title?: string; items: DashStat[] }
  | { kind: "rows"; title: string; items: DashRow[]; empty?: string; searchable?: boolean }
  | { kind: "ranking"; title: string; items: { label: string; value: number; display?: string }[]; empty?: string }
  | { kind: "chart"; title: string; unit?: string; series: { name: string; points: [number, number][] }[] }
  | { kind: "notes"; title: string; tone: Tone; items: string[] }
  | { kind: "facts"; title: string; items: { label: string; value: string }[] };

export interface ServiceDashboard {
  subtitle?: string;
  /** Service-wide actions, shown as a toolbar. */
  actions?: DashAction[];
  sections: DashSection[];
}

type Builder = (service: ServiceWithSecrets) => Promise<ServiceDashboard>;

// ---- Helpers --------------------------------------------------------------------------------

/** Same as the app's bytesLabel: binary units. */
function bytes(value: number): string {
  if (!Number.isFinite(value) || value < 0) return "N/A";
  const units = ["o", "Ko", "Mo", "Go", "To", "Po"];
  let size = value;
  let unit = 0;
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024;
    unit++;
  }
  return `${size.toFixed(size < 10 && unit > 0 ? 1 : 0)} ${units[unit]}`;
}

function duration(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds <= 0) return "—";
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (days > 0) return `${days} j ${hours} h`;
  if (hours > 0) return `${hours} h ${minutes} min`;
  return `${minutes} min`;
}

const date = (value: unknown) => {
  const raw = str(value);
  if (!raw) return undefined;
  const parsed = new Date(/^\d+$/.test(raw) ? Number(raw) * (raw.length <= 10 ? 1000 : 1) : raw);
  return Number.isNaN(parsed.getTime()) ? raw : parsed.toLocaleString("fr-FR", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
};

const records = (value: unknown): Json[] => asArray(asObject(value).records ?? asObject(value).data ?? value) as Json[];

const facts = (title: string, items: [string, unknown][]): DashSection | null => {
  const filled = items.filter(([, value]) => str(value)).map(([label, value]) => ({ label, value: str(value) }));
  return filled.length ? { kind: "facts", title, items: filled } : null;
};

const sections = (...items: (DashSection | null | false | undefined)[]) => items.filter((item): item is DashSection => Boolean(item));

// ---- Media (MediaArrRepository snapshots) ---------------------------------------------------

const QBIT_STATES: Record<string, { label: string; tone?: Tone }> = {
  downloading: { label: "Téléchargement", tone: "info" },
  forceddl: { label: "Téléchargement forcé", tone: "info" },
  metadl: { label: "Métadonnées", tone: "info" },
  uploading: { label: "Partage", tone: "success" },
  forcedup: { label: "Partage forcé", tone: "success" },
  stalledup: { label: "Partage (inactif)", tone: "success" },
  stalleddl: { label: "Bloqué", tone: "warning" },
  queueddl: { label: "En file", tone: "warning" },
  queuedup: { label: "En file", tone: "warning" },
  pauseddl: { label: "En pause" },
  pausedup: { label: "Terminé" },
  stoppeddl: { label: "Arrêté" },
  stoppedup: { label: "Terminé" },
  error: { label: "Erreur", tone: "destructive" },
  missingfiles: { label: "Fichiers manquants", tone: "destructive" },
  checkingdl: { label: "Vérification" },
  checkingup: { label: "Vérification" },
};

const qbittorrent: Builder = async (s) => {
  const [versionResponse, transfer, torrentsRaw] = await Promise.all([
    qbittorrentRequest(s, "/api/v2/app/version"),
    qbittorrentGet(s, "/api/v2/transfer/info"),
    qbittorrentGet(s, "/api/v2/torrents/info?filter=all&sort=added_on&reverse=true"),
  ]);
  const info = asObject(transfer);
  const torrents = asArray(torrentsRaw) as Json[];
  let downloading = 0;
  let seeding = 0;
  let stalled = 0;
  for (const torrent of torrents) {
    const state = str(torrent.state).toLowerCase();
    if (["downloading", "forceddl", "metadl"].includes(state)) downloading++;
    if (["uploading", "forcedup", "stalledup"].includes(state)) seeding++;
    if (["stalleddl", "stalledup", "queueddl", "queuedup"].includes(state)) stalled++;
  }
  const freeSpace = info.free_space_on_disk !== undefined ? num(info.free_space_on_disk) : -1;
  return {
    subtitle: [versionResponse.text.trim(), str(info.connection_status)].filter(Boolean).join(" · "),
    actions: [
      { id: "resume", label: "Tout reprendre", tone: "primary" },
      { id: "pause", label: "Tout mettre en pause" },
      { id: "toggle_alt_speed", label: info.use_alt_speed_limits ? "Désactiver la vitesse alternative" : "Activer la vitesse alternative" },
      { id: "reannounce", label: "Réannoncer tout" },
      { id: "recheck", label: "Vérifier tout", confirm: "Revérifier les données de tous les torrents ? Cela peut prendre du temps." },
    ],
    sections: sections(
      {
        kind: "stats",
        items: [
          { label: "Torrents", value: fmt(torrents.length) },
          { label: "Téléchargement", value: fmt(downloading), tone: downloading ? "info" : undefined },
          { label: "Partage", value: fmt(seeding) },
          { label: "En file / bloqués", value: fmt(stalled) },
          { label: "Réception", value: speed(num(info.dl_info_speed)) },
          { label: "Envoi", value: speed(num(info.up_info_speed)) },
          { label: "Vitesse alternative", value: info.use_alt_speed_limits ? "Activée" : "Désactivée" },
          { label: "Espace libre", value: freeSpace >= 0 ? bytes(freeSpace) : "N/A" },
          ...(info.dht_nodes !== undefined ? [{ label: "Nœuds DHT", value: fmt(num(info.dht_nodes)) }] : []),
          ...(info.alltime_dl !== undefined ? [{ label: "Téléchargé (total)", value: bytes(num(info.alltime_dl)) }] : []),
        ],
      },
      {
        kind: "rows",
        title: "Torrents",
        searchable: true,
        empty: "Aucun torrent",
        items: torrents.map((torrent) => {
          const state = QBIT_STATES[str(torrent.state).toLowerCase()] ?? { label: str(torrent.state) };
          const progress = num(torrent.progress);
          const eta = num(torrent.eta);
          return {
            title: str(torrent.name),
            subtitle: [
              `${bytes(num(torrent.completed ?? num(torrent.size) * progress))} / ${bytes(num(torrent.size))}`,
              num(torrent.dlspeed) > 0 ? `↓ ${speed(num(torrent.dlspeed))}` : null,
              num(torrent.upspeed) > 0 ? `↑ ${speed(num(torrent.upspeed))}` : null,
              torrent.ratio !== undefined ? `ratio ${num(torrent.ratio).toFixed(2)}` : null,
              str(torrent.category) || null,
            ]
              .filter(Boolean)
              .join(" · "),
            meta: progress < 1 && eta > 0 && eta < 8640000 ? duration(eta) : undefined,
            badge: state,
            progress,
            actions: [
              /^(paused|stopped)/.test(str(torrent.state).toLowerCase())
                ? { id: "resume", label: "Reprendre", params: { hash: str(torrent.hash) } }
                : { id: "pause", label: "Mettre en pause", params: { hash: str(torrent.hash) } },
              { id: "reannounce", label: "Réannoncer", params: { hash: str(torrent.hash) } },
              { id: "recheck", label: "Vérifier", params: { hash: str(torrent.hash) } },
              { id: "delete", label: "Supprimer", tone: "destructive" as const, params: { hash: str(torrent.hash) }, confirm: `Supprimer « ${str(torrent.name)} » ? Les fichiers sont conservés.` },
              { id: "delete_data", label: "Supprimer avec les fichiers", tone: "destructive" as const, params: { hash: str(torrent.hash) }, confirm: `Supprimer « ${str(torrent.name)} » et ses fichiers ? Action irréversible.` },
            ],
          };
        }),
      }
    ),
  };
};

const ARR_EVENTS: Record<string, string> = {
  grabbed: "Récupéré",
  downloadFolderImported: "Importé",
  downloadFailed: "Échec",
  movieFileDeleted: "Supprimé",
  episodeFileDeleted: "Supprimé",
  movieFileRenamed: "Renommé",
  episodeFileRenamed: "Renommé",
  downloadIgnored: "Ignoré",
  trackFileImported: "Importé",
  albumImportIncomplete: "Import incomplet",
};

function arrDownloads(queue: unknown): DashRow[] {
  return records(queue)
    .slice(0, 12)
    .map((row) => {
      const title = firstNonBlank(row.title, row.sourceTitle, asObject(row.movie).title, asObject(row.series).title, asObject(row.album).title, row.artistName) ?? "—";
      const total = num(row.size) || num(row.totalSize);
      const left = row.sizeleft ?? row.sizeLeft ?? row.remainingSize;
      const remaining = left === undefined ? undefined : num(left);
      const progress = total > 0 && remaining !== undefined ? Math.min(1, Math.max(0, (total - remaining) / total)) : undefined;
      const eta = firstNonBlank(row.timeleft, row.timeLeft);
      return {
        title,
        subtitle: [total > 0 && remaining !== undefined ? `${bytes(total - remaining)} / ${bytes(total)}` : total > 0 ? bytes(total) : null, firstNonBlank(row.status, row.trackedDownloadStatus, row.protocol)]
          .filter(Boolean)
          .join(" · "),
        meta: eta && eta !== "00:00:00" ? eta : undefined,
        progress,
      };
    });
}

function arrHistory(history: unknown): DashRow[] {
  return records(history)
    .slice(0, 20)
    .map((row) => {
      const event = str(row.eventType);
      return {
        title: firstNonBlank(row.sourceTitle, row.title, asObject(row.movie).title, asObject(row.series).title, asObject(row.album).title, row.artistName) ?? "—",
        subtitle: date(row.date),
        badge: event ? { label: ARR_EVENTS[event] ?? event, tone: event.includes("Failed") ? ("destructive" as const) : event.includes("mported") ? ("success" as const) : undefined } : undefined,
      };
    });
}

function arrBuilder(kind: "movie" | "series" | "artist"): Builder {
  const api = kind === "artist" ? "v1" : "v3";
  const libraryPath = kind === "movie" ? "/api/v3/movie" : kind === "series" ? "/api/v3/series" : "/api/v1/artist";
  return async (s) => {
    const day = (value: Date) => value.toISOString().slice(0, 10);
    const now = new Date();
    const end = new Date(now.getTime() + 14 * 24 * 3600 * 1000);
    const [status, library, queue, history, health, upcoming] = await Promise.all([
      get(s, `/api/${api}/system/status`),
      get(s, libraryPath),
      get(s, `/api/${api}/queue?page=1&pageSize=20&sortDirection=descending`),
      get(s, `/api/${api}/history?page=1&pageSize=20&sortDirection=descending&sortKey=date`),
      get(s, `/api/${api}/health`),
      get(s, `/api/${api}/calendar?start=${day(now)}&end=${day(end)}`),
    ]);
    const items = asArray(library) as Json[];
    const statusObj = asObject(status);
    const queueObj = asObject(queue);
    const healthRows = asArray(health) as Json[];
    const upcomingRows = asArray(upcoming) as Json[];
    const latest = [...items].sort((a, b) => str(b.added).localeCompare(str(a.added))).slice(0, 12);
    return {
      subtitle: versionBranch(statusObj),
      actions: ARR_TOOLBAR,
      sections: sections(
        {
          kind: "stats",
          items: [
            { label: kind === "movie" ? "Films" : kind === "series" ? "Séries" : "Artistes", value: fmt(items.length) },
            { label: "Surveillés", value: fmt(items.filter((item) => item.monitored === true).length) },
            { label: "Téléchargements", value: fmt(num(queueObj.totalRecords ?? queueObj.recordsTotal)) },
            { label: "Problèmes de santé", value: fmt(healthRows.length), tone: healthRows.length ? "warning" : undefined },
            { label: "À venir (14 j)", value: fmt(upcomingRows.length) },
          ],
        },
        healthRows.length > 0 && { kind: "notes", title: "Santé", tone: "warning", items: healthRows.slice(0, 6).map((row) => firstNonBlank(row.message, row.type) ?? "") },
        { kind: "rows", title: "Téléchargements", items: arrDownloads(queue), empty: "Rien en cours" },
        {
          kind: "rows",
          title: "À venir",
          empty: "Rien de prévu sur 14 jours",
          items: upcomingRows.slice(0, 12).map((row) => {
            const series = asObject(row.series);
            const title = str(series.title)
              ? `${str(series.title)} · S${String(num(row.seasonNumber)).padStart(2, "0")}E${String(num(row.episodeNumber)).padStart(2, "0")}`
              : firstNonBlank(row.title, asObject(row.artist).artistName) ?? "—";
            return {
              title,
              subtitle: str(series.title) ? str(row.title) : undefined,
              meta: date(row.airDateUtc ?? row.digitalRelease ?? row.physicalRelease ?? row.inCinemas ?? row.releaseDate),
              badge: row.hasFile ? { label: "Disponible", tone: "success" as const } : undefined,
            };
          }),
        },
        {
          kind: "rows",
          title: "Derniers ajouts",
          items: latest.map((item) => ({
            title: firstNonBlank(item.title, item.artistName) ?? "—",
            subtitle: [num(item.year) > 0 ? String(num(item.year)) : null, str(item.network) || str(item.studio) || null].filter(Boolean).join(" · "),
            meta: date(item.added),
            badge: item.hasFile || num(asObject(item.statistics).percentOfEpisodes) >= 100
              ? { label: "Disponible", tone: "success" as const }
              : item.monitored
                ? { label: "Surveillé", tone: "info" as const }
                : { label: "Non surveillé" },
          })),
        },
        { kind: "rows", title: "Historique récent", items: arrHistory(history), empty: "Aucun événement" }
      ),
    };
  };
}

const REQUEST_STATUS: Record<string, { label: string; tone?: Tone }> = {
  "1": { label: "En attente", tone: "warning" },
  "2": { label: "Approuvée", tone: "info" },
  "3": { label: "Refusée", tone: "destructive" },
  "4": { label: "Partielle", tone: "info" },
  "5": { label: "Disponible", tone: "success" },
};

const jellyseerr: Builder = async (s) => {
  const [status, requestsRaw] = await Promise.all([get(s, "/api/v1/status"), get(s, "/api/v1/request?take=25&skip=0&sort=added&filter=all")]);
  const requests = asObject(requestsRaw);
  const results = asArray(requests.results) as Json[];
  const statusOf = (row: Json) => str(row.status);
  const mediaStatus = (row: Json) => str(asObject(row.media).status);
  const count = (predicate: (row: Json) => boolean) => results.filter(predicate).length;
  const statusObj = asObject(status);
  return {
    subtitle: firstNonBlank(str(statusObj.version) && `v${str(statusObj.version)}`, statusObj.commitTag),
    actions: [
      { id: "recent_scan", label: "Scanner les ajouts récents" },
      { id: "full_scan", label: "Scan complet", confirm: "Lancer un scan complet de la bibliothèque ?" },
    ],
    sections: sections(
      {
        kind: "stats",
        items: [
          { label: "Demandes", value: fmt(num(asObject(requests.pageInfo).results ?? requests.totalResults ?? results.length)) },
          { label: "En attente", value: fmt(count((row) => statusOf(row) === "1")), tone: count((row) => statusOf(row) === "1") ? "warning" : undefined },
          { label: "Approuvées", value: fmt(count((row) => statusOf(row) === "2")) },
          { label: "Disponibles", value: fmt(count((row) => mediaStatus(row) === "5" || statusOf(row) === "5")) },
        ],
      },
      {
        kind: "rows",
        title: "Demandes récentes",
        empty: "Aucune demande",
        items: results.map((row) => {
          const media = asObject(row.media);
          const available = mediaStatus(row) === "5";
          return {
            title: firstNonBlank(media.title, media.name, asObject(row.media).externalServiceSlug) ?? `${str(row.type) === "tv" ? "Série" : "Film"} #${num(media.tmdbId)}`,
            subtitle: [str(row.type) === "tv" ? "Série" : "Film", firstNonBlank(asObject(row.requestedBy).displayName, asObject(row.requestedBy).username)].filter(Boolean).join(" · "),
            meta: date(row.createdAt),
            badge: available ? REQUEST_STATUS["5"] : REQUEST_STATUS[statusOf(row)] ?? { label: statusOf(row) },
            actions:
              statusOf(row) === "1"
                ? [
                    { id: "approve", label: "Approuver", tone: "primary" as const, params: { id: str(row.id) } },
                    { id: "decline", label: "Refuser", tone: "destructive" as const, params: { id: str(row.id) }, confirm: "Refuser cette demande ?" },
                  ]
                : undefined,
          };
        }),
      }
    ),
  };
};

const prowlarr: Builder = async (s) => {
  const [status, indexersRaw, healthRaw, apps, history] = await Promise.all([
    get(s, "/api/v1/system/status"),
    get(s, "/api/v1/indexer"),
    get(s, "/api/v1/health"),
    get(s, "/api/v1/applications"),
    get(s, "/api/v1/history?page=1&pageSize=10&sortDirection=descending&sortKey=date"),
  ]);
  const indexers = asArray(indexersRaw) as Json[];
  const health = asArray(healthRaw) as Json[];
  const statusObj = asObject(status);
  const enabled = indexers.filter((indexer) => (indexer.enable ?? indexer.enabled ?? true) !== false).length;
  const unhealthy = indexers.filter((indexer) => /error|down|unhealthy/.test(str(indexer.status).toLowerCase())).length;
  return {
    subtitle: versionBranch(statusObj),
    actions: [
      { id: "test_indexers", label: "Tester les indexeurs", tone: "primary" },
      { id: "sync_apps", label: "Synchroniser les applications" },
      { id: "health_check", label: "Contrôle de santé" },
    ],
    sections: sections(
      {
        kind: "stats",
        items: [
          { label: "Indexeurs", value: fmt(indexers.length), sub: `${enabled} activés` },
          { label: "Applications", value: fmt(asArray(apps).length) },
          { label: "Santé", value: fmt(health.length), tone: health.length ? "warning" : undefined },
          { label: "En erreur", value: fmt(unhealthy), tone: unhealthy ? "destructive" : undefined },
        ],
      },
      health.length > 0 && { kind: "notes", title: "Santé", tone: "warning", items: health.slice(0, 6).map((row) => firstNonBlank(row.message, row.description) ?? "") },
      {
        kind: "rows",
        title: "Indexeurs",
        searchable: true,
        items: indexers
          .sort((a, b) => str(a.name).localeCompare(str(b.name)))
          .map((indexer) => ({
            title: str(indexer.name),
            subtitle: [str(indexer.protocol), str(indexer.privacy)].filter(Boolean).join(" · "),
            badge: (indexer.enable ?? indexer.enabled ?? true) === false ? { label: "Désactivé" } : { label: "Activé", tone: "success" as const },
          })),
      },
      {
        kind: "rows",
        title: "Activité récente",
        items: records(history).map((row) => ({ title: firstNonBlank(asObject(row.data).query, row.sourceTitle, row.title) ?? str(row.eventType), subtitle: str(row.eventType), meta: date(row.date) })),
        empty: "Aucune activité",
      },
      facts("Système", [
        ["Version", statusObj.version],
        ["Branche", statusObj.branch],
        ["Paquet", statusObj.packageVersion],
      ])
    ),
  };
};

const RELEASE_STATUS: Record<string, { label: string; tone?: Tone }> = {
  PUSH_APPROVED: { label: "Approuvée", tone: "success" },
  PUSH_REJECTED: { label: "Rejetée", tone: "warning" },
  PUSH_ERROR: { label: "Erreur", tone: "destructive" },
  PENDING: { label: "En attente" },
  FILTER_REJECTED: { label: "Filtrée" },
};

const autobrr: Builder = async (s) => {
  const filtersRaw = await get(s, "/api/filters"); // required: validates the token, like the app
  const [configRaw, statsRaw, networksRaw, feedsRaw, releasesRaw] = await Promise.all([
    tryGet(s, "/api/config"),
    tryGet(s, "/api/release/stats"),
    tryGet(s, "/api/irc"),
    tryGet(s, "/api/feeds"),
    tryGet(s, "/api/release?limit=20"),
  ]);
  const filters = (asArray(filtersRaw) as Json[]).sort((a, b) => Number(b.enabled === true) - Number(a.enabled === true) || str(a.name).localeCompare(str(b.name)));
  const config = asObject(configRaw);
  const stats = asObject(statsRaw);
  const networks = asArray(networksRaw) as Json[];
  const enabledNetworks = networks.filter((network) => network.enabled !== false);
  const feeds = asArray(feedsRaw) as Json[];
  const releases = asArray(asObject(releasesRaw).data) as Json[];
  const version = str(config.version).replace(/^v/, "");
  return {
    subtitle: version ? `v${version}` : undefined,
    sections: sections(
      {
        kind: "stats",
        items: [
          { label: "Releases", value: fmt(num(stats.total_count)) },
          { label: "Approuvées", value: fmt(num(stats.push_approved_count)), tone: "success" },
          { label: "Rejetées", value: fmt(num(stats.push_rejected_count) + num(stats.filter_rejected_count)) },
          { label: "Erreurs", value: fmt(num(stats.push_error_count)), tone: num(stats.push_error_count) ? "destructive" : undefined },
          { label: "Filtres", value: `${filters.filter((filter) => filter.enabled === true).length}/${filters.length}` },
          { label: "IRC", value: `${enabledNetworks.filter((network) => network.healthy === true).length}/${enabledNetworks.length}` },
          { label: "Flux", value: `${feeds.filter((feed) => feed.enabled === true).length}/${feeds.length}` },
        ],
      },
      enabledNetworks.some((network) => network.healthy !== true) && {
        kind: "notes",
        title: "Réseaux IRC déconnectés",
        tone: "warning",
        items: enabledNetworks.filter((network) => network.healthy !== true).map((network) => firstNonBlank(network.name, network.server) ?? "IRC"),
      },
      {
        kind: "rows",
        title: "Releases récentes",
        empty: "Aucune release",
        searchable: true,
        items: releases.map((row) => {
          const actions = asArray(row.action_status) as Json[];
          const action = actions.find((a) => a.status === "PUSH_APPROVED") ?? actions.find((a) => a.status === "PUSH_ERROR") ?? actions[0];
          const statusKey = action ? str(action.status) : str(row.filter_status) === "FILTER_REJECTED" ? "FILTER_REJECTED" : "PENDING";
          const indexer = typeof row.indexer === "object" ? firstNonBlank(asObject(row.indexer).name, asObject(row.indexer).identifier) : str(row.indexer);
          return {
            title: firstNonBlank(row.name, row.torrent_name) ?? "—",
            subtitle: [indexer, firstNonBlank(row.filter, action?.filter), action ? str(action.client) : null, num(row.size) > 0 ? bytes(num(row.size)) : null].filter(Boolean).join(" · "),
            meta: date(row.timestamp),
            badge: RELEASE_STATUS[statusKey] ?? { label: statusKey },
          };
        }),
      },
      {
        kind: "rows",
        title: "Filtres",
        items: filters.map((filter) => ({
          title: firstNonBlank(filter.name) ?? `#${num(filter.id)}`,
          subtitle: num(filter.priority) ? `Priorité ${num(filter.priority)}` : undefined,
          badge: filter.enabled === true ? { label: "Activé", tone: "success" as const } : { label: "Désactivé" },
          actions: [
            filter.enabled === true
              ? { id: "filter_disable", label: "Désactiver", params: { id: str(filter.id) } }
              : { id: "filter_enable", label: "Activer", tone: "primary" as const, params: { id: str(filter.id) } },
          ],
        })),
      },
      {
        kind: "rows",
        title: "Réseaux IRC",
        items: networks.map((network) => ({
          title: firstNonBlank(network.name, network.server) ?? "IRC",
          subtitle: str(network.server),
          badge: network.enabled === false ? { label: "Désactivé" } : network.healthy ? { label: "Connecté", tone: "success" as const } : { label: "Déconnecté", tone: "destructive" as const },
        })),
      },
      facts("Système", [
        ["Version", version],
        ["Commit", str(config.commit).slice(0, 7)],
        ["Base de données", config.database_type],
      ])
    ),
  };
};

const bazarr: Builder = async (s) => {
  const [status, badgesRaw, healthRaw, tasksRaw] = await Promise.all([get(s, "/api/system/status"), get(s, "/api/badges"), get(s, "/api/system/health"), get(s, "/api/system/tasks")]);
  const badges = asObject(badgesRaw);
  const statusObj = asObject(status);
  const data = asObject(statusObj.data);
  const health = asArray(asObject(healthRaw).data ?? healthRaw) as Json[];
  const tasks = asArray(asObject(tasksRaw).data) as Json[];
  const labels: Record<string, string> = { episodes: "Épisodes manquants", movies: "Films manquants", providers: "Fournisseurs en erreur", wanted: "Recherchés", missing: "Manquants", series: "Séries" };
  const stats = ["wanted", "missing", "episodes", "movies", "series", "providers"]
    .filter((key) => key in badges)
    .map((key) => ({ label: labels[key] ?? capitalize(key), value: str(badges[key]) }));
  return {
    subtitle: firstNonBlank(str(statusObj.version) && `v${str(statusObj.version)}`, str(data.bazarr_version) && `v${str(data.bazarr_version)}`),
    sections: sections(
      { kind: "stats", items: stats.length ? stats : [{ label: "Tâches", value: fmt(tasks.length) }, { label: "Santé", value: fmt(health.length) }] },
      health.length > 0 && { kind: "notes", title: "Santé", tone: "warning", items: health.slice(0, 8).map((row) => firstNonBlank(row.message, row.issue, row.description) ?? "") },
      {
        kind: "rows",
        title: "Tâches planifiées",
        items: tasks.map((task) => ({
          title: firstNonBlank(task.name, task.task) ?? "Tâche",
          subtitle: firstNonBlank(task.interval) ? `Toutes les ${str(task.interval)}` : undefined,
          meta: firstNonBlank(task.next_run_in) ? `dans ${str(task.next_run_in)}` : undefined,
          badge: task.job_running ? { label: "En cours", tone: "info" as const } : undefined,
        })),
        empty: "Aucune tâche",
      },
      facts("Système", [
        ["Version", firstNonBlank(statusObj.version, data.bazarr_version)],
        ["Python", data.python_version],
        ["Sonarr", data.sonarr_version],
        ["Radarr", data.radarr_version],
        ["Système", data.operating_system],
      ])
    ),
  };
};

const gluetun: Builder = async (s) => {
  const vpnResponse = await firstAvailable(s, ["/v1/vpn/status", "/v1/openvpn/status"]);
  if (!vpnResponse) throw new Error("Gluetun control server did not answer");
  const vpn = asObject(json(vpnResponse));
  const ipResponse = await request(s, "/v1/publicip/ip");
  const ip = asObject(json(ipResponse));
  const ipRaw = ipResponse.text.trim().startsWith("{") ? "" : ipResponse.text.trim();
  const forwardedResponse = await firstAvailable(s, ["/v1/portforward", "/v1/openvpn/portforwarded"]);
  const forwarded = forwardedResponse ? asObject(json(forwardedResponse)) : {};
  const forwardedRaw = forwardedResponse && !forwardedResponse.text.trim().startsWith("{") ? forwardedResponse.text.trim() : "";
  const status = firstNonBlank(vpn.status, asObject(vpn.openvpn).status) ?? "Inconnu";
  const ports = asArray(forwarded.ports).map(num).filter((port) => port > 0);
  const single = firstNonBlank(forwarded.port, forwarded.port_forwarded, forwardedRaw);
  const running = status.toLowerCase() === "running";
  const dns = asObject(await tryGet(s, "/v1/dns/status"));
  return {
    subtitle: firstNonBlank(vpn.provider, asObject(vpn.vpn).provider),
    actions: [{ id: "restart_vpn", label: "Redémarrer le VPN", confirm: "Redémarrer le tunnel VPN ? Les connexions en cours seront coupées." }],
    sections: sections(
      {
        kind: "stats",
        items: [
          { label: "Statut", value: running ? "Connecté" : status, tone: running ? "success" : "warning" },
          { label: "IP publique", value: firstNonBlank(ip.public_ip, ip.ip, ipRaw) ?? "N/A" },
          { label: "Port redirigé", value: ports.length ? ports.join(", ") : single && single !== "0" ? single : "N/A" },
          { label: "Fournisseur", value: firstNonBlank(vpn.provider, asObject(vpn.vpn).provider) ?? "N/A" },
          ...(firstNonBlank(ip.country, asObject(ip.location).country) ? [{ label: "Pays", value: firstNonBlank(ip.country, asObject(ip.location).country)! }] : []),
        ],
      },
      facts("Connexion", [
        ["Serveur", firstNonBlank(vpn.server_name, asObject(vpn.openvpn).server_name)],
        ["Ville", ip.city],
        ["Région", ip.region],
        ["Opérateur", ip.organization],
        ["Fuseau horaire", ip.timezone],
        ["DNS", dns.status],
      ])
    ),
  };
};

const flaresolverr: Builder = async (s) => {
  const health = asObject(await get(s, "/health"));
  const sessionsObj = asObject(json(await request(s, "/v1", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ cmd: "sessions.list" }) })));
  const sessionsList = asArray(sessionsObj.sessions).map(str).filter(Boolean);
  return {
    subtitle: str(health.version) ? `v${str(health.version)}` : undefined,
    actions: [{ id: "create_session", label: "Créer une session" }],
    sections: sections(
      {
        kind: "stats",
        items: [
          { label: "Sessions", value: fmt(sessionsList.length) },
          { label: "Statut", value: str(health.status) || "inconnu", tone: str(health.status) === "ok" ? "success" : "warning" },
          { label: "Message", value: firstNonBlank(health.message, health.msg) ?? "N/A" },
        ],
      },
      {
        kind: "rows",
        title: "Sessions",
        items: sessionsList.map((id) => ({ title: id, actions: [{ id: "destroy_session", label: "Fermer", tone: "destructive" as const, params: { session: id } }] })),
        empty: "Aucune session ouverte",
      },
      facts("Système", [
        ["Version", health.version],
        ["User agent", firstNonBlank(health.userAgent, health.user_agent)],
      ])
    ),
  };
};

// ---- Home services --------------------------------------------------------------------------

const portainer: Builder = async (s) => {
  const endpoints = asArray(await get(s, "/api/endpoints")) as Json[];
  const out: DashSection[] = [];
  let running = 0;
  let total = 0;
  let stopped = 0;
  let images = 0;
  let volumes = 0;
  for (const endpoint of endpoints) {
    if (endpoint.Id === undefined) continue;
    const containers = asArray(await tryGet(s, `/api/endpoints/${endpoint.Id}/docker/containers/json?all=1`)) as Json[];
    const snapshot = asObject(asArray(endpoint.Snapshots)[0]);
    images += num(snapshot.ImageCount);
    volumes += num(snapshot.VolumeCount);
    const up = containers.filter((c) => c.State === "running" || str(c.Status).includes("Up"));
    running += up.length;
    total += containers.length;
    stopped += containers.filter((c) => c.State === "exited").length;
    out.push({
      kind: "rows",
      title: `${str(endpoint.Name) || `Environnement ${endpoint.Id}`} · ${up.length}/${containers.length}`,
      searchable: true,
      empty: "Aucun conteneur",
      items: containers
        .sort((a, b) => Number(b.State === "running") - Number(a.State === "running") || containerName(a).localeCompare(containerName(b)))
        .map((container) => {
          const state = str(container.State);
          const healthMatch = /\((healthy|unhealthy|health: starting)\)/.exec(str(container.Status));
          return {
            title: containerName(container),
            subtitle: [str(container.Image), str(container.Status)].filter(Boolean).join(" · "),
            badge:
              healthMatch?.[1] === "unhealthy"
                ? { label: "Unhealthy", tone: "destructive" as const }
                : state === "running"
                  ? { label: "Actif", tone: "success" as const }
                  : state === "paused"
                    ? { label: "En pause", tone: "warning" as const }
                    : state === "restarting"
                      ? { label: "Redémarrage", tone: "warning" as const }
                      : { label: state === "exited" ? "Arrêté" : state || "—" },
            actions:
              state === "running"
                ? [
                    { id: "restart", label: "Redémarrer", params: { endpoint: str(endpoint.Id), container: str(container.Id) } },
                    { id: "stop", label: "Arrêter", tone: "destructive" as const, params: { endpoint: str(endpoint.Id), container: str(container.Id) }, confirm: `Arrêter ${containerName(container)} ?` },
                  ]
                : [{ id: "start", label: "Démarrer", tone: "primary" as const, params: { endpoint: str(endpoint.Id), container: str(container.Id) } }],
          };
        }),
    });
  }
  return {
    sections: [
      {
        kind: "stats",
        items: [
          { label: "Conteneurs", value: fmt(total) },
          { label: "Actifs", value: fmt(running), tone: "success" },
          { label: "Arrêtés", value: fmt(stopped) },
          { label: "Environnements", value: fmt(endpoints.length) },
          ...(images ? [{ label: "Images", value: fmt(images) }] : []),
          ...(volumes ? [{ label: "Volumes", value: fmt(volumes) }] : []),
        ],
      },
      ...out,
    ],
  };
};

const containerName = (container: Json) => (str(asArray(container.Names)[0]) || str(container.Id).slice(0, 12)).replace(/^\//, "");

const beszel: Builder = async (s) => {
  let lastError: unknown = null;
  for (const base of baseUrls(s)) {
    for (const force of [false, true]) {
      try {
        const token = await beszelToken(s, base, force);
        const headers = { Authorization: `Bearer ${token}` };
        const pb = async (path: string) => json(await requestAt(s, base, path, { noAuth: true, headers }));
        const items = asArray(asObject(await pb("/api/collections/systems/records?sort=name&perPage=200")).items) as Json[];
        if (items.length === 0 && !force && s.secrets.username && s.secrets.password) continue;
        const systems = items.sort((a, b) => str(a.name).toLowerCase().localeCompare(str(b.name).toLowerCase()));
        const online = systems.filter((system) => system.status === "up");
        // Last hour of CPU for each online system (system_stats, newest first), like the app's graphs.
        const series: { name: string; points: [number, number][] }[] = [];
        for (const system of online.slice(0, 4)) {
          const stats = asArray(asObject(await pb(`/api/collections/system_stats/records?sort=-created&perPage=60&filter=${encodeURIComponent(`system='${str(system.id)}'`)}`).catch(() => ({}))).items) as Json[];
          const points = stats
            .map((record) => [new Date(str(record.created).replace(" ", "T")).getTime(), num(asObject(record.stats).cpu)] as [number, number])
            .filter(([time]) => Number.isFinite(time))
            .reverse();
          if (points.length > 1) series.push({ name: str(system.name), points });
        }
        const containers = asArray(asObject(await pb("/api/collections/containers/records?perPage=500").catch(() => ({}))).items) as Json[];
        return {
          sections: sections(
            {
              kind: "stats",
              items: [
                { label: "Systèmes", value: fmt(systems.length) },
                { label: "En ligne", value: fmt(online.length), tone: "success" },
                { label: "Hors ligne", value: fmt(systems.length - online.length), tone: systems.length - online.length ? "destructive" : undefined },
                ...(containers.length ? [{ label: "Conteneurs", value: fmt(containers.length) }] : []),
              ],
            },
            {
              kind: "rows",
              title: "Systèmes",
              items: systems.map((system) => {
                const info = asObject(system.info);
                const up = system.status === "up";
                return {
                  title: str(system.name),
                  subtitle: [str(system.host), str(info.h), num(info.u) ? `uptime ${duration(num(info.u))}` : null, str(info.k) ? `noyau ${str(info.k)}` : null].filter(Boolean).join(" · "),
                  badge: up ? { label: "En ligne", tone: "success" as const } : { label: str(system.status) === "paused" ? "En pause" : "Hors ligne", tone: "destructive" as const },
                  gauges: up
                    ? [
                        { label: "CPU", value: num(info.cpu) },
                        { label: "RAM", value: num(info.mp) },
                        { label: "Disque", value: num(info.dp) },
                      ]
                    : undefined,
                  meta: up && (info.ns !== undefined || info.b !== undefined) ? `réseau ${num(info.b ?? info.ns).toFixed(1)} Mo/s` : undefined,
                };
              }),
            },
            series.length > 0 && { kind: "chart", title: "CPU, dernière heure", unit: "%", series },
            containers.length > 0 && {
              kind: "rows",
              title: "Conteneurs",
              searchable: true,
              items: containers
                .sort((a, b) => num(b.cpu) - num(a.cpu))
                .map((container) => ({
                  title: str(container.name),
                  subtitle: [systems.find((system) => system.id === container.system)?.name, str(container.image), str(container.status)].filter(Boolean).join(" · "),
                  meta: `CPU ${num(container.cpu).toFixed(1)} % · ${num(container.memory).toFixed(0)} Mo`,
                  badge: str(container.health) === "unhealthy" ? { label: "Unhealthy", tone: "destructive" as const } : undefined,
                })),
            }
          ),
        };
      } catch (error) {
        lastError = error;
        if (!(error instanceof ServiceHttpError && [400, 401, 403].includes(error.status)) || force) break;
      }
    }
  }
  throw lastError ?? new Error("Beszel unreachable");
};

const pihole: Builder = async (s) => {
  let lastError: unknown = null;
  for (const base of baseUrls(s)) {
    for (const force of [false, true]) {
      try {
        const sid = await piholeSid(s, base, force);
        const headers: Record<string, string> = sid ? { "X-FTL-SID": sid } : {};
        const call = async (path: string) => asObject(json(await requestAt(s, base, path, { noAuth: true, headers })));
        const optional = (path: string) => call(path).catch((error) => {
          if (error instanceof ServiceHttpError && error.status === 401) throw error;
          return {} as Json;
        });
        const [summary, blocking, history, topDomains, topBlocked, topClients, version] = await Promise.all([
          call("/api/stats/summary"),
          optional("/api/dns/blocking"),
          optional("/api/history"),
          optional("/api/stats/top_domains?count=10"),
          optional("/api/stats/top_domains?count=10&blocked=true"),
          optional("/api/stats/top_clients?count=10"),
          optional("/api/info/version"),
        ]);
        const queries = asObject(summary.queries);
        const gravity = asObject(summary.gravity);
        const clients = asObject(summary.clients);
        const enabled = str(blocking.blocking) === "enabled";
        const ranking = (raw: Json, keys: string[]) => {
          for (const key of keys) {
            const value = raw[key];
            if (Array.isArray(value)) return (value as Json[]).map((item) => ({ label: firstNonBlank(item.domain, item.query, item.name, item.ip) ?? "—", value: num(item.count) }));
            if (value && typeof value === "object") return Object.entries(value as Json).map(([label, count]) => ({ label: label.split("|")[0], value: num(count) }));
          }
          return [];
        };
        const historyRows = asArray(history.history) as Json[];
        const core = asObject(asObject(version.version).core);
        return {
          subtitle: firstNonBlank(asObject(core.local).version && `Pi-hole ${str(asObject(core.local).version)}`),
          actions: enabled
            ? [
                { id: "disable", label: "Pause 5 min", params: { minutes: "5" } },
                { id: "disable", label: "Pause 30 min", params: { minutes: "30" } },
                { id: "disable", label: "Désactiver le blocage", tone: "destructive", confirm: "Désactiver le blocage sans limite de durée ?" },
              ]
            : [{ id: "enable", label: "Activer le blocage", tone: "primary" }],
          sections: sections(
            {
              kind: "stats",
              items: [
                { label: "Blocage", value: enabled ? "Activé" : str(blocking.blocking) ? "Désactivé" : "—", tone: enabled ? "success" : "warning" },
                { label: "Requêtes", value: fmt(num(queries.total)) },
                { label: "Bloquées", value: fmt(num(queries.blocked)) },
                { label: "Taux de blocage", value: `${num(queries.percent_blocked).toFixed(1)} %` },
                { label: "Domaines uniques", value: fmt(num(queries.unique_domains)) },
                { label: "Domaines en liste", value: fmt(num(gravity.domains_being_blocked)) },
                ...(clients.active !== undefined ? [{ label: "Clients actifs", value: fmt(num(clients.active)) }] : []),
                { label: "En cache", value: fmt(num(queries.cached)) },
              ],
            },
            historyRows.length > 1 && {
              kind: "chart",
              title: "Requêtes, dernières 24 h",
              series: [
                { name: "Total", points: historyRows.map((row) => [num(row.timestamp) * 1000, num(row.total)] as [number, number]) },
                { name: "Bloquées", points: historyRows.map((row) => [num(row.timestamp) * 1000, num(row.blocked)] as [number, number]) },
              ],
            },
            { kind: "ranking", title: "Domaines les plus demandés", items: ranking(topDomains, ["domains", "top_domains", "top_queries"]), empty: "Aucune donnée" },
            { kind: "ranking", title: "Domaines les plus bloqués", items: ranking(topBlocked, ["domains", "top_blocked", "top_ads"]), empty: "Aucune donnée" },
            { kind: "ranking", title: "Clients les plus actifs", items: ranking(topClients, ["clients", "top_clients", "top_sources"]), empty: "Aucune donnée" },
            facts("Listes", [["Dernière mise à jour", num(gravity.last_update) ? date(num(gravity.last_update)) : undefined]])
          ),
        };
      } catch (error) {
        lastError = error;
        if (!(error instanceof ServiceHttpError && error.status === 401) || force) break;
      }
    }
  }
  // Pi-hole v5 (legacy API): the summary is all it offers without a session.
  const card = await summarize(s);
  if (card.status === "auth") throw new ServiceHttpError(401, "Pi-hole credentials refused");
  if (card.value) return { sections: [{ kind: "stats", items: [{ label: "Requêtes", value: card.value }, ...(card.details ?? []).map((d) => ({ label: d.label, value: d.value }))] }] };
  throw lastError ?? new Error("Pi-hole unreachable");
};

const uptimeKuma: Builder = async (s) => {
  const text = (await request(s, "/metrics", { headers: { Accept: "text/plain" } })).text;
  const monitors = new Map<string, { name: string; type?: string; url?: string; status?: number; response?: number; cert?: number }>();
  for (const raw of text.split("\n")) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;
    const match = /^([a-zA-Z_:][\w:]*)\{(.*)\}\s+(\S+)/.exec(line);
    if (!match || !match[1].toLowerCase().includes("monitor")) continue;
    const labels = Object.fromEntries([...match[2].matchAll(/(\w+)="((?:[^"\\]|\\.)*)"/g)].map((m) => [m[1], m[2]]));
    const identity = labels.monitor_name ?? labels.name ?? labels.monitor ?? labels.id;
    if (!identity) continue;
    const key = labels.monitor_id ?? labels.id ?? identity;
    const record = monitors.get(key) ?? { name: identity };
    record.name = identity;
    record.type = labels.monitor_type ?? labels.type ?? record.type;
    record.url = labels.monitor_url ?? labels.url ?? labels.monitor_hostname ?? labels.hostname ?? record.url;
    const value = Number(match[3]);
    const metric = match[1].toLowerCase();
    if (metric.endsWith("monitor_status")) record.status = value;
    else if (metric.endsWith("monitor_response_time") && Number.isFinite(value)) record.response = value;
    else if (metric.endsWith("monitor_cert_days_remaining") && Number.isFinite(value)) record.cert = value;
    monitors.set(key, record);
  }
  const STATUS: Record<number, { label: string; tone?: Tone; rank: number }> = {
    0: { label: "Hors ligne", tone: "destructive", rank: 0 },
    2: { label: "En attente", tone: "warning", rank: 1 },
    3: { label: "Maintenance", tone: "info", rank: 2 },
    1: { label: "En ligne", tone: "success", rank: 3 },
  };
  const list = [...monitors.values()].sort((a, b) => (STATUS[a.status ?? -1]?.rank ?? 4) - (STATUS[b.status ?? -1]?.rank ?? 4) || a.name.localeCompare(b.name));
  const count = (status: number) => list.filter((monitor) => monitor.status === status).length;
  const responses = list.map((monitor) => monitor.response).filter((value): value is number => value !== undefined && value >= 0);
  return {
    sections: sections(
      {
        kind: "stats",
        items: [
          { label: "Moniteurs", value: fmt(list.length) },
          { label: "En ligne", value: fmt(count(1)), tone: "success" },
          { label: "Hors ligne", value: fmt(count(0)), tone: count(0) ? "destructive" : undefined },
          { label: "En attente", value: fmt(count(2)), tone: count(2) ? "warning" : undefined },
          { label: "Maintenance", value: fmt(count(3)) },
          ...(responses.length ? [{ label: "Réponse moyenne", value: `${Math.round(responses.reduce((a, b) => a + b, 0) / responses.length)} ms` }] : []),
        ],
      },
      {
        kind: "rows",
        title: "Moniteurs",
        searchable: true,
        items: list.map((monitor) => ({
          title: monitor.name,
          subtitle: [monitor.type, monitor.url && monitor.url !== "null" ? monitor.url : null].filter(Boolean).join(" · "),
          meta: [monitor.response !== undefined && monitor.response >= 0 ? `${Math.round(monitor.response)} ms` : null, monitor.cert !== undefined ? `certificat ${Math.round(monitor.cert)} j` : null].filter(Boolean).join(" · ") || undefined,
          badge: STATUS[monitor.status ?? -1] ? { label: STATUS[monitor.status!].label, tone: STATUS[monitor.status!].tone } : { label: "Inconnu" },
        })),
      }
    ),
  };
};

const peanut: Builder = async (s) => {
  const devices = asArray(await get(s, "/api/v1/devices?meta=true")) as Json[];
  const version = asObject(await tryGet(s, "/api/v1/info"));
  const text = (row: Json, key: string) => str(row[key]) || undefined;
  const number = (row: Json, key: string) => (row[key] === undefined || row[key] === "" ? undefined : Number(row[key]));
  const parsed = devices.map((row, index) => {
    const flags = str(row["ups.status"]).toUpperCase().split(" ").filter(Boolean);
    const model = text(row, "device.model") ?? text(row, "ups.model");
    const state = flags.includes("LB")
      ? { label: "Batterie faible", tone: "destructive" as const }
      : flags.includes("OB")
        ? { label: "Sur batterie", tone: "warning" as const }
        : flags.includes("OL")
          ? { label: "Secteur", tone: "success" as const }
          : flags.includes("OFF")
            ? { label: "Éteint", tone: "destructive" as const }
            : { label: "Inconnu" };
    const load = number(row, "ups.load");
    const nominal = number(row, "ups.realpower.nominal");
    return { row, index, flags, model, state, load, nominal, name: text(row, "peanut.device_id") ?? model ?? `Onduleur ${index + 1}` };
  });
  const charges = parsed.map((device) => number(device.row, "battery.charge")).filter((value): value is number => value !== undefined && !Number.isNaN(value));
  return {
    subtitle: str(version.version) ? `PeaNUT ${str(version.version)}` : undefined,
    sections: [
      {
        kind: "stats",
        items: [
          { label: "Onduleurs", value: fmt(parsed.length) },
          { label: "Sur secteur", value: fmt(parsed.filter((device) => device.flags.includes("OL")).length), tone: "success" },
          { label: "Sur batterie", value: fmt(parsed.filter((device) => device.flags.includes("OB") || device.flags.includes("LB")).length) },
          ...(charges.length ? [{ label: "Batterie la plus basse", value: `${Math.round(Math.min(...charges))} %` }] : []),
        ],
      },
      ...parsed.map<DashSection>((device) => ({
        kind: "facts",
        title: `${device.name} · ${device.state.label}`,
        items: [
          ["Fabricant", text(device.row, "device.mfr") ?? text(device.row, "ups.mfr")],
          ["Modèle", device.model],
          ["Batterie", number(device.row, "battery.charge") !== undefined ? `${Math.round(number(device.row, "battery.charge")!)} %` : undefined],
          ["Autonomie", number(device.row, "battery.runtime") !== undefined ? duration(number(device.row, "battery.runtime")!) : undefined],
          ["Charge", device.load !== undefined ? `${Math.round(device.load)} %` : undefined],
          ["Consommation estimée", device.load !== undefined && device.nominal !== undefined ? `${Math.round((device.nominal * device.load) / 100)} W` : undefined],
          ["Tension d'entrée", number(device.row, "input.voltage") !== undefined ? `${number(device.row, "input.voltage")} V` : undefined],
          ["Tension de sortie", number(device.row, "output.voltage") !== undefined ? `${number(device.row, "output.voltage")} V` : undefined],
          ["Tension batterie", number(device.row, "battery.voltage") !== undefined ? `${number(device.row, "battery.voltage")} V` : undefined],
          ["En charge", device.flags.includes("CHRG") ? "Oui" : undefined],
          ["Batterie à remplacer", device.flags.includes("RB") ? "Oui" : undefined],
          ["Surcharge", device.flags.includes("OVER") ? "Oui" : undefined],
        ]
          .filter(([, value]) => value !== undefined)
          .map(([label, value]) => ({ label: label as string, value: value as string })),
      })),
    ],
  };
};

const safeline: Builder = async (s) => {
  const data = await safelineData(s);
  const MODES: Record<number, { label: string; tone?: Tone }> = { 0: { label: "Protection", tone: "success" }, 1: { label: "Observation", tone: "warning" }, 2: { label: "Maintenance", tone: "info" } };
  return {
    subtitle: data.version ? `v${data.version}` : undefined,
    sections: sections(
      {
        kind: "stats",
        items: [
          { label: "Requêtes aujourd'hui", value: data.todayRequests !== null ? fmt(data.todayRequests) : "—" },
          { label: "Visiteurs", value: data.todayVisitors !== null ? fmt(data.todayVisitors) : "—" },
          ...(data.todayIps !== null ? [{ label: "IP uniques", value: fmt(data.todayIps) }] : []),
          { label: "Bloquées", value: data.blocked !== null ? fmt(data.blocked) : "—", tone: data.blocked ? ("warning" as const) : undefined },
          { label: "IP attaquantes", value: data.attackIps !== null ? fmt(data.attackIps) : "—" },
          { label: "Sites protégés", value: fmt(data.sites.length) },
        ],
      },
      {
        kind: "rows",
        title: "Sites",
        empty: "Aucun site",
        items: data.sites.map((site, index) => {
          const names = asArray(site.server_names).map(str).filter(Boolean);
          const enabled = site.is_enabled !== false && site.is_enabled !== 0;
          return {
            title: str(site.title) || str(site.comment) || names[0] || `Site ${index + 1}`,
            subtitle: [names.join(", "), asArray(site.ports).map(str).join(", "), asArray(site.upstreams).map(str).join(", ")].filter(Boolean).join(" · "),
            badge: enabled ? MODES[num(site.mode)] ?? { label: "—" } : { label: "Désactivé" },
          };
        }),
      },
      {
        kind: "rows",
        title: "Attaques récentes",
        empty: "Aucune attaque récente",
        items: data.events.map((event) => ({
          title: str(event.ip) || "—",
          subtitle: [str(event.host) + (num(event.dst_port) ? `:${num(event.dst_port)}` : ""), [str(event.city), str(event.province), str(event.country)].filter(Boolean).join(", ")].filter(Boolean).join(" · "),
          meta: date(event.start_at),
          badge: num(event.deny_count) > 0 ? { label: `${fmt(num(event.deny_count))} bloquées`, tone: "warning" as const } : { label: `${fmt(num(event.pass_count))} passées` },
        })),
      }
    ),
  };
};

const AMP_STATES: Record<number, { label: string; tone?: Tone }> = {
  20: { label: "En ligne", tone: "success" },
  5: { label: "Démarrage", tone: "info" },
  7: { label: "Démarrage", tone: "info" },
  10: { label: "Démarrage", tone: "info" },
  30: { label: "Redémarrage", tone: "info" },
  40: { label: "Arrêt", tone: "warning" },
  0: { label: "Arrêté" },
  45: { label: "En veille" },
  50: { label: "En veille" },
  60: { label: "En veille" },
  70: { label: "Mise à jour", tone: "info" },
  75: { label: "Mise à jour", tone: "info" },
  80: { label: "Mise à jour", tone: "info" },
  100: { label: "Échec", tone: "destructive" },
  200: { label: "Suspendu", tone: "warning" },
};

const amp: Builder = async (s) => {
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
        if (!force) continue;
        throw new ServiceHttpError(401, "AMP session rejected");
      }
      const instances = data.flatMap((target) => asArray(asObject(target).AvailableInstances)).map(asObject).filter((instance) => str(instance.Module).toUpperCase() !== "ADS");
      const state = (instance: Json) => (instance.Suspended ? AMP_STATES[200] : !instance.Running ? { label: "Hors ligne" } : AMP_STATES[num(instance.AppState)] ?? { label: "Inconnu" });
      const metric = (instance: Json, name: string) => asObject(asObject(instance.Metrics)[name]);
      return {
        sections: [
          {
            kind: "stats",
            items: [
              { label: "Instances", value: fmt(instances.length) },
              { label: "En ligne", value: fmt(instances.filter((instance) => instance.Running && num(instance.AppState) === 20).length), tone: "success" },
              { label: "Joueurs", value: fmt(instances.reduce((total, instance) => total + num(metric(instance, "Active Users").RawValue), 0)) },
            ],
          },
          {
            kind: "rows",
            title: "Instances",
            items: instances.map((instance) => {
              const cpu = metric(instance, "CPU Usage");
              const memory = metric(instance, "Memory Usage");
              const users = metric(instance, "Active Users");
              const running = Boolean(instance.Running);
              return {
                title: firstNonBlank(instance.FriendlyName, instance.InstanceName) ?? "Instance",
                subtitle: [firstNonBlank(instance.ModuleDisplayName, instance.Module), users.RawValue !== undefined ? `${num(users.RawValue)}${num(users.MaxValue) ? `/${num(users.MaxValue)}` : ""} joueurs` : null].filter(Boolean).join(" · "),
                badge: state(instance),
                gauges: running
                  ? [
                      { label: "CPU", value: num(cpu.Percent) },
                      { label: "RAM", value: num(memory.Percent) },
                    ]
                  : undefined,
                meta: running && memory.RawValue !== undefined ? `${fmt(num(memory.RawValue))} ${str(memory.Units) || "Mo"}` : undefined,
                actions: running
                  ? [
                      { id: "restart", label: "Redémarrer", params: { instance: str(instance.InstanceName) } },
                      { id: "stop", label: "Arrêter", tone: "destructive" as const, params: { instance: str(instance.InstanceName) }, confirm: `Arrêter ${str(instance.FriendlyName) || str(instance.InstanceName)} ?` },
                    ]
                  : [{ id: "start", label: "Démarrer", tone: "primary" as const, params: { instance: str(instance.InstanceName) } }],
              };
            }),
          },
        ],
      };
    }
  }
  throw new Error("AMP unreachable");
};

const gitea: Builder = async (s) => {
  const [user, repos, orgs, version] = await Promise.all([
    get(s, "/api/v1/user"),
    get(s, "/api/v1/user/repos?page=1&limit=50"),
    tryGet(s, "/api/v1/user/orgs"),
    tryGet(s, "/api/v1/version"),
  ]);
  const userObj = asObject(user);
  const repoList = (asArray(repos) as Json[]).sort((a, b) => str(b.updated_at).localeCompare(str(a.updated_at)));
  const heatmap = asArray(await tryGet(s, `/api/v1/users/${encodeURIComponent(str(userObj.login))}/heatmap`)) as Json[];
  // Contributions per week over the last 26 weeks, from the heatmap's daily buckets.
  const weekMs = 7 * 24 * 3600 * 1000;
  const now = Date.now();
  const weeks = new Map<number, number>();
  for (let index = 25; index >= 0; index--) weeks.set(Math.floor((now - index * weekMs) / weekMs), 0);
  for (const bucket of heatmap) {
    const week = Math.floor((num(bucket.timestamp) * 1000) / weekMs);
    if (weeks.has(week)) weeks.set(week, (weeks.get(week) ?? 0) + num(bucket.contributions));
  }
  const forgejo = (await tryGet(s, "/api/forgejo/v1/version")) !== null;
  return {
    subtitle: [forgejo ? "Forgejo" : "Gitea", str(asObject(version).version)].filter(Boolean).join(" "),
    sections: sections(
      {
        kind: "stats",
        items: [
          { label: "Dépôts", value: fmt(repoList.length) },
          { label: "Organisations", value: fmt(asArray(orgs).length) },
          { label: "Étoiles", value: fmt(repoList.reduce((total, repo) => total + num(repo.stars_count), 0)) },
          { label: "Tickets ouverts", value: fmt(repoList.reduce((total, repo) => total + num(repo.open_issues_count), 0)) },
          { label: "Pull requests", value: fmt(repoList.reduce((total, repo) => total + num(repo.open_pr_counter), 0)) },
          { label: "Contributions (6 mois)", value: fmt([...weeks.values()].reduce((a, b) => a + b, 0)) },
        ],
      },
      heatmap.length > 0 && { kind: "chart", title: "Contributions par semaine", series: [{ name: "Contributions", points: [...weeks.entries()].map(([week, total]) => [week * weekMs, total] as [number, number]) }] },
      {
        kind: "rows",
        title: "Dépôts",
        searchable: true,
        items: repoList.map((repo) => ({
          title: str(repo.full_name),
          subtitle: [str(repo.description), str(repo.language)].filter(Boolean).join(" · "),
          meta: `${repo.stars_count ? `★ ${num(repo.stars_count)} · ` : ""}${date(repo.updated_at) ?? ""}`,
          badge: repo.private ? { label: "Privé" } : repo.fork ? { label: "Fork" } : repo.archived ? { label: "Archivé" } : undefined,
        })),
      },
      asArray(orgs).length > 0 && {
        kind: "rows",
        title: "Organisations",
        items: (asArray(orgs) as Json[]).map((org) => ({ title: firstNonBlank(org.full_name, org.username, org.name) ?? "—", subtitle: str(org.description) })),
      }
    ),
  };
};

// ---- UniFi (UnifiRepository.getDashboard) --------------------------------------------------

function pick(raw: Json, ...keys: string[]): unknown {
  for (const key of keys) if (raw[key] !== undefined && raw[key] !== null && raw[key] !== "") return raw[key];
  return undefined;
}
const pickStr = (raw: Json, ...keys: string[]) => str(pick(raw, ...keys));
const pickNum = (raw: Json, ...keys: string[]) => {
  const value = pick(raw, ...keys);
  return value === undefined ? undefined : Number(value);
};
const objectArray = (value: unknown): Json[] => asArray(Array.isArray(value) ? value : asObject(value).data ?? asObject(value).items) as Json[];
const bitrate = (bps?: number) => (bps === undefined || !Number.isFinite(bps) ? null : `${(bps / 1_000_000).toFixed(bps < 10_000_000 ? 1 : 0)} Mb/s`);

const unifi: Builder = async (s) => {
  const siteManager = /api\.ui\.com|unifi\.ui\.com/i.test(s.url);
  const localGet = async (path: string) => {
    let lastError: unknown = null;
    for (const prefix of ["/proxy/network/integration/v1/", "/v1/"]) {
      try {
        return await get(s, prefix + path);
      } catch (error) {
        lastError = error;
        if (error instanceof ServiceHttpError && (error.status === 401 || error.status === 403)) throw error;
      }
    }
    throw lastError;
  };

  let sites: Json[] = [];
  let devices: Json[] = [];
  const clients: Json[] = [];
  const networks: Json[] = [];
  let hosts: Json[] = [];
  let isp: Json[] = [];

  if (siteManager) {
    const managerGet = async (path: string) => {
      const collected: Json[] = [];
      let next: string | null = path;
      for (let page = 0; page < 20 && next; page++) {
        const response = asObject(json(await requestAt(s, "https://api.ui.com", next)));
        collected.push(...objectArray(response));
        const token = pickStr(response, "nextToken", "next_token");
        next = token ? `${path}${path.includes("?") ? "&" : "?"}nextToken=${encodeURIComponent(token)}` : null;
      }
      return collected;
    };
    sites = await managerGet("/v1/sites?pageSize=100");
    hosts = await managerGet("/v1/hosts?pageSize=100").catch(() => []);
    devices = (await managerGet("/v1/devices?pageSize=100").catch(() => [])).flatMap((host) => (Array.isArray(host.devices) ? (host.devices as Json[]) : [host]));
    isp = (objectArray(await tryGet(s, "/ea/isp-metrics/5m?duration=24h")) as Json[]).flatMap((series) =>
      (asArray(series.periods) as Json[]).map((period) => {
        const wan = asObject(asObject(period.data).wan);
        return { ...wan, metricTime: period.metricTime };
      })
    );
  } else {
    sites = objectArray(await localGet("sites"));
    for (const site of sites.slice(0, 8)) {
      const id = encodeURIComponent(pickStr(site, "siteId", "id", "_id"));
      const [siteDevices, siteClients, siteNetworks] = await Promise.all([
        localGet(`sites/${id}/devices`).catch(() => []),
        localGet(`sites/${id}/clients?limit=500`).catch(() => []),
        localGet(`sites/${id}/networks`).catch(() => []),
      ]);
      devices.push(...objectArray(siteDevices));
      clients.push(...objectArray(siteClients).map((client) => ({ ...client, __site: pickStr(site, "siteId", "id", "_id") })));
      networks.push(...objectArray(siteNetworks));
    }
  }

  const online = (device: Json) => {
    const flag = pick(device, "online", "isOnline");
    if (typeof flag === "boolean") return flag;
    const state = pickStr(device, "state", "status").toLowerCase();
    return state ? ["online", "connected", "1", "up"].includes(state) : true;
  };
  const onlineDevices = devices.filter(online).length;
  const wireless = clients.filter((client) => pickStr(client, "type").toUpperCase() === "WIRELESS").length;
  const wired = clients.filter((client) => pickStr(client, "type").toUpperCase() === "WIRED").length;
  const guests = clients.filter((client) => pickStr(client, "type").toUpperCase() === "GUEST" || asObject(client.access).authorized === false).length;
  const totalClients = clients.length || sites.reduce((total, site) => total + num(asObject(asObject(site.statistics).counts).totalClient ?? asObject(asObject(site.statistics).counts).clients), 0);
  const upgradeable = devices.filter((device) => pick(device, "upgradeable", "hasUpdate", "updateAvailable") === true).length;

  const ispPoints = (key: string, factor = 1): [number, number][] =>
    isp
      .map((metric) => [new Date(pickStr(metric, "metricTime", "timestamp")).getTime(), Number(pick(metric, key)) * factor] as [number, number])
      .filter(([time, value]) => Number.isFinite(time) && Number.isFinite(value));

  return {
    subtitle: siteManager ? "Site Manager (api.ui.com)" : "API locale",
    sections: sections(
      {
        kind: "stats",
        items: [
          { label: "Sites", value: fmt(sites.length) },
          { label: "Appareils", value: `${onlineDevices}/${devices.length}`, tone: onlineDevices < devices.length ? "warning" : "success" },
          { label: "Clients", value: fmt(totalClients) },
          ...(clients.length ? [{ label: "Wi-Fi", value: fmt(wireless) }, { label: "Filaires", value: fmt(wired) }] : []),
          ...(guests ? [{ label: "Invités", value: fmt(guests) }] : []),
          ...(upgradeable ? [{ label: "Mises à jour", value: fmt(upgradeable), tone: "info" as const }] : []),
          ...(networks.length ? [{ label: "Réseaux", value: fmt(networks.length) }] : []),
        ],
      },
      ispPoints("avgLatency").length > 1 && { kind: "chart", title: "Latence WAN, 24 h", unit: " ms", series: [{ name: "Latence", points: ispPoints("avgLatency") }] },
      ispPoints("download_kbps").length > 1 && {
        kind: "chart",
        title: "Débit WAN, 24 h",
        unit: " Mb/s",
        series: [
          { name: "Descendant", points: ispPoints("download_kbps", 1 / 1000) },
          { name: "Montant", points: ispPoints("upload_kbps", 1 / 1000) },
        ],
      },
      {
        kind: "rows",
        title: "Appareils",
        searchable: true,
        empty: "Aucun appareil",
        items: devices
          .sort((a, b) => Number(online(a)) - Number(online(b)) || pickStr(a, "name").localeCompare(pickStr(b, "name")))
          .map((device) => {
            const up = online(device);
            const cpu = pickNum(device, "cpu", "cpuPercent", "cpu_percent");
            const memory = pickNum(device, "mem", "memory", "memoryPercent", "memory_percent");
            const clientsCount = pickNum(device, "num_sta", "clientCount", "clients");
            const temperature = pickNum(device, "temperature", "temp", "general_temperature");
            return {
              title: pickStr(device, "name", "displayName", "hostname") || pickStr(device, "model") || "Appareil UniFi",
              subtitle: [pickStr(device, "model", "modelKey", "shortname"), pickStr(device, "ipAddress", "ip_address", "ip"), pickStr(device, "firmwareVersion", "version", "firmware") && `v${pickStr(device, "firmwareVersion", "version", "firmware")}`]
                .filter(Boolean)
                .join(" · "),
              meta: [clientsCount !== undefined ? `${clientsCount} clients` : null, temperature !== undefined ? `${Math.round(temperature)} °C` : null, bitrate(pickNum(device, "rx_bps", "rxRateBps", "rx_bytes-r")) && `↓ ${bitrate(pickNum(device, "rx_bps", "rxRateBps", "rx_bytes-r"))}`]
                .filter(Boolean)
                .join(" · ") || undefined,
              badge: !up
                ? { label: "Hors ligne", tone: "destructive" as const }
                : pick(device, "upgradeable", "hasUpdate", "updateAvailable") === true
                  ? { label: "Mise à jour", tone: "info" as const }
                  : { label: "En ligne", tone: "success" as const },
              gauges: up && (cpu !== undefined || memory !== undefined)
                ? [...(cpu !== undefined ? [{ label: "CPU", value: cpu }] : []), ...(memory !== undefined ? [{ label: "RAM", value: memory }] : [])]
                : undefined,
            };
          }),
      },
      clients.length > 0 && {
        kind: "rows",
        title: "Clients",
        searchable: true,
        items: clients
          .sort((a, b) => num(pick(b, "rx_bps", "rxRateBps")) + num(pick(b, "tx_bps", "txRateBps")) - (num(pick(a, "rx_bps", "rxRateBps")) + num(pick(a, "tx_bps", "txRateBps"))))
          .map((client) => {
            const type = pickStr(client, "type").toUpperCase();
            const signal = pickNum(client, "signal", "rssi");
            const connected = pickNum(client, "uptime", "connectedSeconds", "connected_seconds");
            return {
              title: pickStr(client, "name", "hostname", "displayName") || pickStr(client, "macAddress", "mac_address", "mac") || "Client",
              subtitle: [pickStr(client, "ipAddress", "ip_address", "ip"), pickStr(client, "networkName", "network_name", "ssid", "essid"), pickStr(client, "apName", "ap_name", "uplinkDeviceName", "uplink_device_name")]
                .filter(Boolean)
                .join(" · "),
              meta: [signal !== undefined ? `${signal} dBm` : null, connected ? duration(connected) : null].filter(Boolean).join(" · ") || undefined,
              badge: type === "WIRELESS" ? { label: "Wi-Fi", tone: "info" as const } : type === "WIRED" ? { label: "Filaire" } : type === "GUEST" ? { label: "Invité", tone: "warning" as const } : undefined,
              actions:
                str(client.__site) && asObject(client.access).authorized !== true && (type === "GUEST" || asObject(client.access).authorized === false)
                  ? [{ id: "authorize_guest", label: "Autoriser (2 h)", tone: "primary" as const, params: { site: str(client.__site), client: pickStr(client, "id", "_id", "clientId") } }]
                  : undefined,
            };
          }),
      },
      networks.length > 0 && {
        kind: "rows",
        title: "Réseaux",
        items: networks.map((network) => ({
          title: pickStr(network, "name", "displayName") || "Réseau",
          subtitle: [pickStr(network, "subnet", "ipSubnet", "ip_subnet"), pickNum(network, "vlanId", "vlan", "vlan_id") ? `VLAN ${pickNum(network, "vlanId", "vlan", "vlan_id")}` : null].filter(Boolean).join(" · "),
          badge: pickStr(network, "purpose", "type") ? { label: pickStr(network, "purpose", "type") } : undefined,
        })),
      },
      {
        kind: "rows",
        title: "Sites",
        items: sites.map((site) => {
          const counts = asObject(asObject(site.statistics).counts);
          const meta = asObject(site.meta);
          return {
            title: pickStr(meta, "desc", "name") || pickStr(site, "name", "desc") || "Site",
            subtitle: [pickStr(meta, "timezone") || pickStr(site, "timezone"), counts.totalDevice !== undefined ? `${num(counts.totalDevice)} appareils` : null, counts.totalClient !== undefined ? `${num(counts.totalClient)} clients` : null].filter(Boolean).join(" · "),
            badge: pickStr(asObject(site.statistics), "health", "status") ? { label: pickStr(asObject(site.statistics), "health", "status") } : undefined,
          };
        }),
      },
      hosts.length > 0 && {
        kind: "rows",
        title: "Consoles",
        items: hosts.map((host) => {
          const reported = asObject(host.reportedState);
          return {
            title: pickStr(asObject(host.userData), "name") || pickStr(reported, "hostname", "name") || "Console UniFi",
            subtitle: [pickStr(host, "hardwareId", "model") || pickStr(reported, "hardwareId"), pickStr(host, "ipAddress", "ip"), pickStr(reported, "version") && `v${pickStr(reported, "version")}`].filter(Boolean).join(" · "),
            badge: pickStr(host, "status", "state") ? { label: pickStr(host, "status", "state") } : undefined,
          };
        }),
      }
    ),
  };
};

// ---- Registry -------------------------------------------------------------------------------

const BUILDERS: Partial<Record<string, Builder>> = {
  qbittorrent,
  radarr: arrBuilder("movie"),
  sonarr: arrBuilder("series"),
  lidarr: arrBuilder("artist"),
  jellyseerr,
  prowlarr,
  autobrr,
  bazarr,
  gluetun,
  flaresolverr,
  portainer,
  beszel,
  pihole,
  uptime_kuma: uptimeKuma,
  peanut,
  safeline,
  amp,
  gitea,
  unifi_network: unifi,
};

/** Services without a ported dashboard yet: the card figures, as a minimal dashboard. */
async function fallback(service: ServiceWithSecrets): Promise<ServiceDashboard> {
  const card = await summarize(service);
  if (card.status === "auth") throw new ServiceHttpError(401, "credentials refused");
  if (card.status === "offline") throw new Error("unreachable");
  const items: DashStat[] = [
    ...(card.value ? [{ label: card.label ?? "Valeur", value: `${card.value}${card.sub ? ` ${card.sub}` : ""}` }] : []),
    ...(card.metrics ?? []).map((metric) => ({ label: metric.label, value: metric.value })),
    ...(card.details ?? []).map((detail) => ({ label: detail.label, value: detail.value, tone: detail.tone })),
  ];
  return { subtitle: card.headline, sections: items.length ? [{ kind: "stats", items }] : [] };
}

export const hasFullDashboard = (type: string) => type in BUILDERS || type in MORE_DASHBOARDS;

export async function buildDashboard(service: ServiceWithSecrets): Promise<ServiceDashboard> {
  const builder = BUILDERS[service.type] ?? MORE_DASHBOARDS[service.type] ?? fallback;
  return builder(service);
}
