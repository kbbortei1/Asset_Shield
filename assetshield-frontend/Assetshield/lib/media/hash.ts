import * as Crypto from 'expo-crypto';
import { File } from 'expo-file-system';

/**
 * SHA-256 over RAW BINARY BYTES, returned as lowercase hex.
 *
 * The server recomputes the hash over the received file bytes, so we MUST hash
 * the exact decoded bytes — NOT the base64 text. Verified against the handoff
 * fixture (asset-1.jpg → a3ba97e5…fad0): raw-bytes hashing matches, base64-text
 * hashing does not.
 */
export async function sha256OfBytes(bytes: Uint8Array): Promise<string> {
  // Cast: expo-crypto's BufferSource type is stricter than RN's Uint8Array<ArrayBufferLike>.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const buf = await Crypto.digest(Crypto.CryptoDigestAlgorithm.SHA256, bytes as any);
  return bufToHex(buf);
}

/**
 * Hash the exact bytes of a local file. Call this LAST — after any resize /
 * re-encode — and upload immediately. Anything that mutates the bytes after this
 * invalidates the hash (→ 400 HASH_MISMATCH).
 */
export async function sha256OfFile(uri: string): Promise<string> {
  const bytes = await new File(uri).bytes();
  return sha256OfBytes(bytes);
}

function bufToHex(buf: ArrayBuffer): string {
  const view = new Uint8Array(buf);
  let hex = '';
  for (let i = 0; i < view.length; i++) {
    hex += view[i].toString(16).padStart(2, '0');
  }
  return hex;
}
