package com.histmersion.domain

import com.histmersion.api.automation.Managers.{ManagerConfig, Sweep}

/** A research area's watcher when it saves no settings of its own (`agents.managers.watch`): its runs at once and the
  * daily cap of its opt-in auto-ingest.
  */
final case class WatchManagerDefaults(maxRuns: WatchManagerDefaults.MaxRuns, autoIngestPerDay: WatchManagerDefaults.AutoIngestPerDay):

  /** A watcher's settings when it saves none: every sweep, auto-ingest off, its cap. */
  def managerDefaults(m: ManagerDefaults): ManagerConfig =
    ManagerConfig(
      maxRuns = Some(maxRuns),
      maxOpen = Some(2 * maxRuns),
      maxFailures = Some(m.maxFailures),
      sweeps = Some(Sweep.values.toList.map(_.wire)),
      autoIngest = Some(false),
      autoIngestPerDay = Some(autoIngestPerDay),
      dailyUsd = ManagerPolicy.dailyCap(ManagerConfig(), m)
    )

  /** How many legal open-access works a watcher's next acquisition sweep may ingest: none unless it opted in
    * (`autoIngest`, default off), else what is left of its daily cap after the ones it ingested today.
    */
  def ingestAllowance(c: ManagerConfig, ingestedToday: com.histmersion.domain.WatchManagerDefaults.IngestAllowanceIngestedToday): com.histmersion.domain.WatchManagerDefaults.IngestAllowanceResult =
    if !c.autoIngest.contains(true) then 0
    else math.max(0, c.autoIngestPerDay.getOrElse(autoIngestPerDay) - math.max(0, ingestedToday))

object WatchManagerDefaults:
  type IngestAllowanceResult = IngestAllowanceResult.T
  object IngestAllowanceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IngestAllowanceIngestedToday = IngestAllowanceIngestedToday.T
  object IngestAllowanceIngestedToday extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type MaxRuns = MaxRuns.T
  object MaxRuns extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AutoIngestPerDay = AutoIngestPerDay.T
  object AutoIngestPerDay extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
