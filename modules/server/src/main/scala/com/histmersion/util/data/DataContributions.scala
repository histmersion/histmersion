package com.histmersion
package util.data
import com.histmersion.util.problem.{Legacy, Problem}

import com.histmersion.util.data.backup.BackupShare
import com.histmersion.util.data.checks.CheckRule
import com.histmersion.util.data.exchange.{CarriedKind, MergeRules}
import com.histmersion.util.data.maintenance.{CacheSpec, MaintenanceTask}
import com.histmersion.util.storage.Location
import com.histmersion.util.data.mirror.Mirror
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** The registry: every owner's contribution, in the order they were registered, which is the order every operation runs
  * them in. An owner contributes once and a table has one owner.
  */
final class DataContributions private (val all: List[DataContribution]):
  def owners: List[DataOwner] = all.map(_.owner)
  def tables: List[(DataOwner, OwnedTable)] = all.flatMap(c => c.tables.map(c.owner -> _))
  def stores: List[(DataOwner, Location)] = all.flatMap(c => c.stores.map(c.owner -> _))
  def carried: List[CarriedKind] = all.flatMap(_.carried)
  def mergeRules: List[MergeRules] = all.flatMap(_.mergeRules)
  def mirror: List[Mirror.Export] = all.flatMap(_.mirror)
  def caches: List[CacheSpec] = all.flatMap(_.caches)
  def maintenance: List[MaintenanceTask] = all.flatMap(_.maintenance)
  def checks: List[CheckRule] = all.flatMap(_.checks)
  def backups: List[(DataOwner, BackupShare)] = all.map(c => c.owner -> c.backup)

  /** The registered piece of upkeep called `name`. */
  def task(name: MaintenanceTask.Name): Option[MaintenanceTask] = maintenance.find(_.name == name)

  /** The owner of a table, when one registered it. */
  def ownerOf(table: TableName): Option[DataOwner] = tables.collectFirst { case (o, t) if t.name == table => o }

object DataContributions:

  /** The registry, or what makes it inconsistent: an owner registered twice, a table claimed by two owners. */

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  def of(contributions: List[DataContribution]): Either[List[Problem], DataContributions] =
    val owners = contributions.map(_.owner.value)
    val twice =
      owners.diff(owners.distinct).distinct.map(o => Problem.Unclassified(Legacy.Text(s"owner $o is registered twice")))
    val claims = contributions.flatMap(c => c.tables.map(t => t.name.value -> c.owner.value))
    val shared = claims
      .groupMap(_._1)(_._2)
      .toList
      .sortBy(_._1)
      .collect {
        case (t, os) if os.size > 1 => Problem.Unclassified(Legacy.Text(s"table $t is claimed by ${os.mkString(", ")}"))
      }
    Either.cond(twice.isEmpty && shared.isEmpty, DataContributions(contributions), twice ++ shared)
