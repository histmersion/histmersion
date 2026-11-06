package com.histmersion.util.data.exchange

import com.histmersion.api.exchange.MergeDecision

import com.histmersion.api.exchange.MergeIssue
import com.histmersion.domain.MergePlanner

import java.util.UUID

/** What a kind's rules can ask and add while a plan looks at what a pair of sources owns: the input, the revs, which
  * uids are here, and the issues the kind adds.
  */
trait PairQuestions:
  def input: MergePlanner.Input

  /** The bundle's rev of one of its records. */
  def rightRev(u: UUID): com.histmersion.util.data.exchange.PairQuestions.RightRevResult

  /** The rev a record has here. */
  def localRev(u: UUID): com.histmersion.util.data.exchange.PairQuestions.LocalRevResult

  /** A uid this collection's rows have. */
  def here(u: UUID): Boolean

  /** The uid here a uid of the bundle maps onto (a redirect or the ledger), else itself. */
  def toLocal(u: UUID): UUID

  /** A question: the issue, and the decisions an import can carry out itself. */
  def ask(issue: MergeIssue, importable: Set[MergeDecision.Kind]): Unit

  /** A line of the plan's notes. */
  def info(line: com.histmersion.util.data.exchange.PairQuestions.InfoLine): Unit

object PairQuestions:
  type RightRevResult = RightRevResult.T
  object RightRevResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LocalRevResult = LocalRevResult.T
  object LocalRevResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type InfoLine = InfoLine.T
  object InfoLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
