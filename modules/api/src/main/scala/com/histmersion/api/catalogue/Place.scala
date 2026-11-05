package com.histmersion.api.catalogue
import com.histmersion.api.{Ascii, WireEnumValues}

import com.histmersion.api.OpenSide
import com.histmersion.api.given
import com.histmersion.api.WireEnumValues
import com.histmersion.api.Api
import com.histmersion.api.GeoPoint
import com.histmersion.api.StrictJson
import com.histmersion.api.WireEnum
import com.histmersion.api.Write
import com.histmersion.api.{GeoPoint, Write, YearFmt}
import com.histmersion.api.{WireEnum, YearFmt}
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import io.circe.Json
import sttp.tapir.Schema
import com.histmersion.api.json.NewtypeCodecs.given
final case class Place(
    id: com.histmersion.api.catalogue.Place.Id,
    label: Place.Label,
    kind: Place.KindValue,
    geometry: Option[Place.Geometry] = None,
    period: Option[Period] = None,
    source: Option[Place.Source] = None,
    properties: Option[Place.Properties] = None,
    open: Option[OpenSide] = None
):
  def bounds: (Option[com.histmersion.api.catalogue.Place.BoundsResultPart1], Option[com.histmersion.api.catalogue.Place.BoundsResultPart2]) = OpenSide.bounds(period, open)

