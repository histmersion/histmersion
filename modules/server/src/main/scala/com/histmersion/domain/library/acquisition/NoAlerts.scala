package com.histmersion
package domain.library.acquisition
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.library.Doi

/** No citation or author alerts (a test, or a server built without them). */
object NoAlerts extends CitationAlerts:
  private val off = Left(Problem.Unclassified(Legacy.Text("the citation alerts are not connected")))
  def citing(doi: Doi, since: java.time.LocalDate, limit: CitationAlerts.CitingLimit) = off
  def authors(doi: Doi) = off
  def byAuthor(
      authorId: CitationAlerts.ByAuthorAuthorId,
      since: java.time.LocalDate,
      limit: CitationAlerts.ByAuthorLimit
  ) = off
