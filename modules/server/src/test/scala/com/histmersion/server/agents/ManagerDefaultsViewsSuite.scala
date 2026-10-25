package com.histmersion.server.agents

import com.histmersion.config.{AgentManagersConfig, AgentSchedulerConfig, AgentSeedConfig, ReviewManagerConfig,
  SimpleManagerConfig, WatchManagerConfig}
import com.histmersion.domain.{ManagerDefaults, ManagerRunDefaults, ReviewManagerDefaults, SchedulerDefaults,
  SeedPlanDefaults, WatchManagerDefaults}

class ManagerDefaultsViewsSuite extends munit.FunSuite:

  private val managers = AgentManagersConfig(enabled = true, tickSeconds = 30, keepEventsDays = 90, backoffMinutes = 15,
    maxBackoffMinutes = 360, maxFailures = 3,
    review = ReviewManagerConfig(maxRuns = 2, rounds = 3, maxRunsPerArticle = 4, noProgressRuns = 5, reopenLimit = 6,
      cooldownMinutes = 30, lowWater = 7, refillBatch = 10, staleAfterDays = 90, inferFilter = false,
      staleScanMinutes = 45, extendMinNew = 8, extendCooldownDays = 9),
    enrich = SimpleManagerConfig(11), seed = SimpleManagerConfig(12),
    watch = WatchManagerConfig(maxRuns = 13, cron = "@daily", autoIngestPerDay = 14, drainPerTick = 2), dailyUsd = 2.5)

  test("the managers' configured defaults reach the policy field by field; what only the supervisor reads stays behind"):
    assertEquals(ManagerDefaultsViews.defaults(managers),
      ManagerDefaults(maxFailures = 3, backoffMinutes = 15, maxBackoffMinutes = 360, dailyUsd = 2.5,
        review = ReviewManagerDefaults(maxRuns = 2, rounds = 3, maxRunsPerArticle = 4, noProgressRuns = 5, reopenLimit = 6,
          cooldownMinutes = 30, lowWater = 7, refillBatch = 10, staleAfterDays = 90, inferFilter = false, extendMinNew = 8),
        enrich = ManagerRunDefaults(11), seed = ManagerRunDefaults(12),
        watch = WatchManagerDefaults(maxRuns = 13, autoIngestPerDay = 14)))

  test("a seeding plan's checks: its article cap and least sources"):
    assertEquals(ManagerDefaultsViews.seedPlan(AgentSeedConfig(maxArticles = 6, minSources = 5, maxOverlap = 0.5)),
      SeedPlanDefaults(maxArticles = 6, minSources = 5))

  test("reference.conf's defaults convert"):
    val agents = com.histmersion.config.AppConfig.load().agents
    val d      = ManagerDefaultsViews.defaults(agents.managers)
    assertEquals((d.maxFailures, d.review.maxRuns, d.watch.autoIngestPerDay),
      (agents.managers.maxFailures, agents.managers.review.maxRuns, agents.managers.watch.autoIngestPerDay))

  test("the scheduler's configured settings reach the policy with the built-in weights and priorities beneath them"):
    val c = AgentSchedulerConfig(enabled = true, minIntervalMinutes = 5, currencySources = 1, followupsPerSource = 2,
      discoverPerQuery = 3, sweepCandidates = 4, strategy = "goal-first", agingHours = 12, maxWaitHours = 48,
      minShare = 0.1, typeWeights = "review=5", typePriorities = "compile=9", gatheringSlots = 2)
    assertEquals(ManagerDefaultsViews.scheduler(c),
      SchedulerDefaults(strategy = "goal-first", agingHours = 12, maxWaitHours = 48, minShare = 0.1,
        builtInWeights = AgentSchedulerConfig.DefaultWeights, typeWeights = "review=5",
        builtInPriorities = AgentSchedulerConfig.DefaultPriorities, typePriorities = "compile=9", gatheringSlots = 2))
