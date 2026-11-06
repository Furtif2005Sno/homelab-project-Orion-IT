import "server-only";
import { createCipheriv, createDecipheriv, createHash, randomBytes } from "node:crypto";
import { env, requireSecret } from "./env";

// Service credentials at rest: AES-256-GCM, key derived from HOMELAB_ENCRYPTION_KEY.
// Format: "v1:" + base64(nonce[12] | ciphertext | tag[16]).

function key(): Buffer {
  return createHash("sha256").update(requireSecret("HOMELAB_ENCRYPTION_KEY", env.encryptionKey)).digest();
}

export function encryptJson(value: unknown): string {
  const nonce = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key(), nonce);
  const ciphertext = Buffer.concat([cipher.update(JSON.stringify(value), "utf8"), cipher.final()]);
  return "v1:" + Buffer.concat([nonce, ciphertext, cipher.getAuthTag()]).toString("base64");
}

export function decryptJson<T>(sealed: string): T {
  if (!sealed.startsWith("v1:")) throw new Error("Unknown secret format");
  const raw = Buffer.from(sealed.slice(3), "base64");
  const nonce = raw.subarray(0, 12);
  const tag = raw.subarray(raw.length - 16);
  const ciphertext = raw.subarray(12, raw.length - 16);
  const decipher = createDecipheriv("aes-256-gcm", key(), nonce);
  decipher.setAuthTag(tag);
  return JSON.parse(Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString("utf8")) as T;
}
