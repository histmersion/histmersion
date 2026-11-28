package com.histmersion
package api.research.workflows

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.api.{WireEnum, WireEnumValues}
import io.github.iltotore.iron.constraint.any.Pure
import sttp.tapir.Schema

enum PanelVerdict(val wire: PanelVerdict.Wire) derives CanEqual:
  case Pass extends PanelVerdict(PanelVerdict.Wire("PASS"))
  case Minor extends PanelVerdict(PanelVerdict.Wire("MINOR"))
  case Major extends PanelVerdict(PanelVerdict.Wire("MAJOR"))

object PanelVerdict:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  given WireEnumValues[PanelVerdict]:
    def all: Array[PanelVerdict] = values
    def wire(e: PanelVerdict): WireEnumValues.Wire = WireEnumValues.Wire.of(e.wire)
    def fromWire(s: WireEnumValues.Wire): Option[PanelVerdict] =
      values.find(e => WireEnumValues.Wire.of(e.wire) == s.uppered)
  given JsonValueCodec[PanelVerdict] = WireEnum.codec
  given Schema[PanelVerdict] = WireEnum.schema
