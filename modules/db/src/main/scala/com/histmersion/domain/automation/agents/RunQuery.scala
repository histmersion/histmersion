package com.histmersion.domain.automation.agents

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
  type Status = Status.T
  object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Pipelines = Pipelines.T
  object Pipelines extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Managers = Managers.T
  object Managers extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Goal = Goal.T
  object Goal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Models = Models.T
  object Models extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Providers = Providers.T
  object Providers extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Origins = Origins.T
  object Origins extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Q = Q.T
  object Q extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
