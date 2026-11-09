// Service catalog shared by the server and the browser. Keys are the app's backup keys
// (BackupServiceTypeMapper in the Android app), so backup files map one to one.

export type ServiceCategory = "infrastructure" | "network" | "security" | "media" | "games" | "development";

export interface ServiceTypeInfo {
  key: string;
  name: string;
  icon: string;
  /** Logo for the dark themes, when the official one is too dark on a dark surface. */
  iconOnDark?: string;
  category: ServiceCategory;
  /** Brand color, used for the service's own numbers (like the app's home cards). */
  color: string;
}

// Same selfh.st logos as the app (github.com/selfhst/icons), bundled in public/logos so the
// dashboard does not depend on a third-party CDN being reachable from the browser.
const icon = (slug: string) => `/logos/${slug}.png`;

export const SERVICE_TYPES: ServiceTypeInfo[] = [
  { key: "portainer", name: "Portainer", icon: icon("portainer"), iconOnDark: icon("portainer-light"), category: "infrastructure", color: "#13B5EA" },
  { key: "proxmox", name: "Proxmox VE", icon: icon("proxmox"), category: "infrastructure", color: "#F59E0B" },
  { key: "truenas", name: "TrueNAS", icon: icon("truenas-scale"), category: "infrastructure", color: "#0095D5" },
  { key: "beszel", name: "Beszel", icon: icon("beszel"), category: "infrastructure", color: "#8B5CF6" },
  { key: "dockhand", name: "Dockhand", icon: icon("dockhand"), category: "infrastructure", color: "#4A90A4" },
  { key: "dockmon", name: "DockMon", icon: icon("dockmon"), category: "infrastructure", color: "#0EA5E9" },
  { key: "komodo", name: "Komodo", icon: icon("komodo"), category: "infrastructure", color: "#F97316" },
  { key: "linux_update", name: "Linux Update", icon: icon("linux-update-dashboard"), category: "infrastructure", color: "#14B8A6" },
  { key: "patchmon", name: "PatchMon", icon: icon("patchmon"), category: "infrastructure", color: "#0EA5E9" },
  { key: "uptime_kuma", name: "Uptime Kuma", icon: icon("uptime-kuma"), category: "infrastructure", color: "#22C55E" },
  { key: "healthchecks", name: "Healthchecks", icon: icon("healthchecks"), category: "infrastructure", color: "#16A34A" },
  { key: "peanut", name: "PeaNUT", icon: icon("peanut"), category: "infrastructure", color: "#D9A066" },
  { key: "pihole", name: "Pi-hole", icon: icon("pi-hole"), category: "network", color: "#CD2326" },
  { key: "adguard_home", name: "AdGuard Home", icon: icon("adguard-home"), category: "network", color: "#34C759" },
  { key: "technitium", name: "Technitium DNS", icon: icon("technitium"), category: "network", color: "#2D9CDB" },
  { key: "nginx_proxy_manager", name: "Nginx Proxy Manager", icon: icon("nginx-proxy-manager"), category: "network", color: "#F15B2A" },
  { key: "pangolin", name: "Pangolin", icon: icon("pangolin"), category: "network", color: "#FF8A3D" },
  { key: "unifi_network", name: "Ubiquiti Network", icon: icon("ubiquiti-unifi"), category: "network", color: "#007AFF" },
  { key: "safeline", name: "SafeLine", icon: icon("safeline"), category: "security", color: "#34D399" },
  { key: "maltrail", name: "Maltrail", icon: "/logos/maltrail.png", category: "security", color: "#DC2626" },
  { key: "plex", name: "Plex", icon: icon("plex"), category: "media", color: "#E5A00D" },
  { key: "jellystat", name: "Jellystat", icon: icon("jellystat"), category: "media", color: "#C93DF6" },
  { key: "radarr", name: "Radarr", icon: icon("radarr"), category: "media", color: "#FFC230" },
  { key: "sonarr", name: "Sonarr", icon: icon("sonarr"), category: "media", color: "#89C5CF" },
  { key: "lidarr", name: "Lidarr", icon: icon("lidarr"), category: "media", color: "#006B3E" },
  { key: "prowlarr", name: "Prowlarr", icon: icon("prowlarr"), category: "media", color: "#F97316" },
  { key: "autobrr", name: "autobrr", icon: icon("autobrr"), category: "media", color: "#3B82F6" },
  { key: "bazarr", name: "Bazarr", icon: icon("bazarr"), category: "media", color: "#2563EB" },
  { key: "jellyseerr", name: "Seerr", icon: icon("seerr"), category: "media", color: "#6C63FF" },
  { key: "qbittorrent", name: "qBittorrent", icon: icon("qbittorrent"), category: "media", color: "#2C86C1" },
  { key: "gluetun", name: "Gluetun", icon: icon("gluetun"), category: "media", color: "#06B6D4" },
  { key: "flaresolverr", name: "FlareSolverr", icon: icon("flaresolverr"), category: "media", color: "#FF4500" },
  { key: "amp", name: "AMP", icon: icon("cubecoders-amp"), category: "games", color: "#60A5FA" },
  { key: "crafty_controller", name: "Crafty Controller", icon: icon("crafty-controller"), category: "games", color: "#2E86FF" },
  { key: "pterodactyl", name: "Pterodactyl", icon: icon("pterodactyl"), category: "games", color: "#5D87FF" },
  { key: "calagopus", name: "Calagopus", icon: icon("calagopus"), category: "games", color: "#16A34A" },
  { key: "gitea", name: "Gitea", icon: icon("gitea"), category: "development", color: "#609926" },
  { key: "wakapi", name: "Wakapi", icon: icon("wakapi"), category: "development", color: "#2563EB" },
];

/** Forgejo branding for a Gitea-compatible instance (same switch as the app's GitForgeBranding). */
export const FORGEJO: Pick<ServiceTypeInfo, "name" | "icon" | "color"> = { name: "Forgejo", icon: icon("forgejo"), color: "#FF7A1A" };

const BY_KEY = new Map(SERVICE_TYPES.map((type) => [type.key, type]));

/** Aliases the app accepts when reading a backup. */
const ALIASES: Record<string, string> = {
  "uptime-kuma": "uptime_kuma",
  uptimekuma: "uptime_kuma",
  seerr: "jellyseerr",
  overseerr: "jellyseerr",
  linuxupdate: "linux_update",
  technitium_dns: "technitium",
  unifi: "unifi_network",
  ubiquiti: "unifi_network",
  crafty: "crafty_controller",
  truenas_scale: "truenas",
  cubecoders_amp: "amp",
};

export function serviceType(key: string): ServiceTypeInfo | undefined {
  const normalized = key.trim().toLowerCase().replace(/-/g, "_");
  return BY_KEY.get(normalized) ?? BY_KEY.get(ALIASES[normalized] ?? ALIASES[key.trim().toLowerCase()] ?? "");
}

export const CATEGORY_LABELS: Record<ServiceCategory, string> = {
  infrastructure: "Infrastructure",
  network: "Réseau",
  security: "Sécurité",
  media: "Média",
  games: "Jeux",
  development: "Développement",
};

/** Name, logo and color to show for a service, after live branding detection (Gitea / Forgejo). */
export function branding(typeKey: string, brand?: string): { name: string; icon?: string; iconOnDark?: string; color: string; info?: ServiceTypeInfo } {
  const info = serviceType(typeKey);
  if (brand === "forgejo" && info?.key === "gitea") return { ...FORGEJO, info };
  return { name: info?.name ?? typeKey, icon: info?.icon, iconOnDark: info?.iconOnDark, color: info?.color ?? "#a1a1a1", info };
}
