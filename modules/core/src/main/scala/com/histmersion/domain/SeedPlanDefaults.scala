package com.histmersion
package domain

import com.histmersion.api.automation.Managers.ManagerConfig
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}
import com.histmersion.lint.{LintRule, tolerated}

/** The checks a seeding manager's plan is held to when it saves none (`agents.seed.*`). */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@movesTo(FeaturePlace.LibraryWorkflows)
final case class SeedPlanDefaults(maxArticles: SeedPlanDefaults.MaxArticles, minSources: SeedPlanDefaults.MinSources):

  /** A seeding manager's settings when it saves none: its run limits and the plan's checks. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def managerDefaults(m: ManagerDefaults): ManagerConfig =
    ManagerConfig(
      maxRuns = Some(ManagerConfig.MaxRuns(m.seed.maxRuns.value)),
      maxOpen = Some(ManagerConfig.MaxOpen(2 * m.seed.maxRuns.value)),
      maxFailures = Some(ManagerConfig.MaxFailures(m.maxFailures.value)),
      minSources = Some(ManagerConfig.MinSources(minSources.value)),
      maxArticles = Some(ManagerConfig.MaxArticles(maxArticles.value)),
      dailyUsd = ManagerPolicy.dailyCap(ManagerConfig(), m)
    )

object SeedPlanDefaults:
  type MaxArticles = MaxArticles.T
  object MaxArticles extends Newtype[Int, Pure]
  type MinSources = MinSources.T
  object MinSources extends Newtype[Int, Pure]
