import { Auth, b64, capabilityHash, guard, hex, jsonBody, mutate, requireApi, sha, sql, uuid } from "./auth";

export async function upload(auth: Auth, route: string, id: string, request: Request) {
  requireApi(auth.body.length > 0 && hex(auth.capability));
  const digest = await sha(auth.body);
  // Expiry/class/fence metadata live inside the signed canonical target query, never unsigned headers.
  const params = new URL(request.url).searchParams;
  const reservation = params.get("reservation");
  requireApi([...params.keys()].join(",") === (reservation ? "class,expires,generation,grant,reservation" :
    "class,expires,generation,grant"));
  const kind = params.get("class"); const until = Number(params.get("expires"));
  const generation = Number(params.get("generation")); const grant = params.get("grant")!;
  requireApi(kind === "AUTHOR" || kind === "MESSAGE");
  requireApi(Number.isSafeInteger(until) && until > auth.now && until <= auth.now + 7 * 86400000);
  requireApi(Number.isSafeInteger(generation) && generation >= 0 && (hex(grant) || grant === "none"));
  requireApi(kind === "AUTHOR" ? generation > 0 && hex(grant) : generation === 0 && grant === "none");
  requireApi(reservation == null || (uuid(reservation) && kind === "AUTHOR"));
  const hash = await capabilityHash(auth.env, auth.capability);
  requireApi(await sql(auth, "SELECT 1 FROM routes WHERE route_id=? AND sender_handle=? AND capability_hash=?",
    route, auth.device.handle, hash).first(), 403, "FORBIDDEN");
  const tombstone = await sql(auth, "SELECT digest,result FROM packet_tombstones WHERE route_id=? AND delivery_id=?",
    route, id).first<{digest: string | null; result: string}>();
  if (tombstone) {
    requireApi(!tombstone.digest || tombstone.digest === digest, 409, "PACKET_COLLISION");
    // Do not reveal even tombstone status to a guessed route/capability.
    const permit = await sql(auth, "SELECT 1 FROM routes WHERE route_id=? AND sender_handle=? AND capability_hash=?",
      route, auth.device.handle, hash).first();
    requireApi(permit, 403, "FORBIDDEN");
    return {accepted: true, result: tombstone.result};
  }
  const old = await sql(auth, "SELECT digest FROM packets WHERE route_id=? AND delivery_id=?", route, id)
    .first<{digest: string}>();
  requireApi(!old || old.digest === digest, 409, "PACKET_COLLISION");
  return mutate(auth, {accepted: true}, [
    guard(auth, "EXISTS(SELECT 1 FROM routes r JOIN relay_devices d ON d.handle=r.owner_handle " +
      "WHERE r.route_id=? AND r.sender_handle=? AND r.capability_hash=? AND r.state='ACTIVE' " +
      "AND r.expires_at>=? AND d.state='ACTIVE' AND d.recovery_epoch=? AND " +
      "(?='MESSAGE' OR EXISTS(SELECT 1 FROM author_fences f WHERE f.owner_handle=r.authority_handle " +
      "AND f.generation=? AND f.author_handle=? AND f.grant_digest=?))) " +
      "AND NOT EXISTS(SELECT 1 FROM packet_tombstones WHERE route_id=? AND delivery_id=?) " +
      "AND NOT EXISTS(SELECT 1 FROM packets WHERE route_id=? AND delivery_id=? AND digest!=?) " +
      "AND (? IS NULL OR EXISTS(SELECT 1 FROM baseline_reservations WHERE route_id=? AND reservation_id=? " +
      "AND expires_at>? AND (remaining_bytes>=? AND remaining_parts>0 OR EXISTS " +
      "(SELECT 1 FROM packets WHERE route_id=? AND delivery_id=?))))",
      route, auth.device.handle, hash, until, auth.env.RECOVERY_EPOCH, kind, generation, auth.device.handle, grant,
      route, id, route, id, digest, reservation, route, reservation, auth.now, auth.body.length, route, id),
    sql(auth, "INSERT INTO daily_usage(device_handle,day,bytes) SELECT ?,?,? WHERE NOT EXISTS " +
      "(SELECT 1 FROM packets WHERE route_id=? AND delivery_id=?) AND ? IS NULL ON CONFLICT(device_handle,day) " +
      "DO UPDATE SET bytes=bytes+excluded.bytes", auth.device.handle, Math.floor(auth.now / 86400000),
      auth.body.length, route, id, reservation),
    sql(auth, "INSERT INTO packets(route_id,delivery_id,ciphertext,digest,bytes,created_at,expires_at,reservation_id) " +
      "VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(route_id,delivery_id) DO NOTHING",
      route, id, auth.body.slice().buffer, digest, auth.body.length, auth.now, until, reservation)
  ]);
}

