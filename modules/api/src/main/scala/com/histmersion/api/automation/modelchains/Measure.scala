package com.histmersion
package api.automation.modelchains

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

enum Measure(val wire: Measure.Wire) derives CanEqual:
  case Tokens extends Measure(Measure.Wire("tokens"))
  case Usd extends Measure(Measure.Wire("usd"))

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object Measure:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[Measure] = values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))
  given WireEnumValues[Measure]:
    def all: Array[Measure] = values
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: Measure): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: WireEnumValues.Wire): Option[Measure] = parse(Wire((s).value))
  given com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[Measure] = WireEnum.codec
  given Schema[Measure] = WireEnum.schema
