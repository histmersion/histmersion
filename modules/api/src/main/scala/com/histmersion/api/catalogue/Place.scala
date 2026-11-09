package com.histmersion
package api.catalogue
import com.histmersion.api.Problem
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
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Citekey
final case class Place(
    id: Place.Id,
    label: Place.Label,
    kind: Place.KindValue,
    geometry: Option[Place.Geometry] = None,
    period: Option[Period] = None,
    source: Option[Place.Source] = None,
    properties: Option[Place.Properties] = None,
    open: Option[OpenSide] = None
):
  def bounds: (Option[Year], Option[Year]) = OpenSide.bounds(period, open)

object Place:
  type Depth = Depth.T
  object Depth extends Newtype[Int, Pure]
  type Detail = Detail.T
  object Detail extends Newtype[Boolean, Pure]
  type Region = Region.T
  object Region extends Newtype[String, Pure]

  /** The five `kind`s the store recognises. Free text in the column (an importer may carry its own vocabulary), but
    * these are what the CLI/UI offer and what api's `Lpf` maps foreign type labels onto. `people` (2026-10-09) is a
    * people or a civic tribe — the Ubii, the Athenian tribe Aiantis: a group of persons that a gazetteer places at a
    * representative point, never an area (Pleiades files them as regions; `lint --rule place-kind` moves them).
    */

  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type KindValue = KindValue.T
  object KindValue extends Newtype[String, Pure]
  type Geometry = Geometry.T
  object Geometry extends Newtype[String, Pure]
  type Source = Source.T
  object Source extends Newtype[String, Pure]
  type Properties = Properties.T
  object Properties extends Newtype[String, Pure]

  object Kind:
    type Parsed = Parsed.T
    object Parsed extends Newtype[String, Pure]
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
    type All = All.T
    object All extends Newtype[String, Pure]
    type ParseS = ParseS.T
    object ParseS extends Newtype[String, Pure]

    val Region = "region"
    val Polity = "polity"
    val Site = "site"
    val Feature = "feature"
    val People = "people"
    val all: List[All] = List(All(Region), All(Polity), All(Site), All(Feature), All(People))

    /** Map a foreign type label (LPF `types[].label`, an AAT id, a Natural Earth class) onto a kind; `None` when
      * nothing recognisable is there, so the caller's default wins.
      */
    def parse(s: ParseS): Option[Parsed] =
      val t = (s).value.trim.toLowerCase
      if t.isEmpty then None
      else if all.contains(t) then Some(Parsed(t))
      else if t.contains("people") || t.contains("tribe") then Some(Parsed(People))
      else if t.contains("settlement") || t.contains("site") || t.contains("city") || t.contains("town")
        || t.contains("village") || t.contains("fort") || t.contains("temple") || t.contains("sanctuary")
      then Some(Parsed(Site))
      else if t.contains("kingdom") || t.contains("empire") || t.contains("polity") || t.contains("state")
        || t.contains("dynasty") || t.contains("country") || t.contains("nation")
      then Some(Parsed(Polity))
      else if t.contains("region") || t.contains("continent") || t.contains("province")
        || t.contains("territory") || t.contains("area")
      then Some(Parsed(Region))
      else if t.contains("river") || t.contains("mountain") || t.contains("island") || t.contains("lake")
        || t.contains("feature") || t.contains("pass") || t.contains("valley")
      then Some(Parsed(Feature))
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
  enum Precision derives CanEqual:
    case Stated, GridCell

    def wire: Precision.Wire = this match
      case Stated   => Precision.Wire("stated")
      case GridCell => Precision.Wire("grid-cell")

  object Precision:
    type CellsMicroNumber = CellsMicroNumber.T
    object CellsMicroNumber extends Newtype[Long, Pure]
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    /** The atlas grids, as cell sizes in micro-degrees: 5°, 1° and ½°. */
    type CellsMicro = CellsMicroValue
    type CellsMicroValue = CellsMicroNumber
    val CellsMicroValue: CellsMicroNumber.type = CellsMicroNumber

    val CellsMicro: List[CellsMicro] =
      List(CellsMicroNumber(5000000L), CellsMicroNumber(1000000L), CellsMicroNumber(500000L))

    /** Is (`lon`, `lat`) — rounded to a micro-degree — the centre of a cell of one of the grids, in both coordinates?
      * Pure.
      */
    def gridCentre(lon: Longitude, lat: Latitude): Boolean =
      val x = math.round((lon).value * 1e6)
      val y = math.round((lat).value * 1e6)
      (CellsMicro.exists(c =>
        Math.floorMod(x, (c).value) == (c).value / 2 && Math.floorMod(y, (c).value) == (c).value / 2
      ))

    /** The precision of the point of place `placeId`. */
    def of(placeId: Id, lon: Longitude, lat: Latitude): Precision =
      if placeId.isPleiades && ((gridCentre(lon, lat)) == (true)) then GridCell else Stated

    /** What a place says about a grid-cell point ("location: approximate (atlas grid square)" on a card). */
    val Approximate: Card.Note = Card.Note("approximate (atlas grid square)")

    def parse(s: Wire): Option[Precision] = values.find(_.wire.value == Ascii.lower(((s).value.trim)))

    given WireEnumValues[Precision]:
      def all: Array[Precision] = values
      def wire(e: Precision): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
      def fromWire(s: WireEnumValues.Wire): Option[Precision] = parse(Wire((s).value))

  final case class Add(
      label: Add.Label,
      kind: Add.Kind,
      lon: Longitude,
      lat: Latitude,
      source: Add.Source,
      id: Option[Id] = None,
      names: List[Add.Names] = Nil,
      lang: Option[Add.Lang] = None,
      periodStart: Option[Year] = None,
      periodEnd: Option[Year] = None
  )
  object Add:
    export Place.Label
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    export Place.Source
    export Name.{Lang, Value as Names}

    given Schema[Add] = KindlingsSchema
      .derived[Add]
      .schema
      .modify(_.kind)(_.description(s"one of ${Place.Kind.all.mkString(", ")}"))
      .modify(_.source)(_.description("where the coordinates come from (a gazetteer id, a map, a publication)"))
    given Codec.AsObject[Add] = StrictJson.codec(KindlingsCodecAsObject.derived[Add])

  /** What a place's page shows of the gazetteer: the row, its geometry, the hierarchy around it and its records. The
    * gazetteer's own record ([[PlaceEntry]]) and the page the server answers (`PlaceDetail`, which adds the place's
    * pictures) both are one, so the page renders from either.
    */
  trait Card:
    def place: Summary
    def geometry: Option[Geometry]
    def parents: List[Summary]
    def children: List[Summary]
    def findings: Counts.Findings
    def asOf: Option[Year]
    def contested: Entry.Contested
    def footprints: List[Footprint]
    def links: List[Link]
    def types: List[Type]
    def counts: Option[Counts]
    def hasBoundary: Entry.HasBoundary
    def descriptions: List[Description]
    def timeBasis: TimeBasis
    def locationPrecision: Precision
    def siteType: Option[Entry.SiteType]
    def record: Record

    def pointOnlyArea: Boolean = geometry.isDefined && Shape.pointOnlyArea(place.kind, hasBoundary.value)

    def shapeNote: Option[Card.Note] =
      Option
        .when(((pointOnlyArea) == (true)))(Shape.NoBoundaryNote)
        .orElse(Option.when(geometry.isDefined && ((Shape.isPeople(place.kind)) == (true)))(Shape.PeopleNote))

    def locationNote: Option[Card.Note] =
      Option.when(locationPrecision == Precision.GridCell)(
        Precision.Approximate
      )

    def shapeNotes: List[Card.Note] = shapeNote.toList ++ locationNote.map(n => Card.Note(s"location: $n")).toList

    def datedByFindings: Boolean = (asOf.isDefined && timeBasis == TimeBasis.Findings)

  object Card:
    type Note = Note.T
    object Note extends Newtype[String, Pure]

  final case class Counts(
      sources: Counts.Sources,
      findings: Counts.Findings,
      articles: Counts.Articles,
      events: Counts.Events
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Counts:
    type Sources = Sources.T
    object Sources extends Newtype[Int, Pure]
    type Findings = Findings.T
    object Findings extends Newtype[Int, Pure]
    type Articles = Articles.T
    object Articles extends Newtype[Int, Pure]
    type Events = Events.T
    object Events extends Newtype[Int, Pure]

  enum Dating derives CanEqual:
    case Stated, EndOnly, Unknown

    def wire: Dating.Wire = this match
      case Stated  => Dating.Wire("stated")
      case EndOnly => Dating.Wire("end-only")
      case Unknown => Dating.Wire("unknown")

  object Dating:
    type Description = Description.T
    object Description extends Newtype[String, Pure]
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    def parse(s: Wire): Option[Dating] = values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))

    given WireEnumValues[Dating]:
      def all: Array[Dating] = values
      def wire(e: Dating): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
      def fromWire(s: WireEnumValues.Wire): Option[Dating] = parse(Wire((s).value))
    given io.circe.Encoder[Dating] = WireEnum.encoder
    given io.circe.Decoder[Dating] = WireEnum.decoder
    given Schema[Dating] = WireEnum.schema

    def describe(dating: Dating, start: Option[Year], end: Option[Year]): Description = dating match
      case Unknown => Description("date unknown")
      case EndOnly => Description(end.fold("date unknown")(e => s"end ${YearFmt(e)}, start unknown"))
      case Stated  => Description(YearFmt.openPeriod(start, end, none = ("")))

  final case class Description(
      text: Description.Text,
      lang: Description.Lang = Name.Lang(""),
      source: Description.Source = _root_.com.histmersion.api.catalogue.Place.Source("")
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Description:
    type Text = Text.T
    object Text extends Newtype[String, Pure]
    export Name.Lang
    export Place.Source

  final case class Designation(
      id: Designation.Id,
      label: Option[Designation.Label] = None,
      start: Option[Designation.Start] = None,
      end: Option[Designation.End] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema

  object Designation:
    type Id = Id.T
    object Id extends Newtype[String, Pure]
    type Label = Label.T
    object Label extends Newtype[String, Pure]
    type Start = Start.T
    object Start extends Newtype[String, Pure]
    type End = End.T
    object End extends Newtype[String, Pure]

  object Edits:
    type Csv = Csv.T
    object Csv extends Newtype[String, Pure]

    val LocalPrefix = "local_"

    private val LangShape = "^[a-z]{2,3}(-[a-z0-9]{2,8})*$".r
    private val IdShape = "^[a-z0-9][a-z0-9_.-]*$".r

    private def invalid(msg: String) = Api.Error(Problem(msg), Some(Api.Error.Code(Write.CodeInvalid)))

    def name(raw: Name.Value): Either[Api.Error, Name.Value] =
      val n = (raw).value.trim.replaceAll("\\s+", " ")
      if n.isEmpty then Left(invalid("the name is empty"))
      else if n.length > MaxName then Left(invalid(s"a place name is at most $MaxName characters"))
      else Right(Name.Value(n))

    val MaxName = 200

    def lang(raw: Option[Name.Lang]): Either[Api.Error, Name.Lang] =
      raw.map((_).value.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty) match
        case None                            => Right(Name.Lang(""))
        case Some(l) if LangShape.matches(l) => Right(Name.Lang(l))
        case Some(l)                         => Left(invalid(s"'$l' is not a language tag (e.g. en, grc, pl, sr-latn)"))

    def defaultId(label: Label): Id =
      Id(LocalPrefix + Geocode.key(label.value).value.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "").take(64))

    def toInput(in: Add): Either[Api.Error, Input] =
      val label = in.label.value.trim.replaceAll("\\s+", " ")
      val kind = in.kind.value.trim.toLowerCase(java.util.Locale.ROOT)
      val id = in.id.map(_.value).map(_.trim).filter(_.nonEmpty).getOrElse(defaultId(Label(label)).value)
      for
        _ <- Either.cond(label.nonEmpty, (), invalid("the label is empty"))
        _ <- Either.cond(
          Kind.all.contains(kind),
          (),
          invalid(s"kind '$kind' is not one of ${Kind.all.mkString(", ")}")
        )
        _ <- Either.cond(id != LocalPrefix, (), invalid(s"'$label' gives no id — pass one (`id`)"))
        _ <- Either.cond(
          IdShape.matches(id),
          (),
          invalid(s"'$id' is not a place id (lower-case letters, digits, '_', '.' and '-')")
        )
        _ <- Either.cond(
          in.lon.value >= -180 && in.lon.value <= 180 && in.lat.value >= -90 && in.lat.value <= 90 &&
            !in.lon.value.isNaN && !in.lat.value.isNaN,
          (),
          invalid(s"(${in.lon}, ${in.lat}) is not a longitude/latitude")
        )
        _ <- Either.cond(
          in.source.value.trim.nonEmpty,
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
        extra <- in.names.foldLeft[Either[Api.Error, List[Name.Value]]](Right(Nil))((acc, n) =>
          acc.flatMap(ns => name(n).map(ns :+ _))
        )
      yield
        val point = Json
          .obj(
            "type" -> Json.fromString("Point"),
            "coordinates" -> Json.arr(Json.fromDoubleOrNull(in.lon.value), Json.fromDoubleOrNull(in.lat.value))
          )
          .noSpaces
        val names =
          (Name(Name.Value(label)) :: extra.map(Name(_, l))).distinctBy(n => (n.name.value.toLowerCase, n.lang))
        Input(
          Place(
            Id(id),
            Label(label),
            KindValue(kind),
            Some(Geometry(point)),
            period,
            Some(Source(in.source.value.trim)),
            Some(Properties(Json.obj("added" -> Json.True).noSpaces))
          ),
          names
        )

    def csv(rows: List[ManualPlaceName]): Csv =
      def cell(s: String) =
        if (s).exists(c => c == ',' || c == '"' || c == '\n') then "\"" + (s).replace("\"", "\"\"") + "\"" else s
      Csv(
        ("place_id,name,lang,why" :: rows.map(r =>
          List(r.placeId.value, r.name.value, r.lang.value, s"added by hand to ${r.label}").map(cell).mkString(",")
        ))
          .mkString("", "\n", "\n")
      )

  /** The gazetteer's record of one place: the row, its GeoJSON geometry and the hierarchy around it at `asOf`, its
    * footprints, links, types and records; `counts`, what the corpus holds for it, filled by whoever shows its card.
    * Not on the wire: the server answers the page (`PlaceDetail`), which adds the place's pictures.
    */
  final case class Entry(
      place: Summary,
      geometry: Option[Entry.Geometry],
      parents: List[Summary],
      children: List[Summary],
      findings: Counts.Findings,
      asOf: Option[Year],
      contested: Entry.Contested = Entry.Contested(false),
      footprints: List[Footprint] = Nil,
      links: List[Link] = Nil,
      types: List[Type] = Nil,
      counts: Option[Counts] = None,
      hasBoundary: Entry.HasBoundary = Entry.HasBoundary(false),
      descriptions: List[Description] = Nil,
      timeBasis: TimeBasis = TimeBasis.Dated,
      /** `grid-cell` for a Pleiades point that is only the centre of an atlas grid square. */
      locationPrecision: Precision = Precision.Stated,
      siteType: Option[Entry.SiteType] = None,
      record: Record = Record()
  ) extends Card

  object Entry:
    export Place.Geometry
    type Contested = Contested.T
    object Contested extends Newtype[Boolean, Pure]
    type HasBoundary = HasBoundary.T
    object HasBoundary extends Newtype[Boolean, Pure]
    type SiteType = SiteType.T
    object SiteType extends Newtype[String, Pure]

  /** Follows the Pleiades Place/Location split: one place, many dated footprints. */
  final case class Footprint(
      id: Footprint.Id,
      placeId: Id,
      role: Footprint.Role = Footprint.Role("extent"),
      period: Option[Period] = None,
      certainty: Option[Footprint.Certainty] = None,
      accuracyM: Option[Footprint.AccuracyM] = None,
      citekey: Option[Citekey] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema
  object Footprint:

    type Roles = RolesValue
    type RolesValue = Role
    val RolesValue: Role.type = Role
    type Certainties = CertaintiesValue
    type CertaintiesValue = Certainty
    val CertaintiesValue: Certainty.type = Certainty

    type Id = Id.T
    object Id extends Newtype[Int, Pure]
    type Role = Role.T
    object Role extends Newtype[String, Pure]
    type Certainty = Certainty.T
    object Certainty extends Newtype[String, Pure]
    type AccuracyM = AccuracyM.T
    object AccuracyM extends Newtype[Float, Pure]

    val Roles: List[Roles] =
      List(Role("extent"), Role("core"), Role("maximal"), Role("frontier"), Role("seat"), Role("snapshot"))
    val Certainties: List[Certainties] = List(Certainty("definite"), Certainty("approximate"), Certainty("uncertain"))

  final case class Gone(
      placeId: Id,
      label: Gone.Label,
      into: Option[Gone.Into],
      intoLabel: Option[Gone.IntoLabel],
      findingsBefore: Gone.FindingsBefore,
      findingsAfter: Gone.FindingsAfter,
      unlocated: Gone.Unlocated
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Gone:
    export Place.Label
    type Into = Into.T
    object Into extends Newtype[String, Pure]
    type IntoLabel = IntoLabel.T
    object IntoLabel extends Newtype[String, Pure]
    type FindingsBefore = FindingsBefore.T
    object FindingsBefore extends Newtype[Int, Pure]
    type FindingsAfter = FindingsAfter.T
    object FindingsAfter extends Newtype[Int, Pure]
    type Unlocated = Unlocated.T
    object Unlocated extends Newtype[Int, Pure]

  final case class Hit(
      id: Id,
      label: Hit.Label,
      kind: Hit.Kind,
      periodStart: Option[Year],
      periodEnd: Option[Year],
      source: Option[Hit.Source],
      names: Hit.Names,
      matchedName: Option[Hit.MatchedName],
      findings: Hit.Findings,
      contested: Hit.Contested = Hit.Contested(false),
      dating: Dating = Dating.Stated,
      importSource: Option[Hit.ImportSource] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema:
    def toSummary: Summary =
      Summary(id, label, kind, periodStart, periodEnd, source, names, contested, dating, importSource)

  object Hit:
    export Place.Label
    export Summary.Kind
    export Place.Source
    export Summary.Names
    type MatchedName = MatchedName.T
    object MatchedName extends Newtype[String, Pure]
    type Findings = Findings.T
    object Findings extends Newtype[Int, Pure]
    export Summary.Contested
    export Match.ImportSource

  final case class ImportResult(
      places: ImportResult.Places,
      names: ImportResult.Names,
      relations: ImportResult.Relations,
      failed: List[ImportResult.Failed] = Nil,
      job: Option[ImportResult.Job] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object ImportResult:
    type Places = Places.T
    object Places extends Newtype[Int, Pure]
    type Names = Names.T
    object Names extends Newtype[Int, Pure]
    type Relations = Relations.T
    object Relations extends Newtype[Int, Pure]
    type Failed = Failed.T
    object Failed extends Newtype[String, Pure]
    type Job = Job.T
    object Job extends Newtype[String, Pure]

  final case class Input(place: Place, names: List[Name] = Nil, relations: List[Relation] = Nil)

  final case class Link(
      placeId: Id,
      scheme: Link.Scheme,
      externalId: Link.ExternalId,
      relation: Link.Relation = Link.Relation("close_match")
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema
  object Link:
    type SchemeTitle = SchemeTitle.T
    object SchemeTitle extends Newtype[String, Pure]
    type Url = Url.T
    object Url extends Newtype[String, Pure]

    type Schemes = SchemesValue
    type SchemesValue = Scheme
    val SchemesValue: Scheme.type = Scheme
    type Relations = RelationsValue
    type RelationsValue = Relation
    val RelationsValue: Relation.type = Relation
    type UrlScheme = Scheme
    val UrlScheme: Scheme.type = Scheme
    type UrlExternalId = ExternalId
    val UrlExternalId: ExternalId.type = ExternalId

    type Scheme = Scheme.T
    object Scheme extends Newtype[String, Pure]
    type ExternalId = ExternalId.T
    object ExternalId extends Newtype[String, Pure]
    type Relation = Relation.T
    object Relation extends Newtype[String, Pure]

    val Schemes: List[Schemes] =
      List(Scheme("pleiades"), Scheme("wikidata"), Scheme("tgn"), Scheme("geonames"), Scheme("whg"))
    val Relations: List[Relations] =
      List(Relation("close_match"), Relation("exact_match"), Relation("broad_match"), Relation("see_also"))

    def url(scheme: UrlScheme, externalId: UrlExternalId): Option[Url] =
      val id = (externalId).value.trim
      if id.isEmpty then None
      else
        (scheme).value.trim.toLowerCase(java.util.Locale.ROOT) match
          case "wikidata" => Some(Url(s"https://www.wikidata.org/wiki/$id"))
          case "geonames" => Some(Url(s"https://www.geonames.org/$id"))
          case "pleiades" => Some(Url(s"https://pleiades.stoa.org/places/$id"))
          case "tgn"      => Some(Url(s"http://vocab.getty.edu/page/tgn/$id"))
          case "whg"      => Some(Url(s"https://whgazetteer.org/places/$id/portal"))
          case "commons"  => Some(Url(s"https://commons.wikimedia.org/wiki/Category:${id.replace(' ', '_')}"))
          case _          => None

    def schemeTitle(scheme: Scheme): SchemeTitle = (scheme).value.trim.toLowerCase(java.util.Locale.ROOT) match
      case "wikidata" => SchemeTitle("Wikidata")
      case "geonames" => SchemeTitle("GeoNames")
      case "pleiades" => SchemeTitle("Pleiades")
      case "tgn"      => SchemeTitle("Getty TGN")
      case "whg"      => SchemeTitle("World Historical Gazetteer")
      case "commons"  => SchemeTitle("Commons category")
      case other      => SchemeTitle(other)

  final case class Match(
      placeId: Id,
      importSource: Option[Match.ImportSource],
      native: Match.Native = Match.Native(true)
  )

  object Match:
    type ImportSource = ImportSource.T
    object ImportSource extends Newtype[String, Pure]
    type Native = Native.T
    object Native extends Newtype[Boolean, Pure]

  final case class MergeIn(into: Id, reason: Option[Problem] = None)
  object MergeIn:

    given Schema[MergeIn] = KindlingsSchema
      .derived[MergeIn]
      .schema
      .modify(_.into)(_.description("the id of the place it is the same as"))
    given Codec.AsObject[MergeIn] = StrictJson.codec(KindlingsCodecAsObject.derived[MergeIn])

  final case class Name(
      name: Name.Value,
      lang: Name.Lang = Name.Lang(""),
      period: Option[Period] = None,
      open: Option[OpenSide] = None
  ):
    def bounds: (Option[Year], Option[Year]) = OpenSide.bounds(period, open)

  object Name:

    type Value = Value.T
    object Value extends Newtype[String, Pure]
    type Lang = Lang.T
    object Lang extends Newtype[String, Pure]

  final case class NameAdd(name: NameAdd.Name, lang: Option[NameAdd.Lang] = None)
      derives KindlingsSchema,
        com.histmersion.api.json.Schema
  object NameAdd:

    type Name = Name.T
    object Name extends Newtype[String, Pure]
    export Place.Name.Lang

    given Codec.AsObject[NameAdd] = StrictJson.codec(KindlingsCodecAsObject.derived[NameAdd])

  final case class NameChanged(
      placeId: Id,
      label: NameChanged.Label,
      name: NameChanged.Name,
      lang: NameChanged.Lang,
      added: NameChanged.Added,
      changed: NameChanged.Changed,
      wasImported: NameChanged.WasImported = NameChanged.WasImported(false)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object NameChanged:
    export Place.Label
    type Name = Name.T
    object Name extends Newtype[String, Pure]
    export Place.Name.Lang
    type Added = Added.T
    object Added extends Newtype[Boolean, Pure]
    type Changed = Changed.T
    object Changed extends Newtype[Boolean, Pure]
    type WasImported = WasImported.T
    object WasImported extends Newtype[Boolean, Pure]

  final case class NameRemove(name: NameRemove.Name, lang: Option[NameRemove.Lang] = None)
      derives KindlingsSchema,
        com.histmersion.api.json.Schema
  object NameRemove:

    type Name = Name.T
    object Name extends Newtype[String, Pure]
    export Place.Name.Lang

    given Codec.AsObject[NameRemove] = StrictJson.codec(KindlingsCodecAsObject.derived[NameRemove])

  final case class Override(
      placeId: Id,
      label: Override.Label,
      kind: Option[Override.Kind],
      periodStart: Option[Year],
      periodEnd: Option[Year],
      periodSet: Override.PeriodSet,
      reason: Option[Problem],
      sourceKind: Override.SourceKind,
      sourcePeriodStart: Option[Year],
      sourcePeriodEnd: Option[Year],
      updatedAt: Override.UpdatedAt,
      lat: Option[Latitude] = None,
      lon: Option[Longitude] = None,
      sourceLat: Option[Latitude] = None,
      sourceLon: Option[Longitude] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Override:
    export Place.Label
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type PeriodSet = PeriodSet.T
    object PeriodSet extends Newtype[Boolean, Pure]
    type SourceKind = SourceKind.T
    object SourceKind extends Newtype[String, Pure]
    type UpdatedAt = UpdatedAt.T
    object UpdatedAt extends Newtype[String, Pure]

  final case class OverrideChanged(
      placeId: Id,
      label: OverrideChanged.Label,
      reset: OverrideChanged.Reset,
      kindBefore: OverrideChanged.KindBefore,
      kindAfter: OverrideChanged.KindAfter,
      periodBefore: OverrideChanged.PeriodBefore,
      periodAfter: OverrideChanged.PeriodAfter,
      findingsBefore: OverrideChanged.FindingsBefore,
      findingsAfter: OverrideChanged.FindingsAfter,
      unlocated: OverrideChanged.Unlocated,
      latBefore: Option[Latitude] = None,
      lonBefore: Option[Longitude] = None,
      latAfter: Option[Latitude] = None,
      lonAfter: Option[Longitude] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object OverrideChanged:
    export Place.Label
    type Reset = Reset.T
    object Reset extends Newtype[Boolean, Pure]
    type KindBefore = KindBefore.T
    object KindBefore extends Newtype[String, Pure]
    type KindAfter = KindAfter.T
    object KindAfter extends Newtype[String, Pure]
    type PeriodBefore = PeriodBefore.T
    object PeriodBefore extends Newtype[String, Pure]
    type PeriodAfter = PeriodAfter.T
    object PeriodAfter extends Newtype[String, Pure]
    type FindingsBefore = FindingsBefore.T
    object FindingsBefore extends Newtype[Int, Pure]
    type FindingsAfter = FindingsAfter.T
    object FindingsAfter extends Newtype[Int, Pure]
    type Unlocated = Unlocated.T
    object Unlocated extends Newtype[Int, Pure]

  enum OverrideEdit derives CanEqual:
    case Reset

    case Put(
        kind: Option[Put.Kind],
        period: Option[(Option[Year], Option[Year])],
        reason: Option[Problem],
        point: Option[GeoPoint] = None
    )

  object OverrideEdit:
    object Put:
      type Kind = Kind.T
      object Kind extends Newtype[String, Pure]

  final case class OverrideSet(
      kind: Option[OverrideSet.Kind] = None,
      periodStart: Option[Year] = None,
      periodEnd: Option[Year] = None,
      reason: Option[Problem] = None,
      reset: OverrideSet.Reset = OverrideSet.Reset(false),
      pointLat: Option[Latitude] = None,
      pointLon: Option[Longitude] = None
  )
  object OverrideSet:
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Reset = Reset.T
    object Reset extends Newtype[Boolean, Pure]

    given Schema[OverrideSet] = KindlingsSchema
      .derived[OverrideSet]
      .schema
      .modify(_.kind)(_.description(s"one of ${Place.Kind.all.mkString(", ")}"))
      .modify(_.reason)(
        _.description("why: the evidence the decision rests on (required with a point: where the point comes from)")
      )
      .modify(_.pointLat)(
        _.description("latitude of the place's corrected point (with pointLon); only for a place known by a point")
      )
      .modify(_.pointLon)(_.description("longitude of the place's corrected point (with pointLat)"))
    given Codec.AsObject[OverrideSet] = StrictJson.codec(KindlingsCodecAsObject.derived[OverrideSet])

  object Overrides:
    type PointText = PointText.T
    object PointText extends Newtype[String, Pure]

    val MaxReason = 500

    private def invalid(msg: String) = Api.Error(Problem(msg), Some(Api.Error.Code(Write.CodeInvalid)))

    def edit(in: OverrideSet): Either[Api.Error, OverrideEdit] =
      val kind = in.kind.map(_.value.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
      val reason = in.reason.map(_.value.trim.replaceAll("\\s+", " ")).filter(_.nonEmpty)
      val period = Option.when(in.periodStart.isDefined || in.periodEnd.isDefined)((in.periodStart, in.periodEnd))
      val asked = in.pointLat.isDefined || in.pointLon.isDefined
      if (in.reset) == OverrideSet.Reset(true) then
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
            (if !asked then Right(None)
             else
               (in.pointLat, in.pointLon) match
                 case (Some(lat), Some(lon)) =>
                   if lat.value.isNaN || lon.value.isNaN || lat.value < -90 || lat.value > 90 || lon.value < -180 || lon.value > 180
                   then Left(invalid(s"($lat, $lon) is not a latitude/longitude"))
                   else if reason.isEmpty then
                     Left(
                       invalid(
                         "say where the point comes from (`reason`): a gazetteer value such as a Pleiades " +
                           "representative point or a Wikidata coordinate — never an estimate"
                       )
                     )
                   else Right(Some(GeoPoint(lon, lat)))
                 case _ => Left(invalid("a point needs both pointLat and pointLon"))
            ): Either[Api.Error, Option[GeoPoint]]
          _ <- kind.fold(Right(()))(k =>
            Either.cond(
              Kind.all.contains(k),
              (),
              invalid(s"kind '$k' is not one of ${Kind.all.mkString(", ")}")
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
        yield OverrideEdit.Put(
          kind.map(OverrideEdit.Put.Kind(_)),
          period,
          reason.map(Problem(_)),
          point
        )

    def reason(raw: Option[Problem]): Either[Api.Error, Option[Problem]] =
      val r = raw.map((_).value.trim.replaceAll("\\s+", " ")).filter(_.nonEmpty)
      Either.cond(
        r.forall(_.length <= MaxReason),
        (r).map(refactorValue10 => Problem(refactorValue10)),
        invalid(s"a reason is at most $MaxReason characters")
      )

    def parsePoint(raw: PointText): Either[Problem, (Latitude, Longitude)] =
      (raw).value.split(",", -1).map(_.trim).toList match
        case List(a, b) =>
          (a.toDoubleOption, b.toDoubleOption) match
            case (Some(lat), Some(lon))
                if !lat.isNaN && !lon.isNaN && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180 =>
              Right((Latitude(lat), Longitude(lon)))
            case (Some(_), Some(_)) =>
              Left(Problem(s"'$raw' is not a latitude,longitude (latitude −90..90, longitude −180..180)"))
            case _ => Left(Problem(s"'$raw' is not a latitude,longitude pair of numbers, e.g. 37.95,58.21"))
        case _ => Left(Problem(s"'$raw' is not latitude,longitude, e.g. 37.95,58.21"))

    def pointText(lat: Latitude, lon: Longitude): PointText =
      PointText(
        String.format(
          java.util.Locale.ROOT,
          "%.4f %s %.4f %s",
          math.abs((lat).value),
          if (lat).value < 0 then "S" else "N",
          math.abs((lon).value),
          if (lon).value < 0 then "W" else "E"
        )
      )

    def periodText(start: Option[Year], end: Option[Year]): Dating.Description =
      Dating.Description(YearFmt.openPeriod(start, end))

  final case class RebuildResult(
      links: RebuildResult.Links,
      byGeometry: RebuildResult.ByGeometry,
      byName: RebuildResult.ByName,
      located: RebuildResult.Located,
      unlocated: RebuildResult.Unlocated,
      places: RebuildResult.Places
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object RebuildResult:
    type Links = Links.T
    object Links extends Newtype[Int, Pure]
    type ByGeometry = ByGeometry.T
    object ByGeometry extends Newtype[Int, Pure]
    type ByName = ByName.T
    object ByName extends Newtype[Int, Pure]
    type Located = Located.T
    object Located extends Newtype[Int, Pure]
    type Unlocated = Unlocated.T
    object Unlocated extends Newtype[Int, Pure]
    type Places = Places.T
    object Places extends Newtype[Int, Pure]

  final case class Record(
      dateSource: Option[Record.DateSource] = None,
      periods: List[Record.Periods] = Nil,
      designations: List[Designation] = Nil,
      registers: List[Register] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def isEmpty: Boolean = (dateSource.isEmpty && periods.isEmpty && designations.isEmpty && registers.isEmpty)

  object Record:
    type DateSourceText = DateSourceText.T
    object DateSourceText extends Newtype[String, Pure]

    type DateSource = DateSource.T
    object DateSource extends Newtype[String, Pure]
    type Periods = Periods.T
    object Periods extends Newtype[String, Pure]

    def dateSourceText(s: DateSource): DateSourceText = (s).value.trim.toLowerCase match
      case "wikidata-p580"                    => DateSourceText("Wikidata: its start and end time")
      case "wikidata-p571"                    => DateSourceText("Wikidata: its inception")
      case "wikidata-p576"                    => DateSourceText("Wikidata: when it was dissolved or destroyed")
      case p if p.startsWith("p2348-periodo") => DateSourceText("Wikidata: its time period, dated by PeriodO")
      case other                              => DateSourceText(other)

  final case class Register(property: Register.Property, id: Register.Id)
      derives KindlingsCodecAsObject,
        KindlingsSchema:
    def name: Register.Name = Register.names.getOrElse(property, Register.Name((property).value))

  object Register:
    type Name = Name.T
    object Name extends Newtype[String, Pure]

    type Property = Property.T
    object Property extends Newtype[String, Pure]
    type Id = Id.T
    object Id extends Newtype[String, Pure]

    val names: Map[Property, Name] = Map(
      {
        val refactorTuple12 = "P13741" -> "Archaeological Cadastre (Greece)";
        (Property(refactorTuple12._1), Name(refactorTuple12._2))
      }, {
        val refactorTuple13 = "P2186" -> "Wiki Loves Monuments";
        (Property(refactorTuple13._1), Name(refactorTuple13._2))
      },
      { val refactorTuple14 = "P9951" -> "Greek Castles"; (Property(refactorTuple14._1), Name(refactorTuple14._2)) }, {
        val refactorTuple6 = "P8218" -> "Archaeology in Greece Online";
        (Property(refactorTuple6._1), Name(refactorTuple6._2))
      },
      { val refactorTuple7 = "P8068" -> "ToposText"; (Property(refactorTuple7._1), Name(refactorTuple7._2)) }, {
        val refactorTuple8 = "P1936" -> "DARE (Digital Atlas of the Roman Empire)";
        (Property(refactorTuple8._1), Name(refactorTuple8._2))
      }
    )

  final case class Relation(
      childId: Relation.ChildId,
      parentId: Relation.ParentId,
      kind: Relation.Kind = Relation.Kind(Relation.PartOf),
      period: Option[Period] = None,
      open: Option[OpenSide] = None
  ):
    def bounds: (Option[Year], Option[Year]) = OpenSide.bounds(period, open)

  object Relation:

    type ChildId = ChildId.T
    object ChildId extends Newtype[String, Pure]
    type ParentId = ParentId.T
    object ParentId extends Newtype[String, Pure]
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]

    val PartOf = "part_of"
    val PartOfAdmin = "part_of_admin"
    val PartOfRegional = "part_of_regional"
    val PartOfPhysical = "part_of_physical"
    val Succeeds = "succeeds"
    val SameAs = "same_as"
    val VersionOf = "version_of"
    val CapitalOf = "capital_of"
    val all: List[Kind] =
      List(
        Kind(PartOf),
        Kind(PartOfAdmin),
        Kind(PartOfRegional),
        Kind(PartOfPhysical),
        Kind(Succeeds),
        Kind(SameAs),
        Kind(VersionOf),
        Kind(CapitalOf)
      )
    val partOfKinds: Set[Kind] = Set(Kind(PartOf), Kind(PartOfAdmin), Kind(PartOfRegional), Kind(PartOfPhysical))

  final case class RemoveIn(reason: Option[Problem] = None)
      derives KindlingsSchema,
        com.histmersion.api.json.Schema
  object RemoveIn:


    given Codec.AsObject[RemoveIn] = StrictJson.codec(KindlingsCodecAsObject.derived[RemoveIn])

  object Shape:
    type IsAreaKind = Summary.Kind
    val IsAreaKind: Summary.Kind.type = Summary.Kind
    type PointOnlyAreaKind = IsAreaKind
    val PointOnlyAreaKind: IsAreaKind.type = IsAreaKind
    export Summary.Kind as IsPeopleKind

    def isArea(kind: IsAreaKind): Boolean =
      (kind == IsAreaKind(Kind.Region) || kind == IsAreaKind(Kind.Polity))

    def pointOnlyArea(kind: PointOnlyAreaKind, hasBoundary: Boolean): Boolean =
      (((isArea(kind)) == (true)) && !hasBoundary)

    val NoBoundaryNote: Card.Note = Card.Note("point only — boundary not yet available")

    def isPeople(kind: Shape.IsPeopleKind): Boolean = (kind == Shape.IsPeopleKind(Kind.People))

    val PeopleNote: Card.Note = Card.Note("a people or tribe — placed at a representative point, not an area")

  final case class Summary(
      id: Id,
      label: Summary.Label,
      kind: Summary.Kind,
      periodStart: Option[Year],
      periodEnd: Option[Year],
      source: Option[Summary.Source],
      names: Summary.Names,
      contested: Summary.Contested = Summary.Contested(false),
      dating: Dating = Dating.Stated,
      importSource: Option[Summary.ImportSource] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema:
    def when: Dating.Description = Dating.describe(dating, periodStart, periodEnd)

  object Summary:
    export Place.Label
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    export Place.Source
    type Names = Names.T
    object Names extends Newtype[String, Pure]
    type Contested = Contested.T
    object Contested extends Newtype[Boolean, Pure]
    export Match.ImportSource

  enum TimeBasis derives CanEqual:
    case Dated, Timeless, Modern, Grouping, Findings

    def wire: TimeBasis.Wire = this match
      case Dated    => TimeBasis.Wire("dated")
      case Timeless => TimeBasis.Wire("timeless")
      case Modern   => TimeBasis.Wire("modern")
      case Grouping => TimeBasis.Wire("grouping")
      case Findings => TimeBasis.Wire("findings")

  object TimeBasis:
    type FindingsNoteText = FindingsNoteText.T
    object FindingsNoteText extends Newtype[String, Pure]
    type ModernFrom = ModernFrom.T
    object ModernFrom extends Newtype[Long, Pure]
    type VisibleEnd = VisibleEnd.T
    object VisibleEnd extends Newtype[Long, Pure]
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
    type OfKind = OfKind.T
    object OfKind extends Newtype[String, Pure]
    type FindingsNote = FindingsNoteValue
    type FindingsNoteValue = FindingsNoteText
    val FindingsNoteValue: FindingsNoteText.type = FindingsNoteText

    def parse(s: Wire): Option[TimeBasis] =
      values.find(_.wire == Wire((s).value.trim.toLowerCase(java.util.Locale.ROOT)))

    given WireEnumValues[TimeBasis]:
      def all: Array[TimeBasis] = values
      def wire(e: TimeBasis): WireEnumValues.Wire = WireEnumValues.Wire((e.wire).value)
      def fromWire(s: WireEnumValues.Wire): Option[TimeBasis] = parse(Wire((s).value))
    given io.circe.Encoder[TimeBasis] = WireEnum.encoder
    given io.circe.Decoder[TimeBasis] = WireEnum.decoder
    given Schema[TimeBasis] = WireEnum.schema

    def of(kind: OfKind, modern: Boolean, imported: Boolean, undated: Boolean, hasGeometry: Boolean): TimeBasis =
      if !imported && (kind == OfKind(Kind.Feature) || (modern && kind == OfKind(Kind.Region)))
      then Timeless
      else if modern then Modern
      else if undated && !hasGeometry then Grouping
      else if undated then Findings
      else Dated

    def visible(
        basis: TimeBasis,
        hi: VisibleEnd,
        modernFrom: ModernFrom,
        overlaps: => Boolean,
        evidence: => Boolean
    ): Boolean =
      basis match
        case Timeless | Grouping => (true)
        case Modern              => hi.value >= modernFrom.value
        case Dated               => (overlaps)
        case Findings            => (evidence)

    val FindingsNote: FindingsNote = FindingsNoteText("dated by its findings — the place has no date of its own")

  final case class TreeRow(
      id: Id,
      label: TreeRow.Label,
      kind: TreeRow.Kind,
      depth: TreeRow.Depth,
      findings: TreeRow.Findings,
      contested: TreeRow.Contested = TreeRow.Contested(false)
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object TreeRow:
    export Place.Label
    type Kind = Kind.T
    object Kind extends Newtype[String, Pure]
    type Depth = Depth.T
    object Depth extends Newtype[Int, Pure]
    type Findings = Findings.T
    object Findings extends Newtype[Int, Pure]
    type Contested = Contested.T
    object Contested extends Newtype[Boolean, Pure]

  final case class Type(
      placeId: Id,
      typeLabel: Type.TypeLabel,
      period: Option[Period] = None,
      citekey: Option[Citekey] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema,
        com.histmersion.api.json.Schema

  object Type:
    type TypeLabel = TypeLabel.T
    object TypeLabel extends Newtype[String, Pure]

  type Id = Id.T
  object Id extends Newtype[String, io.github.iltotore.iron.constraint.any.Pure]:
    extension (id: Id) def isPleiades: Boolean = id.value.startsWith("pleiades_")
