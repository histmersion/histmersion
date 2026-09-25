-- Histmersion V015 — year convention: NULL periods → int8range(-infinity, infinity) for timeless places.
--
-- The gazetteer (V005) uses NULL to mean "always valid". This migration replaces NULLs with proper
-- unbounded ranges so every temporal overlap query is a single `&&` or `@>` without `IS NULL OR`.
--
-- Convention documented: Histmersion uses HISTORICAL RECKONING (no year zero). -3300 = 3300 BCE.
-- ISO 8601 / astronomical year numbering has a year zero (1 BCE = year 0); conversion for LPF
-- interop is handled in application code (Lpf.toIso / Lpf.fromIso).

-- ── places: NULL period → always-valid ──────────────────────────────────────────────────────────
UPDATE places SET period = 'empty'::int8range WHERE period = 'empty';  -- no-op; ensures range type is loaded
UPDATE places SET period = int8range(NULL, NULL) WHERE period IS NULL;

-- ── place_names: NULL period → always-valid ─────────────────────────────────────────────────────
UPDATE place_names SET period = int8range(NULL, NULL) WHERE period IS NULL;

-- ── place_relations: NULL period → always-valid ─────────────────────────────────────────────────
UPDATE place_relations SET period = int8range(NULL, NULL) WHERE period IS NULL;

-- ── set NOT NULL constraints now that all NULLs are gone ────────────────────────────────────────
ALTER TABLE places          ALTER COLUMN period SET NOT NULL;
ALTER TABLE places          ALTER COLUMN period SET DEFAULT int8range(NULL, NULL);
ALTER TABLE place_names     ALTER COLUMN period SET NOT NULL;
ALTER TABLE place_names     ALTER COLUMN period SET DEFAULT int8range(NULL, NULL);
ALTER TABLE place_relations ALTER COLUMN period SET NOT NULL;
ALTER TABLE place_relations ALTER COLUMN period SET DEFAULT int8range(NULL, NULL);
