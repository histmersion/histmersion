package com.histmersion.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.{Codec, Json}
import sttp.tapir.Schema

/** A person's edits to the gazetteer (#987): a further name for a place, and a place of their own.
  *
  * The bundled seed (`db/places`) is re-imported by every `histmersion init`, and an import REPLACES
  * the names of the places it mentions — so a name added by hand is stored as `manual` (V068), which
  * no import deletes, and a place added by hand is a `local` place (V052) with an id no seed uses,
  * which no import touches and which a bundle carries. Everything that decides whether an edit is
  * well-formed lives here, pure. */

/** `POST /api/places/{id}/names` — `lang` is a language tag (`en`, `grc`, `pl`); absent = unspecified. */
final case class PlaceNameAdd(name: String, lang: Option[String] = None)
object PlaceNameAdd:
  given Schema[PlaceNameAdd] = KindlingsSchema.derived[PlaceNameAdd].schema
  given Codec.AsObject[PlaceNameAdd] = StrictJson.codec(KindlingsCodecAsObject.derived[PlaceNameAdd])

/** `POST /api/places/{id}/names/remove` — only a name a person added can be removed: an imported one
  * would come back with the next seed. */
final case class PlaceNameRemove(name: String, lang: Option[String] = None)
object PlaceNameRemove:
  given Schema[PlaceNameRemove] = KindlingsSchema.derived[PlaceNameRemove].schema
  given Codec.AsObject[PlaceNameRemove] = StrictJson.codec(KindlingsCodecAsObject.derived[PlaceNameRemove])

/** What a name edit did. `changed = false`: the place already had that name as a manual one (add),
  * or never had it (remove is refused instead). `wasImported`: the name was already there from an
  * import — it is now kept as manual, so a later seed that drops it does not lose it. */
final case class PlaceNameChanged(placeId: String, label: String, name: String, lang: String,
    added: Boolean, changed: Boolean, wasImported: Boolean = false) derives KindlingsCodecAsObject
object PlaceNameChanged:
  given Schema[PlaceNameChanged] = KindlingsSchema.derived[PlaceNameChanged].schema

/** One name a person added — `GET /api/places/names`, `histmersion places name list`. */
final case class ManualPlaceName(placeId: String, label: String, name: String, lang: String)
    derives KindlingsCodecAsObject
object ManualPlaceName:
  given Schema[ManualPlaceName] = KindlingsSchema.derived[ManualPlaceName].schema

/** `POST /api/places` — one point place of a person's own. `source` says where the coordinates come
  * from and is required (nothing in the gazetteer is a guessed point); `id` defaults to
  * `local_<label>`; `names` are further names (the label is always one), all in `lang`. */
