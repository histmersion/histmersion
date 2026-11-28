package com.histmersion
package api.research.workflows

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.api.{WireEnum, WireEnumValues}
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema

enum Severity(val wire: Severity.Wire) derives CanEqual:
  case Major extends Severity(Severity.Wire("major"))
  case Minor extends Severity(Severity.Wire("minor"))

object Severity:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  given WireEnumValues[Severity]:
    def all: Array[Severity] = values
    def wire(e: Severity): WireEnumValues.Wire = WireEnumValues.Wire.of(e.wire)
    def fromWire(s: WireEnumValues.Wire): Option[Severity] = values.find(e => WireEnumValues.Wire.of(e.wire) == s.lowered)
  given JsonValueCodec[Severity] = WireEnum.codec
  given Schema[Severity] = WireEnum.schema
