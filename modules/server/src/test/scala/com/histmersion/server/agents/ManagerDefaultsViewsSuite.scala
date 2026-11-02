package com.histmersion.server.agents

import com.histmersion.domain.automation.managers.ManagerDefaultsViews
import com.histmersion.domain.research.workflows.ReviewManagerKind

import com.histmersion.config.{
  AgentManagersConfig,
  AgentSchedulerConfig,
  AgentSeedConfig,
  ReviewManagerConfig,
  SimpleManagerConfig,
  WatchManagerConfig
}
import com.histmersion.domain.{
  ManagerDefaults,
  ManagerRunDefaults,
  ReviewManagerDefaults,
  SchedulerDefaults,
  SeedPlanDefaults,
  WatchManagerDefaults
}

class ManagerDefaultsViewsSuite extends munit.FunSuite:

  private val managers = AgentManagersConfig(
    enabled = true,
    tickSeconds = 30,
    keepEventsDays = 90,
    backoffMinutes = 15,
    maxBackoffMinutes = 360,
    maxFailures = 3,
    review = SimpleManagerConfig(2),
    enrich = SimpleManagerConfig(11),
    seed = SimpleManagerConfig(12),
    watch = SimpleManagerConfig(13),
    dailyUsd = 2.5
  )

  test(
    "the managers' configured defaults reach the policy field by field; what only the supervisor reads stays behind"
  ):
    assertEquals(
      ManagerDefaultsViews.defaults(managers),
      ManagerDefaults(
        maxFailures = 3,
        backoffMinutes = 15,
        maxBackoffMinutes = 360,
        dailyUsd = 2.5,
        review = ManagerRunDefaults(2),
        enrich = ManagerRunDefaults(11),
        seed = ManagerRunDefaults(12),
        watch = ManagerRunDefaults(13)
      )
    )

  test("a review manager's configured defaults reach research's policy field by field; the scan timings stay behind"):
    assertEquals(
      ReviewManagerKind.defaultsOf(
        ReviewManagerConfig(
          maxRuns = 2,
          rounds = 3,
          maxRunsPerArticle = 4,
          noProgressRuns = 5,
          reopenLimit = 6,
          cooldownMinutes = 30,
          lowWater = 7,
          refillBatch = 10,
          staleAfterDays = 90,
          inferFilter = false,
          staleScanMinutes = 45,
          extendMinNew = 8,
          extendCooldownDays = 9
        )
      ),
      ReviewManagerDefaults(
        maxRuns = 2,
        rounds = 3,
        maxRunsPerArticle = 4,
        noProgressRuns = 5,
        reopenLimit = 6,
        cooldownMinutes = 30,
        lowWater = 7,
        refillBatch = 10,
        staleAfterDays = 90,
        inferFilter = false,
        extendMinNew = 8
      )
    )

  test("a seeding plan's checks: its article cap and least sources"):
    assertEquals(
      AgentSeedConfig(maxArticles = 6, minSources = 5, maxOverlap = 0.5).seedPlan,
      SeedPlanDefaults(maxArticles = 6, minSources = 5)
    )

  test("reference.conf's defaults convert"):
    val agents = com.histmersion.config.AppConfig.load().agents
    val d = ManagerDefaultsViews.defaults(agents.managers)
    assertEquals(
      (d.maxFailures, d.review.maxRuns, d.watch.maxRuns),
      (agents.managers.maxFailures, agents.managers.review.maxRuns, agents.managers.watch.maxRuns)
    )

  test("the scheduler's configured settings reach the policy with the built-in weights and priorities beneath them"):
    val c = AgentSchedulerConfig(
      enabled = true,
      minIntervalMinutes = 5,
      currencySources = 1,
      followupsPerSource = 2,
      discoverPerQuery = 3,
      sweepCandidates = 4,
      strategy = "goal-first",
      agingHours = 12,
      maxWaitHours = 48,
      minShare = 0.1,
      typeWeights = "review=5",
      typePriorities = "compile=9",
      gatheringSlots = 2
    )
    assertEquals(
      ManagerDefaultsViews.scheduler(c),
      SchedulerDefaults(
        strategy = "goal-first",
        agingHours = 12,
        maxWaitHours = 48,
        minShare = 0.1,
        builtInWeights = AgentSchedulerConfig.DefaultWeights,
        typeWeights = "review=5",
        builtInPriorities = AgentSchedulerConfig.DefaultPriorities,
        typePriorities = "compile=9",
        gatheringSlots = 2
      )
    )
