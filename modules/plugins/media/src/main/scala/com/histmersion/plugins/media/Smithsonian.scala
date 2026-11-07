package com.histmersion.plugins.media

import com.histmersion.plugin.*
import io.circe.{ACursor, Json}

import java.util.Locale

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
object Smithsonian:
  type Label = Label.T
  object Label extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Unit = Unit.T
  object Unit extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Rows = Rows.T
  object Rows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The header the key travels in (api.data.gov's documented alternative to the `api_key` parameter). */
  val KeyHeader = "X-Api-Key"

  /** Pure: the query — the place's name as a phrase, objects with images only, none of the excluded units (`unit_code`,
    * a fielded term the API's `terms` endpoint lists).
    */
  def query(name: com.histmersion.plugin.CollectionQuery.Text, excludedUnits: List[com.histmersion.plugins.media.Smithsonian.Unit] = Nil): com.histmersion.plugin.CollectionQuery.Text =
    val units = excludedUnits.map(_.value.replaceAll("[^A-Za-z0-9_]", "")).filter(_.nonEmpty)
    com.histmersion.plugin.CollectionQuery.Text(
      s"\"${name.value.replace("\"", " ").trim}\" AND online_media_type:Images" +
        (if units.isEmpty then "" else units.mkString(" AND NOT unit_code:(", " OR ", ")"))
    )

  def searchUrl(api: com.histmersion.plugin.Plugin.Http.GetUrl, name: com.histmersion.plugin.CollectionQuery.Text, rows: com.histmersion.plugins.media.Smithsonian.Rows, excludedUnits: List[com.histmersion.plugins.media.Smithsonian.Unit] = Nil): com.histmersion.plugin.Plugin.Http.GetUrl =
    com.histmersion.plugin.Plugin.Http.GetUrl(
      s"${api.value.stripSuffix("/")}/search?q=${query(name, excludedUnits).value.enc}&rows=${rows.value.max(1).min(1000)}" +
        "&type=edanmdm&row_group=objects"
    )

  /** Pure: the unit a row comes from — `descriptiveNonRepeating.unit_code`, else the row's `unitCode`. */
  def unitOf(json: Json): Option[com.histmersion.plugins.media.Smithsonian.Unit] =
    val c = json.hcursor
    c.downField("content").downField("descriptiveNonRepeating").str("unit_code").orElse(c.str("unitCode")).map(u => Unit(u))

  /** The labels of `freetext.date` that name when the object was made. */
  val DateLabels: Set[com.histmersion.plugins.media.Smithsonian.Label] =
    Set("date", "date made", "date created", "creation date", "production date", "object date").map(l => Label(l))

  /** The labels of `freetext.name` that name who made it. */
  val MakerLabels: Set[com.histmersion.plugins.media.Smithsonian.Label] = Set(
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
  def records(json: Json, excludedUnits: List[com.histmersion.plugins.media.Smithsonian.Unit] = Nil): Either[com.histmersion.plugins.media.Smithsonian.Problem, List[Either[com.histmersion.plugins.media.Smithsonian.Problem, MediaItem]]] =
    val c = json.hcursor
    val resp = c.downField("response")
    resp.downField("rows").focus.flatMap(_.asArray) match
      case Some(rows)                              => Right(rows.toList.map(record(_, excludedUnits)))
      case None if resp.long("rowCount").isDefined => Right(Nil)
      case None                                    =>
        val gov = c.downField("error")
        Left(Problem(List(gov.str("code"), gov.str("message")).flatten.mkString(": ") match
          case "" => resp.str("error").orElse(c.str("message")).getOrElse("no rows in the answer")
          case e  => e))

  private def labelled(c: ACursor, field: String): List[(com.histmersion.plugins.media.Smithsonian.Label, String)] =
    c.downField(field).focus.flatMap(_.asArray).toList.flatten.flatMap { e =>
      val h = e.hcursor
      h.str("content").map(v => Label(h.str("label").getOrElse("").toLowerCase(Locale.ROOT)) -> v)
    }

  /** Pure: one row of a search answer; a row of an excluded unit (a natural-history department's specimen) is withheld
    * with that reason, whatever its licence.
    */
  def record(json: Json, excludedUnits: List[com.histmersion.plugins.media.Smithsonian.Unit] = Nil): Either[com.histmersion.plugins.media.Smithsonian.Problem, MediaItem] =
    val c = json.hcursor
    val cont = c.downField("content")
    val dnr = cont.downField("descriptiveNonRepeating")
    val free = cont.downField("freetext")
    val idx = cont.downField("indexedStructured")
    dnr
      .str("record_ID")
      .orElse(c.str("url").map(_.split(":", 2).last))
      .orElse(c.str("id"))
      .toRight(Problem("no record id"))
      .map { key =>
        val title = c.str("title").orElse(dnr.downField("title").str("content")).getOrElse(key)
        val page = dnr
          .str("record_link")
          .orElse(dnr.str("guid"))
          .getOrElse(s"https://collections.si.edu/search/detail/${c.str("url").getOrElse(s"edanmdm:$key")}")
        val unit = dnr.str("data_source")
        val maker = labelled(free, "name").find((l, _) => MakerLabels(l)).map(_._2)
        val creditLine = labelled(free, "creditLine").headOption.map(_._2)
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
          .map(_.hcursor)
          .filter(m => m.str("type").forall(_.equalsIgnoreCase("Images")))
        val cc0 = media.find(m => m.downField("usage").str("access").exists(_.equalsIgnoreCase("CC0")))
        val rights: Either[com.histmersion.plugins.media.Smithsonian.Problem, MediaRights] = cc0 match
          case Some(_)               => Right(MediaRights.cc0(maker, credit))
          case None if media.isEmpty => Left(Problem("no image on the record"))
          case None                  =>
            val stated = media.flatMap(m => m.downField("usage").str("access")).distinct
            Left(Problem(s"no CC0 image (usage.access ${if stated.isEmpty then "missing" else stated.mkString(", ")})"))
        def resource(m: ACursor, label: String) =
          m.downField("resources")
            .focus
            .flatMap(_.asArray)
            .toList
            .flatten
            .map(_.hcursor)
            .find(r => r.str("label").exists(_.equalsIgnoreCase(label)))
            .flatMap(_.str("url"))
        val image = cc0.flatMap(m => resource(m, "Screen Image").orElse(m.str("content")))
        val thumb = cc0.flatMap(m => resource(m, "Thumbnail Image").orElse(m.str("thumbnail")))
        val original = cc0.flatMap(m => resource(m, "High-resolution JPEG").orElse(m.str("content")))
        // the free-text places first (each value's comma parts, most specific first), then the
        // structured location from its finest level down
        val places = labelled(free, "place").flatMap((l, v) =>
          v.split(",").toList.map(_.trim).filter(_.nonEmpty).map((if l.value.isEmpty then "place" else l.value) -> _)
        )
        val levels = idx.downField("geoLocation").focus.flatMap(_.asArray).toList.flatten.flatMap { g =>
          val h = g.hcursor
          h.keys.toList.flatten.filter(_.matches("L[0-9]+")).sortBy(k => -k.drop(1).toInt).flatMap { k =>
            val l = h.downField(k)
            l.str("content").map(v => l.str("type").getOrElse(k).toLowerCase(Locale.ROOT) -> v)
          }
        }
        val geo = (places ++ levels).distinctBy((_, v) => v.toLowerCase(Locale.ROOT))
        val period = labelled(free, "date")
          .filter((l, _) => DateLabels(l))
          .iterator
          .flatMap((_, v) => FreeTextDates.parse(FreeTextDates.Text(v)))
          .nextOption()
          .flatMap((a, b) => Period.span(a.value, b.value))
        val excluded =
          unitOf(json).filter(u => excludedUnits.exists(_.value.equalsIgnoreCase(u.value))).map(u => MediaItem.Withheld(s"an excluded unit (${u.value})"))
        MediaItem(
          MediaItem.Key(key),
          MediaItem.Title(title),
          MediaItem.PageUrl(page),
          image.map(i => MediaItem.ImageUrl(i)),
          rights.left.map(p => MediaItem.RightsPart1(p.value)),
          thumbUrl = thumb.map(t => MediaItem.ThumbUrl(t)),
          originalUrl = original.map(o => MediaItem.OriginalUrl(o)),
          geography = geo.map((f, v) => (MediaItem.GeographyPart1(f), MediaItem.GeographyPart2(v))),
          period = period,
          withheld = excluded
        )
      }