export async function fetchPackets(auth: Auth, handle: string, request: Request) {
  requireApi(handle === auth.device.handle, 403, "FORBIDDEN");
  const params = new URL(request.url).searchParams;
  requireApi([...params.keys()].every(x => x === "cursor" || x === "limit") && params.size <= 2);
  const cursor = params.get("cursor") ?? "0"; const limit = Number(params.get("limit") ?? 32);
  requireApi(/^(0|[1-9]\d{0,15})$/.test(cursor) && Number.isSafeInteger(Number(cursor)) &&
    Number.isInteger(limit) && limit >= 1 && limit <= 32);
  const rows = await sql(auth, "SELECT p.relay_seq,p.route_id,p.delivery_id,p.ciphertext FROM packets p " +
    "JOIN routes r ON r.route_id=p.route_id JOIN relay_devices d ON d.handle=r.owner_handle " +
    "WHERE r.owner_handle=? AND r.state='ACTIVE' AND d.state='ACTIVE' AND d.recovery_epoch=? " +
    "AND p.relay_seq>? AND p.expires_at>? AND r.expires_at>? ORDER BY p.relay_seq LIMIT ?",
    handle, auth.env.RECOVERY_EPOCH, Number(cursor), auth.now, auth.now, limit).all<any>();
  const packets = []; let size = 128; let next = cursor;
  for (const row of rows.results) {
    const packet = {sequence: row.relay_seq, route: row.route_id, id: row.delivery_id,
      ciphertext: b64(Uint8Array.from(row.ciphertext))};
    const n = JSON.stringify(packet).length + 1;
    if (size + n > 512 * 1024) break;
    packets.push(packet); size += n; next = String(row.relay_seq);
  }
  return {packets, cursor: next};
}

export async function cancelPacket(auth: Auth, route: string, id: string) {
  return mutate(auth, {deleted: true}, [
    guard(auth, "EXISTS(SELECT 1 FROM routes WHERE route_id=? AND (owner_handle=? OR sender_handle=?))",
      route, auth.device.handle, auth.device.handle),
    sql(auth, "INSERT INTO packet_tombstones(route_id,delivery_id,digest,result,expires_at) " +
      "VALUES(?,?,NULL,'CANCELLED',?) ON CONFLICT(route_id,delivery_id) DO NOTHING", route, id, auth.now + 8 * 86400000),
    sql(auth, "DELETE FROM packets WHERE route_id=? AND delivery_id=?", route, id)
  ]);
}
export async function acknowledge(auth: Auth, handle: string) {
  requireApi(handle === auth.device.handle, 403, "FORBIDDEN");
  const items = jsonBody(auth).packets;
  requireApi(Array.isArray(items) && items.length > 0 && items.length <= 128);
  const seen = new Set();
  for (const item of items) {
    requireApi(item && hex(item.route) && uuid(item.id) && !seen.has(item.route + item.id));
    seen.add(item.route + item.id);
  }
  const list = JSON.stringify(items);
  // A bounded JSON parameter avoids 128*4 D1 statements and stays below Free subrequest limits.
  return mutate(auth, {deleted: true}, [
    guard(auth, "NOT EXISTS(SELECT 1 FROM json_each(?) j LEFT JOIN routes r " +
      "ON r.route_id=json_extract(j.value,'$.route') WHERE r.route_id IS NULL OR r.owner_handle!=?)", list, handle),
    sql(auth, "INSERT INTO packet_tombstones(route_id,delivery_id,digest,result,expires_at) " +
      "SELECT p.route_id,p.delivery_id,p.digest,'IMPORTED',? FROM packets p JOIN json_each(?) j " +
      "ON p.route_id=json_extract(j.value,'$.route') AND p.delivery_id=json_extract(j.value,'$.id') WHERE 1 " +
      "ON CONFLICT(route_id,delivery_id) DO NOTHING", auth.now + 8 * 86400000, list),
    sql(auth, "DELETE FROM packets WHERE EXISTS(SELECT 1 FROM json_each(?) j " +
      "WHERE route_id=json_extract(j.value,'$.route') AND delivery_id=json_extract(j.value,'$.id'))", list)
  ]);
}
