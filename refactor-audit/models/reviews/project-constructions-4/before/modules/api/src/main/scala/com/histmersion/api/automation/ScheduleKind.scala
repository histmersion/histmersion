package com.histmersion.api.automation

import com.histmersion.api.WireEnumValues

enum ScheduleKind(val wire: ScheduleKind.Wire, val pipeline: Pipeline):
  case Currency extends ScheduleKind(_root_.com.histmersion.api.automation.ScheduleKind.Wire("currency"), Pipeline.CurrencyCheck)
  case Acquisition extends ScheduleKind(_root_.com.histmersion.api.automation.ScheduleKind.Wire("acquisition"), Pipeline.AcquisitionSweep)

object ScheduleKind:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.api.automation.ScheduleKind.ParseS): Option[ScheduleKind] = values.find(_.wire == (s).value.trim.toLowerCase)
  given WireEnumValues[ScheduleKind]:
    def all: Array[ScheduleKind] = ScheduleKind.values
    def wire(e: ScheduleKind): com.histmersion.api.automation.ScheduleKind.WireResult = e.wire
    def fromWire(s: com.histmersion.api.automation.ScheduleKind.FromWireS): Option[ScheduleKind] = parse(s)
