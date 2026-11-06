// Homelab mobile backup file ("HLAB"), byte-compatible with the Android app's BackupCrypto:
//   magic "HLAB" (4) | version 1 (1) | salt (16) | nonce (12) | AES-256-GCM ciphertext + tag (16)
// Key: PBKDF2-HMAC-SHA256, 600 000 iterations, 256 bits. Uses WebCrypto, so it runs in the
// browser (import preview, the password never leaves the page) and on the server (export).

export interface BackupServiceEntry {
  type: string;
  label: string;
  url: string;
  token?: string | null;
  proxmoxCsrfToken?: string | null;
  username?: string | null;
  apiKey?: string | null;
  piholePassword?: string | null;
  piholeAuthMode?: string | null;
  fallbackUrl?: string | null;
  allowSelfSigned: boolean;
  password?: string | null;
  isPreferred: boolean;
}

export interface BackupEnvelope {
  version: number;
  exportedAt: string;
  appVersion: string;
  services: BackupServiceEntry[];
}

const MAGIC = [0x48, 0x4c, 0x41, 0x42];
const FORMAT_VERSION = 1;
const SALT_LENGTH = 16;
const NONCE_LENGTH = 12;
const HEADER_SIZE = 4 + 1 + SALT_LENGTH + NONCE_LENGTH;
const ITERATIONS = 600_000;

export class BackupFormatError extends Error {}

async function deriveKey(password: string, salt: Uint8Array): Promise<CryptoKey> {
  const material = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveKey"]);
  return crypto.subtle.deriveKey(
    { name: "PBKDF2", hash: "SHA-256", salt: salt as BufferSource, iterations: ITERATIONS },
    material,
    { name: "AES-GCM", length: 256 },
    false,
    ["encrypt", "decrypt"]
  );
}

export async function decryptBackup(data: Uint8Array, password: string): Promise<BackupEnvelope> {
  if (data.length < HEADER_SIZE + 16 || MAGIC.some((byte, index) => data[index] !== byte)) {
    throw new BackupFormatError("Ce fichier n'est pas une sauvegarde Homelab.");
  }
  if (data[4] !== FORMAT_VERSION) {
    throw new BackupFormatError("Version de sauvegarde non prise en charge.");
  }
  const salt = data.slice(5, 5 + SALT_LENGTH);
  const nonce = data.slice(5 + SALT_LENGTH, HEADER_SIZE);
  const key = await deriveKey(password, salt);
  let plain: ArrayBuffer;
  try {
    plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: nonce, tagLength: 128 }, key, data.slice(HEADER_SIZE));
  } catch {
    throw new BackupFormatError("Mot de passe incorrect ou fichier corrompu.");
  }
  const envelope = JSON.parse(new TextDecoder().decode(plain)) as BackupEnvelope;
  if (!Array.isArray(envelope.services)) throw new BackupFormatError("Sauvegarde invalide.");
  return envelope;
}

export async function encryptBackup(envelope: BackupEnvelope, password: string): Promise<Uint8Array> {
  const salt = crypto.getRandomValues(new Uint8Array(SALT_LENGTH));
  const nonce = crypto.getRandomValues(new Uint8Array(NONCE_LENGTH));
  const key = await deriveKey(password, salt);
  const ciphertext = new Uint8Array(
    await crypto.subtle.encrypt({ name: "AES-GCM", iv: nonce, tagLength: 128 }, key, new TextEncoder().encode(JSON.stringify(envelope)))
  );
  const output = new Uint8Array(HEADER_SIZE + ciphertext.length);
  output.set(MAGIC, 0);
  output[4] = FORMAT_VERSION;
  output.set(salt, 5);
  output.set(nonce, 5 + SALT_LENGTH);
  output.set(ciphertext, HEADER_SIZE);
  return output;
}
