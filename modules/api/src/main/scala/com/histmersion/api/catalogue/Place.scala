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
final case class Place(
    id: String,
    label: String,
    kind: String,
    geometry: Option[String] = None,
    period: Option[Period] = None,
    source: Option[String] = None,
    properties: Option[String] = None,
    open: Option[OpenSide] = None
):
  def bounds: (Option[Long], Option[Long]) = OpenSide.bounds(period, open)

object Place:

  /** The five `kind`s the store recognises. Free text in the column (an importer may carry its own vocabulary), but
    * these are what the CLI/UI offer and what api's `Lpf` maps foreign type labels onto. `people` (2026-10-09) is a
    * people or a civic tribe — the Ubii, the Athenian tribe Aiantis: a group of persons that a gazetteer places at a
    * representative point, never an area (Pleiades files them as regions; `lint --rule place-kind` moves them).
    */
  object Kind:
    val Region = "region"
    val Polity = "polity"
    val Site = "site"
    val Feature = "feature"
    val People = "people"
    val all: List[String] = List(Region, Polity, Site, Feature, People)

    /** Map a foreign type label (LPF `types[].label`, an AAT id, a Natural Earth class) onto a kind; `None` when
      * nothing recognisable is there, so the caller's default wins.
      */
    def parse(s: String): Option[String] =
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

    def wire: String = this match
      case Stated   => "stated"
      case GridCell => "grid-cell"

  object Precision:
    /** The atlas grids, as cell sizes in micro-degrees: 5°, 1° and ½°. */
    val CellsMicro: List[Long] = List(5000000L, 1000000L, 500000L)

    /** Is (`lon`, `lat`) — rounded to a micro-degree — the centre of a cell of one of the grids, in both coordinates?
      * Pure.
      */
    def gridCentre(lon: Double, lat: Double): Boolean =
      val x = math.round(lon * 1e6)
      val y = math.round(lat * 1e6)
      CellsMicro.exists(c => Math.floorMod(x, c) == c / 2 && Math.floorMod(y, c) == c / 2)

    /** The precision of the point of place `placeId`. */
    def of(placeId: String, lon: Double, lat: Double): Precision =
      if placeId.startsWith("pleiades_") && gridCentre(lon, lat) then GridCell else Stated

    /** What a place says about a grid-cell point ("location: approximate (atlas grid square)" on a card). */
    val Approximate: String = "approximate (atlas grid square)"

    def parse(s: String): Option[Precision] = values.find(_.wire == Ascii.lower(s.trim))

    given WireEnumValues[Precision]:
      def all: Array[Precision] = Precision.values
      def wire(e: Precision): String = e.wire
      def fromWire(s: String): Option[Precision] = parse(s)

  final case class Add(
      label: String,
      kind: String,
      lon: Double,
      lat: Double,
      source: String,
      id: Option[String] = None,
      names: List[String] = Nil,
      lang: Option[String] = None,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None
  )
  object Add:
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
    def geometry: Option[String]
    def parents: List[Summary]
    def children: List[Summary]
    def findings: Int
    def asOf: Option[Long]
    def contested: Boolean
    def footprints: List[Footprint]
    def links: List[Link]
    def types: List[Type]
    def counts: Option[Counts]
    def hasBoundary: Boolean
    def descriptions: List[Description]
    def timeBasis: TimeBasis
    def locationPrecision: com.histmersion.api.catalogue.Place.Precision
    def siteType: Option[String]
    def record: Record

    def pointOnlyArea: Boolean = geometry.isDefined && Shape.pointOnlyArea(place.kind, hasBoundary)

    def shapeNote: Option[String] =
      Option
        .when(pointOnlyArea)(Shape.NoBoundaryNote)
        .orElse(Option.when(geometry.isDefined && Shape.isPeople(place.kind))(Shape.PeopleNote))

    def locationNote: Option[String] =
      Option.when(locationPrecision == com.histmersion.api.catalogue.Place.Precision.GridCell)(
        com.histmersion.api.catalogue.Place.Precision.Approximate
      )

    def shapeNotes: List[String] = shapeNote.toList ++ locationNote.map(n => s"location: $n").toList

    def datedByFindings: Boolean = asOf.isDefined && timeBasis == TimeBasis.Findings

  final case class Counts(sources: Int, findings: Int, articles: Int, events: Int)
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object Counts:
    given Schema[Counts] = summon[KindlingsSchema[Counts]].schema

  enum Dating:
    case Stated, EndOnly, Unknown

    def wire: String = this match
      case Stated  => "stated"
      case EndOnly => "end-only"
      case Unknown => "unknown"

  object Dating:
    def parse(s: String): Option[Dating] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

    given WireEnumValues[Dating]:
      def all: Array[Dating] = Dating.values
      def wire(e: Dating): String = e.wire
      def fromWire(s: String): Option[Dating] = parse(s)
    given io.circe.Encoder[Dating] = WireEnum.encoder
    given io.circe.Decoder[Dating] = WireEnum.decoder
    given Schema[Dating] = WireEnum.schema

    def describe(dating: Dating, start: Option[Long], end: Option[Long]): String = dating match
      case Unknown => "date unknown"
      case EndOnly => end.fold("date unknown")(e => s"end ${YearFmt(e)}, start unknown")
      case Stated  => YearFmt.openPeriod(start, end, none = "")

  final case class Description(text: String, lang: String = "", source: String = "")
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object Description:
    given Schema[Description] = summon[KindlingsSchema[Description]].schema

  final case class Designation(
      id: String,
      label: Option[String] = None,
      start: Option[String] = None,
      end: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema

  object Edits:

    val LocalPrefix = "local_"

    private val LangShape = "^[a-z]{2,3}(-[a-z0-9]{2,8})*$".r
    private val IdShape = "^[a-z0-9][a-z0-9_.-]*$".r

    private def invalid(msg: String) = Api.Error(msg, Some(Write.CodeInvalid))

    def name(raw: String): Either[Api.Error, String] =
      val n = raw.trim.replaceAll("\\s+", " ")
      if n.isEmpty then Left(invalid("the name is empty"))
      else if n.length > MaxName then Left(invalid(s"a place name is at most $MaxName characters"))
      else Right(n)

    val MaxName = 200

    def lang(raw: Option[String]): Either[Api.Error, String] =
      raw.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty) match
        case None                            => Right("")
        case Some(l) if LangShape.matches(l) => Right(l)
        case Some(l)                         => Left(invalid(s"'$l' is not a language tag (e.g. en, grc, pl, sr-latn)"))

    def defaultId(label: String): String =
      LocalPrefix + Geocode.key(label).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "").take(64)

    def toInput(in: Add): Either[Api.Error, Input] =
      val label = in.label.trim.replaceAll("\\s+", " ")
      val kind = in.kind.trim.toLowerCase(java.util.Locale.ROOT)
      val id = in.id.map(_.trim).filter(_.nonEmpty).getOrElse(defaultId(label))
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
            id,
            label,
            kind,
            Some(point),
            period,
            Some(in.source.trim),
            Some(Json.obj("added" -> Json.True).noSpaces)
          ),
          names
        )

    def csv(rows: List[ManualPlaceName]): String =
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
      geometry: Option[String],
      parents: List[Summary],
      children: List[Summary],
      findings: Int,
      asOf: Option[Long],
      contested: Boolean = false,
      footprints: List[Footprint] = Nil,
      links: List[Link] = Nil,
      types: List[Type] = Nil,
      counts: Option[Counts] = None,
      hasBoundary: Boolean = false,
      descriptions: List[Description] = Nil,
      timeBasis: TimeBasis = TimeBasis.Dated,
      /** `grid-cell` for a Pleiades point that is only the centre of an atlas grid square. */
      locationPrecision: com.histmersion.api.catalogue.Place.Precision =
        com.histmersion.api.catalogue.Place.Precision.Stated,
      siteType: Option[String] = None,
      record: Record = Record()
  ) extends Card

  /** Follows the Pleiades Place/Location split: one place, many dated footprints. */
  final case class Footprint(
      id: Int,
      placeId: String,
      role: String = "extent",
      period: Option[Period] = None,
      certainty: Option[String] = None,
      accuracyM: Option[Float] = None,
      citekey: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object Footprint:
    given Schema[Footprint] = summon[KindlingsSchema[Footprint]].schema
    val Roles: List[String] = List("extent", "core", "maximal", "frontier", "seat", "snapshot")
    val Certainties: List[String] = List("definite", "approximate", "uncertain")

  final case class Gone(
      placeId: String,
      label: String,
      into: Option[String],
      intoLabel: Option[String],
      findingsBefore: Int,
      findingsAfter: Int,
      unlocated: Int
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object Gone:
    given Schema[Gone] = summon[KindlingsSchema[Gone]].schema

  final case class Hit(
      id: String,
      label: String,
      kind: String,
      periodStart: Option[Long],
      periodEnd: Option[Long],
      source: Option[String],
      names: String,
      matchedName: Option[String],
      findings: Int,
      contested: Boolean = false,
      dating: Dating = Dating.Stated,
      importSource: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def toSummary: Summary =
      Summary(id, label, kind, periodStart, periodEnd, source, names, contested, dating, importSource)
  object Hit:
    given Schema[Hit] = summon[KindlingsSchema[Hit]].schema

  final case class ImportResult(
      places: Int,
      names: Int,
      relations: Int,
      failed: List[String] = Nil,
      job: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object ImportResult:
    given Schema[ImportResult] = summon[KindlingsSchema[ImportResult]].schema

  final case class Input(place: Place, names: List[Name] = Nil, relations: List[Relation] = Nil)

  final case class Link(placeId: String, scheme: String, externalId: String, relation: String = "close_match")
      derives KindlingsCodecAsObject,
        KindlingsSchema
  object Link:
    given Schema[Link] = summon[KindlingsSchema[Link]].schema
    val Schemes: List[String] = List("pleiades", "wikidata", "tgn", "geonames", "whg")
    val Relations: List[String] = List("close_match", "exact_match", "broad_match", "see_also")

    def url(scheme: String, externalId: String): Option[String] =
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

    def schemeTitle(scheme: String): String = scheme.trim.toLowerCase(java.util.Locale.ROOT) match
      case "wikidata" => "Wikidata"
      case "geonames" => "GeoNames"
      case "pleiades" => "Pleiades"
      case "tgn"      => "Getty TGN"
      case "whg"      => "World Historical Gazetteer"
      case "commons"  => "Commons category"
      case other      => other

  final case class Match(placeId: String, importSource: Option[String], native: Boolean = true)

  final case class MergeIn(into: String, reason: Option[String] = None)
  object MergeIn:
    given Schema[MergeIn] = KindlingsSchema
      .derived[MergeIn]
      .schema
      .modify(_.into)(_.description("the id of the place it is the same as"))
    given Codec.AsObject[MergeIn] = StrictJson.codec(KindlingsCodecAsObject.derived[MergeIn])

  final case class Name(
      name: String,
      lang: String = "",
      period: Option[Period] = None,
      open: Option[OpenSide] = None
  ):
    def bounds: (Option[Long], Option[Long]) = OpenSide.bounds(period, open)

  final case class NameAdd(name: String, lang: Option[String] = None) derives KindlingsSchema
  object NameAdd:
    given Schema[NameAdd] = summon[KindlingsSchema[NameAdd]].schema
    given Codec.AsObject[NameAdd] = StrictJson.codec(KindlingsCodecAsObject.derived[NameAdd])

  final case class NameChanged(
      placeId: String,
      label: String,
      name: String,
      lang: String,
      added: Boolean,
      changed: Boolean,
      wasImported: Boolean = false
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object NameChanged:
    given Schema[NameChanged] = summon[KindlingsSchema[NameChanged]].schema

  final case class NameRemove(name: String, lang: Option[String] = None) derives KindlingsSchema
  object NameRemove:
    given Schema[NameRemove] = summon[KindlingsSchema[NameRemove]].schema
    given Codec.AsObject[NameRemove] = StrictJson.codec(KindlingsCodecAsObject.derived[NameRemove])

  final case class Override(
      placeId: String,
      label: String,
      kind: Option[String],
      periodStart: Option[Long],
      periodEnd: Option[Long],
      periodSet: Boolean,
      reason: Option[String],
      sourceKind: String,
      sourcePeriodStart: Option[Long],
      sourcePeriodEnd: Option[Long],
      updatedAt: String,
      lat: Option[Double] = None,
      lon: Option[Double] = None,
      sourceLat: Option[Double] = None,
      sourceLon: Option[Double] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object Override:
    given Schema[Override] = summon[KindlingsSchema[Override]].schema

  final case class OverrideChanged(
      placeId: String,
      label: String,
      reset: Boolean,
      kindBefore: String,
      kindAfter: String,
      periodBefore: String,
      periodAfter: String,
      findingsBefore: Int,
      findingsAfter: Int,
      unlocated: Int,
      latBefore: Option[Double] = None,
      lonBefore: Option[Double] = None,
      latAfter: Option[Double] = None,
      lonAfter: Option[Double] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object OverrideChanged:
    given Schema[OverrideChanged] = summon[KindlingsSchema[OverrideChanged]].schema

  enum OverrideEdit:
    case Reset

    case Put(
        kind: Option[String],
        period: Option[(Option[Long], Option[Long])],
        reason: Option[String],
        point: Option[GeoPoint] = None
    )

  final case class OverrideSet(
      kind: Option[String] = None,
      periodStart: Option[Long] = None,
      periodEnd: Option[Long] = None,
      reason: Option[String] = None,
      reset: Boolean = false,
      pointLat: Option[Double] = None,
      pointLon: Option[Double] = None
  )
  object OverrideSet:
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
    val MaxReason = 500

    private def invalid(msg: String) = Api.Error(msg, Some(Write.CodeInvalid))

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

    def reason(raw: Option[String]): Either[Api.Error, Option[String]] =
      val r = raw.map(_.trim.replaceAll("\\s+", " ")).filter(_.nonEmpty)
      Either.cond(r.forall(_.length <= MaxReason), r, invalid(s"a reason is at most $MaxReason characters"))

    def parsePoint(raw: String): Either[String, (Double, Double)] =
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

    def pointText(lat: Double, lon: Double): String =
      String.format(
        java.util.Locale.ROOT,
        "%.4f %s %.4f %s",
        math.abs(lat),
        if lat < 0 then "S" else "N",
        math.abs(lon),
        if lon < 0 then "W" else "E"
      )

    def periodText(start: Option[Long], end: Option[Long]): String = YearFmt.openPeriod(start, end)

  final case class RebuildResult(
      links: Int,
      byGeometry: Int,
      byName: Int,
      located: Int,
      unlocated: Int,
      places: Int
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object RebuildResult:
    given Schema[RebuildResult] = summon[KindlingsSchema[RebuildResult]].schema

  final case class Record(
      dateSource: Option[String] = None,
      periods: List[String] = Nil,
      designations: List[Designation] = Nil,
      registers: List[Register] = Nil
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def isEmpty: Boolean = dateSource.isEmpty && periods.isEmpty && designations.isEmpty && registers.isEmpty

  object Record:
    def dateSourceText(s: String): String = s.trim.toLowerCase match
      case "wikidata-p580"                    => "Wikidata: its start and end time"
      case "wikidata-p571"                    => "Wikidata: its inception"
      case "wikidata-p576"                    => "Wikidata: when it was dissolved or destroyed"
      case p if p.startsWith("p2348-periodo") => "Wikidata: its time period, dated by PeriodO"
      case other                              => other

  final case class Register(property: String, id: String) derives KindlingsCodecAsObject, KindlingsSchema:
    def name: String = Register.names.getOrElse(property, property)

  object Register:
    val names: Map[String, String] = Map(
      "P13741" -> "Archaeological Cadastre (Greece)",
      "P2186" -> "Wiki Loves Monuments",
      "P9951" -> "Greek Castles",
      "P8218" -> "Archaeology in Greece Online",
      "P8068" -> "ToposText",
      "P1936" -> "DARE (Digital Atlas of the Roman Empire)"
    )

  final case class Relation(
      childId: String,
      parentId: String,
      kind: String = Relation.PartOf,
      period: Option[Period] = None,
      open: Option[OpenSide] = None
  ):
    def bounds: (Option[Long], Option[Long]) = OpenSide.bounds(period, open)

  object Relation:
    val PartOf = "part_of"
    val PartOfAdmin = "part_of_admin"
    val PartOfRegional = "part_of_regional"
    val PartOfPhysical = "part_of_physical"
    val Succeeds = "succeeds"
    val SameAs = "same_as"
    val VersionOf = "version_of"
    val CapitalOf = "capital_of"
    val all: List[String] =
      List(PartOf, PartOfAdmin, PartOfRegional, PartOfPhysical, Succeeds, SameAs, VersionOf, CapitalOf)
    val partOfKinds: Set[String] = Set(PartOf, PartOfAdmin, PartOfRegional, PartOfPhysical)

  final case class RemoveIn(reason: Option[String] = None) derives KindlingsSchema
  object RemoveIn:
    given Schema[RemoveIn] = summon[KindlingsSchema[RemoveIn]].schema
    given Codec.AsObject[RemoveIn] = StrictJson.codec(KindlingsCodecAsObject.derived[RemoveIn])

  object Shape:
    def isArea(kind: String): Boolean =
      kind == com.histmersion.api.catalogue.Place.Kind.Region || kind == com.histmersion.api.catalogue.Place.Kind.Polity

    def pointOnlyArea(kind: String, hasBoundary: Boolean): Boolean = isArea(kind) && !hasBoundary

    val NoBoundaryNote: String = "point only — boundary not yet available"

    def isPeople(kind: String): Boolean = kind == com.histmersion.api.catalogue.Place.Kind.People

    val PeopleNote: String = "a people or tribe — placed at a representative point, not an area"

  final case class Summary(
      id: String,
      label: String,
      kind: String,
      periodStart: Option[Long],
      periodEnd: Option[Long],
      source: Option[String],
      names: String,
      contested: Boolean = false,
      dating: Dating = Dating.Stated,
      importSource: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema:
    def when: String = Dating.describe(dating, periodStart, periodEnd)
  object Summary:
    given Schema[Summary] = summon[KindlingsSchema[Summary]].schema

  enum TimeBasis:
    case Dated, Timeless, Modern, Grouping, Findings

    def wire: String = this match
      case Dated    => "dated"
      case Timeless => "timeless"
      case Modern   => "modern"
      case Grouping => "grouping"
      case Findings => "findings"

  object TimeBasis:
    def parse(s: String): Option[TimeBasis] = values.find(_.wire == s.trim.toLowerCase(java.util.Locale.ROOT))

    given WireEnumValues[TimeBasis]:
      def all: Array[TimeBasis] = TimeBasis.values
      def wire(e: TimeBasis): String = e.wire
      def fromWire(s: String): Option[TimeBasis] = parse(s)
    given io.circe.Encoder[TimeBasis] = WireEnum.encoder
    given io.circe.Decoder[TimeBasis] = WireEnum.decoder
    given Schema[TimeBasis] = WireEnum.schema

    def of(kind: String, modern: Boolean, imported: Boolean, undated: Boolean, hasGeometry: Boolean): TimeBasis =
      if !imported && (kind == com.histmersion.api.catalogue.Place.Kind.Feature || (modern && kind == com.histmersion.api.catalogue.Place.Kind.Region))
      then Timeless
      else if modern then Modern
      else if undated && !hasGeometry then Grouping
      else if undated then Findings
      else Dated

    def visible(basis: TimeBasis, hi: Long, modernFrom: Long, overlaps: => Boolean, evidence: => Boolean): Boolean =
      basis match
        case Timeless | Grouping => true
        case Modern              => hi >= modernFrom
        case Dated               => overlaps
        case Findings            => evidence

    val FindingsNote: String = "dated by its findings — the place has no date of its own"

  final case class TreeRow(
      id: String,
      label: String,
      kind: String,
      depth: Int,
      findings: Int,
      contested: Boolean = false
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object TreeRow:
    given Schema[TreeRow] = summon[KindlingsSchema[TreeRow]].schema

  final case class Type(
      placeId: String,
      typeLabel: String,
      period: Option[Period] = None,
      citekey: Option[String] = None
  ) derives KindlingsCodecAsObject,
        KindlingsSchema
  object Type:
    given Schema[Type] = summon[KindlingsSchema[Type]].schema
