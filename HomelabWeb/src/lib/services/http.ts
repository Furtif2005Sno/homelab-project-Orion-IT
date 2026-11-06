import "server-only";
import { Agent, fetch } from "undici";
import type { ServiceWithSecrets } from "../db";

const insecureAgent = new Agent({ connect: { rejectUnauthorized: false } });
const TIMEOUT_MS = 8_000;

export class ServiceHttpError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
  }
}

export interface HttpResponse {
  status: number;
  headers: Headers;
  text: string;
  json<T = unknown>(): T;
}

/** Base URLs to try: the primary URL, then the fallback URL. */
export function baseUrls(service: ServiceWithSecrets): string[] {
  return [service.url, service.fallbackUrl]
    .filter((value): value is string => Boolean(value && value.trim()))
    .map((value) => value.trim().replace(/\/+$/, ""))
    .filter((value, index, all) => all.indexOf(value) === index);
}

const basic = (user: string, pass: string) => "Basic " + Buffer.from(`${user}:${pass}`).toString("base64");

/** Same header authentication as the mobile app's AuthInterceptor, for header-based services. */
export function authHeaders(service: ServiceWithSecrets): Record<string, string> {
  const s = service.secrets;
  const apiKey = s.apiKey?.trim() ?? "";
  const token = s.token?.trim() ?? "";
  switch (service.type) {
    case "portainer":
      return apiKey ? { "X-API-Key": apiKey } : token ? { Authorization: `Bearer ${token}` } : {};
    case "radarr":
    case "sonarr":
    case "lidarr":
    case "prowlarr":
    case "bazarr":
    case "jellyseerr":
    case "healthchecks":
      return apiKey ? { "X-Api-Key": apiKey } : {};
    case "gluetun":
    case "flaresolverr":
      return apiKey ? { "X-Api-Key": apiKey, Authorization: `Bearer ${apiKey}` } : {};
    case "autobrr":
      return apiKey ? { "X-API-Token": apiKey } : {};
    case "jellystat":
      return apiKey ? { "X-API-Token": apiKey } : {};
    case "safeline":
      return apiKey ? { "X-SLCE-API-TOKEN": apiKey } : {};
    case "unifi_network":
      return apiKey ? { "X-API-Key": apiKey } : {};
    case "plex":
      return apiKey ? { "X-Plex-Token": apiKey } : {};
    case "proxmox":
      return apiKey ? { Authorization: `PVEAPIToken=${apiKey}` } : token ? { Cookie: `PVEAuthCookie=${token}` } : {};
    case "uptime_kuma":
    case "peanut":
      return s.password ? { Authorization: basic(s.username ?? "", s.password) } : {};
    case "patchmon":
      return s.username || s.password ? { Authorization: basic(s.username ?? "", s.password ?? "") } : {};
    case "adguard_home":
      if (s.username && s.password) return { Authorization: basic(s.username, s.password) };
      if (token) return { Authorization: `Basic ${token.replace(/^basic:/, "")}` };
      return {};
    case "gitea":
      if (!token) return {};
      return token.startsWith("basic:") ? { Authorization: `Basic ${token.slice(6)}` } : { Authorization: `token ${token}` };
    case "beszel":
    case "crafty_controller":
      return token ? { Authorization: `Bearer ${token}` } : {};
    case "nginx_proxy_manager":
      return token ? { Authorization: `Bearer ${token}`, Cookie: `token=${token}` } : {};
    case "pangolin":
    case "linux_update":
    case "dockmon":
      return apiKey ? { Authorization: `Bearer ${apiKey.replace(/^Bearer\s+/i, "")}` } : {};
    case "komodo":
      return { ...(apiKey ? { "X-Api-Key": apiKey } : {}), ...(s.password ? { "X-Api-Secret": s.password } : {}) };
    case "wakapi":
      return apiKey ? { Authorization: "Basic " + Buffer.from(apiKey).toString("base64") } : {};
    case "pterodactyl":
    case "calagopus":
    case "truenas":
      return apiKey ? { Authorization: `Bearer ${apiKey}` } : {};
    case "pihole":
      return token && s.piholeAuthMode !== "LEGACY" ? { "X-FTL-SID": token } : {};
    case "dockhand":
    case "maltrail":
      return token ? { Cookie: token } : {};
    default:
      return {};
  }
}

export interface RequestOptions {
  method?: string;
  headers?: Record<string, string>;
  body?: string;
  /** Skip the service's own auth headers (login calls, public endpoints). */
  noAuth?: boolean;
}

/** One request against a base URL. Throws ServiceHttpError on non-2xx statuses. */
export async function requestAt(service: ServiceWithSecrets, base: string, path: string, options: RequestOptions = {}): Promise<HttpResponse> {
  const response = await fetch(base + path, {
    method: options.method ?? "GET",
    headers: { Accept: "application/json", ...(options.noAuth ? {} : authHeaders(service)), ...options.headers },
    body: options.body,
    redirect: "follow",
    signal: AbortSignal.timeout(TIMEOUT_MS),
    dispatcher: service.allowSelfSigned ? insecureAgent : undefined,
  });
  const text = await response.text();
  if (!response.ok) throw new ServiceHttpError(response.status, `HTTP ${response.status}`);
  return {
    status: response.status,
    headers: response.headers as unknown as Headers,
    text,
    json<T>() {
      return JSON.parse(text) as T;
    },
  };
}

/** Raw fetch honouring the service's self-signed certificate setting (login flows). */
export function rawFetch(service: ServiceWithSecrets, url: string, init: Parameters<typeof fetch>[1] = {}) {
  return fetch(url, {
    ...init,
    signal: AbortSignal.timeout(TIMEOUT_MS),
    dispatcher: service.allowSelfSigned ? insecureAgent : undefined,
  });
}

/** True when any base URL answers at all: an HTTP error status still proves the service is up. */
export async function isReachable(service: ServiceWithSecrets): Promise<boolean> {
  for (const base of baseUrls(service)) {
    try {
      await requestAt(service, base, "/", { noAuth: true, headers: { Accept: "*/*" } });
      return true;
    } catch (error) {
      if (error instanceof ServiceHttpError) return true;
    }
  }
  return false;
}

/** Tries each base URL in turn; auth errors are final (another URL will not fix credentials). */
export async function request(service: ServiceWithSecrets, path: string, options: RequestOptions = {}): Promise<HttpResponse> {
  let lastError: unknown = null;
  for (const base of baseUrls(service)) {
    try {
      return await requestAt(service, base, path, options);
    } catch (error) {
      if (error instanceof ServiceHttpError && (error.status === 401 || error.status === 403)) throw error;
      lastError = error;
    }
  }
  throw lastError ?? new Error("No URL configured");
}