object Place:

  /** The five `kind`s the store recognises. Free text in the column (an importer may carry its own vocabulary), but
    * these are what the CLI/UI offer and what api's `Lpf` maps foreign type labels onto. `people` (2026-10-09) is a
    * people or a civic tribe — the Ubii, the Athenian tribe Aiantis: a group of persons that a gazetteer places at a
    * representative point, never an area (Pleiades files them as regions; `lint --rule place-kind` moves them).
    */
  type BoundsResultPart1 = BoundsResultPart1.T
  object BoundsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BoundsResultPart2 = BoundsResultPart2.T
  object BoundsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindValue = KindValue.T
  object KindValue extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Geometry = Geometry.T
  object Geometry extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Source = Source.T
  object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Properties = Properties.T
  object Properties extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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
    val all: List[com.histmersion.api.catalogue.Place.Kind.All] = List(Region, Polity, Site, Feature, People)

    /** Map a foreign type label (LPF `types[].label`, an AAT id, a Natural Earth class) onto a kind; `None` when
      * nothing recognisable is there, so the caller's default wins.
      */
    def parse(s: com.histmersion.api.catalogue.Place.Kind.ParseS): Option[com.histmersion.api.catalogue.Place.Kind.ParseResult] =
      val t = s.trim.toLowerCase
      if t.isEmpty then None
      else if all.contains(t) then Some(t)
      else if t.contains("people") || t.contains("tribe") then Some(People)
      else if t.contains("settlement") || t.contains("site") || t.contains("city") || t.contains("town")
        || t.contains("village") || t.contains("fort") || t.contains("temple") || t.contains("sanctuary")
      then Some(Site)
      else if t.contains("kingdom") || t.contains("empire") || t.contains("polity") || t.contains("state")
        || t.contains("dynasty") || t.contains("country") || t.contains("nation")
      then Some(Polity)
      else if t.contains("region") || t.contains("continent") || t.contains("province")
        || t.contains("territory") || t.contains("area")
      then Some(Region)
      else if t.contains("river") || t.contains("mountain") || t.contains("island") || t.contains("lake")
        || t.contains("feature") || t.contains("pass") || t.contains("valley")
      then Some(Feature)
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

    def wire: com.histmersion.api.catalogue.Place.Precision.WireResult = this match
      case Stated   => "stated"
      case GridCell => "grid-cell"

  object Precision:
    /** The atlas grids, as cell sizes in micro-degrees: 5°, 1° and ½°. */
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CellsMicro = CellsMicro.T
    object CellsMicro extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GridCentreResult = GridCentreResult.T
    object GridCentreResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GridCentreLon = GridCentreLon.T
    object GridCentreLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GridCentreLat = GridCentreLat.T
    object GridCentreLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfPlaceId = OfPlaceId.T
    object OfPlaceId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfLon = OfLon.T
    object OfLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfLat = OfLat.T
    object OfLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Approximate = Approximate.T
    object Approximate extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val CellsMicro: List[com.histmersion.api.catalogue.Place.Precision.CellsMicro] = List(5000000L, 1000000L, 500000L)

    /** Is (`lon`, `lat`) — rounded to a micro-degree — the centre of a cell of one of the grids, in both coordinates?
      * Pure.
      */
    def gridCentre(lon: com.histmersion.api.catalogue.Place.Precision.GridCentreLon, lat: com.histmersion.api.catalogue.Place.Precision.GridCentreLat): com.histmersion.api.catalogue.Place.Precision.GridCentreResult =
      val x = math.round(lon * 1e6)
      val y = math.round(lat * 1e6)
      CellsMicro.exists(c => Math.floorMod(x, c) == c / 2 && Math.floorMod(y, c) == c / 2)

    /** The precision of the point of place `placeId`. */
    def of(placeId: com.histmersion.api.catalogue.Place.Precision.OfPlaceId, lon: com.histmersion.api.catalogue.Place.Precision.OfLon, lat: com.histmersion.api.catalogue.Place.Precision.OfLat): Precision =
      if placeId.startsWith("pleiades_") && gridCentre(lon, lat) then GridCell else Stated

    /** What a place says about a grid-cell point ("location: approximate (atlas grid square)" on a card). */
    val Approximate: com.histmersion.api.catalogue.Place.Precision.Approximate = "approximate (atlas grid square)"

    def parse(s: com.histmersion.api.catalogue.Place.Precision.ParseS): Option[Precision] = values.find(_.wire == Ascii.lower(s.trim))

    given WireEnumValues[Precision]:
      def all: Array[Precision] = Precision.values
      def wire(e: Precision): com.histmersion.api.catalogue.Place.Precision.WireResult = e.wire
      def fromWire(s: com.histmersion.api.catalogue.Place.Precision.FromWireS): Option[Precision] = parse(s)

  final case class Add(
      label: Add.Label,
      kind: Add.Kind,
      lon: com.histmersion.api.catalogue.Longitude,
      lat: com.histmersion.api.catalogue.Latitude,
      source: Add.Source,
      id: Option[com.histmersion.api.catalogue.Place.Id] = None,
      names: List[Add.Names] = Nil,
      lang: Option[Add.Lang] = None,
      periodStart: Option[com.histmersion.api.catalogue.Year] = None,
      periodEnd: Option[com.histmersion.api.catalogue.Year] = None
  )
  object Add:
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Source = Source.T
    object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Names = Names.T
    object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Lang = Lang.T
    object Lang extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[Add] = KindlingsSchema
      .derived[Add]
      .schema
      .modify(_.kind)(_.description(s"one of ${com.histmersion.api.catalogue.Place.Kind.all.mkString(", ")}"))
      .modify(_.source)(_.description("where the coordinates come from (a gazetteer id, a map, a publication)"))
    given Codec.AsObject[Add] = StrictJson.codec(KindlingsCodecAsObject.derived[Add])

  /** What a place's page shows of the gazetteer: the row, its geometry, the hierarchy around it and its records. The
    * gazetteer's own record ([[PlaceEntry]]) and the page the server answers (`PlaceDetail`, which adds the place's
    * pictures) both are one, so the page renders from either.
    */
  trait Card:
    def place: Summary
    def geometry: Option[com.histmersion.api.catalogue.Place.Card.GeometryResult]
    def parents: List[Summary]
    def children: List[Summary]
    def findings: com.histmersion.api.catalogue.Place.Card.FindingsResult
    def asOf: Option[com.histmersion.api.catalogue.Place.Card.AsOfResult]
    def contested: com.histmersion.api.catalogue.Place.Card.ContestedResult
    def footprints: List[Footprint]
    def links: List[Link]
    def types: List[Type]
    def counts: Option[Counts]
    def hasBoundary: com.histmersion.api.catalogue.Place.Card.HasBoundaryResult
    def descriptions: List[Description]
    def timeBasis: TimeBasis
    def locationPrecision: com.histmersion.api.catalogue.Place.Precision
    def siteType: Option[com.histmersion.api.catalogue.Place.Card.SiteTypeResult]
    def record: Record

    def pointOnlyArea: com.histmersion.api.catalogue.Place.Card.PointOnlyAreaResult = geometry.isDefined && Shape.pointOnlyArea(place.kind, hasBoundary)

    def shapeNote: Option[com.histmersion.api.catalogue.Place.Card.ShapeNoteResult] =
      Option
        .when(pointOnlyArea)(Shape.NoBoundaryNote)
        .orElse(Option.when(geometry.isDefined && Shape.isPeople(place.kind))(Shape.PeopleNote))

    def locationNote: Option[com.histmersion.api.catalogue.Place.Card.LocationNoteResult] =
      Option.when(locationPrecision == com.histmersion.api.catalogue.Place.Precision.GridCell)(
        com.histmersion.api.catalogue.Place.Precision.Approximate
      )

    def shapeNotes: List[com.histmersion.api.catalogue.Place.Card.ShapeNotesResult] = shapeNote.toList ++ locationNote.map(n => s"location: $n").toList

    def datedByFindings: com.histmersion.api.catalogue.Place.Card.DatedByFindingsResult = asOf.isDefined && timeBasis == TimeBasis.Findings

  object Card:
    type GeometryResult = GeometryResult.T
    object GeometryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FindingsResult = FindingsResult.T
    object FindingsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AsOfResult = AsOfResult.T
    object AsOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ContestedResult = ContestedResult.T
    object ContestedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HasBoundaryResult = HasBoundaryResult.T
    object HasBoundaryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SiteTypeResult = SiteTypeResult.T
    object SiteTypeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PointOnlyAreaResult = PointOnlyAreaResult.T
    object PointOnlyAreaResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ShapeNoteResult = ShapeNoteResult.T
    object ShapeNoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LocationNoteResult = LocationNoteResult.T
    object LocationNoteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ShapeNotesResult = ShapeNotesResult.T
    object ShapeNotesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DatedByFindingsResult = DatedByFindingsResult.T
    object DatedByFindingsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Counts(sources: Counts.Sources, findings: Counts.Findings, articles: Counts.Articles, events: Counts.Events)
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Counts:
    type Sources = Sources.T
    object Sources extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Findings = Findings.T
    object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Articles = Articles.T
    object Articles extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Events = Events.T
    object Events extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  enum Dating:
    case Stated, EndOnly, Unknown

    def wire: com.histmersion.api.catalogue.Place.Dating.WireResult = this match
      case Stated  => "stated"
      case EndOnly => "end-only"
      case Unknown => "unknown"

  object Dating:
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DescribeResult = DescribeResult.T
    object DescribeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DescribeStart = DescribeStart.T
    object DescribeStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DescribeEnd = DescribeEnd.T
    object DescribeEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.catalogue.Place.Dating.ParseS): Option[Dating] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

    given WireEnumValues[Dating]:
      def all: Array[Dating] = Dating.values
      def wire(e: Dating): com.histmersion.api.catalogue.Place.Dating.WireResult = e.wire
      def fromWire(s: com.histmersion.api.catalogue.Place.Dating.FromWireS): Option[Dating] = parse(s)
    given io.circe.Encoder[Dating] = WireEnum.encoder
    given io.circe.Decoder[Dating] = WireEnum.decoder
    given Schema[Dating] = WireEnum.schema

    def describe(dating: Dating, start: Option[com.histmersion.api.catalogue.Place.Dating.DescribeStart], end: Option[com.histmersion.api.catalogue.Place.Dating.DescribeEnd]): com.histmersion.api.catalogue.Place.Dating.DescribeResult = dating match
      case Unknown => "date unknown"
      case EndOnly => end.fold("date unknown")(e => s"end ${YearFmt(e)}, start unknown")
      case Stated  => YearFmt.openPeriod(start, end, none = "")

  final case class Description(text: Description.Text, lang: Description.Lang = "", source: Description.Source = "")
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Description:
    type Text = Text.T
    object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Lang = Lang.T
    object Lang extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Source = Source.T
    object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Designation(
      id: Designation.Id,
      label: Option[Designation.Label] = None,
      start: Option[Designation.Start] = None,
      end: Option[Designation.End] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema

  object Designation:
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Start = Start.T
    object Start extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type End = End.T
    object End extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  object Edits:

    type InvalidMsg = InvalidMsg.T
    object InvalidMsg extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NameResultPart2 = NameResultPart2.T
    object NameResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NameRaw = NameRaw.T
    object NameRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LangResultPart2 = LangResultPart2.T
    object LangResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LangRaw = LangRaw.T
    object LangRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DefaultIdResult = DefaultIdResult.T
    object DefaultIdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DefaultIdLabel = DefaultIdLabel.T
    object DefaultIdLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CsvResult = CsvResult.T
    object CsvResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val LocalPrefix = "local_"

    private val LangShape = "^[a-z]{2,3}(-[a-z0-9]{2,8})*$".r
    private val IdShape = "^[a-z0-9][a-z0-9_.-]*$".r

    private def invalid(msg: com.histmersion.api.catalogue.Place.Edits.InvalidMsg) = Api.Error(msg, Some(Write.CodeInvalid))

    def name(raw: com.histmersion.api.catalogue.Place.Edits.NameRaw): Either[Api.Error, com.histmersion.api.catalogue.Place.Edits.NameResultPart2] =
      val n = raw.trim.replaceAll("\\s+", " ")
      if n.isEmpty then Left(invalid("the name is empty"))
      else if n.length > MaxName then Left(invalid(s"a place name is at most $MaxName characters"))
      else Right(n)

    val MaxName = 200

    def lang(raw: Option[com.histmersion.api.catalogue.Place.Edits.LangRaw]): Either[Api.Error, com.histmersion.api.catalogue.Place.Edits.LangResultPart2] =
      raw.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty) match
        case None                            => Right("")
        case Some(l) if LangShape.matches(l) => Right(l)
        case Some(l)                         => Left(invalid(s"'$l' is not a language tag (e.g. en, grc, pl, sr-latn)"))

    def defaultId(label: com.histmersion.api.catalogue.Place.Edits.DefaultIdLabel): com.histmersion.api.catalogue.Place.Edits.DefaultIdResult =
      LocalPrefix + Geocode.key(label).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "").take(64)

    def toInput(in: Add): Either[Api.Error, Input] =
      val label = in.label.trim.replaceAll("\\s+", " ")
      val kind = in.kind.trim.toLowerCase(java.util.Locale.ROOT)
      val id = in.id.map(_.value).map(_.trim).filter(_.nonEmpty).getOrElse(defaultId(label))
      for
        _ <- Either.cond(label.nonEmpty, (), invalid("the label is empty"))
        _ <- Either.cond(
          com.histmersion.api.catalogue.Place.Kind.all.contains(kind),
          (),
          invalid(s"kind '$kind' is not one of ${com.histmersion.api.catalogue.Place.Kind.all.mkString(", ")}")
        )
        _ <- Either.cond(id != LocalPrefix, (), invalid(s"'$label' gives no id — pass one (`id`)"))
        _ <- Either.cond(
          IdShape.matches(id),
          (),
          invalid(s"'$id' is not a place id (lower-case letters, digits, '_', '.' and '-')")
        )
        _ <- Either.cond(
          in.lon >= -180 && in.lon <= 180 && in.lat >= -90 && in.lat <= 90 &&
            !in.lon.isNaN && !in.lat.isNaN,
          (),
          invalid(s"(${in.lon}, ${in.lat}) is not a longitude/latitude")
        )
        _ <- Either.cond(
          in.source.trim.nonEmpty,
          (),
          invalid("say where the coordinates come from (`source`): a gazetteer id, a map, a publication")
        )
        period <- (in.periodStart, in.periodEnd) match
          case (None, None)                 => Right(None)
          case (Some(a), Some(b)) if a <= b => Right(Some(Period(a, b)))
          case (Some(a), None)              => Right(Some(Period(a, a)))
          case (None, Some(b))              => Right(Some(Period(b, b)))
          case (Some(a), Some(b))           => Left(invalid(s"the period starts ($a) after it ends ($b)"))
        l <- lang(in.lang)
        extra <- in.names.foldLeft[Either[Api.Error, List[String]]](Right(Nil))((acc, n) =>
          acc.flatMap(ns => name(n).map(ns :+ _))
        )
      yield
        val point = Json
          .obj(
            "type" -> Json.fromString("Point"),
            "coordinates" -> Json.arr(Json.fromDoubleOrNull(in.lon), Json.fromDoubleOrNull(in.lat))
          )
          .noSpaces
        val names = (Name(label) :: extra.map(Name(_, l))).distinctBy(n => (n.name.toLowerCase, n.lang))
        Input(
          Place(
            com.histmersion.api.catalogue.Place.Id(id),
            label,
            kind,
            Some(point),
            period,
            Some(in.source.trim),
            Some(Json.obj("added" -> Json.True).noSpaces)
          ),
          names
        )

    def csv(rows: List[ManualPlaceName]): com.histmersion.api.catalogue.Place.Edits.CsvResult =
      def cell(s: String) =
        if s.exists(c => c == ',' || c == '"' || c == '\n') then "\"" + s.replace("\"", "\"\"") + "\"" else s
      ("place_id,name,lang,why" :: rows.map(r =>
        List(r.placeId, r.name, r.lang, s"added by hand to ${r.label}").map(cell).mkString(",")
      ))
        .mkString("", "\n", "\n")

  /** The gazetteer's record of one place: the row, its GeoJSON geometry and the hierarchy around it at `asOf`, its
    * footprints, links, types and records; `counts`, what the corpus holds for it, filled by whoever shows its card.
    * Not on the wire: the server answers the page (`PlaceDetail`), which adds the place's pictures.
    */
  final case class Entry(
      place: Summary,
      geometry: Option[Entry.Geometry],
      parents: List[Summary],
      children: List[Summary],
      findings: Entry.Findings,
      asOf: Option[Entry.AsOf],
      contested: Entry.Contested = false,
      footprints: List[Footprint] = Nil,
      links: List[Link] = Nil,
      types: List[Type] = Nil,
      counts: Option[Counts] = None,
      hasBoundary: Entry.HasBoundary = false,
      descriptions: List[Description] = Nil,
      timeBasis: TimeBasis = TimeBasis.Dated,
      /** `grid-cell` for a Pleiades point that is only the centre of an atlas grid square. */
      locationPrecision: com.histmersion.api.catalogue.Place.Precision =
        com.histmersion.api.catalogue.Place.Precision.Stated,
      siteType: Option[Entry.SiteType] = None,
      record: Record = Record()
  ) extends Card

  object Entry:
    type Geometry = Geometry.T
    object Geometry extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Findings = Findings.T
    object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AsOf = AsOf.T
    object AsOf extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Contested = Contested.T
    object Contested extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type HasBoundary = HasBoundary.T
    object HasBoundary extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SiteType = SiteType.T
    object SiteType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Follows the Pleiades Place/Location split: one place, many dated footprints. */
  final case class Footprint(
      id: Footprint.Id,
      placeId: com.histmersion.api.catalogue.Place.Id,
      role: Footprint.Role = "extent",
      period: Option[Period] = None,
      certainty: Option[Footprint.Certainty] = None,
      accuracyM: Option[Footprint.AccuracyM] = None,
      citekey: Option[com.histmersion.api.library.Citekey] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
  object Footprint:

    type Roles = Roles.T
    object Roles extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Certainties = Certainties.T
    object Certainties extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Role = Role.T
    object Role extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Certainty = Certainty.T
    object Certainty extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AccuracyM = AccuracyM.T
    object AccuracyM extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Float, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Roles: List[com.histmersion.api.catalogue.Place.Footprint.Roles] = List("extent", "core", "maximal", "frontier", "seat", "snapshot")
    val Certainties: List[com.histmersion.api.catalogue.Place.Footprint.Certainties] = List("definite", "approximate", "uncertain")

  final case class Gone(
      placeId: com.histmersion.api.catalogue.Place.Id,
      label: Gone.Label,
      into: Option[Gone.Into],
      intoLabel: Option[Gone.IntoLabel],
      findingsBefore: Gone.FindingsBefore,
      findingsAfter: Gone.FindingsAfter,
      unlocated: Gone.Unlocated
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Gone:
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Into = Into.T
    object Into extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IntoLabel = IntoLabel.T
    object IntoLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FindingsBefore = FindingsBefore.T
    object FindingsBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FindingsAfter = FindingsAfter.T
    object FindingsAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Unlocated = Unlocated.T
    object Unlocated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Hit(
      id: com.histmersion.api.catalogue.Place.Id,
      label: Hit.Label,
      kind: Hit.Kind,
      periodStart: Option[com.histmersion.api.catalogue.Year],
      periodEnd: Option[com.histmersion.api.catalogue.Year],
      source: Option[Hit.Source],
      names: Hit.Names,
      matchedName: Option[Hit.MatchedName],
      findings: Hit.Findings,
      contested: Hit.Contested = false,
      dating: Dating = Dating.Stated,
      importSource: Option[Hit.ImportSource] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def toSummary: Summary =
      Summary(com.histmersion.api.catalogue.Place.Id(id), label, kind, periodStart, periodEnd, source, names, contested, dating, importSource)

  object Hit:
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Source = Source.T
    object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Names = Names.T
    object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MatchedName = MatchedName.T
    object MatchedName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Findings = Findings.T
    object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Contested = Contested.T
    object Contested extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ImportSource = ImportSource.T
    object ImportSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class ImportResult(
      places: ImportResult.Places,
      names: ImportResult.Names,
      relations: ImportResult.Relations,
      failed: List[ImportResult.Failed] = Nil,
      job: Option[ImportResult.Job] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object ImportResult:
    type Places = Places.T
    object Places extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Names = Names.T
    object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Relations = Relations.T
    object Relations extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Failed = Failed.T
    object Failed extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Job = Job.T
    object Job extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Input(place: Place, names: List[Name] = Nil, relations: List[Relation] = Nil)

  final case class Link(placeId: com.histmersion.api.catalogue.Place.Id, scheme: Link.Scheme, externalId: Link.ExternalId, relation: Link.Relation = "close_match")
      derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema
  object Link:

    type Schemes = Schemes.T
    object Schemes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Relations = Relations.T
    object Relations extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UrlResult = UrlResult.T
    object UrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UrlScheme = UrlScheme.T
    object UrlScheme extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UrlExternalId = UrlExternalId.T
    object UrlExternalId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SchemeTitleResult = SchemeTitleResult.T
    object SchemeTitleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SchemeTitleScheme = SchemeTitleScheme.T
    object SchemeTitleScheme extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Scheme = Scheme.T
    object Scheme extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ExternalId = ExternalId.T
    object ExternalId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Relation = Relation.T
    object Relation extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val Schemes: List[com.histmersion.api.catalogue.Place.Link.Schemes] = List("pleiades", "wikidata", "tgn", "geonames", "whg")
    val Relations: List[com.histmersion.api.catalogue.Place.Link.Relations] = List("close_match", "exact_match", "broad_match", "see_also")

    def url(scheme: com.histmersion.api.catalogue.Place.Link.UrlScheme, externalId: com.histmersion.api.catalogue.Place.Link.UrlExternalId): Option[com.histmersion.api.catalogue.Place.Link.UrlResult] =
      val id = externalId.trim
      if id.isEmpty then None
      else
        scheme.trim.toLowerCase(java.util.Locale.ROOT) match
          case "wikidata" => Some(s"https://www.wikidata.org/wiki/$id")
          case "geonames" => Some(s"https://www.geonames.org/$id")
          case "pleiades" => Some(s"https://pleiades.stoa.org/places/$id")
          case "tgn"      => Some(s"http://vocab.getty.edu/page/tgn/$id")
          case "whg"      => Some(s"https://whgazetteer.org/places/$id/portal")
          case "commons"  => Some(s"https://commons.wikimedia.org/wiki/Category:${id.replace(' ', '_')}")
          case _          => None

    def schemeTitle(scheme: com.histmersion.api.catalogue.Place.Link.SchemeTitleScheme): com.histmersion.api.catalogue.Place.Link.SchemeTitleResult = scheme.trim.toLowerCase(java.util.Locale.ROOT) match
      case "wikidata" => "Wikidata"
      case "geonames" => "GeoNames"
      case "pleiades" => "Pleiades"
      case "tgn"      => "Getty TGN"
      case "whg"      => "World Historical Gazetteer"
      case "commons"  => "Commons category"
      case other      => other

  final case class Match(placeId: com.histmersion.api.catalogue.Place.Id, importSource: Option[Match.ImportSource], native: Match.Native = true)

  object Match:
    type ImportSource = ImportSource.T
    object ImportSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Native = Native.T
    object Native extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class MergeIn(into: com.histmersion.api.catalogue.Place.Id, reason: Option[MergeIn.Reason] = None)
  object MergeIn:
    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[MergeIn] = KindlingsSchema
      .derived[MergeIn]
      .schema
      .modify(_.into)(_.description("the id of the place it is the same as"))
    given Codec.AsObject[MergeIn] = StrictJson.codec(KindlingsCodecAsObject.derived[MergeIn])

  final case class Name(
      name: Name.Value,
      lang: Name.Lang = "",
      period: Option[Period] = None,
      open: Option[OpenSide] = None
  ):
    def bounds: (Option[com.histmersion.api.catalogue.Place.Name.BoundsResultPart1], Option[com.histmersion.api.catalogue.Place.Name.BoundsResultPart2]) = OpenSide.bounds(period, open)

  object Name:
    type BoundsResultPart1 = BoundsResultPart1.T
    object BoundsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BoundsResultPart2 = BoundsResultPart2.T
    object BoundsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Value = Value.T
    object Value extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Lang = Lang.T
    object Lang extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class NameAdd(name: NameAdd.Name, lang: Option[NameAdd.Lang] = None) derives KindlingsSchema, com.histmersion.api.json.Schema
  object NameAdd:

    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Lang = Lang.T
    object Lang extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Codec.AsObject[NameAdd] = StrictJson.codec(KindlingsCodecAsObject.derived[NameAdd])

  final case class NameChanged(
      placeId: com.histmersion.api.catalogue.Place.Id,
      label: NameChanged.Label,
      name: NameChanged.Name,
      lang: NameChanged.Lang,
      added: NameChanged.Added,
      changed: NameChanged.Changed,
      wasImported: NameChanged.WasImported = false
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object NameChanged:
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Lang = Lang.T
    object Lang extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Added = Added.T
    object Added extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Changed = Changed.T
    object Changed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WasImported = WasImported.T
    object WasImported extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class NameRemove(name: NameRemove.Name, lang: Option[NameRemove.Lang] = None) derives KindlingsSchema, com.histmersion.api.json.Schema
  object NameRemove:

    type Name = Name.T
    object Name extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Lang = Lang.T
    object Lang extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Codec.AsObject[NameRemove] = StrictJson.codec(KindlingsCodecAsObject.derived[NameRemove])

  final case class Override(
      placeId: com.histmersion.api.catalogue.Place.Id,
      label: Override.Label,
      kind: Option[Override.Kind],
      periodStart: Option[com.histmersion.api.catalogue.Year],
      periodEnd: Option[com.histmersion.api.catalogue.Year],
      periodSet: Override.PeriodSet,
      reason: Option[Override.Reason],
      sourceKind: Override.SourceKind,
      sourcePeriodStart: Option[Override.SourcePeriodStart],
      sourcePeriodEnd: Option[Override.SourcePeriodEnd],
      updatedAt: Override.UpdatedAt,
      lat: Option[com.histmersion.api.catalogue.Latitude] = None,
      lon: Option[com.histmersion.api.catalogue.Longitude] = None,
      sourceLat: Option[Override.SourceLat] = None,
      sourceLon: Option[Override.SourceLon] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Override:
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodSet = PeriodSet.T
    object PeriodSet extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SourceKind = SourceKind.T
    object SourceKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SourcePeriodStart = SourcePeriodStart.T
    object SourcePeriodStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SourcePeriodEnd = SourcePeriodEnd.T
    object SourcePeriodEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SourceLat = SourceLat.T
    object SourceLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SourceLon = SourceLon.T
    object SourceLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class OverrideChanged(
      placeId: com.histmersion.api.catalogue.Place.Id,
      label: OverrideChanged.Label,
      reset: OverrideChanged.Reset,
      kindBefore: OverrideChanged.KindBefore,
      kindAfter: OverrideChanged.KindAfter,
      periodBefore: OverrideChanged.PeriodBefore,
      periodAfter: OverrideChanged.PeriodAfter,
      findingsBefore: OverrideChanged.FindingsBefore,
      findingsAfter: OverrideChanged.FindingsAfter,
      unlocated: OverrideChanged.Unlocated,
      latBefore: Option[OverrideChanged.LatBefore] = None,
      lonBefore: Option[OverrideChanged.LonBefore] = None,
      latAfter: Option[OverrideChanged.LatAfter] = None,
      lonAfter: Option[OverrideChanged.LonAfter] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object OverrideChanged:
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reset = Reset.T
    object Reset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KindBefore = KindBefore.T
    object KindBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type KindAfter = KindAfter.T
    object KindAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodBefore = PeriodBefore.T
    object PeriodBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodAfter = PeriodAfter.T
    object PeriodAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FindingsBefore = FindingsBefore.T
    object FindingsBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FindingsAfter = FindingsAfter.T
    object FindingsAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Unlocated = Unlocated.T
    object Unlocated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LatBefore = LatBefore.T
    object LatBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LonBefore = LonBefore.T
    object LonBefore extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LatAfter = LatAfter.T
    object LatAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type LonAfter = LonAfter.T
    object LonAfter extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]


  enum OverrideEdit:
    case Reset

    case Put(
        kind: Option[String],
        period: Option[(Option[Long], Option[Long])],
        reason: Option[String],
        point: Option[GeoPoint] = None
    )

  final case class OverrideSet(
      kind: Option[OverrideSet.Kind] = None,
      periodStart: Option[com.histmersion.api.catalogue.Year] = None,
      periodEnd: Option[com.histmersion.api.catalogue.Year] = None,
      reason: Option[OverrideSet.Reason] = None,
      reset: OverrideSet.Reset = false,
      pointLat: Option[OverrideSet.PointLat] = None,
      pointLon: Option[OverrideSet.PointLon] = None
  )
  object OverrideSet:
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Reset = Reset.T
    object Reset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PointLat = PointLat.T
    object PointLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PointLon = PointLon.T
    object PointLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Schema[OverrideSet] = KindlingsSchema
      .derived[OverrideSet]
      .schema
      .modify(_.kind)(_.description(s"one of ${com.histmersion.api.catalogue.Place.Kind.all.mkString(", ")}"))
      .modify(_.reason)(
        _.description("why: the evidence the decision rests on (required with a point: where the point comes from)")
      )
      .modify(_.pointLat)(
        _.description("latitude of the place's corrected point (with pointLon); only for a place known by a point")
      )
      .modify(_.pointLon)(_.description("longitude of the place's corrected point (with pointLat)"))
    given Codec.AsObject[OverrideSet] = StrictJson.codec(KindlingsCodecAsObject.derived[OverrideSet])

  object Overrides:
    type InvalidMsg = InvalidMsg.T
    object InvalidMsg extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReasonResultPart2 = ReasonResultPart2.T
    object ReasonResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ReasonRaw = ReasonRaw.T
    object ReasonRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParsePointResultPart1 = ParsePointResultPart1.T
    object ParsePointResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParsePointResultPart2Part1 = ParsePointResultPart2Part1.T
    object ParsePointResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParsePointResultPart2Part2 = ParsePointResultPart2Part2.T
    object ParsePointResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParsePointRaw = ParsePointRaw.T
    object ParsePointRaw extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PointTextResult = PointTextResult.T
    object PointTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PointTextLat = PointTextLat.T
    object PointTextLat extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PointTextLon = PointTextLon.T
    object PointTextLon extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodTextResult = PeriodTextResult.T
    object PeriodTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodTextStart = PeriodTextStart.T
    object PeriodTextStart extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeriodTextEnd = PeriodTextEnd.T
    object PeriodTextEnd extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val MaxReason = 500

    private def invalid(msg: com.histmersion.api.catalogue.Place.Overrides.InvalidMsg) = Api.Error(msg, Some(Write.CodeInvalid))

    def edit(in: OverrideSet): Either[Api.Error, OverrideEdit] =
      val kind = in.kind.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
      val reason = in.reason.map(_.trim.replaceAll("\\s+", " ")).filter(_.nonEmpty)
      val period = Option.when(in.periodStart.isDefined || in.periodEnd.isDefined)((in.periodStart, in.periodEnd))
      val asked = in.pointLat.isDefined || in.pointLon.isDefined
      if in.reset then
        if kind.isDefined || period.isDefined || asked then
          Left(invalid("`reset` drops the override — pass no kind, period or point with it"))
        else Right(OverrideEdit.Reset)
      else
        for
          _ <- Either.cond(
            kind.isDefined || period.isDefined || asked,
            (),
            invalid("say what to set: a kind, a period (periodStart/periodEnd), a point (pointLat/pointLon), or reset")
          )
          point <-
            if !asked then Right(None)
            else
              (in.pointLat, in.pointLon) match
                case (Some(lat), Some(lon)) =>
                  if lat.isNaN || lon.isNaN || lat < -90 || lat > 90 || lon < -180 || lon > 180 then
                    Left(invalid(s"($lat, $lon) is not a latitude/longitude"))
                  else if reason.isEmpty then
                    Left(
                      invalid(
                        "say where the point comes from (`reason`): a gazetteer value such as a Pleiades " +
                          "representative point or a Wikidata coordinate — never an estimate"
                      )
                    )
                  else Right(Some(GeoPoint(lon, lat)))
                case _ => Left(invalid("a point needs both pointLat and pointLon"))
          _ <- kind.fold(Right(()))(k =>
            Either.cond(
              com.histmersion.api.catalogue.Place.Kind.all.contains(k),
              (),
              invalid(s"kind '$k' is not one of ${com.histmersion.api.catalogue.Place.Kind.all.mkString(", ")}")
            )
          )
          _ <- period match
            case Some((Some(a), Some(b))) if a > b => Left(invalid(s"the period starts ($a) after it ends ($b)"))
            case _                                 => Right(())
          _ <- Either.cond(
            reason.forall(_.length <= MaxReason),
            (),
            invalid(s"a reason is at most $MaxReason characters")
          )
        yield OverrideEdit.Put(kind, period, reason, point)

    def reason(raw: Option[com.histmersion.api.catalogue.Place.Overrides.ReasonRaw]): Either[Api.Error, Option[com.histmersion.api.catalogue.Place.Overrides.ReasonResultPart2]] =
      val r = raw.map(_.trim.replaceAll("\\s+", " ")).filter(_.nonEmpty)
      Either.cond(r.forall(_.length <= MaxReason), r, invalid(s"a reason is at most $MaxReason characters"))

    def parsePoint(raw: com.histmersion.api.catalogue.Place.Overrides.ParsePointRaw): Either[com.histmersion.api.catalogue.Place.Overrides.ParsePointResultPart1, (com.histmersion.api.catalogue.Place.Overrides.ParsePointResultPart2Part1, com.histmersion.api.catalogue.Place.Overrides.ParsePointResultPart2Part2)] =
      raw.split(",", -1).map(_.trim).toList match
        case List(a, b) =>
          (a.toDoubleOption, b.toDoubleOption) match
            case (Some(lat), Some(lon))
                if !lat.isNaN && !lon.isNaN && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180 =>
              Right((lat, lon))
            case (Some(_), Some(_)) =>
              Left(s"'$raw' is not a latitude,longitude (latitude −90..90, longitude −180..180)")
            case _ => Left(s"'$raw' is not a latitude,longitude pair of numbers, e.g. 37.95,58.21")
        case _ => Left(s"'$raw' is not latitude,longitude, e.g. 37.95,58.21")

    def pointText(lat: com.histmersion.api.catalogue.Place.Overrides.PointTextLat, lon: com.histmersion.api.catalogue.Place.Overrides.PointTextLon): com.histmersion.api.catalogue.Place.Overrides.PointTextResult =
      String.format(
        java.util.Locale.ROOT,
        "%.4f %s %.4f %s",
        math.abs(lat),
        if lat < 0 then "S" else "N",
        math.abs(lon),
        if lon < 0 then "W" else "E"
      )

    def periodText(start: Option[com.histmersion.api.catalogue.Place.Overrides.PeriodTextStart], end: Option[com.histmersion.api.catalogue.Place.Overrides.PeriodTextEnd]): com.histmersion.api.catalogue.Place.Overrides.PeriodTextResult = YearFmt.openPeriod(start, end)

  final case class RebuildResult(
      links: RebuildResult.Links,
      byGeometry: RebuildResult.ByGeometry,
      byName: RebuildResult.ByName,
      located: RebuildResult.Located,
      unlocated: RebuildResult.Unlocated,
      places: RebuildResult.Places
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object RebuildResult:
    type Links = Links.T
    object Links extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ByGeometry = ByGeometry.T
    object ByGeometry extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ByName = ByName.T
    object ByName extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Located = Located.T
    object Located extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Unlocated = Unlocated.T
    object Unlocated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Places = Places.T
    object Places extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Record(
      dateSource: Option[Record.DateSource] = None,
      periods: List[Record.Periods] = Nil,
      designations: List[Designation] = Nil,
      registers: List[Register] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def isEmpty: com.histmersion.api.catalogue.Place.Record.IsEmptyResult = dateSource.isEmpty && periods.isEmpty && designations.isEmpty && registers.isEmpty

  object Record:
    type IsEmptyResult = IsEmptyResult.T
    object IsEmptyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DateSourceTextResult = DateSourceTextResult.T
    object DateSourceTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DateSourceTextS = DateSourceTextS.T
    object DateSourceTextS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type DateSource = DateSource.T
    object DateSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Periods = Periods.T
    object Periods extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def dateSourceText(s: com.histmersion.api.catalogue.Place.Record.DateSourceTextS): com.histmersion.api.catalogue.Place.Record.DateSourceTextResult = s.trim.toLowerCase match
      case "wikidata-p580"                    => "Wikidata: its start and end time"
      case "wikidata-p571"                    => "Wikidata: its inception"
      case "wikidata-p576"                    => "Wikidata: when it was dissolved or destroyed"
      case p if p.startsWith("p2348-periodo") => "Wikidata: its time period, dated by PeriodO"
      case other                              => other

  final case class Register(property: Register.Property, id: Register.Id) derives KindlingsCodecAsObject, KindlingsSchema:
    def name: com.histmersion.api.catalogue.Place.Register.NameResult = Register.names.getOrElse(property, property)

  object Register:
    type NameResult = NameResult.T
    object NameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NamesPart1 = NamesPart1.T
    object NamesPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NamesPart2 = NamesPart2.T
    object NamesPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Property = Property.T
    object Property extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Id = Id.T
    object Id extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val names: Map[com.histmersion.api.catalogue.Place.Register.NamesPart1, com.histmersion.api.catalogue.Place.Register.NamesPart2] = Map(
      "P13741" -> "Archaeological Cadastre (Greece)",
      "P2186" -> "Wiki Loves Monuments",
      "P9951" -> "Greek Castles",
      "P8218" -> "Archaeology in Greece Online",
      "P8068" -> "ToposText",
      "P1936" -> "DARE (Digital Atlas of the Roman Empire)"
    )

  final case class Relation(
      childId: Relation.ChildId,
      parentId: Relation.ParentId,
      kind: Relation.Kind = Relation.PartOf,
      period: Option[Period] = None,
      open: Option[OpenSide] = None
  ):
    def bounds: (Option[com.histmersion.api.catalogue.Place.Relation.BoundsResultPart1], Option[com.histmersion.api.catalogue.Place.Relation.BoundsResultPart2]) = OpenSide.bounds(period, open)

  object Relation:
    type BoundsResultPart1 = BoundsResultPart1.T
    object BoundsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type BoundsResultPart2 = BoundsResultPart2.T
    object BoundsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type All = All.T
    object All extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PartOfKinds = PartOfKinds.T
    object PartOfKinds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type ChildId = ChildId.T
    object ChildId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParentId = ParentId.T
    object ParentId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val PartOf = "part_of"
    val PartOfAdmin = "part_of_admin"
    val PartOfRegional = "part_of_regional"
    val PartOfPhysical = "part_of_physical"
    val Succeeds = "succeeds"
    val SameAs = "same_as"
    val VersionOf = "version_of"
    val CapitalOf = "capital_of"
    val all: List[com.histmersion.api.catalogue.Place.Relation.All] =
      List(PartOf, PartOfAdmin, PartOfRegional, PartOfPhysical, Succeeds, SameAs, VersionOf, CapitalOf)
    val partOfKinds: Set[com.histmersion.api.catalogue.Place.Relation.PartOfKinds] = Set(PartOf, PartOfAdmin, PartOfRegional, PartOfPhysical)

  final case class RemoveIn(reason: Option[RemoveIn.Reason] = None) derives KindlingsSchema, com.histmersion.api.json.Schema
  object RemoveIn:

    type Reason = Reason.T
    object Reason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given Codec.AsObject[RemoveIn] = StrictJson.codec(KindlingsCodecAsObject.derived[RemoveIn])

  object Shape:
    type IsAreaResult = IsAreaResult.T
    object IsAreaResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsAreaKind = IsAreaKind.T
    object IsAreaKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PointOnlyAreaResult = PointOnlyAreaResult.T
    object PointOnlyAreaResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PointOnlyAreaKind = PointOnlyAreaKind.T
    object PointOnlyAreaKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PointOnlyAreaHasBoundary = PointOnlyAreaHasBoundary.T
    object PointOnlyAreaHasBoundary extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type NoBoundaryNote = NoBoundaryNote.T
    object NoBoundaryNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsPeopleResult = IsPeopleResult.T
    object IsPeopleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type IsPeopleKind = IsPeopleKind.T
    object IsPeopleKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PeopleNote = PeopleNote.T
    object PeopleNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def isArea(kind: com.histmersion.api.catalogue.Place.Shape.IsAreaKind): com.histmersion.api.catalogue.Place.Shape.IsAreaResult =
      kind == com.histmersion.api.catalogue.Place.Kind.Region || kind == com.histmersion.api.catalogue.Place.Kind.Polity

    def pointOnlyArea(kind: com.histmersion.api.catalogue.Place.Shape.PointOnlyAreaKind, hasBoundary: com.histmersion.api.catalogue.Place.Shape.PointOnlyAreaHasBoundary): com.histmersion.api.catalogue.Place.Shape.PointOnlyAreaResult = isArea(kind) && !hasBoundary

    val NoBoundaryNote: com.histmersion.api.catalogue.Place.Shape.NoBoundaryNote = "point only — boundary not yet available"

    def isPeople(kind: com.histmersion.api.catalogue.Place.Shape.IsPeopleKind): com.histmersion.api.catalogue.Place.Shape.IsPeopleResult = kind == com.histmersion.api.catalogue.Place.Kind.People

    val PeopleNote: com.histmersion.api.catalogue.Place.Shape.PeopleNote = "a people or tribe — placed at a representative point, not an area"

  final case class Summary(
      id: com.histmersion.api.catalogue.Place.Id,
      label: Summary.Label,
      kind: Summary.Kind,
      periodStart: Option[com.histmersion.api.catalogue.Year],
      periodEnd: Option[com.histmersion.api.catalogue.Year],
      source: Option[Summary.Source],
      names: Summary.Names,
      contested: Summary.Contested = false,
      dating: Dating = Dating.Stated,
      importSource: Option[Summary.ImportSource] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema:
    def when: com.histmersion.api.catalogue.Place.Summary.WhenResult = Dating.describe(dating, periodStart, periodEnd)

  object Summary:
    type WhenResult = WhenResult.T
    object WhenResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Source = Source.T
    object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Names = Names.T
    object Names extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Contested = Contested.T
    object Contested extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ImportSource = ImportSource.T
    object ImportSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]


  enum TimeBasis:
    case Dated, Timeless, Modern, Grouping, Findings

    def wire: com.histmersion.api.catalogue.Place.TimeBasis.WireResult = this match
      case Dated    => "dated"
      case Timeless => "timeless"
      case Modern   => "modern"
      case Grouping => "grouping"
      case Findings => "findings"

  object TimeBasis:
    type WireResult = WireResult.T
    object WireResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ParseS = ParseS.T
    object ParseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FromWireS = FromWireS.T
    object FromWireS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfKind = OfKind.T
    object OfKind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfModern = OfModern.T
    object OfModern extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfImported = OfImported.T
    object OfImported extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfUndated = OfUndated.T
    object OfUndated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OfHasGeometry = OfHasGeometry.T
    object OfHasGeometry extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type VisibleResult = VisibleResult.T
    object VisibleResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type VisibleHi = VisibleHi.T
    object VisibleHi extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type VisibleModernFrom = VisibleModernFrom.T
    object VisibleModernFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FindingsNote = FindingsNote.T
    object FindingsNote extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    def parse(s: com.histmersion.api.catalogue.Place.TimeBasis.ParseS): Option[TimeBasis] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

    given WireEnumValues[TimeBasis]:
      def all: Array[TimeBasis] = TimeBasis.values
      def wire(e: TimeBasis): com.histmersion.api.catalogue.Place.TimeBasis.WireResult = e.wire
      def fromWire(s: com.histmersion.api.catalogue.Place.TimeBasis.FromWireS): Option[TimeBasis] = parse(s)
    given io.circe.Encoder[TimeBasis] = WireEnum.encoder
    given io.circe.Decoder[TimeBasis] = WireEnum.decoder
    given Schema[TimeBasis] = WireEnum.schema

    def of(kind: com.histmersion.api.catalogue.Place.TimeBasis.OfKind, modern: com.histmersion.api.catalogue.Place.TimeBasis.OfModern, imported: com.histmersion.api.catalogue.Place.TimeBasis.OfImported, undated: com.histmersion.api.catalogue.Place.TimeBasis.OfUndated, hasGeometry: com.histmersion.api.catalogue.Place.TimeBasis.OfHasGeometry): TimeBasis =
      if !imported && (kind == com.histmersion.api.catalogue.Place.Kind.Feature || (modern && kind == com.histmersion.api.catalogue.Place.Kind.Region))
      then Timeless
      else if modern then Modern
      else if undated && !hasGeometry then Grouping
      else if undated then Findings
      else Dated

    def visible(basis: TimeBasis, hi: com.histmersion.api.catalogue.Place.TimeBasis.VisibleHi, modernFrom: com.histmersion.api.catalogue.Place.TimeBasis.VisibleModernFrom, overlaps: => Boolean, evidence: => Boolean): com.histmersion.api.catalogue.Place.TimeBasis.VisibleResult =
      basis match
        case Timeless | Grouping => true
        case Modern              => hi >= modernFrom
        case Dated               => overlaps
        case Findings            => evidence

    val FindingsNote: com.histmersion.api.catalogue.Place.TimeBasis.FindingsNote = "dated by its findings — the place has no date of its own"

  final case class TreeRow(
      id: com.histmersion.api.catalogue.Place.Id,
      label: TreeRow.Label,
      kind: TreeRow.Kind,
      depth: TreeRow.Depth,
      findings: TreeRow.Findings,
      contested: TreeRow.Contested = false
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object TreeRow:
    type Label = Label.T
    object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Kind = Kind.T
    object Kind extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Depth = Depth.T
    object Depth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Findings = Findings.T
    object Findings extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Contested = Contested.T
    object Contested extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]


  final case class Type(
      placeId: com.histmersion.api.catalogue.Place.Id,
      typeLabel: Type.TypeLabel,
      period: Option[Period] = None,
      citekey: Option[com.histmersion.api.library.Citekey] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema, com.histmersion.api.json.Schema

  object Type:
    type TypeLabel = TypeLabel.T
    object TypeLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type Id = Id.T
  object Id extends io.github.iltotore.iron.RefinedType[String, io.github.iltotore.iron.constraint.any.Pure]

