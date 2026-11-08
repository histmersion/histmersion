package com.histmersion
package domain.automation

import com.histmersion.WireEnumValues
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
enum ScheduleKind(val wire: ScheduleKind.Wire, val pipeline: Pipeline):
  case Currency extends ScheduleKind(ScheduleKind.Wire("currency"), Pipeline.CurrencyCheck)
  case Acquisition extends ScheduleKind(ScheduleKind.Wire("acquisition"), Pipeline.AcquisitionSweep)

object ScheduleKind:
  type Wire = Wire.T
  object Wire extends RefinedType[String, Pure]


  def parse(s: Wire): Option[ScheduleKind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
  given WireEnumValues[ScheduleKind]:
    def all: Array[ScheduleKind] = values
    def wire(e: ScheduleKind): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
    def fromWire(s: WireEnumValues.Wire): Option[ScheduleKind] = parse(Wire((s).value))
