package com.histmersion.domain.automation
enum ItemStatus(val wire: ItemStatus.Wire):
  case Pending extends ItemStatus(_root_.com.histmersion.domain.automation.ItemStatus.Wire("pending"))
  case Held extends ItemStatus(_root_.com.histmersion.domain.automation.ItemStatus.Wire("held"))
  case Active extends ItemStatus(_root_.com.histmersion.domain.automation.ItemStatus.Wire("active"))
  case Done extends ItemStatus(_root_.com.histmersion.domain.automation.ItemStatus.Wire("done"))
  case Stopped extends ItemStatus(_root_.com.histmersion.domain.automation.ItemStatus.Wire("stopped"))
  case Failed extends ItemStatus(_root_.com.histmersion.domain.automation.ItemStatus.Wire("failed"))
  case Cancelled extends ItemStatus(_root_.com.histmersion.domain.automation.ItemStatus.Wire("cancelled"))

  /** Settled: only a person (re-open, enqueue) brings it back. */
  def isFinal: com.histmersion.domain.automation.ItemStatus.IsFinalResult = _root_.com.histmersion.domain.automation.ItemStatus.IsFinalResult(this == Done || this == Stopped || this == Failed || this == Cancelled)

object ItemStatus:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type IsFinalResult = IsFinalResult.T
  object IsFinalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.domain.automation.ItemStatus.ParseS): Option[ItemStatus] = values.find(_.wire == (s).value.trim.toLowerCase)
