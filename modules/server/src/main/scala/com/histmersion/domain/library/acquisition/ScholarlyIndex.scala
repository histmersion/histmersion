package com.histmersion
package domain.library.acquisition

import com.histmersion.api.Problem
import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate
import com.histmersion.api.library.WorkRef
import com.histmersion.domain.library.search.Search
import io.github.iltotore.iron.constraint.any.Pure

/** One scholarly index a goal's saved queries are read from (#3761): the works matching a query that are newer than
  * `since` (published, or deposited where the index says so), newest first. A small trait so a test hands fakes.
  */
trait ScholarlyIndex:
  def name: ScholarlyIndex.Name
  def search(
      query: Search.Query,
      since: LocalDate,
      limit: ScholarlyIndex.Limit
  ): Either[Problem, List[WorkRef]]

  /** Why the index would not be asked now (CORE's budget spent, CORE asked to wait), if it would not: a sweep then
    * leaves its watches unread, keeping their watermarks, and says so.
    */
  def unavailable: Option[Problem] = None

object ScholarlyIndex:
  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type Name = Name.T
  object Name extends Newtype[String, Pure]
