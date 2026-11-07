package com.histmersion.domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate
import com.histmersion.api.library.WorkRef

/** The forward-citation and same-author alerts of a goal's sources (OpenAlex): the works citing one, a work's authors,
  * and an author's newer works.
  */
trait CitationAlerts:
  def citing(doi: com.histmersion.domain.library.Doi, since: LocalDate, limit: com.histmersion.domain.library.acquisition.CitationAlerts.CitingLimit): Either[com.histmersion.domain.library.acquisition.CitationAlerts.Problem, List[WorkRef]]
  def authors(doi: com.histmersion.domain.library.Doi): Either[com.histmersion.domain.library.acquisition.CitationAlerts.Problem, List[com.histmersion.domain.AuthorName]]
  def byAuthor(authorId: com.histmersion.domain.library.acquisition.CitationAlerts.ByAuthorAuthorId, since: LocalDate, limit: com.histmersion.domain.library.acquisition.CitationAlerts.ByAuthorLimit): Either[com.histmersion.domain.library.acquisition.CitationAlerts.Problem, List[WorkRef]]

object CitationAlerts:
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitingLimit = CitingLimit.T
  object CitingLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByAuthorAuthorId = ByAuthorAuthorId.T
  object ByAuthorAuthorId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByAuthorLimit = ByAuthorLimit.T
  object ByAuthorLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
