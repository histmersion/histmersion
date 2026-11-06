package com.histmersion.domain.automation

import com.histmersion.WireEnumValues
enum ScheduleKind(val wire: ScheduleKind.Wire, val pipeline: Pipeline):
  case Currency extends ScheduleKind(_root_.com.histmersion.domain.automation.ScheduleKind.Wire("currency"), Pipeline.CurrencyCheck)
  case Acquisition extends ScheduleKind(_root_.com.histmersion.domain.automation.ScheduleKind.Wire("acquisition"), Pipeline.AcquisitionSweep)

object ScheduleKind:
  type Wire = Wire.T
  object Wire extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ParseS = ParseS.T
  object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WireResult = WireResult.T
  object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FromWireS = FromWireS.T
  object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def parse(s: com.histmersion.domain.automation.ScheduleKind.ParseS): Option[ScheduleKind] = values.find(_.wire == _root_.com.histmersion.domain.automation.ScheduleKind.Wire((s).value.trim.toLowerCase))
  given WireEnumValues[ScheduleKind]:
    def all: Array[ScheduleKind] = ScheduleKind.values
    def wire(e: ScheduleKind): _root_.com.histmersion.WireEnumValues.WireResult = e.wire
    def fromWire(s: _root_.com.histmersion.WireEnumValues.FromWireS): _root_.scala.Option[ScheduleKind] = parse(s)
