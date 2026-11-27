package com.histmersion
package api.library
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import scala.annotation.tailrec
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.GeoPick

enum MetadataField:
  case Year, Venue, Doi, Provenance, CalibreId

@tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
object MetadataField:
  export GeoPick.Wire

  def wireOf(f: MetadataField): Wire = f match
    case Year       => Wire("year")
    case Venue      => Wire("venue")
    case Doi        => Wire("doi")
    case Provenance => Wire("provenance")
    case CalibreId  => Wire("calibreId")

  def fromWire(s: Wire): Option[MetadataField] = values.find(wireOf(_) == s)

  given WireEnumValues[MetadataField]:
    def all: Array[MetadataField] = values
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def wire(e: MetadataField): WireEnumValues.Wire = WireEnumValues.Wire((wireOf(e)).value)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
    def fromWire(s: WireEnumValues.Wire): Option[MetadataField] = MetadataField.fromWire(Wire((s).value))

  given JsonValueCodec[MetadataField] = WireEnum.codec
  given Schema[MetadataField] = WireEnum.schema
