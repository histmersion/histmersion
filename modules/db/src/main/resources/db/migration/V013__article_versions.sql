-- Histmersion V013 — ARTICLE VERSIONS: history of every article body change.
--
-- Every time an article's body changes (via put_article or patch_article), the PREVIOUS version of
-- the body is saved here before the update is applied. This gives a full edit history of every
-- compiled article, so a bad rewrite can be identified and its predecessor recovered.

CREATE TABLE article_versions (
  id         BIGSERIAL PRIMARY KEY,
  slug       TEXT NOT NULL REFERENCES articles(slug) ON DELETE CASCADE,
  body       TEXT NOT NULL,
  summary    TEXT,
  updated_at TIMESTAMPTZ NOT NULL,   -- the updated_at of the version being replaced
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()  -- when this version record was created
);

CREATE INDEX article_versions_slug_idx ON article_versions (slug, created_at DESC);
