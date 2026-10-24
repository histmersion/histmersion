-- V102: drain mode (#3762 proposal 4). `histmersion agent autopilot drain` stops the central scheduler from
-- admitting new work while the runs in flight finish — what a deploy needs, instead of turning the
-- autopilot off (which paused ~300 managers each time and restarted everything cold). Since when, by whom,
-- and when it was first seen drained (no run queued or running). NULL drain_since = not draining.
-- V092 and V095 stay unused.
ALTER TABLE agent_autopilot ADD COLUMN drain_since timestamptz;
ALTER TABLE agent_autopilot ADD COLUMN drain_by text;
ALTER TABLE agent_autopilot ADD COLUMN drained_at timestamptz;
