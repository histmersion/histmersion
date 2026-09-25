-- Histmersion V017 — the EVENTS database (plan §1.2): CIDOC-CRM compatible events with four-point
-- time-spans, hierarchical composition, causal/temporal edges and participant roles.
--
-- The four-point time-span (not_before/begin_by/end_from/not_after) records both the certain and
-- the uncertain bounds of an event's temporal extent — "the siege began between -1200 and -1195
-- and ended between -1190 and -1185." The outer_span and inner_span are GENERATED COLUMNS that
-- materialise the two useful int8ranges without a trigger.
--
-- Historicity distinguishes attested events from reconstructed, contested, legendary and mythic
-- ones — all coexist in the same table because the sources discuss them interchangeably, but the
-- web UI can filter on it.

-- ── events ──────────────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS events (
  id            text PRIMARY KEY,              -- slug: 'fall_of_troy', 'battle_of_kadesh'
  label         text NOT NULL,
  kind          text NOT NULL DEFAULT 'event', -- event|battle|migration|construction|destruction|founding|treaty|reign|period
  -- four-point time-span (signed years, historical reckoning, NULL = unknown bound)
  not_before    bigint,                        -- earliest possible start
  begin_by      bigint,                        -- latest possible start
  end_from      bigint,                        -- earliest possible end
  not_after     bigint,                        -- latest possible end
  -- generated ranges for overlap queries
  outer_span    int8range GENERATED ALWAYS AS (
    int8range(coalesce(not_before, begin_by), coalesce(not_after, end_from), '[]')
  ) STORED,
  inner_span    int8range GENERATED ALWAYS AS (
    CASE WHEN begin_by IS NOT NULL AND end_from IS NOT NULL
         THEN int8range(begin_by, end_from, '[]') END
  ) STORED,
  historicity   text NOT NULL DEFAULT 'attested', -- attested|inferred|contested|legendary|mythic
  geom          geometry(Geometry, 4326),       -- where it happened (point or polygon)
  place_id      text REFERENCES places(id) ON DELETE SET NULL,
  parent_id     text REFERENCES events(id) ON DELETE SET NULL,
  description   text,
  citekey       text                            -- primary source
);
CREATE INDEX IF NOT EXISTS events_outer_span ON events USING gist (outer_span);
CREATE INDEX IF NOT EXISTS events_inner_span ON events USING gist (inner_span) WHERE inner_span IS NOT NULL;
CREATE INDEX IF NOT EXISTS events_geom       ON events USING gist (geom) WHERE geom IS NOT NULL;
CREATE INDEX IF NOT EXISTS events_place      ON events (place_id) WHERE place_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS events_parent     ON events (parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS events_kind       ON events (kind);
CREATE INDEX IF NOT EXISTS events_historicity ON events (historicity);

-- ── event_datings: per-source dating (the ChronOntology pattern) ─────────────────────────────
-- Different sources date the same event differently. Each dating records the source's four-point
-- span and an optional note explaining the dating method (e.g. dendrochronology, radiocarbon).
CREATE TABLE IF NOT EXISTS event_datings (
  event_id   text NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  citekey    text NOT NULL,
  not_before bigint,
  begin_by   bigint,
  end_from   bigint,
  not_after  bigint,
  method     text,                              -- dendrochronology|radiocarbon|stratigraphy|textual|astronomical
  note       text,
  PRIMARY KEY (event_id, citekey)
);

-- ── event_edges: temporal ordering and causal relationships ──────────────────────────────────
-- Allen interval algebra relations (before, meets, overlaps, during, starts, finishes, equals)
-- plus causal kinds (causes, enables, triggers, responds_to). Every edge carries a mandatory
-- citekey — no unsourced causal claims.
CREATE TABLE IF NOT EXISTS event_edges (
  src_id     text NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  dst_id     text NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  kind       text NOT NULL,                     -- before|meets|overlaps|during|starts|finishes|equals|causes|enables|triggers|responds_to|falls_within
  citekey    text NOT NULL,                     -- mandatory source
  note       text,
  PRIMARY KEY (src_id, dst_id, kind)
);
CREATE INDEX IF NOT EXISTS event_edges_dst ON event_edges (dst_id);

-- ── event_participants: who was involved and in what role ─────────────────────────────────────
-- "Actor" is a string (not an FK to a separate actors table — the corpus is about places and
-- dates, not prosopography). The role is free text: commander, builder, defender, attacker,
-- signatory, ruler.
CREATE TABLE IF NOT EXISTS event_participants (
  event_id   text NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  actor      text NOT NULL,
  role       text NOT NULL DEFAULT 'participant',
  citekey    text,
  PRIMARY KEY (event_id, actor, role)
);

COMMENT ON TABLE events IS 'CIDOC-CRM inspired: E5 Event with EDTF-like four-point timespan, P9 compositional hierarchy (parent_id), P7 location (place_id/geom).';
