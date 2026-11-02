package com.histmersion.api.catalogue
import io.circe.Json
import com.histmersion.values.PlaceKind
import com.histmersion.api.{ApiError, Write}
import com.histmersion.api.given

object PlaceEdits:

  val LocalPrefix = "local_"

  private val LangShape = "^[a-z]{2,3}(-[a-z0-9]{2,8})*$".r
  private val IdShape = "^[a-z0-9][a-z0-9_.-]*$".r

  private def invalid(msg: String) = ApiError(msg, Some(Write.CodeInvalid))

  def name(raw: String): Either[ApiError, String] =
    val n = raw.trim.replaceAll("\\s+", " ")
    if n.isEmpty then Left(invalid("the name is empty"))
    else if n.length > MaxName then Left(invalid(s"a place name is at most $MaxName characters"))
    else Right(n)

  val MaxName = 200

  def lang(raw: Option[String]): Either[ApiError, String] =
    raw.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty) match
      case None                            => Right("")
      case Some(l) if LangShape.matches(l) => Right(l)
      case Some(l)                         => Left(invalid(s"'$l' is not a language tag (e.g. en, grc, pl, sr-latn)"))

  def defaultId(label: String): String =
    LocalPrefix + Geocode.key(label).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "").take(64)

  def toInput(in: PlaceAdd): Either[ApiError, PlaceInput] =
    val label = in.label.trim.replaceAll("\\s+", " ")
    val kind = in.kind.trim.toLowerCase(java.util.Locale.ROOT)
    val id = in.id.map(_.trim).filter(_.nonEmpty).getOrElse(defaultId(label))
    for
      _ <- Either.cond(label.nonEmpty, (), invalid("the label is empty"))
      _ <- Either.cond(
        PlaceKind.all.contains(kind),
        (),
        invalid(s"kind '$kind' is not one of ${PlaceKind.all.mkString(", ")}")
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
      extra <- in.names.foldLeft[Either[ApiError, List[String]]](Right(Nil))((acc, n) =>
        acc.flatMap(ns => name(n).map(ns :+ _))
      )
    yield
      val point = Json
        .obj(
          "type" -> Json.fromString("Point"),
          "coordinates" -> Json.arr(Json.fromDoubleOrNull(in.lon), Json.fromDoubleOrNull(in.lat))
        )
        .noSpaces
      val names = (PlaceName(label) :: extra.map(PlaceName(_, l))).distinctBy(n => (n.name.toLowerCase, n.lang))
      PlaceInput(
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
