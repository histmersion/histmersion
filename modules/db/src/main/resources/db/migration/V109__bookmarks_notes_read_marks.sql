-- V109: a person's own bookmarks, notes and read marks. They are the person's data, not the corpus's, and
-- are kept so that they can later travel between collections by the rules of person edits: a stable
-- UUIDv7 id given at creation, the instance that created the row (`origin`), when it was made and last
-- changed, and a deletion kept as a tombstone in the row (`deleted_at`) rather than a removed row.
-- `user_id` is whose they are: one fixed local user ('local') while the server runs for one person.
--
-- What they point at: an item of the collection (`target_kind` source, article, event, place, picture or
-- goal; `target_id` its key as the web UI names it — a citekey, a slug, an event's or a place's id, a
-- picture's uid), a point on the map (`lat`, `lon`, optionally `period_start`..`period_end`), a saved view
-- of Explore (`view`: the page's own address state — filters, span, layers — with the map's centre and
-- zoom), or a period (`period_start`..`period_end`, signed years, BCE negative). `target_uid` is the
-- item's own uid where its table has one (sources, articles, events, goals), so a rename does not lose it;
-- `target_label` is what the item was called when it was marked, for an item that has no record here.
-- No foreign keys: an item may be deleted or renamed while what a person wrote about it stays.

CREATE TABLE IF NOT EXISTS bookmarks (
  id           uuid             PRIMARY KEY DEFAULT uuidv7(),
  user_id      text             NOT NULL DEFAULT 'local',
  target_kind  text             NOT NULL CHECK (target_kind IN
                 ('source', 'article', 'event', 'place', 'picture', 'goal', 'view', 'point', 'period')),
  target_id    text,
  target_uid   uuid,
  target_label text,
  lat          double precision CHECK (lat BETWEEN -90 AND 90),
  lon          double precision CHECK (lon BETWEEN -180 AND 180),
  zoom         double precision,
  period_start bigint,
  period_end   bigint,
  view         text,
  label        text,
  origin       uuid             NOT NULL DEFAULT histmersion_instance_id(),
  created_at   timestamptz      NOT NULL DEFAULT now(),
  updated_at   timestamptz      NOT NULL DEFAULT now(),
  deleted_at   timestamptz
);
-- an item is bookmarked once per person; views, points and periods as often as they are saved
CREATE UNIQUE INDEX IF NOT EXISTS bookmarks_item_once ON bookmarks (user_id, target_kind, target_id)
  WHERE deleted_at IS NULL AND target_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS bookmarks_user_live ON bookmarks (user_id, created_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS notes (
  id           uuid             PRIMARY KEY DEFAULT uuidv7(),
  user_id      text             NOT NULL DEFAULT 'local',
  target_kind  text             NOT NULL CHECK (target_kind IN
                 ('source', 'article', 'event', 'place', 'picture', 'goal', 'point')),
  target_id    text,
  target_uid   uuid,
  target_label text,
  lat          double precision CHECK (lat BETWEEN -90 AND 90),
  lon          double precision CHECK (lon BETWEEN -180 AND 180),
  period_start bigint,
  period_end   bigint,
  body         text             NOT NULL,
  origin       uuid             NOT NULL DEFAULT histmersion_instance_id(),
  created_at   timestamptz      NOT NULL DEFAULT now(),
  updated_at   timestamptz      NOT NULL DEFAULT now(),
  deleted_at   timestamptz
);
CREATE INDEX IF NOT EXISTS notes_user_live ON notes (user_id, updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS notes_target ON notes (user_id, target_kind, target_id) WHERE deleted_at IS NULL;

-- One row per person and item: `read_at` set while the person has marked it read (cleared, not deleted,
-- when marked unread: the row is the item's latest state), `opened_at` when the person last opened it.
CREATE TABLE IF NOT EXISTS read_marks (
  id           uuid        PRIMARY KEY DEFAULT uuidv7(),
  user_id      text        NOT NULL DEFAULT 'local',
  target_kind  text        NOT NULL CHECK (target_kind IN ('source', 'article')),
  target_id    text        NOT NULL,
  target_uid   uuid,
  target_label text,
  read_at      timestamptz,
  opened_at    timestamptz,
  origin       uuid        NOT NULL DEFAULT histmersion_instance_id(),
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, target_kind, target_id)
);
CREATE INDEX IF NOT EXISTS read_marks_opened ON read_marks (user_id, opened_at DESC) WHERE opened_at IS NOT NULL;
