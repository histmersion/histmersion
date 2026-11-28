package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.Wording

enum CheckLevel(val wire: CheckLevel.Wire) derives CanEqual:
  case Ok extends CheckLevel(CheckLevel.Wire("ok"))
  case Warn extends CheckLevel(CheckLevel.Wire("warn"))
  case Fail extends CheckLevel(CheckLevel.Wire("fail"))

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object CheckLevel:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]:
    given Display[T] = Display(wire => Wording.humanize(Wording.Id(wire.value)))

  def parse(s: Wire): Option[CheckLevel] = values.find(_.wire == s)
  given WireEnumValues[CheckLevel]:
    def all: Array[CheckLevel] = values
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: CheckLevel): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: WireEnumValues.Wire): Option[CheckLevel] = parse(Wire((s).value))
  given com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[CheckLevel] = WireEnum.codec
  given Schema[CheckLevel] = WireEnum.schema
