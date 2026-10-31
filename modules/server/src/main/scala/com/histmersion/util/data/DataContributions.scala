package com.histmersion.util.data

import com.histmersion.util.data.checks.CheckRule
import com.histmersion.util.data.exchange.{CarriedKind, MergeRules}
import com.histmersion.util.data.mirror.MirrorExport
import com.histmersion.util.data.maintenance.{CacheSpec, MaintenanceTask}
import com.histmersion.util.storage.Location

/** The registry: every owner's contribution, in the order they were registered, which is the order every operation
  * runs them in. An owner contributes once and a table has one owner.
  */
final class DataContributions private (val all: List[DataContribution]):
  def owners: List[DataOwner] = all.map(_.owner)
  def tables: List[(DataOwner, OwnedTable)] = all.flatMap(c => c.tables.map(c.owner -> _))
  def stores: List[(DataOwner, Location)] = all.flatMap(c => c.stores.map(c.owner -> _))
  def carried: List[CarriedKind] = all.flatMap(_.carried)
  def mergeRules: List[MergeRules] = all.flatMap(_.mergeRules)
  def mirror: List[MirrorExport] = all.flatMap(_.mirror)
  def caches: List[CacheSpec] = all.flatMap(_.caches)
  def maintenance: List[MaintenanceTask] = all.flatMap(_.maintenance)
  def checks: List[CheckRule] = all.flatMap(_.checks)

  /** The owner of a table, when one registered it. */
  def ownerOf(table: TableName): Option[DataOwner] = tables.collectFirst { case (o, t) if t.name == table => o }

object DataContributions:
  /** The registry, or what makes it inconsistent: an owner registered twice, a table claimed by two owners. */
  def of(contributions: List[DataContribution]): Either[List[String], DataContributions] =
    val owners = contributions.map(_.owner.value)
    val twice = owners.diff(owners.distinct).distinct.map(o => s"owner $o is registered twice")
    val claims = contributions.flatMap(c => c.tables.map(t => t.name.value -> c.owner.value))
    val shared = claims
      .groupMap(_._1)(_._2)
      .toList
      .sortBy(_._1)
      .collect { case (t, os) if os.size > 1 => s"table $t is claimed by ${os.mkString(", ")}" }
    Either.cond(twice.isEmpty && shared.isEmpty, DataContributions(contributions), twice ++ shared)
