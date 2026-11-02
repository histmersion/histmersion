package com.histmersion.util.data.exchange

import com.histmersion.api.exchange.{MergeDecision, MergeTally}
import com.histmersion.domain.MergePlanner
import com.histmersion.util.data.exchange.BundleEntities

import java.util.UUID
import com.histmersion.util.data.exchange.Bundle

/** What a kind's rules of a plan can do once its questions are decided: fold a record of the bundle into one here, read
  * the decision in force for an issue, count what the apply does, and keep this collection's row of a line.
  */
trait PlanFolds:
  def input: MergePlanner.Input

  /** The bundle's lines in this collection's uids and labels. */
  def localized: BundleEntities

  /** A record of the bundle folded into one here: its uid becomes a redirect. */
  def fold(foreign: UUID, here: UUID, kind: Bundle.Kind): Unit

  /** The record here a record of the bundle is folded into, when it is. */
  def foldedTo(foreign: UUID): Option[UUID]

  /** A uid this collection's rows have. */
  def here(u: UUID): Boolean

  /** The uid here a uid of the bundle maps onto (a redirect or the ledger), else itself. */
  def toLocal(u: UUID): UUID

  /** The decision in force for an issue that applies. */
  def decisionIn(issue: String): Option[MergeDecision]

  /** What the apply does, counted. */
  def count(f: MergeTally => MergeTally): Unit

  /** This collection's row of a line stays as it is. */
  def keepLocal(foreign: UUID): Unit
