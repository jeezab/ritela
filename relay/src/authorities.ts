import { Auth, guard, hex, jsonBody, mutate, requireApi, sha, sql, uuid, verify } from "./auth";

/** Fixed ASCII encoding shared with Android; no JSON canonicalization in signatures. */
export function transferBytes(owner: string, next: string, expected: number,
  oldGrant: string, newGrant: string, transition: string): string {
  return ["ritela.author.transfer", "1", owner, next, String(expected), oldGrant, newGrant, transition].join("\n");
}
export async function initializeAuthority(auth: Auth, handle: string) {
  requireApi(handle === auth.device.handle, 403, "FORBIDDEN");
  const b = jsonBody(auth); requireApi(hex(b.grant));
  const existing = await sql(auth, "SELECT * FROM author_fences WHERE owner_handle=?", handle).first<any>();
  if (existing) {
    requireApi(existing.generation === 1 && existing.author_handle === handle && existing.grant_digest === b.grant,
      409, "AUTHOR_CONFLICT");
    return {generation: 1};
  }
  return mutate(auth, {generation: 1}, [sql(auth,
    "INSERT INTO author_fences(owner_handle,generation,author_handle,grant_digest) VALUES(?,1,?,?)", handle, handle, b.grant)]);
}
export async function transferAuthority(auth: Auth, handle: string) {
  const b = jsonBody(auth);
  requireApi(uuid(b.next) && uuid(b.transition) && Number.isSafeInteger(b.expected) && b.expected > 0 &&
    b.expected < Number.MAX_SAFE_INTEGER && hex(b.oldGrant) && hex(b.newGrant));
  const raw = transferBytes(handle, b.next, b.expected, b.oldGrant, b.newGrant, b.transition);
  const digest = await sha(raw);
  const replay = await sql(auth, "SELECT digest,generation FROM author_transitions WHERE owner_handle=? AND transition_id=?",
    handle, b.transition).first<{digest: string; generation: number}>();
  if (replay) {
    requireApi(replay.digest === digest, 409, "TRANSITION_COLLISION");
    const fence = await sql(auth, "SELECT author_handle FROM author_fences WHERE owner_handle=?", handle).first<any>();
    requireApi(auth.device.handle === handle || auth.device.handle === b.next || auth.device.handle === fence?.author_handle,
      403, "FORBIDDEN");
    return {generation: replay.generation, transition: b.transition};
  }
  const old = await sql(auth, "SELECT f.author_handle,d.api_public_key FROM author_fences f JOIN relay_devices d " +
    "ON d.handle=f.author_handle WHERE f.owner_handle=? AND f.generation=? AND f.grant_digest=? " +
    "AND d.state='ACTIVE' AND d.recovery_epoch=?", handle, b.expected, b.oldGrant, auth.env.RECOVERY_EPOCH).first<any>();
  const next = await sql(auth, "SELECT api_public_key FROM relay_devices WHERE handle=? AND state='ACTIVE' " +
    "AND recovery_epoch=?", b.next, auth.env.RECOVERY_EPOCH).first<any>();
  requireApi(old && next && old.author_handle !== b.next && auth.device.handle === old.author_handle,
    403, "FORBIDDEN");
  requireApi(await verify(old.api_public_key, raw, b.oldSignature) &&
    await verify(next.api_public_key, raw, b.nextSignature), 403, "TRANSFER_SIGNATURE");
  return mutate(auth, {generation: b.expected + 1, transition: b.transition}, [
    guard(auth, "EXISTS(SELECT 1 FROM author_fences WHERE owner_handle=? AND generation=? " +
      "AND grant_digest=? AND author_handle=?) AND EXISTS(SELECT 1 FROM relay_devices " +
      "WHERE handle=? AND api_public_key=? AND state='ACTIVE' AND recovery_epoch=?)",
      handle, b.expected, b.oldGrant, old.author_handle, b.next, next.api_public_key, auth.env.RECOVERY_EPOCH),
    sql(auth, "UPDATE author_fences SET generation=?,author_handle=?,grant_digest=?,transition_id=? " +
      "WHERE owner_handle=?", b.expected + 1, b.next, b.newGrant, b.transition, handle),
    sql(auth, "INSERT INTO author_transitions VALUES(?,?,?,?)", handle, b.transition, digest, b.expected + 1),
    sql(auth, "DELETE FROM baseline_reservations WHERE route_id IN (SELECT route_id FROM routes WHERE authority_handle=?)", handle),
    // Old pending ciphertext is removed atomically; new author needs newly bootstrapped routes.
    sql(auth, "DELETE FROM packets WHERE relay_seq IN (SELECT p.relay_seq FROM packets p JOIN routes r " +
      "ON r.route_id=p.route_id WHERE r.authority_handle=? LIMIT 32)", handle),
    sql(auth, "UPDATE routes SET state='REVOKED' WHERE authority_handle=?", handle)
  ]);
}
