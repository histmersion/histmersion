-- V088: catalogue events' stated DATING and PRECISION, and CORRECTIONS BY HAND that survive a re-import
-- (the data-source assessment's P0 fixes, 2026-10-16).
--
-- 1. `events.central` / `plus_minus` / `dating_method`: the year a catalogue STATES, its stated ± and how
--    it was dated (the Smithsonian GVP's `StartDateYear`, `StartDateYearUncertainty`,
--    `StartEvidenceMethod`) — kept beside the four-point span the ± widened, so a card can say
--    "250 CE ± 2500 (tephrochronology)" and the span need not carry the whole story.
-- 2. `events.uncertainty`: GENERATED — the event's ± in years: the stated one, or half the width of its
--    widest uncertain window (the start window, the end window, or, with no certain core, the whole
--    span), astronomical arithmetic (no year 0). The SAME rule as core.EventPrecision.uncertainty
--    (EventCorrectionsRepoSuite checks the two agree); core.EventPrecision classifies it (≤ 50 precise,
--    ≤ 500 approximate, more imprecise), and the map's feed leaves the imprecise ones out.
-- 3. `corrections`: a person's change to one FIELD of something an importer wrote, with what upstream
--    said when it was made (`upstream_value`) and at the last refresh (`upstream_now`). On a refresh:
--    upstream unchanged → kept (`active`); upstream now says the person's value → retired (`agreed`);
--    upstream changed to something else → kept and flagged (`conflict`; `histmersion events corrections`,
--    the lint rule `event-correction-conflict`). Generic over the target (`event` now, `place` next — the
--    assessment's §B.4 has `place_overrides` become its canonical-target form); no foreign key for that
--    reason: a trigger on `events` keeps it in step — an event's corrections follow its rename and go
--    with it, whichever path renames or deletes it (so a re-created event never inherits old ones).

ALTER TABLE events ADD COLUMN IF NOT EXISTS central       bigint;
ALTER TABLE events ADD COLUMN IF NOT EXISTS plus_minus    bigint;
ALTER TABLE events ADD COLUMN IF NOT EXISTS dating_method text;

ALTER TABLE events ADD COLUMN IF NOT EXISTS uncertainty bigint GENERATED ALWAYS AS (
  CASE WHEN coalesce(not_before, begin_by, end_from, not_after) IS NULL THEN NULL
  ELSE greatest(coalesce(plus_minus, 0),
    CASE WHEN begin_by IS NULL AND end_from IS NULL THEN
      abs((coalesce(not_after, end_from, begin_by, not_before) + (coalesce(not_after, end_from, begin_by, not_before) < 0)::int)
        - (coalesce(not_before, begin_by, end_from, not_after) + (coalesce(not_before, begin_by, end_from, not_after) < 0)::int)) / 2
    ELSE greatest(
      coalesce((begin_by + (begin_by < 0)::int) - (not_before + (not_before < 0)::int), 0),
      coalesce((not_after + (not_after < 0)::int) - (end_from + (end_from < 0)::int), 0),
      0) / 2
    END)
  END
) STORED;
CREATE INDEX IF NOT EXISTS events_uncertainty ON events (uncertainty) WHERE uncertainty > 500;

CREATE TABLE IF NOT EXISTS corrections (
  id             bigserial   PRIMARY KEY,
  target_kind    text        NOT NULL CHECK (target_kind IN ('event', 'place')),
  target_id      text        NOT NULL,
  field          text        NOT NULL,
  value          jsonb       NOT NULL,              -- the person's value (JSON null: cleared)
  upstream       text,                              -- who upstream is (the catalogue: gvp | noaa | wikidata)
  upstream_value jsonb       NOT NULL,              -- what upstream said when the correction was made
  upstream_now   jsonb,                             -- what upstream said at the last refresh
  status         text        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'agreed', 'conflict')),
  created_at     timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now(),
  checked_at     timestamptz,
  UNIQUE (target_kind, target_id, field)
);
CREATE INDEX IF NOT EXISTS corrections_status ON corrections (status) WHERE status <> 'agreed';

CREATE OR REPLACE FUNCTION events_follow_corrections() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    DELETE FROM corrections WHERE target_kind = 'event' AND target_id = OLD.id;
    RETURN OLD;
  ELSIF NEW.id IS DISTINCT FROM OLD.id THEN
    UPDATE corrections SET target_id = NEW.id WHERE target_kind = 'event' AND target_id = OLD.id;
  END IF;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS events_follow_corrections ON events;
CREATE TRIGGER events_follow_corrections AFTER DELETE OR UPDATE OF id ON events
  FOR EACH ROW EXECUTE FUNCTION events_follow_corrections();
