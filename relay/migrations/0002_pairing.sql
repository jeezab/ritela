CREATE TABLE pairing_replies (
  slot TEXT PRIMARY KEY,
  owner_handle TEXT NOT NULL,
  body TEXT NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE INDEX pairing_expiry ON pairing_replies(expires_at);
