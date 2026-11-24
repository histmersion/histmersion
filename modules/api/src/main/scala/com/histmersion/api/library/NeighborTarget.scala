package com.histmersion
package api.library
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.research.Article
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
enum NeighborTarget derives CanEqual:
  case Source, Article, Unresolved

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object NeighborTarget:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  given WireEnumValues[NeighborTarget]:
    def all: Array[NeighborTarget] = values
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: NeighborTarget): WireEnumValues.Wire = WireEnumValues.Wire((Wire(e.toString.toLowerCase)).value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def fromWire(s: WireEnumValues.Wire): Option[NeighborTarget] =
      values.find(_.toString.equalsIgnoreCase((s).value))

  given JsonValueCodec[NeighborTarget] = WireEnum.codec
  given Schema[NeighborTarget] = WireEnum.schema
