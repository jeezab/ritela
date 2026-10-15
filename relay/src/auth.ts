export interface Env {
  DB: D1Database;
  CAPABILITY_PEPPER: string;
  RECOVERY_EPOCH: string;
  RATE_LIMITER: RateLimit;
}
export class ApiError extends Error {
  constructor(public status: number, public code: string) { super(code); }
}
export function requireApi(ok: unknown, status = 400, code = "INVALID_REQUEST"): asserts ok {
  if (!ok) throw new ApiError(status, code);
}
export const uuid = (value: unknown): value is string => typeof value === "string" &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(value);
export const hex = (value: unknown): value is string => typeof value === "string" && /^[0-9a-f]{64}$/.test(value);
export const utf8 = (value: string) => new TextEncoder().encode(value);
export function b64(value: ArrayBuffer | Uint8Array): string {
  const bytes = new Uint8Array(value); let raw = "";
  for (let i = 0; i < bytes.length; i += 16384) raw += String.fromCharCode(...bytes.subarray(i, i + 16384));
  return btoa(raw);
}
export function unbase64(value: string, max = 4096): Uint8Array<ArrayBuffer> {
  requireApi(value.length <= max && /^[A-Za-z0-9+/]*={0,2}$/.test(value));
  const bytes = Uint8Array.from(atob(value), c => c.charCodeAt(0));
  requireApi(b64(bytes) === value);
  return bytes;
}
export async function sha(value: Uint8Array | string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", typeof value === "string" ? utf8(value) : value.slice().buffer as ArrayBuffer);
  return [...new Uint8Array(digest)].map(x => x.toString(16).padStart(2, "0")).join("");
}
export async function capabilityHash(env: Env, capability: string): Promise<string> {
  requireApi(hex(capability));
  const key = await crypto.subtle.importKey("raw", utf8(env.CAPABILITY_PEPPER),
    { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return [...new Uint8Array(await crypto.subtle.sign("HMAC", key, utf8(capability)))]
    .map(x => x.toString(16).padStart(2, "0")).join("");
}
export function canonical(method: string, target: string, device: string, version: string,
  time: string, requestId: string, bodyHash: string, capHash: string): string {
  return ["ritela.relay", "1", method, target, device, version, time, requestId, bodyHash, capHash].join("\n");
}
export async function verify(publicKey: string, body: string, signature: string): Promise<boolean> {
  try {
    const key = await crypto.subtle.importKey("spki", unbase64(publicKey),
      { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]);
    const bytes = unbase64(signature, 100);
    return bytes.length === 64 && await crypto.subtle.verify(
      { name: "ECDSA", hash: "SHA-256" }, key, bytes, utf8(body));
  } catch { return false; }
}
export async function readBounded(request: Request, limit: number): Promise<Uint8Array> {
  requireApi(!request.headers.has("content-encoding"), 415, "ENCODING_UNSUPPORTED");
  const reader = request.body?.getReader();
  if (!reader) return new Uint8Array();
  const chunks: Uint8Array[] = []; let size = 0;
  try {
    while (true) {
      const result = await reader.read(); if (result.done) break;
      size += result.value.length;
      if (size > limit) { await reader.cancel(); throw new ApiError(413, "TOO_LARGE"); }
      chunks.push(result.value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  return bytes;
}
export interface Device { handle: string; api_public_key: string; key_version: number; }
export interface Auth {
  env: Env; device: Device; now: number; requestId: string; digest: string;
  body: Uint8Array; target: string; capability: string;
}
export function jsonBody(auth: Auth): Record<string, any> {
  requireApi(auth.body.length <= 16384);
  let value; try { value = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(auth.body)); }
  catch { throw new ApiError(400, "INVALID_JSON"); }
  requireApi(value && typeof value === "object" && !Array.isArray(value));
  return value;
}
export async function authenticate(request: Request, env: Env, body: Uint8Array,
  registrationKey?: string): Promise<Auth> {
  const url = new URL(request.url); const target = url.pathname + url.search;
  requireApi(!target.includes("%") && !target.includes("//") && /^[a-zA-Z0-9/?.=&_-]+$/.test(target));
  const handle = request.headers.get("x-ritela-device") ?? "";
  const time = request.headers.get("x-ritela-time") ?? "";
  const requestId = request.headers.get("x-ritela-request") ?? "";
  const version = request.headers.get("x-ritela-key-version") ?? "";
  const capability = request.headers.get("x-ritela-capability") ?? "";
  const now = Date.now();
  requireApi(uuid(handle) && uuid(requestId) && version === "1" && /^\d{13}$/.test(time), 401, "UNAUTHORIZED");
  requireApi(Math.abs(Number(time) - now) <= 300000, 401, "CLOCK_WINDOW");
  requireApi(capability === "" || hex(capability));
  const device = registrationKey ? {handle, api_public_key: registrationKey, key_version: 1} :
    await env.DB.prepare("SELECT handle,api_public_key,key_version FROM relay_devices " +
      "WHERE handle=? AND (state='ACTIVE' OR (?=1 AND state='REVOKED')) AND recovery_epoch=?")
      .bind(handle, request.method === "DELETE" && target === `/v1/devices/${handle}` ? 1 : 0,
        env.RECOVERY_EPOCH).first<Device>();
  requireApi(device, 401, "UNAUTHORIZED");
  const message = canonical(request.method, target, handle, version, time, requestId,
    await sha(body), await sha(capability));
  requireApi(await verify(device.api_public_key, message, request.headers.get("x-ritela-signature") ?? ""),
    401, "UNAUTHORIZED");
  return { env, device, now, requestId, digest: await sha(message), body, target, capability };
}
export const sql = (auth: Auth, query: string, ...args: any[]) => auth.env.DB.prepare(query).bind(...args);
export function guard(auth: Auth, condition: string, ...args: any[]): D1PreparedStatement {
  return sql(auth, "INSERT INTO mutation_guards(id,ok) SELECT ?,CASE WHEN " +
    "EXISTS(SELECT 1 FROM relay_devices WHERE handle=? AND api_public_key=? " +
    "AND state='ACTIVE' AND recovery_epoch=?) AND (" + condition + ") THEN 1 ELSE 0 END",
    auth.requestId, auth.device.handle, auth.device.api_public_key, auth.env.RECOVERY_EPOCH, ...args);
}
/** All guards, nonce and effects share one D1 transaction, including quota triggers. */
export async function mutate(auth: Auth, result: unknown, statements: D1PreparedStatement[]): Promise<unknown> {
  const replay = await sql(auth, "SELECT digest,result FROM request_nonces WHERE device_handle=? AND request_id=?",
    auth.device.handle, auth.requestId).first<{digest: string; result: string}>();
  if (replay) { requireApi(replay.digest === auth.digest, 409, "REQUEST_COLLISION"); return JSON.parse(replay.result); }
  const day = Math.floor(auth.now / 86400000);
  try {
    await auth.env.DB.batch([
      guard(auth, "EXISTS(SELECT 1 FROM relay_state WHERE id=1 AND recovery_epoch=?)", auth.env.RECOVERY_EPOCH),
      sql(auth, "DELETE FROM mutation_guards WHERE id=?", auth.requestId),
      sql(auth, "INSERT INTO request_nonces VALUES(?,?,?,?,?)", auth.device.handle, auth.requestId,
        auth.digest, JSON.stringify(result), auth.now + 600000),
      sql(auth, "INSERT INTO daily_usage(device_handle,day,mutations) VALUES(?,?,1) " +
        "ON CONFLICT(device_handle,day) DO UPDATE SET mutations=mutations+1", auth.device.handle, day),
      ...statements,
      sql(auth, "DELETE FROM mutation_guards WHERE id=?", auth.requestId)
    ]);
  } catch (error) {
    const won = await sql(auth, "SELECT digest,result FROM request_nonces WHERE device_handle=? AND request_id=?",
      auth.device.handle, auth.requestId).first<{digest: string; result: string}>();
    if (won) { requireApi(won.digest === auth.digest, 409, "REQUEST_COLLISION"); return JSON.parse(won.result); }
    if (String(error).includes("CHECK constraint failed: bytes") ||
        String(error).includes("CHECK constraint failed: mutations") ||
        String(error).includes("CHECK constraint failed: count") ||
        String(error).includes("CHECK constraint failed: packets") ||
        String(error).includes("CHECK constraint failed: tombstones") ||
        String(error).includes("CHECK constraint failed: nonces")) throw new ApiError(429, "QUOTA");
    throw new ApiError(409, "MUTATION_REJECTED");
  }
  return result;
}
