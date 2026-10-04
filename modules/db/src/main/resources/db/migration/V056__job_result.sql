-- V056: a merge apply is a `jobs` row (kind 'merge-apply', #888), so `GET /api/merge/apply/{job}` and
-- the CLI's poll survive a restart. The plan it carries out is in `detail` (`JobDetail.planHash`), the
-- bundle's sha256 in `sha256`; what it produced — the apply's report, applied or refused — is here.
--
-- A restart turns a still-running apply into `failed: interrupted` like any other unfinished job
-- (`JobRepo.failInterrupted`); the apply's own transaction guarantees nothing was half applied.

ALTER TABLE jobs ADD COLUMN IF NOT EXISTS result jsonb;