final case class PlaceAdd(
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
object PlaceAdd:
  given Schema[PlaceAdd] = KindlingsSchema.derived[PlaceAdd].schema
    .modify(_.kind)(_.description(s"one of ${PlaceKind.all.mkString(", ")}"))
    .modify(_.source)(_.description("where the coordinates come from (a gazetteer id, a map, a publication)"))
  given Codec.AsObject[PlaceAdd] = StrictJson.codec(KindlingsCodecAsObject.derived[PlaceAdd])

object PlaceEdits:

  /** The prefix of the ids [[PlaceAdd]] makes up — no seed file uses it, so a re-seed never touches them. */
  val LocalPrefix = "local_"

  private val LangShape = "^[a-z]{2,3}(-[a-z0-9]{2,8})*$".r
  private val IdShape   = "^[a-z0-9][a-z0-9_.-]*$".r

  private def invalid(msg: String) = ApiError(msg, Some(Write.CodeInvalid))

  /** A name as stored: trimmed, inner whitespace collapsed; blank is refused. */
  def name(raw: String): Either[ApiError, String] =
    val n = raw.trim.replaceAll("\\s+", " ")
    if n.isEmpty then Left(invalid("the name is empty"))
    else if n.length > MaxName then Left(invalid(s"a place name is at most $MaxName characters"))
    else Right(n)

  val MaxName = 200

  /** A language tag as stored: lower case; absent or blank = `""` (unspecified, as the seed stores it). */
  def lang(raw: Option[String]): Either[ApiError, String] =
    raw.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty) match
      case None                             => Right("")
      case Some(l) if LangShape.matches(l)  => Right(l)
      case Some(l)                          => Left(invalid(s"'$l' is not a language tag (e.g. en, grc, pl, sr-latn)"))

  /** `local_` + the label as a slug, diacritics folded: "Gornji Grad" → `local_gornji_grad`,
    * "Kraków" → `local_krakow`. A label in another script gives no slug — pass an id. */
  def defaultId(label: String): String =
    LocalPrefix + Geocode.key(label).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "").take(64)

  /** A validated [[PlaceAdd]] as the import row it becomes: a GeoJSON point, the label and every
    * further name, the period, the source. Pure — the caller checks the id is free. */
  def toInput(in: PlaceAdd): Either[ApiError, PlaceInput] =
    val label = in.label.trim.replaceAll("\\s+", " ")
    val kind  = in.kind.trim.toLowerCase(java.util.Locale.ROOT)
    val id    = in.id.map(_.trim).filter(_.nonEmpty).getOrElse(defaultId(label))
    for
      _      <- Either.cond(label.nonEmpty, (), invalid("the label is empty"))
      _      <- Either.cond(PlaceKind.all.contains(kind), (), invalid(s"kind '$kind' is not one of ${PlaceKind.all.mkString(", ")}"))
      _      <- Either.cond(id != LocalPrefix, (), invalid(s"'$label' gives no id — pass one (`id`)"))
      _      <- Either.cond(IdShape.matches(id), (),
                  invalid(s"'$id' is not a place id (lower-case letters, digits, '_', '.' and '-')"))
      _      <- Either.cond(in.lon >= -180 && in.lon <= 180 && in.lat >= -90 && in.lat <= 90 &&
                  !in.lon.isNaN && !in.lat.isNaN, (), invalid(s"(${in.lon}, ${in.lat}) is not a longitude/latitude"))
      _      <- Either.cond(in.source.trim.nonEmpty, (),
                  invalid("say where the coordinates come from (`source`): a gazetteer id, a map, a publication"))
      period <- (in.periodStart, in.periodEnd) match
                  case (None, None)                   => Right(None)
                  case (Some(a), Some(b)) if a <= b   => Right(Some(Period(a, b)))
                  case (Some(a), None)                => Right(Some(Period(a, a)))
                  case (None, Some(b))                => Right(Some(Period(b, b)))
                  case (Some(a), Some(b))             => Left(invalid(s"the period starts ($a) after it ends ($b)"))
      l      <- lang(in.lang)
      extra  <- in.names.foldLeft[Either[ApiError, List[String]]](Right(Nil))((acc, n) => acc.flatMap(ns => name(n).map(ns :+ _)))
    yield
      val point = Json.obj("type" -> Json.fromString("Point"),
        "coordinates" -> Json.arr(Json.fromDoubleOrNull(in.lon), Json.fromDoubleOrNull(in.lat))).noSpaces
      val names = (PlaceName(label) :: extra.map(PlaceName(_, l))).distinctBy(n => (n.name.toLowerCase, n.lang))
      PlaceInput(
        Place(id, label, kind, Some(point), period, Some(in.source.trim), Some(Json.obj("added" -> Json.True).noSpaces)),
        names)

  /** The CSV `histmersion places name list --csv` writes — the columns of `db/places/supplement/names.csv`,
    * so a name a person added can be promoted into the bundled seed by pasting the lines. */
  def csv(rows: List[ManualPlaceName]): String =
    def cell(s: String) = if s.exists(c => c == ',' || c == '"' || c == '\n') then "\"" + s.replace("\"", "\"\"") + "\"" else s
    ("place_id,name,lang,why" :: rows.map(r => List(r.placeId, r.name, r.lang, s"added by hand to ${r.label}").map(cell).mkString(",")))
      .mkString("", "\n", "\n")

// ── a person's kind and period for a place (V080) ─────────────────────────────────────────────────

/** `POST /api/places/{id}/override` — what a person decides a place IS, over what its source says:
  * `kind` and/or the period `periodStart`..`periodEnd` (signed years; one side alone leaves the other
  * open) and/or the POINT `pointLat`/`pointLon` (V081: a place known by a point whose stored point is
  * wrong; `reason` is then required — it names the gazetteer value the point comes from). Given again,
  * only what is passed changes — a kind, one side of the period, the point. `reset: true` drops the
  * override and puts back what the source says. Every re-import keeps it (the database applies it,
  * V080/V081). */
