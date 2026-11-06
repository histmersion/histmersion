package com.histmersion.domain

import com.histmersion.api.automation.Managers.ManagerConfig

/** The checks a seeding manager's plan is held to when it saves none (`agents.seed.*`). */
final case class SeedPlanDefaults(maxArticles: SeedPlanDefaults.MaxArticles, minSources: SeedPlanDefaults.MinSources):

  /** A seeding manager's settings when it saves none: its run limits and the plan's checks. */
  def managerDefaults(m: ManagerDefaults): ManagerConfig =
    ManagerConfig(
      maxRuns = Some(ManagerConfig.MaxRuns(m.seed.maxRuns.value)),
      maxOpen = Some(ManagerConfig.MaxOpen(2 * m.seed.maxRuns.value)),
      maxFailures = Some(m.maxFailures),
      minSources = Some(minSources),
      maxArticles = Some(maxArticles),
      dailyUsd = ManagerPolicy.dailyCap(ManagerConfig(), m)
    )

object SeedPlanDefaults:
  type MaxArticles = MaxArticles.T
  object MaxArticles extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MinSources = MinSources.T
  object MinSources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
