package com.histmersion
package plugins.media
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import com.histmersion.util.json.Cursor
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.Problem

import com.histmersion.plugin.*

import java.util.Locale
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{BoundaryKind, atBoundary}
import com.histmersion.lint.{LintRule, tolerated}

/** The Smithsonian Open Access API (https://edan.si.edu/openaccess/apidocs/): `GET {api}/search?q=&rows=
  * &type=edanmdm&row_group=objects` with an api.data.gov key (sent as the `X-Api-Key` header, which api.data.gov
  * accepts in place of `api_key`, so the key never sits in a URL that could be logged). The answer is
  * `{status, responseCode, response: {rows: [...], rowCount, message}}`; each row is an EDAN `edanmdm` record — the
  * same schema as the Smithsonian's open-data dump (`content.descriptiveNonRepeating`, `content.indexedStructured`,
  * `content.freetext`). A record is used only through an image media item whose OWN `usage.access` is `CC0`; any other
  * is refused. Place fields: `freetext.place` (each value's comma parts, most specific first), then
  * `indexedStructured.geoLocation` from its finest level down. Dates: `freetext.date` entries labelled as the object's
  * date (never an accession or collection date), read by [[FreeTextDates]]. Pure.
  */
@atBoundary(BoundaryKind.Json, "reads the smithsonian collection's JSON answers into media records")
object Smithsonian:

  /** The plugin's id: what its problems name. */
  val Name = "media-smithsonian"
  type Label = Label.T
  object Label extends Newtype[String, Pure]
  type Unit = Unit.T
  object Unit extends Newtype[String, Pure]

  /** The header the key travels in (api.data.gov's documented alternative to the `api_key` parameter). */
  val KeyHeader = "X-Api-Key"

  /** Pure: the query — the place's name as a phrase, objects with images only, none of the excluded units (`unit_code`,
    * a fielded term the API's `terms` endpoint lists).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def query(name: MediaPlace.Label, excludedUnits: List[Unit] = Nil): CollectionQuery.Text =
    val units = excludedUnits.map(_.value.replaceAll("[^A-Za-z0-9_]", "")).filter(_.nonEmpty)
    CollectionQuery.Text(
      s"\"${name.value.replace("\"", " ").trim}\" AND online_media_type:Images" +
        (if units.isEmpty then "" else units.mkString(" AND NOT unit_code:(", " OR ", ")"))
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def searchUrl(
      api: CollectionSettings.ApiUrl,
      name: MediaPlace.Label,
      rows: CollectionSettings.Candidates,
      excludedUnits: List[Unit] = Nil
  ): Plugin.Http.Url =
    Plugin.Http.Url(
      s"${api.value.stripSuffix("/")}/search?q=${query(name, excludedUnits).value.enc}&rows=${rows.value.max(1).min(1000)}" +
        "&type=edanmdm&row_group=objects"
    )

  /** Pure: the unit a row comes from — `descriptiveNonRepeating.unit_code`, else the row's `unitCode`. */
  def unitOf(json: Json): Option[Unit] =
    val c = json.cursor
    c.downField("content")
      .downField("descriptiveNonRepeating")
      .str("unit_code")
      .orElse(c.str("unitCode"))
      .map(u => Unit(u))

  /** The labels of `freetext.date` that name when the object was made. */
  val DateLabels: Set[Label] =
    Set("date", "date made", "date created", "creation date", "production date", "object date").map(l => Label(l))

  /** The labels of `freetext.name` that name who made it. */
  val MakerLabels: Set[Label] = Set(
    "artist",
    "maker",
    "creator",
    "author",
    "manufacturer",
    "photographer",
    "designer",
    "sculptor",
    "potter",
    "illustrator",
    "engraver"
  ).map(l => Label(l))

  /** Pure: the rows of a search answer; an error answer (api.data.gov's `{error: {code, message}}`, or EDAN's
    * `{response: {error}}`) is a `Left`.
    */
  def records(json: Json, excludedUnits: List[Unit] = Nil): Either[Problem, List[Either[Problem, MediaItem]]] =
    val c = json.cursor
    val resp = c.downField("response")
    resp.downField("rows").focus.flatMap(_.asArray) match
      case Some(rows)                              => Right(rows.toList.map(record(_, excludedUnits)))
      case None if resp.long("rowCount").isDefined => Right(Nil)
      case None                                    =>
        val gov = c.downField("error")
        Left(
          (List(gov.str("code"), gov.str("message")).flatten.mkString(": ") match
            case "" => resp.str("error").orElse(c.str("message"))
            case e  => Some(e)
          ).fold(MediaPlugin.missing("rows"))(MediaPlugin.said(Name, Problem.Plugin.Claim.Failed, _))
        )

  extension (c: Cursor)
    private def labelled(field: String): List[(Label, String)] =
      c.downField(field).focus.flatMap(_.asArray).toList.flatten.flatMap { e =>
        val h = e.cursor
        h.str("content").map(v => Label(h.str("label").getOrElse("").toLowerCase(Locale.ROOT)) -> v)
      }

  /** Pure: one row of a search answer; a row of an excluded unit (a natural-history department's specimen) is withheld
    * with that reason, whatever its licence.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def record(json: Json, excludedUnits: List[Unit] = Nil): Either[Problem, MediaItem] =
    val c = json.cursor
    val cont = c.downField("content")
    val dnr = cont.downField("descriptiveNonRepeating")
    val free = cont.downField("freetext")
    val idx = cont.downField("indexedStructured")
    dnr
      .str("record_ID")
      .orElse(c.str("url").map(_.split(":", 2).last))
      .orElse(c.str("id"))
      .toRight(MediaPlugin.missing("id"))
      .map { key =>
        val title = c.str("title").orElse(dnr.downField("title").str("content")).getOrElse(key)
        val page = dnr
          .str("record_link")
          .orElse(dnr.str("guid"))
          .getOrElse(s"https://collections.si.edu/search/detail/${c.str("url").getOrElse(s"edanmdm:$key")}")
        val unit = dnr.str("data_source")
        val maker = free.labelled("name").find((l, _) => MakerLabels(l)).map(_._2)
        val creditLine = free.labelled("creditLine").headOption.map(_._2)
        val credit =
          (List(Some(title), maker, unit).flatten ++ List("Smithsonian Institution") ++ creditLine.toList :+ "CC0")
            .mkString(", ")
        val media = dnr
          .downField("online_media")
          .downField("media")
          .focus
          .flatMap(_.asArray)
          .toList
          .flatten
          .map(_.cursor)
          .filter(m => m.str("type").forall(_.equalsIgnoreCase("Images")))
        val cc0 = media.find(m => m.downField("usage").str("access").exists(_.equalsIgnoreCase("CC0")))
        val rights: Either[Problem, MediaRights] = cc0 match
          case Some(_) => Right(MediaRights.cc0(maker.map(MediaRights.Author(_)), MediaRights.Credit(credit)))
          case None if media.isEmpty => Left(MediaPlugin.rejected(Name, key, Problem.Plugin.Refusal.NoImage))
          case None                  =>
            val stated = media.flatMap(m => m.downField("usage").str("access")).distinct
            Left(
              MediaPlugin.rejected(
                Name,
                key,
                Problem.Plugin.Refusal
                  .NoOpenImage(Problem.Field("usage.access"), stated.toVector.map(v => Problem.Raw(s"$v")))
              )
            )
        def resource(m: Cursor, label: String) =
          m.downField("resources")
            .focus
            .flatMap(_.asArray)
            .toList
            .flatten
            .map(_.cursor)
            .find(r => r.str("label").exists(_.equalsIgnoreCase(label)))
            .flatMap(_.str("url"))
        val image = cc0.flatMap(m => resource(m, "Screen Image").orElse(m.str("content")))
        val thumb = cc0.flatMap(m => resource(m, "Thumbnail Image").orElse(m.str("thumbnail")))
        val original = cc0.flatMap(m => resource(m, "High-resolution JPEG").orElse(m.str("content")))
        // the free-text places first (each value's comma parts, most specific first), then the
        // structured location from its finest level down
        val places = free
          .labelled("place")
          .flatMap((l, v) =>
            v.split(",").toList.map(_.trim).filter(_.nonEmpty).map((if l.value.isEmpty then "place" else l.value) -> _)
          )
        val levels = idx.downField("geoLocation").focus.flatMap(_.asArray).toList.flatten.flatMap { g =>
          val h = g.cursor
          h.keys.toList.flatten.filter(_.matches("L[0-9]+")).sortBy(k => -k.drop(1).toInt).flatMap { k =>
            val l = h.downField(k)
            l.str("content").map(v => l.str("type").getOrElse(k).toLowerCase(Locale.ROOT) -> v)
          }
        }
        val geo = (places ++ levels).distinctBy((_, v) => v.toLowerCase(Locale.ROOT))
        val period = free
          .labelled("date")
          .filter((l, _) => DateLabels(l))
          .iterator
          .flatMap((_, v) => FreeTextDates.parse(FreeTextDates.Text(v)))
          .nextOption()
          .flatMap((a, b) => Period.span(a.value, b.value))
        val excluded =
          unitOf(json)
            .filter(u => excludedUnits.exists(_.value.equalsIgnoreCase(u.value)))
            .map(u => MediaItem.Withheld(s"an excluded unit (${u.value})"))
        MediaItem(
          MediaItem.Key(key),
          MediaItem.Title(title),
          MediaItem.PageUrl(page),
          image.map(i => MediaItem.ImageUrl(i)),
          rights,
          thumbUrl = thumb.map(t => MediaItem.ThumbUrl(t)),
          originalUrl = original.map(o => MediaItem.OriginalUrl(o)),
          geography = geo.map((f, v) => (MediaItem.Geography.Field(f), MediaItem.Geography.Value(v))),
          period = period,
          withheld = excluded
        )
      }
