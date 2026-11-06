package com.histmersion.domain.catalogue

import com.histmersion.{Ascii, WireEnumValues}
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.string.Match

object Place:

  /** A place's id: the gazetteer it comes from, an underscore, that gazetteer's id (`geonames_2911611`). */
  type Id = Id.T

  object Id extends RefinedType[String, Match["[a-z][a-z0-9]*_.+"]]

  /** The five `kind`s the store recognises. Free text in the column (an importer may carry its own vocabulary), but
    * these are what the CLI/UI offer and what api's `Lpf` maps foreign type labels onto. `people` (2026-10-09) is a
    * people or a civic tribe — the Ubii, the Athenian tribe Aiantis: a group of persons that a gazetteer places at a
    * representative point, never an area (Pleiades files them as regions; `lint --rule place-kind` moves them).
    */
  object Kind:
    type All = All.T
    object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseResult = ParseResult.T
    object ParseResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Region = "region"
    val Polity = "polity"
    val Site = "site"
    val Feature = "feature"
    val People = "people"
    val all: List[com.histmersion.domain.catalogue.Place.Kind.All] = List(_root_.com.histmersion.domain.catalogue.Place.Kind.All(Region), _root_.com.histmersion.domain.catalogue.Place.Kind.All(Polity), _root_.com.histmersion.domain.catalogue.Place.Kind.All(Site), _root_.com.histmersion.domain.catalogue.Place.Kind.All(Feature), _root_.com.histmersion.domain.catalogue.Place.Kind.All(People))

    /** Map a foreign type label (LPF `types[].label`, an AAT id, a Natural Earth class) onto a kind; `None` when
      * nothing recognisable is there, so the caller's default wins.
      */
    def parse(s: com.histmersion.domain.catalogue.Place.Kind.ParseS): Option[com.histmersion.domain.catalogue.Place.Kind.ParseResult] =
      val t = (s).value.trim.toLowerCase
      if t.isEmpty then None
      else if all.contains(t) then Some(_root_.com.histmersion.domain.catalogue.Place.Kind.ParseResult(t))
      else if t.contains("people") || t.contains("tribe") then Some(_root_.com.histmersion.domain.catalogue.Place.Kind.ParseResult(People))
      else if t.contains("settlement") || t.contains("site") || t.contains("city") || t.contains("town")
        || t.contains("village") || t.contains("fort") || t.contains("temple") || t.contains("sanctuary")
      then Some(_root_.com.histmersion.domain.catalogue.Place.Kind.ParseResult(Site))
      else if t.contains("kingdom") || t.contains("empire") || t.contains("polity") || t.contains("state")
        || t.contains("dynasty") || t.contains("country") || t.contains("nation")
      then Some(_root_.com.histmersion.domain.catalogue.Place.Kind.ParseResult(Polity))
      else if t.contains("region") || t.contains("continent") || t.contains("province")
        || t.contains("territory") || t.contains("area")
      then Some(_root_.com.histmersion.domain.catalogue.Place.Kind.ParseResult(Region))
      else if t.contains("river") || t.contains("mountain") || t.contains("island") || t.contains("lake")
        || t.contains("feature") || t.contains("pass") || t.contains("valley")
      then Some(_root_.com.histmersion.domain.catalogue.Place.Kind.ParseResult(Feature))
      else None

  /** How exact a place's POINT is (V077). Pleiades gives a place whose only location is a square of the Barrington
    * Atlas grid the centre of that square as its representative point: Thessalia (the late Roman province) at 37.5 N
    * 22.5 E — in the Peloponnese — and Viennensis and Tarraconensis both at 42.5 N 2.5 W. Such a point says "somewhere
    * in this square", not "here" ([[GridCell]]).
    *
    * Told from the coordinates alone ([[LocationPrecision.of]]), because the seed keeps only the representative point:
    * a Pleiades point that is the centre of a 5°, 1° or ½° cell. On the live corpus (2026-10-09) 2 426 Pleiades points
    * are, and 2 363 of them are "rough" in Pleiades' own locations dump; no precise point of any size sits exactly on a
    * centre at five decimals. Only Pleiades rows: a Wikidata coordinate in whole minutes (37°15′ = 37.25) is coarse,
    * but it is not an atlas square. `places.location_precision` is the SQL twin (a generated column), held equal by
    * `PlaceCleanupSuite`.
    */
  enum Precision:
    case Stated, GridCell

    def wire: com.histmersion.domain.catalogue.Place.Precision.WireResult = this match
      case Stated   => _root_.com.histmersion.domain.catalogue.Place.Precision.WireResult("stated")
      case GridCell => _root_.com.histmersion.domain.catalogue.Place.Precision.WireResult("grid-cell")

  object Precision:
    /** The atlas grids, as cell sizes in micro-degrees: 5°, 1° and ½°. */
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CellsMicro = CellsMicroValue
    type CellsMicroValue = CellsMicroValue.T
    object CellsMicroValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GridCentreResult = GridCentreResult.T
    object GridCentreResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Approximate = ApproximateValue
    type ApproximateValue = ApproximateValue.T
    object ApproximateValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val CellsMicro: List[com.histmersion.domain.catalogue.Place.Precision.CellsMicro] = List(_root_.com.histmersion.domain.catalogue.Place.Precision.CellsMicroValue(5000000L), _root_.com.histmersion.domain.catalogue.Place.Precision.CellsMicroValue(1000000L), _root_.com.histmersion.domain.catalogue.Place.Precision.CellsMicroValue(500000L))

    /** Is (`lon`, `lat`) — rounded to a micro-degree — the centre of a cell of one of the grids, in both coordinates?
      * Pure.
      */
    def gridCentre(lon: com.histmersion.Longitude, lat: com.histmersion.Latitude): com.histmersion.domain.catalogue.Place.Precision.GridCentreResult =
      val x = math.round(lon * 1e6)
      val y = math.round(lat * 1e6)
      _root_.com.histmersion.domain.catalogue.Place.Precision.GridCentreResult(CellsMicro.exists(c => Math.floorMod(x, c) == c / 2 && Math.floorMod(y, c) == c / 2))

    /** The precision of the point of place `placeId`. */
    def of(placeId: com.histmersion.domain.catalogue.Place.Id, lon: com.histmersion.Longitude, lat: com.histmersion.Latitude): Precision =
      if placeId.startsWith("pleiades_") && gridCentre(lon, lat) then GridCell else Stated

    /** What a place says about a grid-cell point ("location: approximate (atlas grid square)" on a card). */
    val Approximate: com.histmersion.domain.catalogue.Place.Precision.Approximate = _root_.com.histmersion.domain.catalogue.Place.Precision.ApproximateValue("approximate (atlas grid square)")

    def parse(s: com.histmersion.domain.catalogue.Place.Precision.ParseS): Option[Precision] = values.find(_.wire == Ascii.lower(_root_.com.histmersion.Ascii.LowerS((s).value.trim)))

    given WireEnumValues[Precision]:
      def all: Array[Precision] = Precision.values
      def wire(e: Precision): _root_.com.histmersion.WireEnumValues.WireResult = _root_.com.histmersion.WireEnumValues.WireResult((e.wire).value)
      def fromWire(s: _root_.com.histmersion.WireEnumValues.FromWireS): _root_.scala.Option[Precision] = parse(_root_.com.histmersion.domain.catalogue.Place.Precision.ParseS((s).value))
