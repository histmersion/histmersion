package com.histmersion.util.data.exchange

import com.histmersion.api.exchange.Bundle

import com.histmersion.domain.{MergePlanner, MergeSettings}
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.DataOwner

import com.augustnagro.magnum.DbCon

/** How a merge between two collections treats one kind of record: whether lines of other kinds can point at its records
  * (a plan looks them up as live here), how the ledger names one, and what a plan must know of this collection for the
  * kind besides its rows.
  */
trait MergeRules:
  def owner: DataOwner
  def kind: Bundle.Kind

  /** SQL for the label of the record here whose uid is `l.local_uid`, as `merge ledger` shows it. */
  def ledgerLabel: Option[com.histmersion.util.data.exchange.LedgerRow.Label] = None

  /** Lines of other kinds refer to its records by uid. */
  def referable: Boolean = false

  /** The planner's rules of the kind, when its owner registers them (the matching, folding and questions of its
    * records); several kinds of one owner may share one.
    */
  def planning: Option[PlanRules] = None

  /** What the plan knows of this collection, with this kind's share added: the labels its records have here, the rows
    * here a bundle's record may be one with. `bundle` is the bundle's lines, `localized` the same in this collection's
    * uids; `in` holds this collection's rows (`local`) and what the other kinds gathered; `settings` are the plan's.
    */
  def gather(
      bundle: BundleEntities,
      localized: BundleEntities,
      in: MergePlanner.Input,
      settings: MergeSettings
  )(using DbCon): MergePlanner.Input =
    in

object MergeRules:
