package com.histmersion
package config

import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{FeaturePlace, movesTo}

/** A research area's watcher (`agents.managers.watch`, docs/research-process-managers.md §4 (c)): its runs at once, the
  * cron a new one gets (UTC), and the default daily cap of its OPT-IN auto-ingest of legal open-access works (a
  * watcher's own `autoIngestPerDay` wins; auto-ingest itself is off unless the watcher turns it on), and how many works
  * of the watchers' ingest backlog one managers' tick ingests at most (`drainPerTick`: each fetches a file, so a tick
  * takes only a few; the daily caps bound the day).
  */
@movesTo(FeaturePlace.LibraryWorkflows)
final case class WatchManagerConfig(
    maxRuns: WatchManagerConfig.MaxRuns,
    cron: WatchManagerConfig.Cron,
    autoIngestPerDay: WatchManagerConfig.AutoIngestPerDay,
    drainPerTick: WatchManagerConfig.DrainPerTick = WatchManagerConfig.DrainPerTick(3)
) derives ConfigReader

object WatchManagerConfig:
  type MaxRuns = MaxRuns.T
  object MaxRuns extends Newtype[Int, Pure]
  type Cron = Cron.T
  object Cron extends Newtype[String, Pure]
  type AutoIngestPerDay = AutoIngestPerDay.T
  object AutoIngestPerDay extends Newtype[Int, Pure]
  type DrainPerTick = DrainPerTick.T
  object DrainPerTick extends Newtype[Int, Pure]
