package com.histmersion.config

import hearth.kindlings.sconfigderivation.ConfigReader

/** A research area's watcher (`agents.managers.watch`, docs/research-process-managers.md §4 (c)): its runs at once, the
  * cron a new one gets (UTC), and the default daily cap of its OPT-IN auto-ingest of legal open-access works (a
  * watcher's own `autoIngestPerDay` wins; auto-ingest itself is off unless the watcher turns it on), and how many works
  * of the watchers' ingest backlog one managers' tick ingests at most (`drainPerTick`: each fetches a file, so a tick
  * takes only a few; the daily caps bound the day).
  */
final case class WatchManagerConfig(maxRuns: Int, cron: String, autoIngestPerDay: Int, drainPerTick: Int = 3)
    derives ConfigReader
