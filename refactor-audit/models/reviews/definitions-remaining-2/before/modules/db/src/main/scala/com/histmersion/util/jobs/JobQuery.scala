package com.histmersion.util.jobs

/** What the jobs list is filtered by (core `ListFilters.JobFilter`, validated): any of these states and kinds; `since`
  * / `until` bound `created_at` (until exclusive); `q` = text in what the job is about — its citekey, an agent run's
  * target or pipeline, its stored detail — or the start of its id.
  */
final case class JobQuery(
    status: List[String] = Nil,
    kinds: List[String] = Nil,
    since: Option[java.time.Instant] = None,
    until: Option[java.time.Instant] = None,
    q: Option[String] = None
)
