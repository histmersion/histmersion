-- V096: the autopilot switch (docs/research-process-managers.md "As built: the autopilot"). ONE row:
-- whether it is on, who switched it and when, the managers it paused when it was turned off (resumed
-- when it is turned on again — a manager a person paused is never touched), and when it last made sure
-- every goal has its managers and ran its housekeeping (a backup, the safe lint repairs), so a restart
-- does not repeat a daily job. V092 and V095 are left unused on purpose.
CREATE TABLE agent_autopilot (
  id             boolean PRIMARY KEY DEFAULT true CHECK (id),
  enabled        boolean NOT NULL DEFAULT false,
  changed_at     timestamptz NOT NULL DEFAULT now(),
  changed_by     text NOT NULL DEFAULT '',
  paused         text[] NOT NULL DEFAULT '{}',
  last_ensure_at timestamptz,
  last_backup_at timestamptz,
  last_lint_at   timestamptz
);
INSERT INTO agent_autopilot (id) VALUES (true);
