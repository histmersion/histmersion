package com.histmersion.domain.library.acquisition

/** What the new-source search asks of research: the stored sources an article cites (research implements it). */
trait ArticleCitations:
  /** The citekeys of the stored sources article `slug` cites, in its order. */
  def cited(slug: com.histmersion.domain.research.Slug): List[com.histmersion.domain.library.Citekey]