final case class PlaceOverrideSet(
    kind: Option[String] = None,
    periodStart: Option[Long] = None,
    periodEnd: Option[Long] = None,
    reason: Option[String] = None,
    reset: Boolean = false,
    pointLat: Option[Double] = None,
    pointLon: Option[Double] = None
)
object PlaceOverrideSet:
  given Schema[PlaceOverrideSet] = KindlingsSchema.derived[PlaceOverrideSet].schema
    .modify(_.kind)(_.description(s"one of ${PlaceKind.all.mkString(", ")}"))
    .modify(_.reason)(_.description("why: the evidence the decision rests on (required with a point: where the point comes from)"))
    .modify(_.pointLat)(_.description("latitude of the place's corrected point (with pointLon); only for a place known by a point"))
    .modify(_.pointLon)(_.description("longitude of the place's corrected point (with pointLat)"))
  given Codec.AsObject[PlaceOverrideSet] = StrictJson.codec(KindlingsCodecAsObject.derived[PlaceOverrideSet])

/** One override — `GET /api/places/overrides`, `histmersion places overrides`: what a person set
  * (`None` = the source's), why, and what the source says (`sourceKind`, `sourcePeriod*`; both `None`
  * = undated; `sourceLat`/`sourceLon` = the source's point when a point is set and the source gave one). */
final case class PlaceOverride(
    placeId: String, label: String, kind: Option[String], periodStart: Option[Long], periodEnd: Option[Long],
    periodSet: Boolean, reason: Option[String], sourceKind: String, sourcePeriodStart: Option[Long],
    sourcePeriodEnd: Option[Long], updatedAt: String,
    lat: Option[Double] = None, lon: Option[Double] = None,
    sourceLat: Option[Double] = None, sourceLon: Option[Double] = None) derives KindlingsCodecAsObject
object PlaceOverride:
  given Schema[PlaceOverride] = KindlingsSchema.derived[PlaceOverride].schema

/** What a `places set` did: the kind and period before and after, and how the place's findings moved
  * (`findingsBefore` → `findingsAfter`; `unlocated` = findings that had it and now have no place at all).
  * `latBefore`…`lonAfter`: the place's point before and after, when it is a point. */
final case class PlaceOverrideChanged(
    placeId: String, label: String, reset: Boolean,
    kindBefore: String, kindAfter: String,
    periodBefore: String, periodAfter: String,
    findingsBefore: Int, findingsAfter: Int, unlocated: Int,
    latBefore: Option[Double] = None, lonBefore: Option[Double] = None,
    latAfter: Option[Double] = None, lonAfter: Option[Double] = None) derives KindlingsCodecAsObject
object PlaceOverrideChanged:
  given Schema[PlaceOverrideChanged] = KindlingsSchema.derived[PlaceOverrideChanged].schema

/** `POST /api/places/{id}/merge` — the place is the same as `into`: its findings, links, children,
  * events, attestations and asset links move there, it is deleted, and its id stays an old id of
  * `into` — which no re-seed brings back as a place of its own. */
final case class PlaceMergeIn(into: String, reason: Option[String] = None)
object PlaceMergeIn:
  given Schema[PlaceMergeIn] = KindlingsSchema.derived[PlaceMergeIn].schema
    .modify(_.into)(_.description("the id of the place it is the same as"))
  given Codec.AsObject[PlaceMergeIn] = StrictJson.codec(KindlingsCodecAsObject.derived[PlaceMergeIn])

/** `POST /api/places/{id}/remove` — delete a place nothing refers to (no event, attestation, asset
  * link, child, name added by hand, nor a finding it is the only place of); no re-seed brings it back. */
final case class PlaceRemoveIn(reason: Option[String] = None)
object PlaceRemoveIn:
  given Schema[PlaceRemoveIn] = KindlingsSchema.derived[PlaceRemoveIn].schema
  given Codec.AsObject[PlaceRemoveIn] = StrictJson.codec(KindlingsCodecAsObject.derived[PlaceRemoveIn])

/** What a merge or a removal did. `into`/`intoLabel` are empty for a removal; `findingsBefore` counts
  * the findings either place had, `findingsAfter` those `into` has; `unlocated` = findings that had one
  * of them and now have no place at all. */
final case class PlaceGone(placeId: String, label: String, into: Option[String], intoLabel: Option[String],
    findingsBefore: Int, findingsAfter: Int, unlocated: Int) derives KindlingsCodecAsObject
object PlaceGone:
  given Schema[PlaceGone] = KindlingsSchema.derived[PlaceGone].schema

