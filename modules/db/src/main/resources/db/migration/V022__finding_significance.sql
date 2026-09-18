-- hist-db V022 — finding significance (plan §2.6).
--
-- Distinguishes how central a finding is to its source:
-- mention (passing reference), discussion (substantive coverage),
-- analysis (deep examination), primary_topic (the source is about this).
-- The map uses it to size dots: mention = small faded, primary_topic = large emphasized.

ALTER TABLE findings ADD COLUMN IF NOT EXISTS significance text
  CHECK (significance IS NULL OR significance IN ('mention', 'discussion', 'analysis', 'primary_topic'));
