PRAGMA foreign_keys = ON;
CREATE TABLE relay_state (
  id INTEGER PRIMARY KEY CHECK(id=1), recovery_epoch TEXT,
  bytes INTEGER NOT NULL DEFAULT 0 CHECK(bytes>=0 AND bytes+reserved_bytes<=314572800),
  reserved_bytes INTEGER NOT NULL DEFAULT 0 CHECK(reserved_bytes>=0),
  reserved_count INTEGER NOT NULL DEFAULT 0 CHECK(reserved_count>=0),
  packets INTEGER NOT NULL DEFAULT 0 CHECK(packets>=0 AND packets+reserved_count<=32768),
  tombstones INTEGER NOT NULL DEFAULT 0 CHECK(tombstones BETWEEN 0 AND 65536),
  nonces INTEGER NOT NULL DEFAULT 0 CHECK(nonces BETWEEN 0 AND 32768)
);
INSERT INTO relay_state(id) VALUES(1);
CREATE TABLE activation_tokens (
  token_hash TEXT PRIMARY KEY, expires_at INTEGER NOT NULL,
  redeemed_by TEXT, registration_digest TEXT
);
CREATE TABLE relay_devices (
  handle TEXT PRIMARY KEY, api_public_key TEXT NOT NULL, key_version INTEGER NOT NULL CHECK(key_version=1),
  state TEXT NOT NULL CHECK(state IN ('ACTIVE','REVOKED')), recovery_epoch TEXT NOT NULL,
  bytes INTEGER NOT NULL DEFAULT 0 CHECK(bytes>=0 AND bytes+reserved_bytes<=16777216),
  reserved_bytes INTEGER NOT NULL DEFAULT 0 CHECK(reserved_bytes>=0),
  created_at INTEGER NOT NULL
);
CREATE INDEX activation_expiry ON activation_tokens(expires_at);
CREATE TABLE author_fences (
  owner_handle TEXT PRIMARY KEY REFERENCES relay_devices(handle),
  generation INTEGER NOT NULL CHECK(generation>0), author_handle TEXT NOT NULL REFERENCES relay_devices(handle),
  grant_digest TEXT NOT NULL, transition_id TEXT
);
CREATE TABLE author_transitions (
  owner_handle TEXT NOT NULL, transition_id TEXT NOT NULL, digest TEXT NOT NULL,
  generation INTEGER NOT NULL, PRIMARY KEY(owner_handle,transition_id)
);
CREATE TABLE routes (
  route_id TEXT PRIMARY KEY, owner_handle TEXT NOT NULL REFERENCES relay_devices(handle),
  sender_handle TEXT NOT NULL REFERENCES relay_devices(handle), authority_handle TEXT REFERENCES author_fences(owner_handle),
  capability_hash TEXT NOT NULL, state TEXT NOT NULL CHECK(state IN ('ACTIVE','REVOKED')),
  expires_at INTEGER NOT NULL, bytes INTEGER NOT NULL DEFAULT 0 CHECK(bytes>=0 AND bytes+reserved_bytes<=8388608),
  reserved_bytes INTEGER NOT NULL DEFAULT 0 CHECK(reserved_bytes>=0),
  reserved_count INTEGER NOT NULL DEFAULT 0 CHECK(reserved_count>=0),
  count INTEGER NOT NULL DEFAULT 0 CHECK(count>=0 AND count+reserved_count<=256)
);
CREATE INDEX routes_owner_state ON routes(owner_handle,state);
CREATE INDEX routes_sender_state ON routes(sender_handle,state);
CREATE INDEX routes_authority ON routes(authority_handle);
CREATE INDEX routes_state ON routes(state);
CREATE INDEX routes_expiry ON routes(expires_at);
CREATE TABLE baseline_reservations (
  route_id TEXT PRIMARY KEY REFERENCES routes(route_id), reservation_id TEXT NOT NULL UNIQUE,
  total_bytes INTEGER NOT NULL CHECK(total_bytes BETWEEN 1 AND 6291456),
  total_parts INTEGER NOT NULL CHECK(total_parts BETWEEN 1 AND 64),
  remaining_bytes INTEGER NOT NULL CHECK(remaining_bytes>=0 AND remaining_bytes<=total_bytes),
  remaining_parts INTEGER NOT NULL CHECK(remaining_parts>=0 AND remaining_parts<=total_parts),
  expires_at INTEGER NOT NULL
);
CREATE INDEX reservations_expiry ON baseline_reservations(expires_at);
CREATE TABLE packets (
  relay_seq INTEGER PRIMARY KEY AUTOINCREMENT,
  route_id TEXT NOT NULL REFERENCES routes(route_id), delivery_id TEXT NOT NULL,
  ciphertext BLOB NOT NULL CHECK(length(ciphertext) BETWEEN 1 AND 262144),
  digest TEXT NOT NULL, bytes INTEGER NOT NULL CHECK(bytes=length(ciphertext)), reservation_id TEXT,
  created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL,
  UNIQUE(route_id,delivery_id)
);
CREATE INDEX packets_expiry ON packets(expires_at);
CREATE INDEX packets_route_seq ON packets(route_id,relay_seq);
CREATE TABLE packet_tombstones (
  route_id TEXT NOT NULL REFERENCES routes(route_id), delivery_id TEXT NOT NULL,
  digest TEXT, result TEXT NOT NULL, expires_at INTEGER NOT NULL,
  PRIMARY KEY(route_id,delivery_id)
);
CREATE INDEX tombstones_expiry ON packet_tombstones(expires_at);
CREATE TABLE request_nonces (
  device_handle TEXT NOT NULL, request_id TEXT NOT NULL, digest TEXT NOT NULL,
  result TEXT NOT NULL, expires_at INTEGER NOT NULL,
  PRIMARY KEY(device_handle,request_id)
);
CREATE INDEX nonces_expiry ON request_nonces(expires_at);
CREATE TABLE mutation_guards (id TEXT PRIMARY KEY, ok INTEGER NOT NULL CHECK(ok=1));
CREATE TABLE daily_usage (
  device_handle TEXT NOT NULL, day INTEGER NOT NULL,
  mutations INTEGER NOT NULL DEFAULT 0 CHECK(mutations BETWEEN 0 AND 4096),
  bytes INTEGER NOT NULL DEFAULT 0 CHECK(bytes BETWEEN 0 AND 1048576),
  PRIMARY KEY(device_handle,day)
);
CREATE TRIGGER reserve_packet AFTER INSERT ON packets BEGIN
  UPDATE baseline_reservations SET remaining_bytes=remaining_bytes-NEW.bytes,remaining_parts=remaining_parts-1
    WHERE reservation_id=NEW.reservation_id;
  UPDATE routes SET bytes=bytes+NEW.bytes,count=count+1 WHERE route_id=NEW.route_id;
  UPDATE relay_devices SET bytes=bytes+NEW.bytes WHERE handle=(
    SELECT owner_handle FROM routes WHERE route_id=NEW.route_id);
  UPDATE relay_state SET bytes=bytes+NEW.bytes,packets=packets+1 WHERE id=1;
