package com.histmersion
package domain.reviewarticle

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
import com.histmersion.domain.reviewarticle.Finding
import com.histmersion.domain.reviewarticle.Severity
import com.histmersion.Newtype

enum Verdict(val wire: Verdict.Wire, val rank: Verdict.Rank):
  case Pass extends Verdict(Verdict.Wire("PASS"), Verdict.Rank(0))
  case Minor extends Verdict(Verdict.Wire("MINOR"), Verdict.Rank(1))
  case Major extends Verdict(Verdict.Wire("MAJOR"), Verdict.Rank(2))

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object Verdict:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  type Rank = Rank.T
  object Rank extends Newtype[Int, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[Verdict] = values.find(_.wire.value == s.value.trim.toUpperCase)
  def worst(vs: Iterable[Verdict]): Verdict = vs.foldLeft(Pass)((a, b) => if b.rank > a.rank then b else a)

  def of(fs: Iterable[Finding]): Verdict =
    if fs.exists(_.severity == Severity.Major) then Major else if fs.nonEmpty then Minor else Pass
  given WireEnumValues[Verdict]:
    def all: Array[Verdict] = values
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: Verdict): WireEnumValues.Wire = WireEnumValues.Wire(e.wire.value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: WireEnumValues.Wire): Option[Verdict] = parse(Wire(s.value))
  given JsonValueCodec[Verdict] = WireEnum.codec
  given Schema[Verdict] = WireEnum.schema
