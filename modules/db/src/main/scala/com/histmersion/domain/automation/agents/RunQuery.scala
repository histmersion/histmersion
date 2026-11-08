package com.histmersion
package domain.automation.agents

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** What the runs list is filtered by (core `ListFilters.RunFilter`, validated): the lists match any of their values;
  * `since` / `until` bound `created_at` (until exclusive); `q` = text in the target.
  */
final case class RunQuery(
    status: List[RunQuery.Status] = Nil,
    pipelines: List[RunQuery.Pipelines] = Nil,
    managers: List[RunQuery.Managers] = Nil,
    goal: Option[RunQuery.Goal] = None,
    models: List[RunQuery.Models] = Nil,
    providers: List[RunQuery.Providers] = Nil,
    origins: List[RunQuery.Origins] = Nil,
    since: Option[java.time.Instant] = None,
    until: Option[java.time.Instant] = None,
    q: Option[RunQuery.Q] = None
)

object RunQuery:
  type Limit = Limit.T
  object Limit extends RefinedType[Int, Pure]
  type UpTo = UpTo.T
  object UpTo extends RefinedType[String, Pure]
  type Status = Status.T
  object Status extends RefinedType[String, Pure]
  type Pipelines = Pipelines.T
  object Pipelines extends RefinedType[String, Pure]
  type Managers = Managers.T
  object Managers extends RefinedType[String, Pure]
  type Goal = Goal.T
  object Goal extends RefinedType[String, Pure]
  type Models = Models.T
  object Models extends RefinedType[String, Pure]
  type Providers = Providers.T
  object Providers extends RefinedType[String, Pure]
  type Origins = Origins.T
  object Origins extends RefinedType[String, Pure]
  type Q = Q.T
  object Q extends RefinedType[String, Pure]
