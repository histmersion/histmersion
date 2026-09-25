-- Histmersion V010 — figures kept with the text (plan §1.8), and OCR as an asynchronous JOB.
--
-- assets: the images a conversion produced (or the pre-existing `md/<citekey>/figures/*` of the old
-- pipeline), stored as bytea next to the Markdown that references them. The body's links are
-- normalised to the RELATIVE form `assets/<name>`, so one Markdown value works both on disk and on
-- the web, where the server rewrites them to `/source/{citekey}/assets/{name}`.
--
-- jobs: OCR of a 500-page scan under docling takes tens of minutes — far longer than any sane HTTP
-- timeout — so `POST /api/ingest` stores the upload, enqueues a job and returns its id. Job rows
-- outlive the process, which is how a restart can mark whatever was `running` as
-- `failed: interrupted` instead of leaving it queued forever.

CREATE TABLE IF NOT EXISTS assets (
  citekey text NOT NULL REFERENCES sources(citekey) ON DELETE CASCADE,
  name    text NOT NULL,                 -- link-safe file name; `assets/<name>` in the Markdown
  mime    text NOT NULL,
  bytes   bytea NOT NULL,
  sha256  text NOT NULL,                 -- identical pictures repeat across pages: deduplicated per source
  width   int,
  height  int,
  PRIMARY KEY (citekey, name)
);
CREATE INDEX IF NOT EXISTS assets_sha256 ON assets (sha256);

CREATE TABLE IF NOT EXISTS jobs (
  id         text PRIMARY KEY,           -- opaque, client-visible (returned by ingest/reocr)
  kind       text NOT NULL,              -- ingest|reocr
  citekey    text,                       -- the source the job writes to (no FK: it may not exist yet)
  status     text NOT NULL,              -- queued|running|done|failed
  progress   text,                       -- human-readable stage ("converting", "storing 12 asset(s)")
  error      text,                       -- set iff status = 'failed'
  sha256     text,                       -- of the uploaded original; makes a re-run idempotent
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS jobs_status  ON jobs (status, created_at DESC);
CREATE INDEX IF NOT EXISTS jobs_citekey ON jobs (citekey);
CREATE INDEX IF NOT EXISTS jobs_sha256  ON jobs (sha256);
