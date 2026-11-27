package com.histmersion
package domain.library.acquisition
import com.histmersion.util.problem.Problem

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.domain.library.Doi
import com.histmersion.lint.{tolerated, LintRule}

/** No citation or author alerts (a test, or a server built without them). */
object NoAlerts extends CitationAlerts:
  private val off = Left(Problem.Config.Unavailable(Problem.Config.Capability.CitationAlerts))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def citing(doi: Doi, since: java.time.LocalDate, limit: CitationAlerts.CitingLimit) = off
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def authors(doi: Doi) = off
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def byAuthor(
      authorId: CitationAlerts.ByAuthorAuthorId,
      since: java.time.LocalDate,
      limit: CitationAlerts.ByAuthorLimit
  ) = off
