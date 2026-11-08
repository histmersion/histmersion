package com.histmersion
package server.agents

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
import com.histmersion.domain.research.workflows.SeedManagerKind
import com.histmersion.config.AppConfig

class ManagerDefaultsViewsSuite extends munit.FunSuite:

  private val managers = AgentManagersConfig(
    enabled = AgentManagersConfig.Enabled(true),
    tickSeconds = AgentManagersConfig.TickSeconds(30),
    keepEventsDays = AgentManagersConfig.KeepEventsDays(90),
    backoffMinutes = AgentManagersConfig.BackoffMinutes(15),
    maxBackoffMinutes = AgentManagersConfig.MaxBackoffMinutes(360),
    maxFailures = AgentManagersConfig.MaxFailures(3),
    review = SimpleManagerConfig(SimpleManagerConfig.MaxRuns(2)),
    enrich = SimpleManagerConfig(SimpleManagerConfig.MaxRuns(11)),
    seed = SimpleManagerConfig(SimpleManagerConfig.MaxRuns(12)),
    watch = SimpleManagerConfig(SimpleManagerConfig.MaxRuns(13)),
    dailyUsd = AgentManagersConfig.DailyUsd(2.5)
  )

  test(
    "the managers' configured defaults reach the policy field by field; what only the supervisor reads stays behind"
  ):
    assertEquals(
      ManagerDefaultsViews.defaults(managers),
      ManagerDefaults(
        maxFailures = ManagerDefaults.MaxFailures(3),
        backoffMinutes = ManagerDefaults.BackoffMinutes(15),
        maxBackoffMinutes = ManagerDefaults.MaxBackoffMinutes(360),
        dailyUsd = ManagerDefaults.DailyUsd(2.5),
        review = ManagerRunDefaults(ManagerRunDefaults.MaxRuns(2)),
        enrich = ManagerRunDefaults(ManagerRunDefaults.MaxRuns(11)),
        seed = ManagerRunDefaults(ManagerRunDefaults.MaxRuns(12)),
        watch = ManagerRunDefaults(ManagerRunDefaults.MaxRuns(13))
      )
    )

  test("a review manager's configured defaults reach research's policy field by field; the scan timings stay behind"):
    assertEquals(
      ReviewManagerKind.defaultsOf(
        ReviewManagerConfig(
          maxRuns = ReviewManagerConfig.MaxRuns(2),
          rounds = ReviewManagerConfig.Rounds(3),
          maxRunsPerArticle = ReviewManagerConfig.MaxRunsPerArticle(4),
          noProgressRuns = ReviewManagerConfig.NoProgressRuns(5),
          reopenLimit = ReviewManagerConfig.ReopenLimit(6),
          cooldownMinutes = ReviewManagerConfig.CooldownMinutes(30),
          lowWater = ReviewManagerConfig.LowWater(7),
          refillBatch = ReviewManagerConfig.RefillBatch(10),
          staleAfterDays = ReviewManagerConfig.StaleAfterDays(90),
          inferFilter = ReviewManagerConfig.InferFilter(false),
          staleScanMinutes = ReviewManagerConfig.StaleScanMinutes(45),
          extendMinNew = ReviewManagerConfig.ExtendMinNew(8),
          extendCooldownDays = ReviewManagerConfig.ExtendCooldownDays(9)
        )
      ),
      ReviewManagerDefaults(
        maxRuns = ReviewManagerDefaults.MaxRuns(2),
        rounds = ReviewManagerDefaults.Rounds(3),
        maxRunsPerArticle = ReviewManagerDefaults.MaxRunsPerArticle(4),
        noProgressRuns = ReviewManagerDefaults.NoProgressRuns(5),
        reopenLimit = ReviewManagerDefaults.ReopenLimit(6),
        cooldownMinutes = ReviewManagerDefaults.CooldownMinutes(30),
        lowWater = ReviewManagerDefaults.LowWater(7),
        refillBatch = ReviewManagerDefaults.RefillBatch(10),
        staleAfterDays = ReviewManagerDefaults.StaleAfterDays(90),
        inferFilter = ReviewManagerDefaults.InferFilter(false),
        extendMinNew = ReviewManagerDefaults.ExtendMinNew(8)
      )
    )

  test("a seeding plan's checks: its article cap and least sources"):
    assertEquals(
      SeedManagerKind
        .plan(AgentSeedConfig(maxArticles = AgentSeedConfig.MaxArticles(6), minSources = AgentSeedConfig.MinSources(5), maxOverlap = AgentSeedConfig.MaxOverlap(0.5))),
      SeedPlanDefaults(maxArticles = SeedPlanDefaults.MaxArticles(6), minSources = SeedPlanDefaults.MinSources(5))
    )

  test("reference.conf's defaults convert"):
    val agents = AppConfig.load().agents
    val d = ManagerDefaultsViews.defaults(agents.managers)
    assertEquals(
      (d.maxFailures.value, d.review.maxRuns.value, d.watch.maxRuns.value),
      (agents.managers.maxFailures.value, agents.managers.review.maxRuns.value, agents.managers.watch.maxRuns.value)
    )

  test("the scheduler's configured settings reach the policy with the built-in weights and priorities beneath them"):
    val c = AgentSchedulerConfig(
      enabled = AgentSchedulerConfig.Enabled(true),
      minIntervalMinutes = AgentSchedulerConfig.MinIntervalMinutes(5),
      currencySources = AgentSchedulerConfig.CurrencySources(1),
      followupsPerSource = AgentSchedulerConfig.FollowupsPerSource(2),
      discoverPerQuery = AgentSchedulerConfig.DiscoverPerQuery(3),
      sweepCandidates = AgentSchedulerConfig.SweepCandidates(4),
      strategy = AgentSchedulerConfig.Strategy("goal-first"),
      agingHours = AgentSchedulerConfig.AgingHours(12),
      maxWaitHours = AgentSchedulerConfig.MaxWaitHours(48),
      minShare = AgentSchedulerConfig.MinShare(0.1),
      typeWeights = AgentSchedulerConfig.TypeWeights("review=5"),
      typePriorities = AgentSchedulerConfig.TypePriorities("compile=9"),
      gatheringSlots = AgentSchedulerConfig.GatheringSlots(2)
    )
    assertEquals(
      ManagerDefaultsViews.scheduler(c),
      SchedulerDefaults(
        strategy = SchedulerDefaults.Strategy("goal-first"),
        agingHours = SchedulerDefaults.AgingHours(12),
        maxWaitHours = SchedulerDefaults.MaxWaitHours(48),
        minShare = SchedulerDefaults.MinShare(0.1),
        builtInWeights = SchedulerDefaults.BuiltInWeights(AgentSchedulerConfig.DefaultWeights.value),
        typeWeights = SchedulerDefaults.TypeWeights("review=5"),
        builtInPriorities = SchedulerDefaults.BuiltInPriorities(AgentSchedulerConfig.DefaultPriorities.value),
        typePriorities = SchedulerDefaults.TypePriorities("compile=9"),
        gatheringSlots = SchedulerDefaults.GatheringSlots(2)
      )
    )
