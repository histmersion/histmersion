package com.histmersion
package util.data.exchange

import java.util.UUID
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** A record of the bundle that a kind's rules found to be one with a record here before anything is decided — an event
  * with the same dating and place, a place with the same geometry: what `merge apply` folds, and what the plan says
  * about it.
  */
trait PlanMerge:

  /** The fold carried out: the redirect, the line that replaces the record here, the count. */
  def fold(foreign: UUID, here: UUID, folds: PlanFolds): Unit

  /** The plan's line about it: `label` is the record's label here, `bundleLabel` the bundle's. */
  def info(label: PlanMerge.InfoLabel, bundleLabel: PlanMerge.InfoBundleLabel): PlanMerge.InfoResult

object PlanMerge:
  type InfoResult = InfoResult.T
  object InfoResult extends RefinedType[String, Pure]
  type InfoLabel = InfoLabel.T
  object InfoLabel extends RefinedType[String, Pure]
  type InfoBundleLabel = InfoBundleLabel.T
  object InfoBundleLabel extends RefinedType[String, Pure]
