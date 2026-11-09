package com.histmersion
package util.data.exchange

import java.util.UUID
import io.github.iltotore.iron.constraint.any.Pure

/** A record of the bundle that a kind's rules found to be one with a record here before anything is decided — an event
  * with the same dating and place, a place with the same geometry: what `merge apply` folds, and what the plan says
  * about it.
  */
trait PlanMerge:

  /** The fold carried out: the redirect, the line that replaces the record here, the count. */
  def fold(foreign: UUID, here: UUID, folds: PlanFolds): Unit

  /** The plan's line about it: `label` is the record's label here, `bundleLabel` the bundle's. */
  def info(label: PlanMerge.InfoLabel, bundleLabel: PlanMerge.InfoBundleLabel): PlanMerge.InfoText

object PlanMerge:
  type InfoText = InfoText.T
  object InfoText extends Newtype[String, Pure]
  type InfoLabel = InfoLabel.T
  object InfoLabel extends Newtype[String, Pure]
  type InfoBundleLabel = InfoBundleLabel.T
  object InfoBundleLabel extends Newtype[String, Pure]
