package com.histmersion
package domain.automation.agents

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
  object Limit extends Newtype[Int, Pure]
  type UpTo = UpTo.T
  object UpTo extends Newtype[String, Pure]
  type Status = Status.T
  object Status extends Newtype[String, Pure]
  type Pipelines = Pipelines.T
  object Pipelines extends Newtype[String, Pure]
  type Managers = Managers.T
  object Managers extends Newtype[String, Pure]
  type Goal = Goal.T
  object Goal extends Newtype[String, Pure]
  type Models = Models.T
  object Models extends Newtype[String, Pure]
  type Providers = Providers.T
  object Providers extends Newtype[String, Pure]
  type Origins = Origins.T
  object Origins extends Newtype[String, Pure]
  type Q = Q.T
  object Q extends Newtype[String, Pure]
