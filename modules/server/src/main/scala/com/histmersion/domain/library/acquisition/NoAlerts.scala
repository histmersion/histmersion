package com.histmersion.domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*

/** No citation or author alerts (a test, or a server built without them). */
object NoAlerts extends CitationAlerts:
  private val off = Left(CitationAlerts.Problem("the citation alerts are not connected"))
  def citing(doi: com.histmersion.domain.library.Doi, since: java.time.LocalDate, limit: CitationAlerts.CitingLimit) = off
  def authors(doi: com.histmersion.domain.library.Doi) = off
  def byAuthor(authorId: CitationAlerts.ByAuthorAuthorId, since: java.time.LocalDate, limit: CitationAlerts.ByAuthorLimit) = off
