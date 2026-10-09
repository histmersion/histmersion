-- V078: an event's SCALE (#1440, 2026-10-09) — is it LOCAL (a battle: one place where it happened) or
-- REGIONAL (the Bronze Age began in a region)? The map's playback draws the two differently: a local
-- event as a point and a label at its site that fade after it ends, a regional one as a label at the
-- region's centre with a faint outline of the region for the event's span.
--
-- NULL = not stated: the scale is DERIVED on every read (`core.EventScale.effective`) from the event's
-- kind (battle, siege, destruction local; migration, technological change, cultural shift, era, war
-- regional), else its own geometry (a point local, an area regional), else its place's kind (a site
-- local; a region, polity or people regional). A stated value wins over all of that, and `auto` on a
-- patch clears it back to NULL. Not backfilled: a stored default would stop following the kind.
ALTER TABLE events ADD COLUMN IF NOT EXISTS scale text;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'events_scale_check') THEN
    ALTER TABLE events ADD CONSTRAINT events_scale_check CHECK (scale IN ('local', 'regional'));
  END IF;
END $$;
