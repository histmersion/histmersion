package com.histmersion
package domain.research.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.api.WireEnum
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.Newtype

enum Severity(val wire: Severity.Wire) derives CanEqual:
  case Major extends Severity(Severity.Wire("major"))
  case Minor extends Severity(Severity.Wire("minor"))

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object Severity:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[Severity] = values.find(_.wire.value == s.value.trim.toLowerCase)
  given WireEnumValues[Severity]:
    def all: Array[Severity] = values
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: Severity): WireEnumValues.Wire = WireEnumValues.Wire(e.wire.value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: WireEnumValues.Wire): Option[Severity] = parse(Wire(s.value))
  given JsonValueCodec[Severity] = WireEnum.codec
  given Schema[Severity] = WireEnum.schema
