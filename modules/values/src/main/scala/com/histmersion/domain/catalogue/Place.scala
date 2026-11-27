package com.histmersion
package domain.catalogue

import com.histmersion.{Ascii, WireEnumValues}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

object Place:

  type Id = Id.T

  object Id extends Newtype[String, Match["[a-z][a-z0-9-]*(_.+)?"]]:
    extension (id: T) def isPleiades: Boolean = id.value.startsWith("pleiades_")
    extension (id: T) def isBlank: Boolean = id.value.trim.isEmpty
    extension (id: T) def trimmed: Id = assume(id.value.trim)

  object Kind:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    val Region = "region"
    val Polity = "polity"
    val Site = "site"
    val Feature = "feature"
    val People = "people"
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    val all: List[Wire] = List(Wire(Region), Wire(Polity), Wire(Site), Wire(Feature), Wire(People))

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.SilentCompare, "pending G53 (no comparison of unrelated types)")
    def parse(s: Wire): Option[Wire] =
      val t = (s).value.trim.toLowerCase
      if t.isEmpty then None
      else if all.contains(t) then Some(Wire(t))
      else if t.contains("people") || t.contains("tribe") then Some(Wire(People))
      else if t.contains("settlement") || t.contains("site") || t.contains("city") || t.contains("town")
        || t.contains("village") || t.contains("fort") || t.contains("temple") || t.contains("sanctuary")
      then Some(Wire(Site))
      else if t.contains("kingdom") || t.contains("empire") || t.contains("polity") || t.contains("state")
        || t.contains("dynasty") || t.contains("country") || t.contains("nation")
      then Some(Wire(Polity))
      else if t.contains("region") || t.contains("continent") || t.contains("province")
        || t.contains("territory") || t.contains("area")
      then Some(Wire(Region))
      else if t.contains("river") || t.contains("mountain") || t.contains("island") || t.contains("lake")
        || t.contains("feature") || t.contains("pass") || t.contains("valley")
      then Some(Wire(Feature))
      else None

  enum Precision derives CanEqual:
    case Stated, GridCell

    def wire: Precision.Wire = this match
      case Stated   => Precision.Wire("stated")
      case GridCell => Precision.Wire("grid-cell")

  object Precision:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    type CellsMicro = CellsMicroValue
    type CellsMicroValue = CellsMicroValue.T
    object CellsMicroValue extends Newtype[Long, Pure]
    type Approximate = ApproximateValue
    type ApproximateValue = ApproximateValue.T
    object ApproximateValue extends Newtype[String, Pure]

    val CellsMicro: List[CellsMicro] =
      List(CellsMicroValue(5000000L), CellsMicroValue(1000000L), CellsMicroValue(500000L))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def gridCentre(lon: Longitude, lat: Latitude): Boolean =
      val x = lon.micro
      val y = lat.micro
      (CellsMicro.map(_.value).exists(c => Math.floorMod(x, c) == c / 2 && Math.floorMod(y, c) == c / 2))

    def of(placeId: Id, lon: Longitude, lat: Latitude): Precision =
      if placeId.isPleiades && gridCentre(lon, lat) then GridCell else Stated

    val Approximate: Approximate = ApproximateValue("approximate (atlas grid square)")

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(s: Wire): Option[Precision] = values.find(_.wire.value == Ascii.lower(((s).value.trim)))

    given WireEnumValues[Precision]:
      def all: Array[Precision] = values
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def wire(e: Precision): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def fromWire(s: WireEnumValues.Wire): Option[Precision] = parse(Wire((s).value))
