-- V100: the central agent scheduler (docs/research-goal-advancement.md "As built"). One decision per
-- managers' tick admits the managers' pending items across every job type; its strategy is
-- agents.scheduler.strategy unless a person switched it live (`histmersion agent scheduler strategy`):
-- that choice, who made it and when, on the autopilot's one row. NULL = the config's.
-- V092 and V095 stay unused.
ALTER TABLE agent_autopilot ADD COLUMN scheduler_strategy text;
ALTER TABLE agent_autopilot ADD COLUMN scheduler_by text;
ALTER TABLE agent_autopilot ADD COLUMN scheduler_at timestamptz;
