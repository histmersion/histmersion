package com.histmersion
package api.research.workflows

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.api.{WireEnum, WireEnumValues}

enum LessonSource(val wire: WireEnumValues.Wire) derives CanEqual:
  case Rejection extends LessonSource(WireEnumValues.Wire("rejection"))
  case Recheck extends LessonSource(WireEnumValues.Wire("recheck"))

object LessonSource:
  given WireEnumValues[LessonSource]:
    def all: Array[LessonSource] = values
    def wire(e: LessonSource): WireEnumValues.Wire = e.wire
    def fromWire(s: WireEnumValues.Wire): Option[LessonSource] = values.find(_.wire == s.lowered)
  given JsonValueCodec[LessonSource] = WireEnum.codec
