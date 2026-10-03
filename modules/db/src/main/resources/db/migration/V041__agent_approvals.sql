-- Histmersion V041 — approvals: changes an agent run PROPOSED and a person decides
-- (docs/research-embedded-agents.md §5.2, §13).
--
-- An agent never changes a source's status (beyond filling `unassessed` with current/foundational) or
-- adds a disagreement edge on its own: it records a proposal here, with the verified quote it rests on.
-- Approving applies it through the same patch a person would send; rejecting keeps it as a record.
-- `agents.approvals.*` = auto applies at once and records the row already approved.
CREATE TABLE IF NOT EXISTS approvals (
  id             bigserial PRIMARY KEY,
  job_id         text REFERENCES jobs(id) ON DELETE SET NULL,   -- the run that proposed it (kept if the run goes)
  kind           text NOT NULL,                                 -- status-change | edge
  citekey        text NOT NULL,                                 -- the source the change is on (an edge starts here)
  status         text,                                          -- status-change: the proposed status
  current_status text,                                          -- status-change: the status when proposed
  edge_kind      text,                                          -- edge: challenges | refutes | updates | superseded_by
  target         text,                                          -- edge: the citekey it points to
  proposal       jsonb NOT NULL,                                -- the verified proposal (evidence, quote)
  state          text NOT NULL DEFAULT 'open',                  -- open | approved | rejected | failed
  note           text,                                          -- the person's note with the decision
  result         text,                                          -- what applying it did, or why it failed
  created_at     timestamptz NOT NULL DEFAULT now(),
  decided_at     timestamptz
);
CREATE INDEX IF NOT EXISTS approvals_state ON approvals (state, created_at DESC);
-- the same change is proposed at most once while it is open (two runs, or two rounds, agreeing)
CREATE UNIQUE INDEX IF NOT EXISTS approvals_open_once
  ON approvals (kind, citekey, coalesce(status, ''), coalesce(edge_kind, ''), coalesce(target, ''))
  WHERE state = 'open';
