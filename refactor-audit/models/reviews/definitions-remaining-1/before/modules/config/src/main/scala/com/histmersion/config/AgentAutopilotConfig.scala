package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** The autopilot (docs/research-process-managers.md "As built: the autopilot"): ONE switch, kept in the database
  * (`histmersion agent autopilot on|off`), that makes the managers carry every research goal from planned to reviewed.
  * These are its settings while it is on:
  *
  *   - `ensureMinutes`: how often the supervisor makes sure every goal has its managers and moves the goal statuses on
  *     from the facts (also at once when it is turned on);
  *   - `ingestPerDay`: legal open-access works ALL watchers together may ingest per UTC day (0 = only each watcher's
  *     own cap); `watchIngestPerDay`: a watcher's own cap when the autopilot creates it;
  *   - `watchCron`, `seedCron`: the cron a watcher and a seeding manager it creates gets (an `@daily` / `@weekly` /
  *     `@monthly` is spread over the hours and days per goal, so 60 watchers do not fire at the same minute);
  *     `finishedWatchCron`: a reviewed goal's watcher;
  *   - `extendMinNew`: what the review manager's `extendMinNew` is set to (when it has none);
  *   - `backupHours`, `lintHours`: a backup (pg_dump, the configured pruning) and the safe `lint --fix` repairs every
  *     so many hours (0 = never), never while a backup, a vacuum or a restore runs;
  *   - the FOCUS (docs/research-process-managers.md "As built: the focus"): `maxActiveGoals` goals at most are active
  *     for model work at a time (0 = no cap; a person's override in the database wins); the enrich and review managers
  *     start `focusRatio` items of active goals to one of any other; a goal plan is approved by code only while its
  *     goal is active and its unfinished sub-goals plus the plan's new ones stay within `maxOpenSubGoals`; the status
  *     lists the next `focusQueueShown` waiting goals.
  */
final case class AgentAutopilotConfig(
    ensureMinutes: Int = 5,
    ingestPerDay: Int = 20,
    watchIngestPerDay: Int = 5,
    watchCron: String = "@weekly",
    seedCron: String = "@weekly",
    finishedWatchCron: String = "@monthly",
    extendMinNew: Int = 3,
    backupHours: Int = 24,
    lintHours: Int = 24,
    maxActiveGoals: Int = 8,
    focusRatio: Int = 3,
    maxOpenSubGoals: Int = 5,
    focusQueueShown: Int = 10,
    /** `ingestPerDay` scaled to the ingest backlog ([[AutopilotPolicy.scaledCap]]; 0 = fixed), at most the ceiling. */
    ingestBacklogShare: Double = 0,
    ingestCeiling: Int = 100,
    /** The UTC hour after which the day's approval digest is written to the worklog (-1 = never). */
    digestHour: Int = 7,
    /** The stall report's default: a goal with no progress in this many days is stalled. */
    stallDays: Int = 3,
    /** #3763: once a goal's articles are all compiled and reviewed, its next article is proposed — at most once per
      * this many hours after its last proposal settled (0 = never by itself; the seeding cron still fires).
      */
    nextArticleHours: Int = 24,
    /** Goal plans are offered (a plan-goal run started, an open goal plan decided by code) only while at most this many
      * goals with an approved plan are unfinished (0 = no limit).
      */
    maxUnfinishedGoals: Int = 10
) derives ConfigReader
