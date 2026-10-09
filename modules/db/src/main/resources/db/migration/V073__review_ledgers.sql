-- V073: the review ledger of an article (docs/research-review-convergence.md §6.1) — every finding the
-- review-article panel raised on it, with its status (open / fixed / rejected / wont-fix), the lessons
-- learned, and the article's one backlog item (`task`, #934). The value is the pure
-- `ReviewLedger.Ledger` as JSON; a run reads it at its start and writes it after each pass. It follows
-- the article: renamed with it, deleted with it.
CREATE TABLE IF NOT EXISTS review_ledgers (
  slug       text PRIMARY KEY REFERENCES articles(slug) ON DELETE CASCADE ON UPDATE CASCADE,
  ledger     jsonb NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);
