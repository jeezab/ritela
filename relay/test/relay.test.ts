import { env } from "cloudflare:workers";
import { applyD1Migrations, type D1Migration } from "cloudflare:test";
import { beforeAll, beforeEach, describe, expect, it } from "vitest";
import worker from "../src/index";
import { Env, b64, canonical, sha, utf8, verify } from "../src/auth";
import signatureVectors from "../test-vectors/api-signatures.json";
import { transferBytes } from "../src/authorities";
import { cleanup } from "../src/cleanup";
import { measure } from "./measure";

const bindings = {...env, RATE_LIMITER: {limit: async () => ({success: true})}} as unknown as Env & {TEST_MIGRATIONS: D1Migration[]};
type Client = {handle: string; publicKey: string; privateKey: CryptoKey};
const random = () => [...crypto.getRandomValues(new Uint8Array(32))].map(x => x.toString(16).padStart(2, "0")).join("");
async function newClient(): Promise<Client> {
  const keys = await crypto.subtle.generateKey({name: "ECDSA", namedCurve: "P-256"}, true, ["sign", "verify"]);
  return {handle: crypto.randomUUID(), publicKey: b64(await crypto.subtle.exportKey("spki", keys.publicKey)), privateKey: keys.privateKey};
}
async function sign(client: Client, value: string) {
  return b64(await crypto.subtle.sign({name: "ECDSA", hash: "SHA-256"}, client.privateKey, utf8(value)));
}
async function request(client: Client, method: string, target: string, value: unknown = undefined,
  capability = "", requestId: string = crypto.randomUUID(), time = String(Date.now())) {
  const body = value instanceof Uint8Array ? value : value === undefined ? new Uint8Array() : utf8(JSON.stringify(value));
  const headers = new Headers({"x-ritela-device": client.handle, "x-ritela-key-version": "1",
    "x-ritela-time": time, "x-ritela-request": requestId, "x-ritela-capability": capability});
  headers.set("x-ritela-signature", await sign(client, canonical(method, target, client.handle, "1", time,
    requestId, await sha(body), await sha(capability))));
  return new Request("https://relay.invalid" + target, {method, headers, ...(method === "GET" ? {} : {body: body.slice().buffer})});
}
async function call(client: Client, method: string, target: string, value?: unknown, cap = "") {
  return worker.fetch(await request(client, method, target, value, cap), bindings);
}
async function enroll(): Promise<Client> {
  const client = await newClient(); const token = random();
  await bindings.DB.prepare("INSERT INTO activation_tokens(token_hash,expires_at) VALUES(?,?)")
    .bind(await sha(token), Date.now() + 600000).run();
  const response = await call(client, "POST", "/v1/devices", {handle: client.handle, publicKey: client.publicKey, activation: token});
  expect(response.status).toBe(200); return client;
}
async function route(owner: Client, sender: Client, authority?: Client) {
  const id = random(), capability = random();
  expect((await call(owner, "POST", "/v1/routes", {route: id, sender: sender.handle, capability,
    authority: authority?.handle, expiresAt: Date.now() + 86400000})).status).toBe(200);
  return {id, capability};
}
const path = (r: {id: string}, id: string, kind = "MESSAGE", generation = 0, grant = "none") =>
  `/v1/routes/${r.id}/packets/${id}?class=${kind}&expires=${Date.now() + 3600000}&generation=${generation}&grant=${grant}`;
const fetchPath = (c: Client) => `/v1/devices/${c.handle}/packets`;

beforeAll(async () => { await applyD1Migrations(bindings.DB, bindings.TEST_MIGRATIONS); });
beforeEach(async () => {
  for (const table of ["packets", "packet_tombstones", "baseline_reservations", "routes", "author_transitions", "author_fences",
    "request_nonces", "daily_usage", "relay_devices", "activation_tokens", "mutation_guards"])
    await bindings.DB.prepare(`DELETE FROM ${table}`).run();
  await bindings.DB.prepare("UPDATE relay_state SET bytes=0,packets=0,tombstones=0,nonces=0," +
    "reserved_bytes=0,reserved_count=0,recovery_epoch=? WHERE id=1")
    .bind(bindings.RECOVERY_EPOCH).run();
});

