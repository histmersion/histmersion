package com.histmersion.domain.library.acquisition

import com.histmersion.config.*
import com.histmersion.domain.*

import java.time.LocalDate
import com.histmersion.api.library.WorkRef

/** One scholarly index a goal's saved queries are read from (#3761): the works matching a query that are newer than
  * `since` (published, or deposited where the index says so), newest first. A small trait so a test hands fakes.
  */
trait ScholarlyIndex:
  def name: String
  def search(query: String, since: LocalDate, limit: Int): Either[String, List[WorkRef]]

  /** Why the index would not be asked now (CORE's budget spent, CORE asked to wait), if it would not: a sweep then
    * leaves its watches unread, keeping their watermarks, and says so.
    */
  def unavailable: Option[String] = None
