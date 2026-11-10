import { Auth, hex, jsonBody, mutate, requireApi, sql } from "./auth";

/** One signed reply per slot. The slot is known only to the two phones that share the invitation. */
export async function publishPairing(auth: Auth) {
  const body = jsonBody(auth);
  requireApi(hex(body.slot) && typeof body.invitation === "string" &&
    body.invitation.length > 0 && body.invitation.length <= 12000);
  requireApi(Number.isSafeInteger(body.expiresAt) && body.expiresAt > auth.now &&
    body.expiresAt <= auth.now + 7 * 86400000);
  const existing = await sql(auth, "SELECT owner_handle,expires_at FROM pairing_replies WHERE slot=?",
    body.slot).first<{owner_handle: string; expires_at: number}>();
  if (existing && existing.expires_at > auth.now && existing.owner_handle !== auth.device.handle) {
    return {stored: false};
  }
  return mutate(auth, {stored: true}, [
    sql(auth, "DELETE FROM pairing_replies WHERE slot=? AND expires_at<=?", body.slot, auth.now),
    sql(auth, "INSERT INTO pairing_replies(slot,owner_handle,body,expires_at) VALUES(?,?,?,?) " +
      "ON CONFLICT(slot) DO UPDATE SET owner_handle=excluded.owner_handle,body=excluded.body," +
      "expires_at=excluded.expires_at WHERE pairing_replies.owner_handle=excluded.owner_handle " +
      "OR pairing_replies.expires_at<=?",
      body.slot, auth.device.handle, body.invitation, body.expiresAt, auth.now)
  ]);
}

export async function readPairing(auth: Auth, slot: string) {
  requireApi(hex(slot));
  const row = await sql(auth, "SELECT body,expires_at FROM pairing_replies WHERE slot=?", slot)
    .first<{body: string; expires_at: number}>();
  if (!row || row.expires_at <= auth.now) return {invitation: null};
  return {invitation: row.body};
}
