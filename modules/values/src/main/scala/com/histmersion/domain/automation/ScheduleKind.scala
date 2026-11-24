package com.histmersion
package domain.automation

import com.histmersion.WireEnumValues
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}
enum ScheduleKind(val wire: ScheduleKind.Wire, val pipeline: Pipeline) derives CanEqual:
  case Currency extends ScheduleKind(ScheduleKind.Wire("currency"), Pipeline.CurrencyCheck)
  case Acquisition extends ScheduleKind(ScheduleKind.Wire("acquisition"), Pipeline.AcquisitionSweep)

object ScheduleKind:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[ScheduleKind] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
  given WireEnumValues[ScheduleKind]:
    def all: Array[ScheduleKind] = values
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: ScheduleKind): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: WireEnumValues.Wire): Option[ScheduleKind] = parse(Wire((s).value))
