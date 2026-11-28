package com.histmersion
package api.research.workflows

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.api.{WireEnum, WireEnumValues}

enum LedgerStatus(val wire: WireEnumValues.Wire) derives CanEqual:
  case Open extends LedgerStatus(WireEnumValues.Wire("open"))
  case Fixed extends LedgerStatus(WireEnumValues.Wire("fixed"))
  case Rejected extends LedgerStatus(WireEnumValues.Wire("rejected"))
  case WontFix extends LedgerStatus(WireEnumValues.Wire("wont-fix"))
  case SetAside extends LedgerStatus(WireEnumValues.Wire("set-aside"))

object LedgerStatus:
  given WireEnumValues[LedgerStatus]:
    def all: Array[LedgerStatus] = values
    def wire(e: LedgerStatus): WireEnumValues.Wire = e.wire
    def fromWire(s: WireEnumValues.Wire): Option[LedgerStatus] = values.find(_.wire == s.lowered)
  given JsonValueCodec[LedgerStatus] = WireEnum.codec
