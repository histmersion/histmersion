package com.histmersion
package api.research.workflows

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.api.{WireEnum, WireEnumValues}
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema

enum Reviewer(val wire: Reviewer.Wire) derives CanEqual:
  case Mechanical extends Reviewer(Reviewer.Wire("mechanical"))
  case Rules extends Reviewer(Reviewer.Wire("rules"))
  case ColdReader extends Reviewer(Reviewer.Wire("cold-reader"))
  case Skeptic extends Reviewer(Reviewer.Wire("skeptic"))

object Reviewer:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  given WireEnumValues[Reviewer]:
    def all: Array[Reviewer] = values
    def wire(e: Reviewer): WireEnumValues.Wire = WireEnumValues.Wire.of(e.wire)
    def fromWire(s: WireEnumValues.Wire): Option[Reviewer] = values.find(e => WireEnumValues.Wire.of(e.wire) == s.lowered)
  given JsonValueCodec[Reviewer] = WireEnum.codec
  given Schema[Reviewer] = WireEnum.schema
