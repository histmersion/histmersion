package com.histmersion.api.automation

import com.histmersion.api.WireEnumValues

enum ScheduleKind(val wire: String, val pipeline: Pipeline):
  case Currency extends ScheduleKind("currency", Pipeline.CurrencyCheck)
  case Acquisition extends ScheduleKind("acquisition", Pipeline.AcquisitionSweep)

object ScheduleKind:
  def parse(s: String): Option[ScheduleKind] = values.find(_.wire == s.trim.toLowerCase)
  given WireEnumValues[ScheduleKind]:
    def all: Array[ScheduleKind] = ScheduleKind.values
    def wire(e: ScheduleKind): String = e.wire
    def fromWire(s: String): Option[ScheduleKind] = parse(s)
