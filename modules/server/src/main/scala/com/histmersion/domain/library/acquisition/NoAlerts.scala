package com.histmersion.domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*

/** No citation or author alerts (a test, or a server built without them). */
object NoAlerts extends CitationAlerts:
  type AuthorId = AuthorId.T
  object AuthorId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val off = Left("the citation alerts are not connected")
  def citing(doi: com.histmersion.domain.library.Doi, since: java.time.LocalDate, limit: com.histmersion.domain.library.acquisition.NoAlerts.Limit) = off
  def authors(doi: com.histmersion.domain.library.Doi) = off
  def byAuthor(authorId: com.histmersion.domain.library.acquisition.NoAlerts.AuthorId, since: java.time.LocalDate, limit: com.histmersion.domain.library.acquisition.NoAlerts.Limit) = off
