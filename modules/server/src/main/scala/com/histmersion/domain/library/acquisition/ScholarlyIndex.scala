package com.histmersion.domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate
import com.histmersion.api.library.WorkRef

/** One scholarly index a goal's saved queries are read from (#3761): the works matching a query that are newer than
  * `since` (published, or deposited where the index says so), newest first. A small trait so a test hands fakes.
  */
trait ScholarlyIndex:
  def name: com.histmersion.domain.library.acquisition.ScholarlyIndex.Name
  def search(query: com.histmersion.domain.library.search.Search.Query, since: LocalDate, limit: com.histmersion.domain.library.acquisition.ScholarlyIndex.Limit): Either[com.histmersion.domain.library.acquisition.ScholarlyIndex.Problem, List[WorkRef]]

  /** Why the index would not be asked now (CORE's budget spent, CORE asked to wait), if it would not: a sweep then
    * leaves its watches unread, keeping their watermarks, and says so.
    */
  def unavailable: Option[com.histmersion.domain.library.acquisition.ScholarlyIndex.Problem] = None

object ScholarlyIndex:
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Name = Name.T
  object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
