-- V099: deterministic new-source search (#3761, the design of #3645). What each research goal WATCHES —
-- a saved query on one scholarly index, the works citing one of its sources, an author of one — with the
-- day it was last read (`watermark`: the next read asks only for what is newer), and the CANDIDATES the
-- watches found: one row per goal and DOI (deduplicated across indexes), with every watch that found it
-- and a state (new: unseen · seen: shown, not taken · queued: on the acquisition list or ingested ·
-- dismissed). No model writes here; an agent or a person only filters the new ones.
-- V092 and V095 stay unused.
CREATE TABLE candidate_watches (
  goal        text NOT NULL REFERENCES goals(slug) ON DELETE CASCADE ON UPDATE CASCADE,
  kind        text NOT NULL CHECK (kind IN ('query', 'citing', 'author')),
  subject     text NOT NULL,
  index_name  text NOT NULL,
  watermark   date,
  last_run    timestamptz,
  last_error  text,
  last_found  int NOT NULL DEFAULT 0,
  PRIMARY KEY (goal, kind, subject, index_name)
);

CREATE TABLE source_candidates (
  id          bigserial PRIMARY KEY,
  goal        text NOT NULL REFERENCES goals(slug) ON DELETE CASCADE ON UPDATE CASCADE,
  doi         text NOT NULL,
  title       text NOT NULL,
  year        int,
  authors     text[] NOT NULL DEFAULT '{}',
  venue       text,
  abstract    text,
  is_oa       boolean NOT NULL DEFAULT false,
  arxiv_id    text,
  found_by    text[] NOT NULL DEFAULT '{}',
  state       text NOT NULL DEFAULT 'new' CHECK (state IN ('new', 'seen', 'queued', 'dismissed')),
  first_seen  timestamptz NOT NULL DEFAULT now(),
  last_seen   timestamptz NOT NULL DEFAULT now(),
  UNIQUE (goal, doi)
);
CREATE INDEX source_candidates_state ON source_candidates (goal, state, id);
