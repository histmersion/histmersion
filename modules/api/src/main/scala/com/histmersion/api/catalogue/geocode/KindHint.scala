package com.histmersion
package api.catalogue.geocode

import com.histmersion.api.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import io.github.iltotore.iron.constraint.any.Pure

enum KindHint derives CanEqual:
  case Site, Region, Polity, Water, Unknown

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def wire: KindHint.Wire = KindHint.Wire(toString.toLowerCase(java.util.Locale.ROOT))

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object KindHint:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): KindHint =
    val t = (s).value.trim.toLowerCase(java.util.Locale.ROOT)
    values
      .find(_.wire == Wire(t))
      .getOrElse(t match
        case "city" | "town" | "settlement" | "village" | "temple" | "fort" | "sanctuary" | "cemetery" => Site
        case "country" | "area" | "province" | "landscape" | "valley" | "plain" | "island"             => Region
        case "state" | "kingdom" | "empire" | "polity" | "dynasty"                                     => Polity
        case "sea" | "lake" | "river" | "gulf" | "bay" | "strait"                                      => Water
        case _                                                                                         => Unknown)

  given WireEnumValues[KindHint]:
    def all: Array[KindHint] = values
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: KindHint): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: WireEnumValues.Wire): Option[KindHint] =
      values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))
  given JsonValueCodec[KindHint] = WireEnum.codec
  given Schema[KindHint] = WireEnum.schema
