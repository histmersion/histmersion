-- V091: the critique checklist's stored reasons (docs/research-review-convergence.md §6.8).
--
-- An article's critique checklist is every typed disagreement edge (challenges, refutes, updates,
-- superseded_by) with at least one end cited by the article. Code marks a pair `addressed` when both
-- citekeys are cited in one paragraph; otherwise it is `open` unless a one-line reason here says the
-- critique does not bear on what the article cites the source for (`reasoned`). The review skeptic
-- rules on each open pair, the fixer may set a missing-critique finding aside with a reason, and a
-- person sets one with `histmersion article patch <slug> --critique-reason <src>><dst>=<why>`.
--
-- One reason per article and pair of sources, whatever the edge kind(s) between them; `src` is the
-- critic (the edge's start) as it was when the reason was set. Kept beside the article rather than in
-- its row so writing one does not move the article's update token (a review pass stores reasons while
-- the fixer holds that token). It follows the article (rename, delete) and both sources (a citekey
-- rename carries it; deleting either source drops it).
CREATE TABLE IF NOT EXISTS article_critique_reasons (
  slug    text NOT NULL REFERENCES articles(slug) ON DELETE CASCADE ON UPDATE CASCADE,
  src     text NOT NULL REFERENCES sources(citekey) ON DELETE CASCADE ON UPDATE CASCADE,
  dst     text NOT NULL REFERENCES sources(citekey) ON DELETE CASCADE ON UPDATE CASCADE,
  reason  text NOT NULL,
  set_by  text NOT NULL DEFAULT 'person',
  set_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (slug, src, dst),
  CHECK (src <> dst)
);
CREATE INDEX IF NOT EXISTS article_critique_reasons_dst ON article_critique_reasons (dst);
CREATE INDEX IF NOT EXISTS article_critique_reasons_src ON article_critique_reasons (src);
