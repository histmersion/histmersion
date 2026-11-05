package com.histmersion.domain.automation
enum ItemStatus(val wire: String):
  case Pending extends ItemStatus("pending")
  case Held extends ItemStatus("held")
  case Active extends ItemStatus("active")
  case Done extends ItemStatus("done")
  case Stopped extends ItemStatus("stopped")
  case Failed extends ItemStatus("failed")
  case Cancelled extends ItemStatus("cancelled")

  /** Settled: only a person (re-open, enqueue) brings it back. */
  def isFinal: Boolean = this == Done || this == Stopped || this == Failed || this == Cancelled

object ItemStatus:
  def parse(s: String): Option[ItemStatus] = values.find(_.wire == s.trim.toLowerCase)
