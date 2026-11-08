package com.histmersion
package domain

import com.histmersion.api.automation.Managers.{ManagerConfig, Sweep}
import io.github.iltotore.iron.constraint.any.Pure

/** A research area's watcher when it saves no settings of its own (`agents.managers.watch`): its runs at once and the
  * daily cap of its opt-in auto-ingest.
  */
final case class WatchManagerDefaults(
    maxRuns: WatchManagerDefaults.MaxRuns,
    autoIngestPerDay: WatchManagerDefaults.AutoIngestPerDay
):

  /** A watcher's settings when it saves none: every sweep, auto-ingest off, its cap. */
  def managerDefaults(m: ManagerDefaults): ManagerConfig =
    ManagerConfig(
      maxRuns = Some(ManagerConfig.MaxRuns(maxRuns.value)),
      maxOpen = Some(ManagerConfig.MaxOpen(2 * maxRuns.value)),
      maxFailures = Some(ManagerConfig.MaxFailures(m.maxFailures.value)),
      sweeps = Some(Sweep.values.toList.map(_.wire)),
      autoIngest = Some(ManagerConfig.AutoIngest(false)),
      autoIngestPerDay = Some(ManagerConfig.AutoIngestPerDay(autoIngestPerDay.value)),
      dailyUsd = ManagerPolicy.dailyCap(ManagerConfig(), m)
    )

  /** How many legal open-access works a watcher's next acquisition sweep may ingest: none unless it opted in
    * (`autoIngest`, default off), else what is left of its daily cap after the ones it ingested today.
    */
  def ingestAllowance(
      c: ManagerConfig,
      ingestedToday: WatchManagerDefaults.IngestedToday
  ): WatchManagerDefaults.IngestAllowance =
    if !c.autoIngest.contains(ManagerConfig.AutoIngest(true)) then WatchManagerDefaults.IngestAllowance(0)
    else
      WatchManagerDefaults.IngestAllowance(
        math.max(0, c.autoIngestPerDay.fold(autoIngestPerDay.value)(_.value) - math.max(0, ingestedToday.value))
      )

object WatchManagerDefaults:
  type IngestAllowance = IngestAllowance.T
  object IngestAllowance extends Newtype[Int, Pure]
  type IngestedToday = IngestedToday.T
  object IngestedToday extends Newtype[Int, Pure]

  type MaxRuns = MaxRuns.T
  object MaxRuns extends Newtype[Int, Pure]
  type AutoIngestPerDay = AutoIngestPerDay.T
  object AutoIngestPerDay extends Newtype[Int, Pure]