describe("closed pilot relay on local workerd/D1", () => {
  it("repeats concurrent registration and confirms device deletion without restoring rights", async () => {
    const client = await newClient(), token = random();
    await bindings.DB.prepare("INSERT INTO activation_tokens(token_hash,expires_at) VALUES(?,?)")
      .bind(await sha(token), Date.now() + 60000).run();
    const body = {handle: client.handle, publicKey: client.publicKey, activation: token};
    const responses = await Promise.all([call(client, "POST", "/v1/devices", body), call(client, "POST", "/v1/devices", body)]);
    expect(responses.map(x => x.status)).toEqual([200, 200]);
    expect((await call(client, "DELETE", `/v1/devices/${client.handle}`)).status).toBe(200);
    expect((await call(client, "DELETE", `/v1/devices/${client.handle}`)).status).toBe(200);
    expect((await call(client, "GET", fetchPath(client))).status).toBe(401);
    expect((await call(client, "POST", "/v1/devices", body)).status).toBe(403);
  });
  it("reserves the complete baseline before upload and consumes quota exactly once", async () => {
    const a = await enroll(), b = await enroll(), grant = random();
    await call(a, "POST", `/v1/authorities/${a.handle}/initialize`, {grant});
    const r = await route(b, a, a), reservation = crypto.randomUUID();
    const body = {id: reservation, bytes: 2097152, parts: 8, expiresAt: Date.now() + 60000};
    expect((await call(a, "POST", `/v1/routes/${r.id}/reserve`, body)).status).toBe(409);
    expect((await call(b, "POST", `/v1/routes/${r.id}/reserve`, body)).status).toBe(200);
    expect((await call(b, "POST", `/v1/routes/${r.id}/reserve`, body)).status).toBe(200);
    expect((await bindings.DB.prepare("SELECT reserved_bytes FROM relay_state").first<any>()).reserved_bytes).toBe(2097152);
    await bindings.DB.prepare("UPDATE daily_usage SET bytes=1048576 WHERE device_handle=?").bind(a.handle).run();
    const target = path(r, crypto.randomUUID(), "AUTHOR", 1, grant) + "&reservation=" + reservation;
    expect((await call(a, "PUT", target, new Uint8Array(262144), r.capability)).status).toBe(200);
    expect((await call(a, "PUT", target, new Uint8Array(262144), r.capability)).status).toBe(200);
    const state = await bindings.DB.prepare("SELECT * FROM relay_state").first<any>();
    expect(state.bytes).toBe(262144); expect(state.reserved_bytes).toBe(2097152 - 262144);
    expect(state.packets + state.reserved_count).toBe(8);
    expect((await bindings.DB.prepare("SELECT bytes FROM daily_usage WHERE device_handle=?").bind(a.handle).first<any>()).bytes).toBe(1048576);
    expect((await call(b, "DELETE", `/v1/routes/${r.id}`)).status).toBe(200);
    const revoked = await bindings.DB.prepare("SELECT * FROM relay_state").first<any>();
    expect(revoked.bytes + revoked.reserved_bytes).toBe(0); expect(revoked.reserved_count).toBe(0);
  });

  it("reservation overflow rolls back and expiry releases all reserved capacity", async () => {
    const a = await enroll(), b = await enroll(), grant = random();
    await call(a, "POST", `/v1/authorities/${a.handle}/initialize`, {grant});
    const r = await route(b, a, a), other = await route(b, a, a), third = await route(b, a, a);
    const reserve = () => ({id: crypto.randomUUID(), bytes: 6291456, parts: 64, expiresAt: Date.now() + 60000});
    expect((await call(b, "POST", `/v1/routes/${r.id}/reserve`, reserve())).status).toBe(200);
    expect((await call(b, "POST", `/v1/routes/${other.id}/reserve`, reserve())).status).toBe(200);
    expect((await call(b, "POST", `/v1/routes/${third.id}/reserve`, reserve())).status).toBe(429);
    expect((await bindings.DB.prepare("SELECT COUNT(*) AS n FROM baseline_reservations").first<any>()).n).toBe(2);
    await cleanup(bindings, Date.now() + 60001);
    const state = await bindings.DB.prepare("SELECT * FROM relay_state").first<any>();
    expect(state.reserved_bytes).toBe(0); expect(state.reserved_count).toBe(0);
  });

  it("global row-density admission is atomic even for tiny payloads", async () => {
    const a = await enroll(), b = await enroll(); const r = await route(b, a);
    await bindings.DB.prepare("UPDATE relay_state SET packets=32768").run();
    expect((await call(a, "PUT", path(r, crypto.randomUUID()), new Uint8Array(1), r.capability)).status).toBe(429);
    expect((await bindings.DB.prepare("SELECT COUNT(*) AS n FROM packets").first<any>()).n).toBe(0);
    expect((await bindings.DB.prepare("SELECT bytes FROM relay_state").first<any>()).bytes).toBe(0);
  });
  it("verifies both JCA and WebCrypto signature vectors and the exact canonical bytes", async () => {
    const message = canonical("GET", "/v1/devices/00000000-0000-4000-8000-000000000001/packets?cursor=0&limit=32",
      "00000000-0000-4000-8000-000000000001", "1", "1700000000000",
      "00000000-0000-4000-8000-000000000002", await sha(new Uint8Array()), await sha(""));
    expect(signatureVectors.map(v => v.source).sort()).toEqual(["JCA", "WebCrypto"]);
    for (const v of signatureVectors) {
      expect(v.message).toBe(message);
      expect(await verify(v.publicKey, message, v.signature)).toBe(true);
      expect(await verify(v.publicKey, message + "x", v.signature)).toBe(false);
    }
  });
  it("returns 429 before storage when the edge limiter rejects", async () => {
    const limited = {...bindings, RATE_LIMITER: {limit: async () => ({success: false})}};
    const response = await worker.fetch(new Request("https://relay.invalid/v1/devices"), limited);
    expect(response.status).toBe(429); expect(response.headers.get("Retry-After")).toBe("60");
  });

  it("measures local D1 costs without sensitive diagnostic fields", async () => {
    const a = await enroll(), b = await enroll(); const r = await route(b, a);
    const summaries: Record<string, unknown> = {};
    async function measured(label: string, req: Request) {
      const m = measure(bindings.DB); const started = performance.now();
      const res = await worker.fetch(req, {...bindings, DB: m.db});
      expect(m.totals.rowsRead).toBeLessThanOrEqual(64);
      summaries[label] = {rowsReadUpperBound: 2 ** Math.ceil(Math.log2(Math.max(1, m.totals.rowsRead))), rowsWritten: m.totals.rowsWritten,
        statements: m.totals.statements, responseBytes: utf8(await res.text()).length};
      expect(res.status).toBe(200); return m.totals;
    }
    const empty = await measured("emptyFetch", await request(b, "GET", fetchPath(b)));
    expect(empty.rowsWritten).toBe(0);
    const id = crypto.randomUUID(), target = path(r, id);
    await measured("upload8KiB", await request(a, "PUT", target, new Uint8Array(8192), r.capability));
    await measured("duplicateUpload", await request(a, "PUT", target, new Uint8Array(8192), r.capability));
    await measured("fetch8KiB", await request(b, "GET", fetchPath(b)));
    await measured("importAck", await request(b, "POST", `/v1/devices/${b.handle}/ack`, {packets: [{route: r.id, id}]}));
    await measured("cancelBeforeUpload", await request(a, "DELETE", `/v1/routes/${r.id}/packets/${crypto.randomUUID()}`));
    await measured("revoke", await request(b, "DELETE", `/v1/routes/${r.id}`));
    expect(summaries).toMatchSnapshot();
  });
  it("requires activation and proof of possession; redeem is atomic", async () => {
    const a = await newClient(), b = await newClient(), token = random();
    const body = {handle: a.handle, publicKey: a.publicKey, activation: token};
    expect((await call(a, "POST", "/v1/devices", body)).status).toBe(403);
    await bindings.DB.prepare("INSERT INTO activation_tokens(token_hash,expires_at) VALUES(?,?)")
      .bind(await sha(token), Date.now() + 60000).run();
    const results = await Promise.all([call(a, "POST", "/v1/devices", body),
      call(b, "POST", "/v1/devices", {...body, handle: b.handle, publicKey: b.publicKey})]);
    expect(results.map(x => x.status).sort()).toEqual([200, 403]);
    const winner = results[0].status === 200 ? a : b;
    expect((await call(winner, "POST", "/v1/devices", {handle: winner.handle, publicKey: winner.publicKey, activation: token})).status).toBe(200);
  });

  it("isolates reads and requires assigned sender plus capability", async () => {
    const a = await enroll(), b = await enroll(), stranger = await enroll(); const r = await route(b, a);
    const id = crypto.randomUUID(), payload = utf8("synthetic opaque ciphertext");
    expect((await call(stranger, "PUT", path(r, id), payload, r.capability)).status).toBe(403);
    expect((await call(a, "PUT", path(r, id), payload, random())).status).toBe(403);
    expect((await call(a, "PUT", path(r, id), payload, r.capability)).status).toBe(200);
    expect((await call(a, "GET", fetchPath(b))).status).toBe(403);
    const response = await call(b, "GET", fetchPath(b)); const data = await response.json() as any;
    expect(data.packets[0].ciphertext).toBe(b64(payload));
    expect((await (await call(stranger, "GET", fetchPath(stranger))).json() as any).packets).toEqual([]);
    expect((await call(stranger, "DELETE", `/v1/routes/${r.id}`)).status).toBe(409);
  });

  it("rejects signature/path/body changes and bounded-clock replay", async () => {
    const a = await enroll();
    const req = await request(a, "GET", fetchPath(a));
    expect((await worker.fetch(new Request(req.url + "?cursor=1", {headers: req.headers}), bindings)).status).toBe(401);
    expect((await worker.fetch(await request(a, "GET", fetchPath(a), undefined, "", crypto.randomUUID(),
      String(Date.now() - 300001)), bindings)).status).toBe(401);
    const bad = await request(a, "POST", "/v1/routes", {});
    expect((await worker.fetch(new Request(bad.url, {method: "POST", headers: bad.headers, body: "{\"changed\":true}"}), bindings)).status).toBe(401);
  });

  it("keeps immutable IDs and idempotent nonce results; empty fetch has no nonce writes", async () => {
    const a = await enroll(), b = await enroll(); const r = await route(b, a), id = crypto.randomUUID();
    const target = path(r, id), data = utf8("one"); const req = await request(a, "PUT", target, data, r.capability);
    expect((await worker.fetch(new Request(req.url, {method: req.method, headers: req.headers, body: data.slice().buffer}), bindings)).status).toBe(200);
    expect((await worker.fetch(new Request(req.url, {method: req.method, headers: req.headers, body: data.slice().buffer}), bindings)).status).toBe(200);
    expect((await call(a, "PUT", target, utf8("other"), r.capability)).status).toBe(409);
    const altered = await request(a, "PUT", target, utf8("other"), r.capability,
      req.headers.get("x-ritela-request")!, req.headers.get("x-ritela-time")!);
    expect((await worker.fetch(altered, bindings)).status).toBe(409);
    const count = await bindings.DB.prepare("SELECT COUNT(*) AS n FROM request_nonces").first<any>();
    await call(b, "GET", fetchPath(b)); await call(a, "GET", fetchPath(a));
    expect((await bindings.DB.prepare("SELECT COUNT(*) AS n FROM request_nonces").first<any>()).n).toBe(count.n);
  });

  it("cancel before upload leaves a tombstone and revoke serializes with upload", async () => {
    const a = await enroll(), b = await enroll(); const r = await route(b, a), id = crypto.randomUUID();
    expect((await call(a, "DELETE", `/v1/routes/${r.id}/packets/${id}`)).status).toBe(200);
    expect((await call(a, "PUT", path(r, id), utf8("late"), r.capability)).status).toBe(200);
    expect((await (await call(b, "GET", fetchPath(b))).json() as any).packets).toHaveLength(0);
    await Promise.all([call(a, "PUT", path(r, crypto.randomUUID()), utf8("race"), r.capability),
      call(b, "DELETE", `/v1/routes/${r.id}`)]);
    expect((await bindings.DB.prepare("SELECT COUNT(*) AS n FROM packets").first<any>()).n).toBe(0);
    expect((await bindings.DB.prepare("SELECT bytes FROM relay_state").first<any>()).bytes).toBe(0);
  });

  it("reserves daily quota atomically and rolls back failed batches", async () => {
    const a = await enroll(), b = await enroll(); const r = await route(b, a);
    await bindings.DB.prepare("INSERT INTO daily_usage(device_handle,day,bytes) VALUES(?,?,?)")
      .bind(a.handle, Math.floor(Date.now() / 86400000), 1048576 - 100).run();
    const results = await Promise.all([0, 1].map(() => call(a, "PUT", path(r, crypto.randomUUID()), new Uint8Array(75), r.capability)));
    expect(results.map(x => x.status).sort()).toEqual([200, 429]);
    expect((await bindings.DB.prepare("SELECT COUNT(*) AS n FROM packets").first<any>()).n).toBe(1);
    expect((await bindings.DB.prepare("SELECT bytes FROM relay_state").first<any>()).bytes).toBe(75);
    expect((await bindings.DB.prepare("SELECT bytes FROM daily_usage WHERE device_handle=?").bind(a.handle).first<any>()).bytes).toBe(1048576 - 25);
  });

  it("bounds streamed uploads and fetch bytes; ACK is owner-only and atomic", async () => {
    const a = await enroll(), b = await enroll(), stranger = await enroll(); const r = await route(b, a);
    expect((await call(a, "PUT", path(r, crypto.randomUUID()), new Uint8Array(262145), r.capability)).status).toBe(413);
    const ids = [crypto.randomUUID(), crypto.randomUUID()];
    for (const id of ids) expect((await call(a, "PUT", path(r, id), new Uint8Array(262144), r.capability)).status).toBe(200);
    const fetched = await call(b, "GET", fetchPath(b)); const text = await fetched.text();
    expect(text.length).toBeLessThanOrEqual(512 * 1024); expect(JSON.parse(text).packets).toHaveLength(1);
    expect((await call(stranger, "POST", `/v1/devices/${b.handle}/ack`, {packets: [{route: r.id, id: ids[0]}]})).status).toBe(403);
    expect((await call(b, "POST", `/v1/devices/${b.handle}/ack`, {packets: [
      {route: r.id, id: ids[0]}, {route: random(), id: crypto.randomUUID()}]})).status).toBe(409);
    expect((await bindings.DB.prepare("SELECT COUNT(*) AS n FROM packets").first<any>()).n).toBe(2);
    expect((await call(b, "POST", `/v1/devices/${b.handle}/ack`, {packets: [{route: r.id, id: ids[0]}]})).status).toBe(200);
    expect((await call(a, "PUT", path(r, ids[0]), new Uint8Array(262144), r.capability)).status).toBe(200);
    expect((await bindings.DB.prepare("SELECT COUNT(*) AS n FROM packets").first<any>()).n).toBe(1);
  });

  it("CAS transfer needs both keys, fences old generation and is idempotent", async () => {
    const a = await enroll(), next = await enroll(), competitor = await enroll(), b = await enroll();
    const grant = random(), nextGrant = random();
    expect((await call(a, "POST", `/v1/authorities/${a.handle}/initialize`, {grant})).status).toBe(200);
    const r = await route(b, a, a);
    expect((await call(a, "PUT", path(r, crypto.randomUUID(), "AUTHOR", 1, grant), utf8("old"), r.capability)).status).toBe(200);
    async function proposal(device: Client) {
      const transition = crypto.randomUUID();
      const raw = transferBytes(a.handle, device.handle, 1, grant, nextGrant, transition);
      return {next: device.handle, transition, expected: 1, oldGrant: grant, newGrant: nextGrant,
        oldSignature: await sign(a, raw), nextSignature: await sign(device, raw)};
    }
    const first = await proposal(next), second = await proposal(competitor);
    expect((await call(a, "POST", `/v1/authorities/${a.handle}/transfer`, {...first, nextSignature: first.oldSignature})).status).toBe(403);
    const results = await Promise.all([call(a, "POST", `/v1/authorities/${a.handle}/transfer`, first),
      call(a, "POST", `/v1/authorities/${a.handle}/transfer`, second)]);
    expect(results.map(x => x.status).sort()).toEqual([200, 409]);
    const winner = results[0].status === 200 ? first : second;
    expect((await call(a, "POST", `/v1/authorities/${a.handle}/transfer`, winner)).status).toBe(200);
    expect((await call(a, "PUT", path(r, crypto.randomUUID(), "AUTHOR", 1, grant), utf8("stale"), r.capability)).status).toBe(409);
    expect((await bindings.DB.prepare("SELECT COUNT(*) AS n FROM packets").first<any>()).n).toBe(0);
  });

  it("expires without GET writes, cleans bounded batches and fails closed after restore epoch", async () => {
    const a = await enroll(), b = await enroll(); const r = await route(b, a);
    await call(a, "PUT", path(r, crypto.randomUUID()), utf8("expiry"), r.capability);
    await bindings.DB.prepare("UPDATE packets SET expires_at=?").bind(Date.now() - 1).run();
    expect((await (await call(b, "GET", fetchPath(b))).json() as any).packets).toHaveLength(0);
    const meta = await cleanup(bindings);
    expect(meta[2].meta.rows_written).toBeGreaterThan(0);
    expect((await bindings.DB.prepare("SELECT bytes FROM relay_state").first<any>()).bytes).toBe(0);
    const changed = {...bindings, RECOVERY_EPOCH: "new-external-epoch-00000000000000"};
    expect((await worker.fetch(await request(a, "GET", fetchPath(a)), changed)).status).toBe(503);
    await bindings.DB.prepare("UPDATE relay_state SET recovery_epoch=?").bind(changed.RECOVERY_EPOCH).run();
    expect((await worker.fetch(await request(a, "GET", fetchPath(a)), changed)).status).toBe(401);
  });
});
