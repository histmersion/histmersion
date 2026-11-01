package com.histmersion.util.data.exchange

import java.util.UUID

/** A record of the bundle that a kind's rules found to be one with a record here before anything is decided — an event
  * with the same dating and place, a place with the same geometry: what `merge apply` folds, and what the plan says
  * about it.
  */
trait PlanMerge:

  /** The fold carried out: the redirect, the line that replaces the record here, the count. */
  def fold(foreign: UUID, here: UUID, folds: PlanFolds): Unit

  /** The plan's line about it: `label` is the record's label here, `bundleLabel` the bundle's. */
  def info(label: String, bundleLabel: String): String
