package com.histmersion.domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*

/** No citation or author alerts (a test, or a server built without them). */
object NoAlerts extends CitationAlerts:
  type CitingLimit = CitingLimit.T
  object CitingLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByAuthorAuthorId = ByAuthorAuthorId.T
  object ByAuthorAuthorId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByAuthorLimit = ByAuthorLimit.T
  object ByAuthorLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val off = Left("the citation alerts are not connected")
  def citing(doi: com.histmersion.domain.library.Doi, since: java.time.LocalDate, limit: com.histmersion.domain.library.acquisition.NoAlerts.CitingLimit) = off
  def authors(doi: com.histmersion.domain.library.Doi) = off
  def byAuthor(authorId: com.histmersion.domain.library.acquisition.NoAlerts.ByAuthorAuthorId, since: java.time.LocalDate, limit: com.histmersion.domain.library.acquisition.NoAlerts.ByAuthorLimit) = off
