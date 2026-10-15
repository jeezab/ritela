import { ApiError, Auth, Env, authenticate, hex, jsonBody, readBounded, requireApi, sha, sql, uuid } from "./auth";
import { initializeAuthority, transferAuthority } from "./authorities";
import { cleanup } from "./cleanup";
import { acknowledge, cancelPacket, fetchPackets, upload } from "./packets";
import { createRoute, reserveBaseline, revokeDevice, revokeRoute } from "./routes";

async function register(auth: Auth) {
  const b = jsonBody(auth);
  requireApi(b.handle === auth.device.handle && hex(b.activation) && b.publicKey === auth.device.api_public_key);
  const existingDevice = await sql(auth, "SELECT 1 FROM relay_devices WHERE handle=? AND api_public_key=? " +
    "AND state='ACTIVE' AND recovery_epoch=?", b.handle, b.publicKey, auth.env.RECOVERY_EPOCH).first();
  if (existingDevice) return {handle: b.handle};
  const tokenHash = await sha(b.activation); const bodyHash = await sha(auth.body);
  const token = await sql(auth, "SELECT * FROM activation_tokens WHERE token_hash=?", tokenHash).first<any>();
  if (token?.redeemed_by) {
    requireApi(token.redeemed_by === b.handle && token.registration_digest === bodyHash, 403, "ACTIVATION_REJECTED");
    const existing = await sql(auth, "SELECT 1 FROM relay_devices WHERE handle=? AND api_public_key=? " +
      "AND state='ACTIVE' AND recovery_epoch=?", b.handle, b.publicKey, auth.env.RECOVERY_EPOCH).first();
    requireApi(existing, 403, "ACTIVATION_REJECTED");
    return {handle: b.handle};
  }
  try {
    await auth.env.DB.batch([
      sql(auth, "INSERT INTO mutation_guards(id,ok) SELECT ?,CASE WHEN EXISTS(SELECT 1 FROM activation_tokens " +
        "WHERE token_hash=? AND expires_at>? AND redeemed_by IS NULL) AND " +
        "(SELECT COUNT(*) FROM relay_devices)<100 AND EXISTS(SELECT 1 FROM relay_state WHERE id=1 " +
        "AND recovery_epoch=?) THEN 1 ELSE 0 END", auth.requestId, tokenHash, auth.now, auth.env.RECOVERY_EPOCH),
      sql(auth, "INSERT INTO relay_devices(handle,api_public_key,key_version,state,recovery_epoch,created_at) " +
        "VALUES(?,?,1,'ACTIVE',?,?)", b.handle, b.publicKey, auth.env.RECOVERY_EPOCH, auth.now),
      sql(auth, "UPDATE activation_tokens SET redeemed_by=?,registration_digest=? WHERE token_hash=?",
        b.handle, bodyHash, tokenHash),
      sql(auth, "DELETE FROM mutation_guards WHERE id=?", auth.requestId)
    ]);
  } catch {
    const won = await sql(auth, "SELECT 1 FROM relay_devices WHERE handle=? AND api_public_key=? " +
      "AND state='ACTIVE' AND recovery_epoch=?", b.handle, b.publicKey, auth.env.RECOVERY_EPOCH).first();
    if (won) return {handle: b.handle};
    throw new ApiError(403, "ACTIVATION_REJECTED");
  }
  return {handle: b.handle};
}
export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      requireApi(env.CAPABILITY_PEPPER?.length >= 32 && env.RECOVERY_EPOCH?.length >= 16 && env.RATE_LIMITER, 503, "NOT_CONFIGURED");
      requireApi((await env.RATE_LIMITER.limit({key: "edge:" + await sha(request.headers.get("CF-Connecting-IP") ?? "local")})).success,
        429, "RATE_LIMIT");
      const state = await env.DB.prepare("SELECT recovery_epoch FROM relay_state WHERE id=1").first<any>();
      requireApi(state?.recovery_epoch === env.RECOVERY_EPOCH, 503, "RECOVERY_FENCE");
      const path = new URL(request.url).pathname;
      const isUpload = request.method === "PUT" && /^\/v1\/routes\/[^/]+\/packets\/[^/]+$/.test(path);
      const body = await readBounded(request, isUpload ? 262144 : 16384);
      const enrolling = path === "/v1/devices" && request.method === "POST";
      let registrationKey: string | undefined;
      if (enrolling) {
        try { registrationKey = JSON.parse(new TextDecoder("utf-8", {fatal: true}).decode(body)).publicKey; }
        catch { throw new ApiError(400, "INVALID_JSON"); }
        requireApi(typeof registrationKey === "string" && registrationKey.length <= 512);
      }
      const auth = await authenticate(request, env, body, registrationKey);
      requireApi((await env.RATE_LIMITER.limit({key: "device:" + auth.device.handle})).success, 429, "RATE_LIMIT");
      let result: unknown;
      const parts = path.split("/").slice(1);
      if (enrolling) result = await register(auth);
      else if (path === "/v1/routes" && request.method === "POST") result = await createRoute(auth);
      else if (parts[0] === "v1" && parts.length === 4 && parts[1] === "authorities" && uuid(parts[2]) && request.method === "POST") {
        if (parts[3] === "initialize") result = await initializeAuthority(auth, parts[2]);
        else if (parts[3] === "transfer") result = await transferAuthority(auth, parts[2]);
        else throw new ApiError(404, "NOT_FOUND");
      } else if (parts[0] === "v1" && parts[1] === "routes" && hex(parts[2])) {
        if (parts.length === 3 && request.method === "DELETE") result = await revokeRoute(auth, parts[2]);
        else if (parts.length === 4 && parts[3] === "reserve" && request.method === "POST") result = await reserveBaseline(auth, parts[2]);
        else if (parts.length === 5 && parts[3] === "packets" && uuid(parts[4])) {
          if (request.method === "PUT") result = await upload(auth, parts[2], parts[4], request);
          else if (request.method === "DELETE") result = await cancelPacket(auth, parts[2], parts[4]);
          else throw new ApiError(404, "NOT_FOUND");
        } else throw new ApiError(404, "NOT_FOUND");
      } else if (parts[0] === "v1" && parts[1] === "devices" && uuid(parts[2])) {
        if (parts.length === 3 && request.method === "DELETE") result = await revokeDevice(auth, parts[2]);
        else if (parts.length === 4 && parts[3] === "packets" && request.method === "GET")
          result = await fetchPackets(auth, parts[2], request);
        else if (parts.length === 4 && parts[3] === "ack" && request.method === "POST") result = await acknowledge(auth, parts[2]);
        else throw new ApiError(404, "NOT_FOUND");
      } else throw new ApiError(404, "NOT_FOUND");
      return Response.json(result, { headers: {"Cache-Control": "no-store"} });
    } catch (error) {
      // Deliberately do not log exception objects (D1 errors can contain bound parameters).
      const failure = error instanceof ApiError ? error : new ApiError(503, "UNAVAILABLE");
      return Response.json({error: failure.code}, {status: failure.status,
        headers: {"Cache-Control": "no-store", ...(failure.status === 429 ? {"Retry-After": "60"} : {})}});
    }
  },
  async scheduled(_event: ScheduledController, env: Env) { await cleanup(env); }
};
