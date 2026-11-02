package com.histmersion.util.data.exchange

import com.histmersion.api.*
import com.histmersion.api.given
import com.histmersion.db.{BundleImport, BundleRepo}
import com.histmersion.util.data.{DataOwner, TableName}
import com.histmersion.values.BundleKind

import hearth.kindlings.optics.syntax.*

/** Today's carried kinds of the owners that do not register their own yet (the catalogue, the media, the library and
  * research do), by owner: the lines through `BundleRepo`'s reads, the rows in through the steps of
  * `BundleImport.Writer`.
  *
  * The ranks are the import's dependency order: tombstones first (a deleted row makes room for one created in its
  * place); every rename before the rows that carry the new label; goals before their aliases.
  */
object CarriedKindsAdapter:

  private val Automation = DataOwner("automation")
  private val Identity = DataOwner("util.identity")

  private def rows(rank: Int)(write: BundleImport.Writer => Unit) = RowsIn(ImportStage.Rows, ImportRank(rank), write)

  /** A kind of record, by its wire name. */
  private def kind(wire: String): BundleKind =
    BundleKind.fromWire(wire).getOrElse(throw IllegalArgumentException(s"no kind of record '$wire'"))

  /** The entry of a set records own, by its file's stem. */
  private def set(stem: String): BundleEntry = BundleEntry(s"entities/$stem.ndjson")

  private val goals = CarriedKind.records[BundleGoal](
    Automation,
    kind("goal"),
    TableName("goals"),
    List(rows(42)(_.goalRenames()), rows(48)(_.goals()))
  )(scope => BundleRepo.goals(scope).toList)(_.updatedAt, (es, ls) => es.modify(_.goals).setTo(ls))

  private val queue = CarriedKind.records[BundleQueueItem](
    Automation,
    kind("queue-item"),
    TableName("queue_items"),
    List(rows(50)(_.queueItems()))
  )(scope => BundleRepo.queue(scope).toList)(_.updatedAt, (es, ls) => es.modify(_.queue).setTo(ls))

  private val goalAliases =
    CarriedKind.owned[BundleGoalAlias](Automation, set("goal-aliases"), List(rows(49)(_.goalAliases())))(scope =>
      BundleRepo.goalAliases(scope).toList
    )(a => Some(a.createdAt), (es, ls) => es.modify(_.goalAliases).setTo(ls))

  private val tombstones =
    CarriedKind.owned[BundleTombstone](Identity, set("tombstones"), List(rows(0)(_.tombstones())))(scope =>
      BundleRepo.tombstones(scope).toList
    )(t => Some(t.deletedAt), (es, ls) => es.modify(_.tombstones).setTo(ls))

  /** Every kind, by owner. */
  val all: List[CarriedKind] = List(goals, queue, goalAliases, tombstones)

  def of(owner: DataOwner): List[CarriedKind] = all.filter(_.owner == owner)
