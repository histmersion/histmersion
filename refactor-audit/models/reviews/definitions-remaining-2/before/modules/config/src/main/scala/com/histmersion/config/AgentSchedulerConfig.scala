package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The crons and the goal sweeps. Since phase 3 a goal's schedules are its watcher (`watch:<goal>`) and every manager's
  * cron fires from the managers' tick (docs/research-process-managers.md, "As built (phase 3)"); the scheduler thread
  * and its `tick-seconds` are gone. `enabled = false` stops every manager's cron at once (they are also off whenever
  * agents are); a cron may not fire more often than every `minIntervalMinutes`; a currency check looks at up to
  * `currencySources` of a goal's sources and `followupsPerSource` citing papers each; an acquisition sweep asks
  * `discoverPerQuery` works per query and offers at most `sweepCandidates` to the ranking model.
  */
final case class AgentSchedulerConfig(
    enabled: Boolean,
    minIntervalMinutes: Int,
    currencySources: Int,
    followupsPerSource: Int,
    discoverPerQuery: Int,
    sweepCandidates: Int,
    /** The central scheduler ([[SchedulerPolicy]]; docs/research-goal-advancement.md "As built"): which strategy admits
      * the managers' work (`type-balanced` | `goal-first`; a person's choice saved in the database wins), one priority
      * level per `agingHours` waited (0 = no aging), an item waiting `maxWaitHours` goes next (0 = never), every job
      * type with work gets a start among the last ⌈1/`minShare`⌉ (0 = no minimum), the job types' weights
      * (`type-balanced`) and priorities ("type=n, …"), the runs one plan pool takes at once per call it serves
      * (`poolOvercommit` × its max-calls-in-flight), the places kept for goals still gathering their sources
      * (`gatheringSlots`), and how many recent starts the shares are counted over (`shareWindow`).
      */
    strategy: String = "type-balanced",
    agingHours: Int = 24,
    maxWaitHours: Int = 72,
    minShare: Double = 0.05,
    typeWeights: String = AgentSchedulerConfig.DefaultWeights,
    typePriorities: String = AgentSchedulerConfig.DefaultPriorities,
    poolOvercommit: Int = 2,
    gatheringSlots: Int = 1,
    shareWindow: Int = 100,
    /** Seed before the plan (#3762 proposal 5): goals with no source run the deterministic candidate search and queue
      * legal open-access ingests before their plan is approved — at most `seedGoalsPerTick` goals a tick (0 = off),
      * each at most once per `seedEveryHours`, at most `seedIngestPerGoal` works each.
      */
    seedGoalsPerTick: Int = 2,
    seedEveryHours: Int = 24,
    seedIngestPerGoal: Int = 5
) derives ConfigReader

object AgentSchedulerConfig:
  /** The job types' weights when none are configured: reviews, compiles and enrichment advance fastest, proposals,
    * plans and sweeps next, extensions, filters and currency checks last — never 0.
    */
  val DefaultWeights = "review=3, compile=3, enrich=3, propose=2, goal-plan=2, sweep=2, extend=1, filter=1, currency=1"

  /** The job types' priorities when none are configured: finish first (a review before a compile before a proposal …),
    * the order the focus used.
    */
  val DefaultPriorities =
    "review=9, compile=8, extend=7, filter=6, propose=5, goal-plan=4, enrich=3, sweep=2, currency=1"
