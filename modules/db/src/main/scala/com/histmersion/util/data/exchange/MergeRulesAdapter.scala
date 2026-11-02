package com.histmersion.util.data.exchange

import com.histmersion.util.data.DataOwner
import com.histmersion.values.BundleKind

/** Today's merge rules of the owners that do not register their own yet (the catalogue, the media, the library and
  * research do), by owner.
  */
object MergeRulesAdapter:

  final private case class Adapted(
      owner: DataOwner,
      kind: BundleKind,
      override val ledgerLabel: Option[String],
      override val referable: Boolean
  ) extends MergeRules

  private def kind(wire: String): BundleKind =
    BundleKind.fromWire(wire).getOrElse(throw IllegalArgumentException(s"no kind of record '$wire'"))

  private def label(column: String, table: String) = Some(s"SELECT $column FROM $table WHERE uid = l.local_uid")

  /** Every kind, by owner. */
  val all: List[MergeRules] = List(
    Adapted(DataOwner("automation"), kind("goal"), label("slug", "goals"), referable = true),
    Adapted(DataOwner("automation"), kind("queue-item"), label("left(text, 60)", "queue_items"), referable = false)
  )

  def of(owner: DataOwner): List[MergeRules] = all.filter(_.owner == owner)
