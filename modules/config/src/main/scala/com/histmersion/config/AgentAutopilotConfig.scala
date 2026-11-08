package com.histmersion
package config

import com.histmersion.config.NewtypeReaders.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure

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
    ensureMinutes: AgentAutopilotConfig.EnsureMinutes = AgentAutopilotConfig.EnsureMinutes(5),
    ingestPerDay: AgentAutopilotConfig.IngestPerDay = AgentAutopilotConfig.IngestPerDay(20),
    watchIngestPerDay: AgentAutopilotConfig.WatchIngestPerDay = AgentAutopilotConfig.WatchIngestPerDay(5),
    watchCron: AgentAutopilotConfig.WatchCron = AgentAutopilotConfig.WatchCron("@weekly"),
    seedCron: AgentAutopilotConfig.SeedCron = AgentAutopilotConfig.SeedCron("@weekly"),
    finishedWatchCron: AgentAutopilotConfig.FinishedWatchCron = AgentAutopilotConfig.FinishedWatchCron("@monthly"),
    extendMinNew: AgentAutopilotConfig.ExtendMinNew = AgentAutopilotConfig.ExtendMinNew(3),
    backupHours: AgentAutopilotConfig.BackupHours = AgentAutopilotConfig.BackupHours(24),
    lintHours: AgentAutopilotConfig.LintHours = AgentAutopilotConfig.LintHours(24),
    maxActiveGoals: AgentAutopilotConfig.MaxActiveGoals = AgentAutopilotConfig.MaxActiveGoals(8),
    focusRatio: AgentAutopilotConfig.FocusRatio = AgentAutopilotConfig.FocusRatio(3),
    maxOpenSubGoals: AgentAutopilotConfig.MaxOpenSubGoals = AgentAutopilotConfig.MaxOpenSubGoals(5),
    focusQueueShown: AgentAutopilotConfig.FocusQueueShown = AgentAutopilotConfig.FocusQueueShown(10),
    /** `ingestPerDay` scaled to the ingest backlog ([[AutopilotPolicy.scaledCap]]; 0 = fixed), at most the ceiling. */
    ingestBacklogShare: AgentAutopilotConfig.IngestBacklogShare = AgentAutopilotConfig.IngestBacklogShare(0.0),
    ingestCeiling: AgentAutopilotConfig.IngestCeiling = AgentAutopilotConfig.IngestCeiling(100),
    /** The UTC hour after which the day's approval digest is written to the worklog (-1 = never). */
    digestHour: AgentAutopilotConfig.DigestHour = AgentAutopilotConfig.DigestHour(7),
    /** The stall report's default: a goal with no progress in this many days is stalled. */
    stallDays: AgentAutopilotConfig.StallDays = AgentAutopilotConfig.StallDays(3),
    /** #3763: once a goal's articles are all compiled and reviewed, its next article is proposed — at most once per
      * this many hours after its last proposal settled (0 = never by itself; the seeding cron still fires).
      */
    nextArticleHours: AgentAutopilotConfig.NextArticleHours = AgentAutopilotConfig.NextArticleHours(24),
    /** Goal plans are offered (a plan-goal run started, an open goal plan decided by code) only while at most this many
      * goals with an approved plan are unfinished (0 = no limit).
      */
    maxUnfinishedGoals: AgentAutopilotConfig.MaxUnfinishedGoals = AgentAutopilotConfig.MaxUnfinishedGoals(10)
) derives ConfigReader

object AgentAutopilotConfig:
  type EnsureMinutes = EnsureMinutes.T
  object EnsureMinutes extends Newtype[Int, Pure]
  type IngestPerDay = IngestPerDay.T
  object IngestPerDay extends Newtype[Int, Pure]
  type WatchIngestPerDay = WatchIngestPerDay.T
  object WatchIngestPerDay extends Newtype[Int, Pure]
  type WatchCron = WatchCron.T
  object WatchCron extends Newtype[String, Pure]
  type SeedCron = SeedCron.T
  object SeedCron extends Newtype[String, Pure]
  type FinishedWatchCron = FinishedWatchCron.T
  object FinishedWatchCron extends Newtype[String, Pure]
  type ExtendMinNew = ExtendMinNew.T
  object ExtendMinNew extends Newtype[Int, Pure]
  type BackupHours = BackupHours.T
  object BackupHours extends Newtype[Int, Pure]
  type LintHours = LintHours.T
  object LintHours extends Newtype[Int, Pure]
  type MaxActiveGoals = MaxActiveGoals.T
  object MaxActiveGoals extends Newtype[Int, Pure]
  type FocusRatio = FocusRatio.T
  object FocusRatio extends Newtype[Int, Pure]
  type MaxOpenSubGoals = MaxOpenSubGoals.T
  object MaxOpenSubGoals extends Newtype[Int, Pure]
  type FocusQueueShown = FocusQueueShown.T
  object FocusQueueShown extends Newtype[Int, Pure]
  type IngestBacklogShare = IngestBacklogShare.T
  object IngestBacklogShare extends Newtype[Double, Pure]
  type IngestCeiling = IngestCeiling.T
  object IngestCeiling extends Newtype[Int, Pure]
  type DigestHour = DigestHour.T
  object DigestHour extends Newtype[Int, Pure]
  type StallDays = StallDays.T
  object StallDays extends Newtype[Int, Pure]
  type NextArticleHours = NextArticleHours.T
  object NextArticleHours extends Newtype[Int, Pure]
  type MaxUnfinishedGoals = MaxUnfinishedGoals.T
  object MaxUnfinishedGoals extends Newtype[Int, Pure]
