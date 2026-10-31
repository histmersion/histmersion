package com.histmersion.util.data.exchange

import com.histmersion.util.data.DataOwner
import com.histmersion.values.BundleKind

/** How a merge between two collections treats one kind of record: whether lines of other kinds can point at its
  * records (a plan looks them up as live here), and how the ledger names one. The matching, folding and decisions of
  * every kind are still one planner (`MergePlanner`), which each owner's slice splits into its own rules.
  */
trait MergeRules:
  def owner: DataOwner
  def kind: BundleKind

  /** SQL for the label of the record here whose uid is `l.local_uid`, as `merge ledger` shows it. */
  def ledgerLabel: Option[String] = None

  /** Lines of other kinds refer to its records by uid. */
  def referable: Boolean = false