/** A validated [[PlaceOverrideSet]]. */
enum PlaceOverrideEdit:
  /** Drop the override; the source's kind and period come back. */
  case Reset
  /** Set the kind and/or the period (`(start, end)`, `None` = open on that side) and/or the point;
    * `None` keeps what the override had (or the source's). */
  case Put(kind: Option[String], period: Option[(Option[Long], Option[Long])], reason: Option[String],
      point: Option[GeoPoint] = None)

object PlaceOverrides:
  val MaxReason = 500

  private def invalid(msg: String) = ApiError(msg, Some(Write.CodeInvalid))

  /** Validate a [[PlaceOverrideSet]]. Pure. */
  def edit(in: PlaceOverrideSet): Either[ApiError, PlaceOverrideEdit] =
    val kind   = in.kind.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
    val reason = in.reason.map(_.trim.replaceAll("\\s+", " ")).filter(_.nonEmpty)
    val period = Option.when(in.periodStart.isDefined || in.periodEnd.isDefined)((in.periodStart, in.periodEnd))
    val asked  = in.pointLat.isDefined || in.pointLon.isDefined
    if in.reset then
      if kind.isDefined || period.isDefined || asked then
        Left(invalid("`reset` drops the override — pass no kind, period or point with it"))
      else Right(PlaceOverrideEdit.Reset)
    else
      for
        _ <- Either.cond(kind.isDefined || period.isDefined || asked, (),
               invalid("say what to set: a kind, a period (periodStart/periodEnd), a point (pointLat/pointLon), or reset"))
        point <- if !asked then Right(None) else
               (in.pointLat, in.pointLon) match
                 case (Some(lat), Some(lon)) =>
                   if lat.isNaN || lon.isNaN || lat < -90 || lat > 90 || lon < -180 || lon > 180 then
                     Left(invalid(s"($lat, $lon) is not a latitude/longitude"))
                   else if reason.isEmpty then
                     Left(invalid("say where the point comes from (`reason`): a gazetteer value such as a Pleiades " +
                       "representative point or a Wikidata coordinate — never an estimate"))
                   else Right(Some(GeoPoint(lon, lat)))
                 case _ => Left(invalid("a point needs both pointLat and pointLon"))
        _ <- kind.fold(Right(()))(k => Either.cond(PlaceKind.all.contains(k), (),
               invalid(s"kind '$k' is not one of ${PlaceKind.all.mkString(", ")}")))
        _ <- period match
               case Some((Some(a), Some(b))) if a > b => Left(invalid(s"the period starts ($a) after it ends ($b)"))
               case _                                 => Right(())
        _ <- Either.cond(reason.forall(_.length <= MaxReason), (), invalid(s"a reason is at most $MaxReason characters"))
      yield PlaceOverrideEdit.Put(kind, period, reason, point)

  /** A reason as stored: trimmed, inner whitespace collapsed, at most [[MaxReason]] characters. */
  def reason(raw: Option[String]): Either[ApiError, Option[String]] =
    val r = raw.map(_.trim.replaceAll("\\s+", " ")).filter(_.nonEmpty)
    Either.cond(r.forall(_.length <= MaxReason), r, invalid(s"a reason is at most $MaxReason characters"))

  /** `--point 37.95,58.21` (latitude first, as a map gives it) → (lat, lon). Pure. */
  def parsePoint(raw: String): Either[String, (Double, Double)] =
    raw.split(",", -1).map(_.trim).toList match
      case List(a, b) =>
        (a.toDoubleOption, b.toDoubleOption) match
          case (Some(lat), Some(lon)) if !lat.isNaN && !lon.isNaN && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180 =>
            Right((lat, lon))
          case (Some(_), Some(_)) => Left(s"'$raw' is not a latitude,longitude (latitude −90..90, longitude −180..180)")
          case _                  => Left(s"'$raw' is not a latitude,longitude pair of numbers, e.g. 37.95,58.21")
      case _ => Left(s"'$raw' is not latitude,longitude, e.g. 37.95,58.21")

  /** A point as a reader sees it: "38.5000 N 57.5000 E", "16.1011 S 31.4592 W". */
  def pointText(lat: Double, lon: Double): String =
    String.format(java.util.Locale.ROOT, "%.4f %s %.4f %s",
      math.abs(lat), if lat < 0 then "S" else "N", math.abs(lon), if lon < 0 then "W" else "E")

  /** A period as a reader sees it: "27 BCE–410 CE", "from 27 BCE", "until 410 CE", "undated". */
  def periodText(start: Option[Long], end: Option[Long]): String = YearFmt.openPeriod(start, end)
