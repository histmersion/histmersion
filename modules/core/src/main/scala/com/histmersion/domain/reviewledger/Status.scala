package com.histmersion
package domain.reviewledger

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.api.WireEnumValues
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.api.WireEnum
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

enum Status(val wire: WireEnumValues.Wire) derives CanEqual:
  case Open extends Status(WireEnumValues.Wire("open"))
  case Fixed extends Status(WireEnumValues.Wire("fixed"))
  case Rejected extends Status(WireEnumValues.Wire("rejected"))
  case WontFix extends Status(WireEnumValues.Wire("wont-fix"))

  case SetAside extends Status(WireEnumValues.Wire("set-aside"))

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object Status:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: WireEnumValues.Wire): Option[Status] = values.find(_.wire.value == s.value.trim.toLowerCase)
  given WireEnumValues[Status]:
    def all: Array[Status] = values
    def wire(e: Status): WireEnumValues.Wire = e.wire
    def fromWire(s: WireEnumValues.Wire): Option[Status] = parse(s)
  given JsonValueCodec[Status] = WireEnum.codec
