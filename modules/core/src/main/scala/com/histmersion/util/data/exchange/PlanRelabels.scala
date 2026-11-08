package com.histmersion
package util.data.exchange

import com.histmersion.domain.MergePlanner
import com.histmersion.util.data.exchange.BundleEntities

import java.util.UUID
import io.github.iltotore.iron.constraint.any.Pure

/** What a kind's rules of a plan see when the bundle's labels a different record has here are given new ones: the rows
  * as first decided, the bundle's lines in this collection's terms, and where the plan says what was relabelled.
  */
trait PlanRelabels:

  /** The rows as first decided, before any fold. */
  def rows: List[MergePlanner.Row]

  /** The bundle's lines in this collection's uids and labels. */
  def localized: BundleEntities

  /** Whether the line of a bundle uid is written, as first decided. */
  def writesInitially(foreign: UUID): Boolean

  /** A line of the plan's notes. */
  def info(line: PlanRelabels.InfoLine): Unit

  /** A line of what the plan relabels. */
  def relabelled(line: PlanRelabels.RelabelledLine): Unit

object PlanRelabels:
  type InfoLine = InfoLine.T
  object InfoLine extends Newtype[String, Pure]
  type RelabelledLine = RelabelledLine.T
  object RelabelledLine extends Newtype[String, Pure]
