package com.histmersion.server.agents

import com.histmersion.testsupport.ComparedByValue.given

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
    enabled = com.histmersion.config.AgentManagersConfig.Enabled(true),
    tickSeconds = com.histmersion.config.AgentManagersConfig.TickSeconds(30),
    keepEventsDays = com.histmersion.config.AgentManagersConfig.KeepEventsDays(90),
    backoffMinutes = com.histmersion.config.AgentManagersConfig.BackoffMinutes(15),
    maxBackoffMinutes = com.histmersion.config.AgentManagersConfig.MaxBackoffMinutes(360),
    maxFailures = com.histmersion.config.AgentManagersConfig.MaxFailures(3),
    review = SimpleManagerConfig(com.histmersion.config.SimpleManagerConfig.MaxRuns(2)),
    enrich = SimpleManagerConfig(com.histmersion.config.SimpleManagerConfig.MaxRuns(11)),
    seed = SimpleManagerConfig(com.histmersion.config.SimpleManagerConfig.MaxRuns(12)),
    watch = SimpleManagerConfig(com.histmersion.config.SimpleManagerConfig.MaxRuns(13)),
    dailyUsd = AgentManagersConfig.DailyUsd(2.5)
  )

  test(
    "the managers' configured defaults reach the policy field by field; what only the supervisor reads stays behind"
  ):
    assertEquals(
      ManagerDefaultsViews.defaults(managers),
      ManagerDefaults(
        maxFailures = com.histmersion.domain.ManagerDefaults.MaxFailures(3),
        backoffMinutes = com.histmersion.domain.ManagerDefaults.BackoffMinutes(15),
        maxBackoffMinutes = com.histmersion.domain.ManagerDefaults.MaxBackoffMinutes(360),
        dailyUsd = ManagerDefaults.DailyUsd(2.5),
        review = ManagerRunDefaults(com.histmersion.domain.ManagerRunDefaults.MaxRuns(2)),
        enrich = ManagerRunDefaults(com.histmersion.domain.ManagerRunDefaults.MaxRuns(11)),
        seed = ManagerRunDefaults(com.histmersion.domain.ManagerRunDefaults.MaxRuns(12)),
        watch = ManagerRunDefaults(com.histmersion.domain.ManagerRunDefaults.MaxRuns(13))
      )
    )

  test("a review manager's configured defaults reach research's policy field by field; the scan timings stay behind"):
    assertEquals(
      ReviewManagerKind.defaultsOf(
        ReviewManagerConfig(
          maxRuns = com.histmersion.config.ReviewManagerConfig.MaxRuns(2),
          rounds = com.histmersion.config.ReviewManagerConfig.Rounds(3),
          maxRunsPerArticle = com.histmersion.config.ReviewManagerConfig.MaxRunsPerArticle(4),
          noProgressRuns = com.histmersion.config.ReviewManagerConfig.NoProgressRuns(5),
          reopenLimit = com.histmersion.config.ReviewManagerConfig.ReopenLimit(6),
          cooldownMinutes = com.histmersion.config.ReviewManagerConfig.CooldownMinutes(30),
          lowWater = com.histmersion.config.ReviewManagerConfig.LowWater(7),
          refillBatch = com.histmersion.config.ReviewManagerConfig.RefillBatch(10),
          staleAfterDays = com.histmersion.config.ReviewManagerConfig.StaleAfterDays(90),
          inferFilter = com.histmersion.config.ReviewManagerConfig.InferFilter(false),
          staleScanMinutes = com.histmersion.config.ReviewManagerConfig.StaleScanMinutes(45),
          extendMinNew = com.histmersion.config.ReviewManagerConfig.ExtendMinNew(8),
          extendCooldownDays = com.histmersion.config.ReviewManagerConfig.ExtendCooldownDays(9)
        )
      ),
      ReviewManagerDefaults(
        maxRuns = com.histmersion.domain.ReviewManagerDefaults.MaxRuns(2),
        rounds = com.histmersion.domain.ReviewManagerDefaults.Rounds(3),
        maxRunsPerArticle = com.histmersion.domain.ReviewManagerDefaults.MaxRunsPerArticle(4),
        noProgressRuns = com.histmersion.domain.ReviewManagerDefaults.NoProgressRuns(5),
        reopenLimit = com.histmersion.domain.ReviewManagerDefaults.ReopenLimit(6),
        cooldownMinutes = com.histmersion.domain.ReviewManagerDefaults.CooldownMinutes(30),
        lowWater = com.histmersion.domain.ReviewManagerDefaults.LowWater(7),
        refillBatch = com.histmersion.domain.ReviewManagerDefaults.RefillBatch(10),
        staleAfterDays = com.histmersion.domain.ReviewManagerDefaults.StaleAfterDays(90),
        inferFilter = com.histmersion.domain.ReviewManagerDefaults.InferFilter(false),
        extendMinNew = com.histmersion.domain.ReviewManagerDefaults.ExtendMinNew(8)
      )
    )

  test("a seeding plan's checks: its article cap and least sources"):
    assertEquals(
      com.histmersion.domain.research.workflows.SeedManagerKind
        .plan(AgentSeedConfig(maxArticles = com.histmersion.config.AgentSeedConfig.MaxArticles(6), minSources = com.histmersion.config.AgentSeedConfig.MinSources(5), maxOverlap = AgentSeedConfig.MaxOverlap(0.5))),
      SeedPlanDefaults(maxArticles = com.histmersion.domain.SeedPlanDefaults.MaxArticles(6), minSources = com.histmersion.domain.SeedPlanDefaults.MinSources(5))
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
      enabled = com.histmersion.config.AgentSchedulerConfig.Enabled(true),
      minIntervalMinutes = com.histmersion.config.AgentSchedulerConfig.MinIntervalMinutes(5),
      currencySources = com.histmersion.config.AgentSchedulerConfig.CurrencySources(1),
      followupsPerSource = com.histmersion.config.AgentSchedulerConfig.FollowupsPerSource(2),
      discoverPerQuery = com.histmersion.config.AgentSchedulerConfig.DiscoverPerQuery(3),
      sweepCandidates = com.histmersion.config.AgentSchedulerConfig.SweepCandidates(4),
      strategy = com.histmersion.config.AgentSchedulerConfig.Strategy("goal-first"),
      agingHours = com.histmersion.config.AgentSchedulerConfig.AgingHours(12),
      maxWaitHours = com.histmersion.config.AgentSchedulerConfig.MaxWaitHours(48),
      minShare = AgentSchedulerConfig.MinShare(0.1),
      typeWeights = com.histmersion.config.AgentSchedulerConfig.TypeWeights("review=5"),
      typePriorities = com.histmersion.config.AgentSchedulerConfig.TypePriorities("compile=9"),
      gatheringSlots = AgentSchedulerConfig.GatheringSlots(2)
    )
    assertEquals(
      ManagerDefaultsViews.scheduler(c),
      SchedulerDefaults(
        strategy = com.histmersion.domain.SchedulerDefaults.Strategy("goal-first"),
        agingHours = com.histmersion.domain.SchedulerDefaults.AgingHours(12),
        maxWaitHours = com.histmersion.domain.SchedulerDefaults.MaxWaitHours(48),
        minShare = SchedulerDefaults.MinShare(0.1),
        builtInWeights = SchedulerDefaults.BuiltInWeights(AgentSchedulerConfig.DefaultWeights.value),
        typeWeights = com.histmersion.domain.SchedulerDefaults.TypeWeights("review=5"),
        builtInPriorities = SchedulerDefaults.BuiltInPriorities(AgentSchedulerConfig.DefaultPriorities.value),
        typePriorities = com.histmersion.domain.SchedulerDefaults.TypePriorities("compile=9"),
        gatheringSlots = com.histmersion.domain.SchedulerDefaults.GatheringSlots(2)
      )
    )
