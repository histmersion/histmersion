-- V101: the daily approval digest (#3762 proposal 3): when the managers' tick last wrote the ONE worklog
-- line of everything waiting for a person, so it is written once a UTC day, restarts included.
-- V092 and V095 stay unused.
ALTER TABLE agent_autopilot ADD COLUMN last_digest_at timestamptz;
