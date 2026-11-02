package com.histmersion.util.data.exchange

import com.histmersion.api.MergeIssue
import com.histmersion.domain.MergePlanner
import com.histmersion.values.DecisionKind

import java.util.UUID

/** What a kind's rules can ask and add while a plan looks at what a pair of sources owns: the input, the revs, which
  * uids are here, and the issues the kind adds.
  */
trait PairQuestions:
  def input: MergePlanner.Input

  /** The bundle's rev of one of its records. */
  def rightRev(u: UUID): String

  /** The rev a record has here. */
  def localRev(u: UUID): String

  /** A uid this collection's rows have. */
  def here(u: UUID): Boolean

  /** The uid here a uid of the bundle maps onto (a redirect or the ledger), else itself. */
  def toLocal(u: UUID): UUID

  /** A question: the issue, and the decisions an import can carry out itself. */
  def ask(issue: MergeIssue, importable: Set[DecisionKind]): Unit

  /** A line of the plan's notes. */
  def info(line: String): Unit
