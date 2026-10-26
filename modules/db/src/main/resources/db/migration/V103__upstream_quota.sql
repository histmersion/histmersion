-- V103: the budget of a metered upstream (CORE), kept across restarts: the tokens its uses spent on a
-- (UTC) day, a "do not ask until" the upstream gave (a 429, its rate-limit window spent) and what its
-- last answer's rate-limit headers said. One row per upstream; the server rewrites it after each call.
CREATE TABLE upstream_quota (
  upstream       text PRIMARY KEY,
  day            date NOT NULL,
  lookup_tokens  integer NOT NULL DEFAULT 0,
  search_tokens  integer NOT NULL DEFAULT 0,
  blocked_until  timestamptz,
  blocked_why    text,
  rate_limit     integer,
  rate_remaining integer,
  observed_at    timestamptz,
  updated_at     timestamptz NOT NULL DEFAULT now()
);
