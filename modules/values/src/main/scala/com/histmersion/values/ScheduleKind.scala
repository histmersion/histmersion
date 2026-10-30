package com.histmersion.values

/** What a goal's schedule starts: a currency check of its sources, or an acquisition sweep of its queries.
  */
enum ScheduleKind(val wire: String, val pipeline: Pipeline):
  case Currency extends ScheduleKind("currency", Pipeline.CurrencyCheck)
  case Acquisition extends ScheduleKind("acquisition", Pipeline.AcquisitionSweep)

object ScheduleKind:
  def parse(s: String): Option[ScheduleKind] = values.find(_.wire == s.trim.toLowerCase)
  given WireEnumValues[ScheduleKind]:
    def all: Array[ScheduleKind] = ScheduleKind.values
    def wire(e: ScheduleKind): String = e.wire
    def fromWire(s: String): Option[ScheduleKind] = parse(s)
