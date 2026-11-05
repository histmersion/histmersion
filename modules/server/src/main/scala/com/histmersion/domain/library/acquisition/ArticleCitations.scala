package com.histmersion.domain.library.acquisition

/** What the new-source search asks of research: the stored sources an article cites (research implements it). */
trait ArticleCitations:
  /** The citekeys of the stored sources article `slug` cites, in its order. */
  def cited(slug: com.histmersion.domain.library.acquisition.ArticleCitations.CitedSlug): List[com.histmersion.domain.library.acquisition.ArticleCitations.CitedResult]

object ArticleCitations:
  type CitedResult = CitedResult.T
  object CitedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedSlug = CitedSlug.T
  object CitedSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
