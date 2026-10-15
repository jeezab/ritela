import { Auth, capabilityHash, guard, hex, jsonBody, mutate, requireApi, sql, uuid } from "./auth";

export async function createRoute(auth: Auth) {
  const b = jsonBody(auth);
  requireApi(hex(b.route) && uuid(b.sender) && hex(b.capability));
  requireApi(b.authority == null || uuid(b.authority));
  requireApi(Number.isSafeInteger(b.expiresAt) && b.expiresAt > auth.now && b.expiresAt <= auth.now + 7 * 86400000);
  const hash = await capabilityHash(auth.env, b.capability);
  const previous = await sql(auth, "SELECT * FROM routes WHERE route_id=?", b.route).first<any>();
  if (previous) {
    requireApi(previous.owner_handle === auth.device.handle && previous.sender_handle === b.sender &&
      previous.capability_hash === hash && previous.authority_handle === (b.authority ?? null) &&
      previous.expires_at === b.expiresAt && previous.state === "ACTIVE", 409, "ROUTE_COLLISION");
    return {route: b.route};
  }
  return mutate(auth, {route: b.route}, [
    guard(auth, "EXISTS(SELECT 1 FROM relay_devices WHERE handle=? AND state='ACTIVE' AND recovery_epoch=?) " +
      "AND (SELECT COUNT(*) FROM routes WHERE owner_handle=?)<64 " +
      "AND (? IS NULL OR EXISTS(SELECT 1 FROM author_fences WHERE owner_handle=?))",
      b.sender, auth.env.RECOVERY_EPOCH, auth.device.handle, b.authority ?? null, b.authority ?? null),
    sql(auth, "INSERT INTO routes(route_id,owner_handle,sender_handle,authority_handle,capability_hash,state,expires_at) " +
      "VALUES(?,?,?,?,?,'ACTIVE',?)", b.route, auth.device.handle, b.sender, b.authority ?? null, hash, b.expiresAt)
  ]);
}
export async function revokeRoute(auth: Auth, route: string) {
  return mutate(auth, {revoked: true}, [
    guard(auth, "EXISTS(SELECT 1 FROM routes WHERE route_id=? AND (owner_handle=? OR sender_handle=?))",
      route, auth.device.handle, auth.device.handle),
    sql(auth, "UPDATE routes SET state='REVOKED' WHERE route_id=?", route),
    sql(auth, "DELETE FROM baseline_reservations WHERE route_id=?", route),
    sql(auth, "DELETE FROM packets WHERE relay_seq IN (SELECT relay_seq FROM packets WHERE route_id=? LIMIT 32)", route)
  ]);
}
export async function revokeDevice(auth: Auth, handle: string) {
  requireApi(handle === auth.device.handle, 403, "FORBIDDEN");
  const previous = await sql(auth, "SELECT state FROM relay_devices WHERE handle=?", handle).first<any>();
  if (previous?.state === "REVOKED") return {revoked: true};
  return mutate(auth, {revoked: true}, [
    sql(auth, "UPDATE relay_devices SET state='REVOKED' WHERE handle=?", handle),
    sql(auth, "UPDATE routes SET state='REVOKED' WHERE owner_handle=? OR sender_handle=?", handle, handle),
    sql(auth, "DELETE FROM baseline_reservations WHERE route_id IN " +
      "(SELECT route_id FROM routes WHERE owner_handle=? OR sender_handle=?)", handle, handle),
    sql(auth, "DELETE FROM packets WHERE relay_seq IN (SELECT p.relay_seq FROM packets p JOIN routes r " +
      "ON r.route_id=p.route_id WHERE r.owner_handle=? OR r.sender_handle=? LIMIT 32)", handle, handle)
  ]);
}

export async function reserveBaseline(auth: Auth, route: string) {
  const b = jsonBody(auth);
  requireApi(uuid(b.id) && Number.isSafeInteger(b.bytes) && b.bytes > 0 && b.bytes <= 6291456 &&
    Number.isInteger(b.parts) && b.parts > 0 && b.parts <= 64 && Number.isSafeInteger(b.expiresAt) &&
    b.expiresAt > auth.now && b.expiresAt <= auth.now + 600000);
  const old = await sql(auth, "SELECT * FROM baseline_reservations WHERE route_id=?", route).first<any>();
  if (old?.reservation_id === b.id) {
    requireApi(old.total_bytes === b.bytes && old.total_parts === b.parts && old.expires_at === b.expiresAt &&
      old.expires_at > auth.now, 409, "RESERVATION_COLLISION");
    requireApi(await sql(auth, "SELECT 1 FROM routes WHERE route_id=? AND owner_handle=? AND state='ACTIVE'",
      route, auth.device.handle).first(), 403, "FORBIDDEN");
    return {reservation: b.id};
  }
  return mutate(auth, {reservation: b.id}, [
    guard(auth, "EXISTS(SELECT 1 FROM routes r JOIN author_fences f ON r.authority_handle=f.owner_handle " +
      "WHERE r.route_id=? AND r.owner_handle=? AND r.state='ACTIVE' AND r.expires_at>=? AND f.author_handle=r.sender_handle)",
      route, auth.device.handle, b.expiresAt),
    sql(auth, "DELETE FROM baseline_reservations WHERE route_id=? AND expires_at<=?", route, auth.now),
    sql(auth, "INSERT INTO baseline_reservations VALUES(?,?,?,?,?,?,?)", route, b.id, b.bytes, b.parts,
      b.bytes, b.parts, b.expiresAt)
  ]);
}
