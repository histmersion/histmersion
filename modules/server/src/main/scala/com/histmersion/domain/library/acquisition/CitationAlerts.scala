package com.histmersion
package domain.library.acquisition

import com.histmersion.api.Problem
import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate
import com.histmersion.api.library.WorkRef
import com.histmersion.domain.library.Doi
import io.github.iltotore.iron.constraint.any.Pure

/** The forward-citation and same-author alerts of a goal's sources (OpenAlex): the works citing one, a work's authors,
  * and an author's newer works.
  */
trait CitationAlerts:
  def citing(
      doi: Doi,
      since: LocalDate,
      limit: CitationAlerts.CitingLimit
  ): Either[Problem, List[WorkRef]]
  def authors(doi: Doi): Either[Problem, List[AuthorName]]
  def byAuthor(
      authorId: CitationAlerts.ByAuthorAuthorId,
      since: LocalDate,
      limit: CitationAlerts.ByAuthorLimit
  ): Either[Problem, List[WorkRef]]

object CitationAlerts:
  type CitingLimit = CitingLimit.T
  object CitingLimit extends Newtype[Int, Pure]
  type ByAuthorAuthorId = ByAuthorAuthorId.T
  object ByAuthorAuthorId extends Newtype[String, Pure]
  type ByAuthorLimit = ByAuthorLimit.T
  object ByAuthorLimit extends Newtype[Int, Pure]
