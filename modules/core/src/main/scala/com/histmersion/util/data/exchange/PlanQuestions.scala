package com.histmersion.util.data.exchange

import com.histmersion.api.IssueDetail
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.values.{BundleKind, DecisionKind}

import java.util.UUID

/** What a kind's rules of a plan can ask and add while the plan collects its questions about the rows, before any is
  * decided: besides a pair's questions, the bundle's lines as they came and in this collection's terms, which lines
  * would be written, and the merges a kind adds.
  */
trait PlanQuestions extends PairQuestions:

  /** The bundle's lines, as they came. */
  def bundle: BundleEntities

  /** The bundle's lines in this collection's uids and labels. */
  def localized: BundleEntities

  /** A uid the bundle's lines have, in this collection's terms. */
  def inLocalized(u: UUID): Boolean

  /** The bundle's uid of a uid here. */
  def bundleUidOf(u: UUID): UUID

  /** A line the rows' decisions write, before any question is answered. */
  def writesInitially(u: UUID): Boolean

  /** Whether a record of the bundle is already merged with one here. */
  def merged(foreign: UUID): Boolean

  /** A record of the bundle that is one with a record here. */
  def merge(foreign: UUID, here: UUID, how: PlanMerge): Unit

  /** The labels of `rows` that a different record has here, as questions (or merges, when `merge` finds them one). */
  def labelTaken[A](
      kind: BundleKind,
      rows: List[A],
      uid: A => UUID,
      label: A => String,
      owner: A => Option[UUID],
      what: String,
      options: List[DecisionKind],
      merge: (A, UUID) => Option[PlanMerge],
      detailOf: (A, UUID) => Option[IssueDetail]
  ): Unit
