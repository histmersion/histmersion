package com.histmersion
package util.data

import com.histmersion.util.data.backup.BackupShare
import com.histmersion.util.data.checks.CheckRule
import com.histmersion.util.data.diagnostics.DataFigures
import com.histmersion.util.data.exchange.{CarriedKind, MergeRules}
import com.histmersion.util.data.maintenance.{CacheSpec, MaintenanceTask}
import com.histmersion.util.storage.Location
import com.augustnagro.magnum.DbCon
import com.histmersion.util.data.mirror.Mirror

/** What an owner says about the data it holds; data management runs every operation over all of them from one place.
  * Every member but the owner, its tables and its figures says "nothing of that kind" unless the owner overrides it.
  */
trait DataContribution:
  def owner: DataOwner

  /** Its tables: sized, vacuumed and checked as its own. */
  def tables: List[OwnedTable]

  /** Its file stores: backed up, sized and pruned. */
  def stores: List[Location] = Nil

  /** What a backup takes of it beside the dump, and what a restore must not drop unasked. */
  def backup: BackupShare = BackupShare.none

  /** The kinds of record a bundle carries for it: their rows out and in, their tombstones. */
  def carried: List[CarriedKind] = Nil

  /** Per kind of record: how a merge matches, folds and decides it. */
  def mergeRules: List[MergeRules] = Nil

  /** The Markdown mirror's notes of its records. */
  def mirror: List[Mirror.Export] = Nil

  /** What it keeps derived from its rows: how big it is, when it is invalidated. */
  def caches: List[CacheSpec] = Nil

  /** Its upkeep: rebuilds, prunes, reindexing. */
  def maintenance: List[MaintenanceTask] = Nil

  /** Its lint rules and health checks, with their safe fixes. */
  def checks: List[CheckRule] = Nil

  /** Its counts and sizes, for the one view of all the data. */
  def figures(using DbCon): DataFigures
