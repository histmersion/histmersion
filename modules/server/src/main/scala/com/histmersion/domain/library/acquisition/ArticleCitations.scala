package com.histmersion
package domain.library.acquisition

import com.histmersion.domain.research.Slug
import com.histmersion.domain.library.Citekey

/** What the new-source search asks of research: the stored sources an article cites (research implements it). */
trait ArticleCitations:
  /** The citekeys of the stored sources article `slug` cites, in its order. */
  def cited(slug: Slug): List[Citekey]

