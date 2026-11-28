package com.histmersion
package domain.research.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.api.WireEnumValues
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.histmersion.api.WireEnum

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
enum LessonSource(val wire: WireEnumValues.Wire):
  case Rejection extends LessonSource(WireEnumValues.Wire("rejection"))
  case Recheck extends LessonSource(WireEnumValues.Wire("recheck"))

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object LessonSource:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: WireEnumValues.Wire): Option[LessonSource] = values.find(_.wire.value == s.value.trim.toLowerCase)
  given WireEnumValues[LessonSource]:
    def all: Array[LessonSource] = values
    def wire(e: LessonSource): WireEnumValues.Wire = e.wire
    def fromWire(s: WireEnumValues.Wire): Option[LessonSource] = parse(s)
  given JsonValueCodec[LessonSource] = WireEnum.codec
