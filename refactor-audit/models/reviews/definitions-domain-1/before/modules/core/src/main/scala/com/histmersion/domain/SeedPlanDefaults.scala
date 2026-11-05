package com.histmersion.domain

import com.histmersion.api.automation.Managers.ManagerConfig

/** The checks a seeding manager's plan is held to when it saves none (`agents.seed.*`). */
final case class SeedPlanDefaults(maxArticles: Int, minSources: Int):

  /** A seeding manager's settings when it saves none: its run limits and the plan's checks. */
  def managerDefaults(m: ManagerDefaults): ManagerConfig =
    ManagerConfig(
      maxRuns = Some(m.seed.maxRuns),
      maxOpen = Some(2 * m.seed.maxRuns),
      maxFailures = Some(m.maxFailures),
      minSources = Some(minSources),
      maxArticles = Some(maxArticles),
      dailyUsd = ManagerPolicy.dailyCap(ManagerConfig(), m)
    )
