package com.histmersion.domain.automation.agents

/** What the runs list is filtered by (core `ListFilters.RunFilter`, validated): the lists match any of their values;
  * `since` / `until` bound `created_at` (until exclusive); `q` = text in the target.
  */
final case class RunQuery(
    status: List[String] = Nil,
    pipelines: List[String] = Nil,
    managers: List[String] = Nil,
    goal: Option[String] = None,
    models: List[String] = Nil,
    providers: List[String] = Nil,
    origins: List[String] = Nil,
    since: Option[java.time.Instant] = None,
    until: Option[java.time.Instant] = None,
    q: Option[String] = None
)
