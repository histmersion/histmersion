package com.histmersion.domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate
import com.histmersion.api.library.WorkRef

/** The forward-citation and same-author alerts of a goal's sources (OpenAlex): the works citing one, a work's authors,
  * and an author's newer works.
  */
trait CitationAlerts:
  def citing(doi: com.histmersion.domain.library.Doi, since: LocalDate, limit: com.histmersion.domain.library.acquisition.CitationAlerts.CitingLimit): Either[com.histmersion.domain.library.acquisition.CitationAlerts.CitingResultPart1, List[WorkRef]]
  def authors(doi: com.histmersion.domain.library.Doi): Either[com.histmersion.domain.library.acquisition.CitationAlerts.AuthorsResultPart1, List[com.histmersion.domain.library.acquisition.CitationAlerts.AuthorsResultPart2]]
  def byAuthor(authorId: com.histmersion.domain.library.acquisition.CitationAlerts.ByAuthorAuthorId, since: LocalDate, limit: com.histmersion.domain.library.acquisition.CitationAlerts.ByAuthorLimit): Either[com.histmersion.domain.library.acquisition.CitationAlerts.ByAuthorResultPart1, List[WorkRef]]

object CitationAlerts:
  type CitingResultPart1 = CitingResultPart1.T
  object CitingResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitingLimit = CitingLimit.T
  object CitingLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuthorsResultPart1 = AuthorsResultPart1.T
  object AuthorsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AuthorsResultPart2 = AuthorsResultPart2.T
  object AuthorsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByAuthorResultPart1 = ByAuthorResultPart1.T
  object ByAuthorResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByAuthorAuthorId = ByAuthorAuthorId.T
  object ByAuthorAuthorId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ByAuthorLimit = ByAuthorLimit.T
  object ByAuthorLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
