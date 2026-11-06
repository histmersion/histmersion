package com.histmersion.api.automation
enum ItemStatus(val wire: ItemStatus.Wire):
  case Pending extends ItemStatus(_root_.com.histmersion.api.automation.ItemStatus.Wire("pending"))
  case Held extends ItemStatus(_root_.com.histmersion.api.automation.ItemStatus.Wire("held"))
  case Active extends ItemStatus(_root_.com.histmersion.api.automation.ItemStatus.Wire("active"))
  case Done extends ItemStatus(_root_.com.histmersion.api.automation.ItemStatus.Wire("done"))
  case Stopped extends ItemStatus(_root_.com.histmersion.api.automation.ItemStatus.Wire("stopped"))
  case Failed extends ItemStatus(_root_.com.histmersion.api.automation.ItemStatus.Wire("failed"))
  case Cancelled extends ItemStatus(_root_.com.histmersion.api.automation.ItemStatus.Wire("cancelled"))

  /** Settled: only a person (re-open, enqueue) brings it back. */
  def isFinal: Boolean = (this == Done || this == Stopped || this == Failed || this == Cancelled)

object ItemStatus:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  def parse(s: com.histmersion.api.automation.ItemStatus.Wire): Option[ItemStatus] = values.find(_.wire == _root_.com.histmersion.api.automation.ItemStatus.Wire((s).value.trim.toLowerCase))
