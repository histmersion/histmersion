package com.histmersion
package util.jobs

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** What the jobs list is filtered by (core `ListFilters.JobFilter`, validated): any of these states and kinds; `since`
  * / `until` bound `created_at` (until exclusive); `q` = text in what the job is about — its citekey, an agent run's
  * target or pipeline, its stored detail — or the start of its id.
  */
final case class JobQuery(
    status: List[JobQuery.Status] = Nil,
    kinds: List[JobQuery.Kinds] = Nil,
    since: Option[java.time.Instant] = None,
    until: Option[java.time.Instant] = None,
    q: Option[JobQuery.Q] = None
)

object JobQuery:
  type Status = Status.T
  object Status extends RefinedType[String, Pure]
  type Kinds = Kinds.T
  object Kinds extends RefinedType[String, Pure]
  type Q = Q.T
  object Q extends RefinedType[String, Pure]
