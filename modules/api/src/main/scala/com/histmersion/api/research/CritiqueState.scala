package com.histmersion
package api.research
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.GeoPick

enum CritiqueState(val wire: CritiqueState.Wire) derives CanEqual:
  case Addressed extends CritiqueState(CritiqueState.Wire("addressed"))
  case Reasoned extends CritiqueState(CritiqueState.Wire("reasoned"))
  case Open extends CritiqueState(CritiqueState.Wire("open"))

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object CritiqueState:
  export GeoPick.Wire

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[CritiqueState] = values.find(_.wire == Wire((s).value.trim.toLowerCase))
  given WireEnumValues[CritiqueState]:
    def all: Array[CritiqueState] = values
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: CritiqueState): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: WireEnumValues.Wire): Option[CritiqueState] = parse(Wire((s).value))
  given JsonValueCodec[CritiqueState] = WireEnum.codec
  given Schema[CritiqueState] = WireEnum.schema
