package com.histmersion
package api.automation

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
enum ItemStatus(val wire: ItemStatus.Wire):
  case Pending extends ItemStatus(ItemStatus.Wire("pending"))
  case Held extends ItemStatus(ItemStatus.Wire("held"))
  case Active extends ItemStatus(ItemStatus.Wire("active"))
  case Done extends ItemStatus(ItemStatus.Wire("done"))
  case Stopped extends ItemStatus(ItemStatus.Wire("stopped"))
  case Failed extends ItemStatus(ItemStatus.Wire("failed"))
  case Cancelled extends ItemStatus(ItemStatus.Wire("cancelled"))

  /** Settled: only a person (re-open, enqueue) brings it back. */
  def isFinal: Boolean = (this == Done || this == Stopped || this == Failed || this == Cancelled)

object ItemStatus:
  type Wire = Wire.T
  object Wire extends RefinedType[String, Pure]


  def parse(s: Wire): Option[ItemStatus] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
