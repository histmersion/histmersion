package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** A research area's watcher (`agents.managers.watch`, docs/research-process-managers.md §4 (c)): its runs at once, the
  * cron a new one gets (UTC), and the default daily cap of its OPT-IN auto-ingest of legal open-access works (a
  * watcher's own `autoIngestPerDay` wins; auto-ingest itself is off unless the watcher turns it on), and how many works
  * of the watchers' ingest backlog one managers' tick ingests at most (`drainPerTick`: each fetches a file, so a tick
  * takes only a few; the daily caps bound the day).
  */
final case class WatchManagerConfig(maxRuns: WatchManagerConfig.MaxRuns, cron: WatchManagerConfig.Cron, autoIngestPerDay: WatchManagerConfig.AutoIngestPerDay, drainPerTick: WatchManagerConfig.DrainPerTick = WatchManagerConfig.DrainPerTick(3))
    derives ConfigReader

object WatchManagerConfig:
  type MaxRuns = MaxRuns.T
  object MaxRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Cron = Cron.T
  object Cron extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AutoIngestPerDay = AutoIngestPerDay.T
  object AutoIngestPerDay extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DrainPerTick = DrainPerTick.T
  object DrainPerTick extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
