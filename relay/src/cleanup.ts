import { Env } from "./auth";

/** Small indexed batches. No payload, route, key or capability logging. */
export async function cleanup(env: Env, now = Date.now()) {
  return env.DB.batch([
    env.DB.prepare("DELETE FROM baseline_reservations WHERE route_id IN (SELECT route_id FROM baseline_reservations " +
      "WHERE expires_at<=? ORDER BY expires_at LIMIT 128)").bind(now),
    env.DB.prepare("DELETE FROM packets WHERE relay_seq IN (SELECT p.relay_seq FROM packets p JOIN routes r " +
      "ON r.route_id=p.route_id WHERE r.state='REVOKED' LIMIT 128)"),
    env.DB.prepare("DELETE FROM packets WHERE relay_seq IN (SELECT relay_seq FROM packets " +
      "WHERE expires_at<=? ORDER BY expires_at LIMIT 128)").bind(now),
    env.DB.prepare("DELETE FROM request_nonces WHERE rowid IN (SELECT rowid FROM request_nonces " +
      "WHERE expires_at<=? ORDER BY expires_at LIMIT 256)").bind(now),
    env.DB.prepare("DELETE FROM packet_tombstones WHERE rowid IN (SELECT rowid FROM packet_tombstones " +
      "WHERE expires_at<=? ORDER BY expires_at LIMIT 128)").bind(now),
    env.DB.prepare("DELETE FROM activation_tokens WHERE token_hash IN (SELECT token_hash FROM activation_tokens " +
      "WHERE expires_at<=? ORDER BY expires_at LIMIT 32)").bind(now),
    env.DB.prepare("DELETE FROM daily_usage WHERE rowid IN (SELECT rowid FROM daily_usage " +
      "WHERE day<? LIMIT 128)").bind(Math.floor(now / 86400000) - 2),
    env.DB.prepare("DELETE FROM routes WHERE route_id IN (SELECT r.route_id FROM routes r WHERE r.expires_at<=? " +
      "AND NOT EXISTS(SELECT 1 FROM packets p WHERE p.route_id=r.route_id) " +
      "AND NOT EXISTS(SELECT 1 FROM packet_tombstones t WHERE t.route_id=r.route_id) LIMIT 16)").bind(now)
  ]);
}
