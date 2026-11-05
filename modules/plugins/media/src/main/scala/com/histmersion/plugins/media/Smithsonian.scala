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

  /** The header the key travels in (api.data.gov's documented alternative to the `api_key` parameter). */
  type RecordRightsPart1 = RecordRightsPart1.T
  object RecordRightsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordResourceLabel = RecordResourceLabel.T
  object RecordResourceLabel extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type QueryResult = QueryResult.T
  object QueryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueryName = QueryName.T
  object QueryName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueryExcludedUnits = QueryExcludedUnits.T
  object QueryExcludedUnits extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlResult = SearchUrlResult.T
  object SearchUrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlApi = SearchUrlApi.T
  object SearchUrlApi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlName = SearchUrlName.T
  object SearchUrlName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlRows = SearchUrlRows.T
  object SearchUrlRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlExcludedUnits = SearchUrlExcludedUnits.T
  object SearchUrlExcludedUnits extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type UnitOfResult = UnitOfResult.T
  object UnitOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DateLabels = DateLabels.T
  object DateLabels extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MakerLabels = MakerLabels.T
  object MakerLabels extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordsResultPart1 = RecordsResultPart1.T
  object RecordsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordsResultPart2Part1 = RecordsResultPart2Part1.T
  object RecordsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordsExcludedUnits = RecordsExcludedUnits.T
  object RecordsExcludedUnits extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelledResultPart1 = LabelledResultPart1.T
  object LabelledResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelledResultPart2 = LabelledResultPart2.T
  object LabelledResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LabelledField = LabelledField.T
  object LabelledField extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordResultPart1 = RecordResultPart1.T
  object RecordResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordExcludedUnits = RecordExcludedUnits.T
  object RecordExcludedUnits extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val KeyHeader = "X-Api-Key"

  /** Pure: the query — the place's name as a phrase, objects with images only, none of the excluded units (`unit_code`,
    * a fielded term the API's `terms` endpoint lists).
    */
  def query(name: com.histmersion.plugins.media.Smithsonian.QueryName, excludedUnits: List[com.histmersion.plugins.media.Smithsonian.QueryExcludedUnits] = Nil): com.histmersion.plugins.media.Smithsonian.QueryResult =
    val units = excludedUnits.map(_.replaceAll("[^A-Za-z0-9_]", "")).filter(_.nonEmpty)
    s"\"${name.replace("\"", " ").trim}\" AND online_media_type:Images" +
      (if units.isEmpty then "" else units.mkString(" AND NOT unit_code:(", " OR ", ")"))

  def searchUrl(api: com.histmersion.plugins.media.Smithsonian.SearchUrlApi, name: com.histmersion.plugins.media.Smithsonian.SearchUrlName, rows: com.histmersion.plugins.media.Smithsonian.SearchUrlRows, excludedUnits: List[com.histmersion.plugins.media.Smithsonian.SearchUrlExcludedUnits] = Nil): com.histmersion.plugins.media.Smithsonian.SearchUrlResult =
    s"${api.stripSuffix("/")}/search?q=${query(name, excludedUnits).enc}&rows=${rows.max(1).min(1000)}" +
      "&type=edanmdm&row_group=objects"

  /** Pure: the unit a row comes from — `descriptiveNonRepeating.unit_code`, else the row's `unitCode`. */
  def unitOf(json: Json): Option[com.histmersion.plugins.media.Smithsonian.UnitOfResult] =
    val c = json.hcursor
    c.downField("content").downField("descriptiveNonRepeating").str("unit_code").orElse(c.str("unitCode"))

  /** The labels of `freetext.date` that name when the object was made. */
  val DateLabels: Set[com.histmersion.plugins.media.Smithsonian.DateLabels] =
    Set("date", "date made", "date created", "creation date", "production date", "object date")

  /** The labels of `freetext.name` that name who made it. */
  val MakerLabels: Set[com.histmersion.plugins.media.Smithsonian.MakerLabels] = Set(
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
  )

  /** Pure: the rows of a search answer; an error answer (api.data.gov's `{error: {code, message}}`, or EDAN's
    * `{response: {error}}`) is a `Left`.
    */
  def records(json: Json, excludedUnits: List[com.histmersion.plugins.media.Smithsonian.RecordsExcludedUnits] = Nil): Either[com.histmersion.plugins.media.Smithsonian.RecordsResultPart1, List[Either[com.histmersion.plugins.media.Smithsonian.RecordsResultPart2Part1, MediaItem]]] =
    val c = json.hcursor
    val resp = c.downField("response")
    resp.downField("rows").focus.flatMap(_.asArray) match
      case Some(rows)                              => Right(rows.toList.map(record(_, excludedUnits)))
      case None if resp.long("rowCount").isDefined => Right(Nil)
      case None                                    =>
        val gov = c.downField("error")
        Left(List(gov.str("code"), gov.str("message")).flatten.mkString(": ") match
          case "" => resp.str("error").orElse(c.str("message")).getOrElse("no rows in the answer")
          case e  => e)

  private def labelled(c: ACursor, field: com.histmersion.plugins.media.Smithsonian.LabelledField): List[(com.histmersion.plugins.media.Smithsonian.LabelledResultPart1, com.histmersion.plugins.media.Smithsonian.LabelledResultPart2)] =
    c.downField(field).focus.flatMap(_.asArray).toList.flatten.flatMap { e =>
      val h = e.hcursor
      h.str("content").map(v => h.str("label").getOrElse("").toLowerCase(Locale.ROOT) -> v)
    }

  /** Pure: one row of a search answer; a row of an excluded unit (a natural-history department's specimen) is withheld
    * with that reason, whatever its licence.
    */
  def record(json: Json, excludedUnits: List[com.histmersion.plugins.media.Smithsonian.RecordExcludedUnits] = Nil): Either[com.histmersion.plugins.media.Smithsonian.RecordResultPart1, MediaItem] =
    val c = json.hcursor
    val cont = c.downField("content")
    val dnr = cont.downField("descriptiveNonRepeating")
    val free = cont.downField("freetext")
    val idx = cont.downField("indexedStructured")
    dnr
      .str("record_ID")
      .orElse(c.str("url").map(_.split(":", 2).last))
      .orElse(c.str("id"))
      .toRight("no record id")
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
        val rights: Either[com.histmersion.plugins.media.Smithsonian.RecordRightsPart1, MediaRights] = cc0 match
          case Some(_)               => Right(MediaRights.cc0(maker, credit))
          case None if media.isEmpty => Left("no image on the record")
          case None                  =>
            val stated = media.flatMap(m => m.downField("usage").str("access")).distinct
            Left(s"no CC0 image (usage.access ${if stated.isEmpty then "missing" else stated.mkString(", ")})")
        def resource(m: ACursor, label: com.histmersion.plugins.media.Smithsonian.RecordResourceLabel) =
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
          v.split(",").toList.map(_.trim).filter(_.nonEmpty).map((if l.isEmpty then "place" else l) -> _)
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
          .flatMap((_, v) => FreeTextDates.parse(v))
          .nextOption()
          .flatMap((a, b) => Period.span(a, b))
        val excluded =
          unitOf(json).filter(u => excludedUnits.exists(_.equalsIgnoreCase(u))).map(u => s"an excluded unit ($u)")
        MediaItem(
          key,
          title,
          page,
          image,
          rights,
          thumbUrl = thumb,
          originalUrl = original,
          geography = geo,
          period = period,
          withheld = excluded
        )
      }
