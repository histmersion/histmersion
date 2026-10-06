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