END;
CREATE TRIGGER reserve_baseline AFTER INSERT ON baseline_reservations BEGIN
  UPDATE routes SET reserved_bytes=reserved_bytes+NEW.remaining_bytes,reserved_count=reserved_count+NEW.remaining_parts
    WHERE route_id=NEW.route_id;
  UPDATE relay_devices SET reserved_bytes=reserved_bytes+NEW.remaining_bytes WHERE handle=(
    SELECT owner_handle FROM routes WHERE route_id=NEW.route_id);
  UPDATE relay_state SET reserved_bytes=reserved_bytes+NEW.remaining_bytes,reserved_count=reserved_count+NEW.remaining_parts WHERE id=1;
END;
CREATE TRIGGER consume_baseline AFTER UPDATE OF remaining_bytes,remaining_parts ON baseline_reservations BEGIN
  UPDATE routes SET reserved_bytes=reserved_bytes+NEW.remaining_bytes-OLD.remaining_bytes,
    reserved_count=reserved_count+NEW.remaining_parts-OLD.remaining_parts WHERE route_id=NEW.route_id;
  UPDATE relay_devices SET reserved_bytes=reserved_bytes+NEW.remaining_bytes-OLD.remaining_bytes WHERE handle=(
    SELECT owner_handle FROM routes WHERE route_id=NEW.route_id);
  UPDATE relay_state SET reserved_bytes=reserved_bytes+NEW.remaining_bytes-OLD.remaining_bytes,
    reserved_count=reserved_count+NEW.remaining_parts-OLD.remaining_parts WHERE id=1;
END;
CREATE TRIGGER release_baseline AFTER DELETE ON baseline_reservations BEGIN
  UPDATE routes SET reserved_bytes=reserved_bytes-OLD.remaining_bytes,reserved_count=reserved_count-OLD.remaining_parts
    WHERE route_id=OLD.route_id;
  UPDATE relay_devices SET reserved_bytes=reserved_bytes-OLD.remaining_bytes WHERE handle=(
    SELECT owner_handle FROM routes WHERE route_id=OLD.route_id);
  UPDATE relay_state SET reserved_bytes=reserved_bytes-OLD.remaining_bytes,reserved_count=reserved_count-OLD.remaining_parts WHERE id=1;
END;
CREATE TRIGGER release_packet AFTER DELETE ON packets BEGIN
  UPDATE routes SET bytes=bytes-OLD.bytes,count=count-1 WHERE route_id=OLD.route_id;
  UPDATE relay_devices SET bytes=bytes-OLD.bytes WHERE handle=(
    SELECT owner_handle FROM routes WHERE route_id=OLD.route_id);
  UPDATE relay_state SET bytes=bytes-OLD.bytes,packets=packets-1 WHERE id=1;
END;
CREATE TRIGGER reserve_tombstone AFTER INSERT ON packet_tombstones BEGIN
  UPDATE relay_state SET tombstones=tombstones+1 WHERE id=1;
END;
CREATE TRIGGER release_tombstone AFTER DELETE ON packet_tombstones BEGIN
  UPDATE relay_state SET tombstones=tombstones-1 WHERE id=1;
END;
CREATE TRIGGER reserve_nonce AFTER INSERT ON request_nonces BEGIN
  UPDATE relay_state SET nonces=nonces+1 WHERE id=1;
END;
CREATE TRIGGER release_nonce AFTER DELETE ON request_nonces BEGIN
  UPDATE relay_state SET nonces=nonces-1 WHERE id=1;
END;
