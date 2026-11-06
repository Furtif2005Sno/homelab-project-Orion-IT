import "server-only";
import { mkdirSync } from "node:fs";
import path from "node:path";
import { randomUUID } from "node:crypto";
import { DatabaseSync } from "node:sqlite";
import { decryptJson, encryptJson } from "./crypto";
import { env } from "./env";

/** Credentials kept encrypted at rest. Field names follow the app's backup entries. */
export interface ServiceSecrets {
  token?: string | null;
  proxmoxCsrfToken?: string | null;
  username?: string | null;
  apiKey?: string | null;
  piholePassword?: string | null;
  piholeAuthMode?: string | null;
  password?: string | null;
}

export interface ServiceRecord {
  id: string;
  type: string;
  label: string;
  url: string;
  fallbackUrl: string | null;
  allowSelfSigned: boolean;
  isPreferred: boolean;
  position: number;
  updatedAt: string;
}

export interface ServiceWithSecrets extends ServiceRecord {
  secrets: ServiceSecrets;
}

interface ServiceRow {
  id: string;
  type: string;
  label: string;
  url: string;
  fallback_url: string | null;
  allow_self_signed: number;
  is_preferred: number;
  position: number;
  secrets: string;
  updated_at: string;
  deleted_at: string | null;
}

let database: DatabaseSync | null = null;

function db(): DatabaseSync {
  if (database) return database;
  mkdirSync(env.dataDir, { recursive: true });
  database = new DatabaseSync(path.join(env.dataDir, "homelab.db"));
  database.exec(`
    PRAGMA journal_mode = WAL;
    CREATE TABLE IF NOT EXISTS services (
      id TEXT PRIMARY KEY,
      type TEXT NOT NULL,
      label TEXT NOT NULL,
      url TEXT NOT NULL,
      fallback_url TEXT,
      allow_self_signed INTEGER NOT NULL DEFAULT 0,
      is_preferred INTEGER NOT NULL DEFAULT 0,
      position INTEGER NOT NULL DEFAULT 0,
      secrets TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      -- Tombstone instead of a hard delete, so a future mobile sync can propagate deletions.
      deleted_at TEXT
    );
  `);
  return database;
}

function toRecord(row: ServiceRow): ServiceRecord {
  return {
    id: row.id,
    type: row.type,
    label: row.label,
    url: row.url,
    fallbackUrl: row.fallback_url,
    allowSelfSigned: row.allow_self_signed === 1,
    isPreferred: row.is_preferred === 1,
    position: row.position,
    updatedAt: row.updated_at,
  };
}

export function listServices(): ServiceRecord[] {
  const rows = db()
    .prepare("SELECT * FROM services WHERE deleted_at IS NULL ORDER BY position, label")
    .all() as unknown as ServiceRow[];
  return rows.map(toRecord);
}

export function getService(id: string): ServiceWithSecrets | null {
  const row = db().prepare("SELECT * FROM services WHERE id = ? AND deleted_at IS NULL").get(id) as unknown as ServiceRow | undefined;
  if (!row) return null;
  return { ...toRecord(row), secrets: decryptJson<ServiceSecrets>(row.secrets) };
}

export function listServicesWithSecrets(): ServiceWithSecrets[] {
  const rows = db()
    .prepare("SELECT * FROM services WHERE deleted_at IS NULL ORDER BY position, label")
    .all() as unknown as ServiceRow[];
  return rows.map((row) => ({ ...toRecord(row), secrets: decryptJson<ServiceSecrets>(row.secrets) }));
}

export interface ServiceInput {
  id?: string;
  type: string;
  label: string;
  url: string;
  fallbackUrl?: string | null;
  allowSelfSigned?: boolean;
  isPreferred?: boolean;
  secrets: ServiceSecrets;
}

/** Inserts or updates by id; returns the stored id. */
export function upsertService(input: ServiceInput): string {
  const id = input.id ?? randomUUID();
  const now = new Date().toISOString();
  const position = (db().prepare("SELECT COALESCE(MAX(position), -1) + 1 AS next FROM services").get() as { next: number }).next;
  db().prepare(`
    INSERT INTO services (id, type, label, url, fallback_url, allow_self_signed, is_preferred, position, secrets, updated_at, deleted_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
    ON CONFLICT(id) DO UPDATE SET
      type = excluded.type, label = excluded.label, url = excluded.url, fallback_url = excluded.fallback_url,
      allow_self_signed = excluded.allow_self_signed, is_preferred = excluded.is_preferred,
      secrets = excluded.secrets, updated_at = excluded.updated_at, deleted_at = NULL
  `).run(
    id,
    input.type,
    input.label,
    input.url,
    input.fallbackUrl ?? null,
    input.allowSelfSigned ? 1 : 0,
    input.isPreferred ? 1 : 0,
    position,
    encryptJson(input.secrets),
    now
  );
  return id;
}

/** Same service already stored? (backups carry no id, so type + URL + label identify it). */
export function findServiceId(type: string, url: string, label: string): string | null {
  const row = db()
    .prepare("SELECT id FROM services WHERE type = ? AND url = ? AND label = ? AND deleted_at IS NULL")
    .get(type, url, label) as { id: string } | undefined;
  return row?.id ?? null;
}

export function deleteService(id: string): boolean {
  const now = new Date().toISOString();
  const result = db().prepare("UPDATE services SET deleted_at = ?, updated_at = ? WHERE id = ? AND deleted_at IS NULL").run(now, now, id);
  return Number(result.changes) > 0;
}
