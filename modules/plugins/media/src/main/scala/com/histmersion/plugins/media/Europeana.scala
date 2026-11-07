package com.histmersion.plugins.media

import com.histmersion.plugin.*
import io.circe.{ACursor, Json}

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale

/** The Europeana APIs (Search, Record and Thumbnail), with a user's personal key sent as the `X-Api-Key` header
  * (Europeana's preferred form; the `wskey` parameter is deprecated). Search:
  * `/record/v2/search.json?query=where:("<name>")&reusability=open&media=true&thumbnail=true&qf=TYPE:IMAGE&profile=rich`
  * — `reusability=open` is Europeana's own filter for PDM, CC0, CC BY and CC BY-SA. Each item's licence is still read
  * from its own `rights` (the edm:rights URL) and judged by the host's allowlist; a rightsstatements.org statement or
  * any other non-CC URL is unreadable as a licence and refused. A record to store is read again through the Record API
  * (`/record/v2/{id}.json`): the licence of the web resource that is stored (its `webResourceEdmRights`, else the
  * aggregation's `edmRights`) must be the search's. The pictures are Europeana's own cached renditions through the
  * keyless Thumbnail API v3 (400 px stored, 200 px the thumbnail; the media file's URL hashed with MD5, as the API
  * documents); the provider's file (`edmIsShownBy`) is recorded as the original. Pure.
  */
object Europeana:
  type Clause = Clause.T
  object Clause extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Field = Field.T
  object Field extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Preview = Preview.T
  object Preview extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Source = Source.T
  object Source extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Text = Text.T
  object Text extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ValuesOfJsonResult = ValuesOfJsonResult.T
  object ValuesOfJsonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type AnyOfVs = AnyOfVs.T
  object AnyOfVs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlName = _root_.com.histmersion.plugin.CollectionQuery.Text
  val SearchUrlName: _root_.com.histmersion.plugin.CollectionQuery.Text.type = _root_.com.histmersion.plugin.CollectionQuery.Text
  type SearchUrlRows = SearchUrlRows.T
  object SearchUrlRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbnailUrlWidth = ThumbnailUrlWidth.T
  object ThumbnailUrlWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValuesResult = ValuesResult.T
  object ValuesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordsApi = _root_.com.histmersion.plugin.Plugin.Http.GetUrl
  val RecordsApi: _root_.com.histmersion.plugin.Plugin.Http.GetUrl.type = _root_.com.histmersion.plugin.Plugin.Http.GetUrl
  type RecordApi = _root_.com.histmersion.plugin.Plugin.Http.GetUrl
  val RecordApi: _root_.com.histmersion.plugin.Plugin.Http.GetUrl.type = _root_.com.histmersion.plugin.Plugin.Http.GetUrl

  val KeyHeader = "X-Api-Key"
  val Spatial = "dcterms:spatial"
  val Place = "edm:Place"

  /** The records an import refuses whatever place they name: those of an aggregator (`PROVIDER`, its name exactly, any
    * case), of an institution whose name contains one of `dataProviders` (any case), or whose `dc:type` is one of
    * `types` (letters compared, any case: `Preserved Specimen` = `PreservedSpecimen`). The aggregators and types are
    * excluded in the search as well; a type is read again from the Record API, since a search item does not carry it.
    */
  final case class Exclusions(providers: List[Exclusions.Providers], dataProviders: List[Exclusions.DataProviders], types: List[Exclusions.Types])
  object Exclusions:
    type Providers = Providers.T
    object Providers extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DataProviders = DataProviders.T
    object DataProviders extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Types = Types.T
    object Types extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    val none: Exclusions = Exclusions(Nil, Nil, Nil)

  private def phrase(s: com.histmersion.plugins.media.Europeana.Text) = "\"" + s.replace("\"", " ").trim + "\""
  private def anyOf(field: com.histmersion.plugins.media.Europeana.Field, vs: List[com.histmersion.plugins.media.Europeana.AnyOfVs]) =
    val ps = vs.map(_.trim).filter(_.nonEmpty)
    if ps.isEmpty then "" else ps.map(phrase).mkString(s" NOT $field:(", " OR ", ")")

  /** Pure: the query — the place as Europeana's aggregated `where` field, without the excluded aggregators (`PROVIDER`)
    * and types (`proxy_dc_type`).
    */
  def query(name: com.histmersion.plugin.CollectionQuery.Text, ex: Exclusions = Exclusions.none): com.histmersion.plugin.CollectionQuery.Text =
    s"where:(${phrase(name)})" + anyOf("PROVIDER", ex.providers) + anyOf("proxy_dc_type", ex.types)

  def searchUrl(api: com.histmersion.plugin.Plugin.Http.GetUrl, name: com.histmersion.plugins.media.Europeana.SearchUrlName, rows: com.histmersion.plugins.media.Europeana.SearchUrlRows, ex: Exclusions = Exclusions.none): com.histmersion.plugin.Plugin.Http.GetUrl =
    s"${api.stripSuffix("/")}/record/v2/search.json?query=${query(name, ex).enc}&reusability=open&media=true" +
      s"&thumbnail=true&qf=${"TYPE:IMAGE".enc}&profile=rich&rows=${rows.max(1).min(100)}"

  private def letters(s: com.histmersion.plugins.media.Europeana.Text) = s.toLowerCase(Locale.ROOT).filter(_.isLetterOrDigit)

  /** Pure: why a record's aggregator or institution excludes it, or `None`. */
  def excludedBy(providers: List[com.histmersion.plugins.media.Europeana.Exclusions.Providers], dataProviders: List[com.histmersion.plugins.media.Europeana.Exclusions.DataProviders], ex: Exclusions): Option[com.histmersion.plugins.media.Europeana.Clause] =
    providers
      .find(p => ex.providers.exists(_.trim.equalsIgnoreCase(p)))
      .map(p => s"an excluded provider ($p)")
      .orElse(
        dataProviders
          .find { d =>
            val name = d.toLowerCase(Locale.ROOT)
            ex.dataProviders.map(_.trim.toLowerCase(Locale.ROOT)).exists(f => f.nonEmpty && name.contains(f))
          }
          .map(d => s"an excluded data provider ($d)")
      )

  /** Pure: the first of a record's `dc:type` values that is excluded, or `None`. */
  def excludedType(types: List[com.histmersion.plugins.media.Europeana.Exclusions.Types], ex: Exclusions): Option[com.histmersion.plugins.media.Europeana.Clause] =
    val banned = ex.types.map(letters).filter(_.nonEmpty).toSet
    types.find(t => banned(letters(t)))

  /** Pure: the Record API URL of a Europeana id (`/2058611/_kimbl_3_1`), each segment encoded. */
  def recordUrl(api: com.histmersion.plugin.Plugin.Http.GetUrl, id: com.histmersion.plugin.MediaItem.Key): com.histmersion.plugin.Plugin.Http.GetUrl =
    val path = id.split("/").toList.filter(_.nonEmpty).map(s => s.enc.replace("+", "%20")).mkString("/")
    s"${api.stripSuffix("/")}/record/v2/$path.json"

  /** Pure: the Thumbnail API v3 URL of a media file at `width` (200 or 400). */
  def thumbnailUrl(api: com.histmersion.plugin.Plugin.Http.GetUrl, media: com.histmersion.plugin.MediaItem.Key, width: com.histmersion.plugins.media.Europeana.ThumbnailUrlWidth): com.histmersion.plugin.Plugin.Http.GetUrl =
    val md5 =
      java.security.MessageDigest.getInstance("MD5").digest(media.getBytes(UTF_8)).map(b => f"${b & 0xff}%02x").mkString
    s"${api.stripSuffix("/")}/thumbnail/v3/$width/$md5.jpg"

  /** Pure: the media URL a search's `edmPreview` (a Thumbnail API v2 URL) was made from — its `uri` parameter — or
    * `None` when it is not one.
    */
  def previewSource(preview: com.histmersion.plugins.media.Europeana.Preview): Option[com.histmersion.plugins.media.Europeana.Source] =
    scala.util
      .Try(java.net.URI.create(preview.trim))
      .toOption
      .flatMap(u => Option(u.getRawQuery))
      .toList
      .flatMap(_.split("&"))
      .map(_.split("=", 2))
      .collectFirst { case Array("uri", v) =>
        java.net.URLDecoder.decode(v, UTF_8)
      }
      .filter(_.nonEmpty)

  /** Pure: a licence's short name for the credit line (`CC-BY-SA-4.0` → `CC BY-SA 4.0`). */
  def shortName(l: Licence.Read): com.histmersion.plugin.Licence = l.family match
    case Licence.Read.Cc0          => com.histmersion.plugin.Licence("CC0")
    case Licence.Read.PublicDomain => com.histmersion.plugin.Licence("Public Domain Mark")
    case _                         =>
      val parts = l.id.split("-").toList
      val (name, rest) = parts.span(p => !p.headOption.exists(_.isDigit))
      (name match
        case "CC" :: kinds => ("CC " + kinds.mkString("-")) :: rest
        case other         => other ++ rest
      ).mkString(" ")

  /** Pure: the values of a field that may be a string, an array of strings, a LangMap (`{"en": [..]}`) or an array of
    * LangMaps (`[{"def": "Athens"}]`) — English and language-less values first.
    */
  def values(c: ACursor, field: com.histmersion.plugins.media.Europeana.Field): List[com.histmersion.plugins.media.Europeana.ValuesResult] =
    def ofJson(j: Json): List[com.histmersion.plugins.media.Europeana.ValuesOfJsonResult] =
      j.asString.toList ++ j.asArray.toList.flatten.flatMap(ofJson) ++ j.asObject.toList.flatMap { o =>
        val langs = o.keys.toList.sortBy(k => if k == "en" then 0 else if k == "def" then 1 else 2)
        langs.flatMap(k => o(k).toList.flatMap(ofJson))
      }
    c.downField(field).focus.toList.flatMap(ofJson).map(_.trim).filter(_.nonEmpty).distinct

  private def isUri(s: com.histmersion.plugins.media.Europeana.Text) = s.startsWith("http://") || s.startsWith("https://")

  /** Pure: the items of a search answer; `success: false` is a `Left` with Europeana's message. */
  def records(
      json: Json,
      api: com.histmersion.plugins.media.Europeana.RecordsApi,
      licences: LicenceReader,
      ex: Exclusions = Exclusions.none
  ): Either[com.histmersion.plugins.media.Europeana.Problem, List[Either[com.histmersion.plugin.MediaItem.Key, MediaItem]]] =
    val c = json.hcursor
    if c.bool("success").contains(false) then
      Left(List(c.str("error"), c.str("message")).flatten.distinct.mkString(": ") match
        case "" => com.histmersion.plugins.media.Europeana.Problem("the search failed")
        case e  => e)
    else
      c.downField("items").focus.flatMap(_.asArray) match
        case Some(items)                              => Right(items.toList.map(record(_, api, licences, ex)))
        case None if c.long("totalResults").isDefined => Right(Nil)
        case None                                     => Left(com.histmersion.plugins.media.Europeana.Problem("no items in the answer"))

  /** Pure: one search item; one of an excluded aggregator or institution ([[Exclusions]]) is withheld with that reason.
    */
  def record(
      json: Json,
      api: com.histmersion.plugins.media.Europeana.RecordApi,
      licences: LicenceReader,
      ex: Exclusions = Exclusions.none
  ): Either[com.histmersion.plugins.media.Europeana.Problem, MediaItem] =
    val c = json.hcursor
    c.str("id").filter(_.startsWith("/")).toRight("no Europeana id").map { id =>
      val title = values(c, com.histmersion.plugins.media.Europeana.Field("title")).headOption.orElse(values(c, "dcTitleLangAware").headOption).getOrElse(id)
      val creator = values(c, com.histmersion.plugins.media.Europeana.Field("dcCreator")).filterNot(isUri).headOption
      val provider = values(c, com.histmersion.plugins.media.Europeana.Field("dataProvider")).headOption
      val shownBy = values(c, com.histmersion.plugins.media.Europeana.Field("edmIsShownBy")).headOption
      val media = values(c, com.histmersion.plugins.media.Europeana.Field("edmPreview")).headOption.flatMap(previewSource).orElse(shownBy)
      val rights: Either[com.histmersion.plugins.media.Europeana.Problem, MediaRights] = values(c, com.histmersion.plugins.media.Europeana.Field("rights")).headOption match
        case None    => Left(com.histmersion.plugins.media.Europeana.Problem("no rights statement on the record"))
        case Some(u) =>
          val lic = licences.readUrl(u)
          if lic.family == Licence.Read.Unknown then Left(s"not an open licence ($u)")
          else
            Right(
              MediaRights(
                Licence(u),
                Some(u),
                creator,
                (List(Some(title), creator, provider).flatten ++ List("via Europeana", shortName(lic))).mkString(", ")
              )
            )
      val spatial = values(c, com.histmersion.plugins.media.Europeana.Field("dctermsSpatial"))
      val geo = spatial.filterNot(isUri).map(Spatial -> _) ++
        (values(c, "edmPlaceLabel") ++ values(c, "edmPlaceLabelLangAware")).distinct.map(Place -> _)
      val ids = (spatial.filter(isUri) ++ values(c, "edmPlace")).flatMap(GazetteerUri.of).distinct
      val years = values(c, com.histmersion.plugins.media.Europeana.Field("year")).flatMap(y => y.trim.toLongOption).filter(_ != 0)
      val period = Option.when(years.nonEmpty)(()).flatMap(_ => Period.span(years.min, years.max))
      MediaItem(
        id.stripPrefix("/"),
        title,
        s"https://www.europeana.eu/item$id",
        media.map(thumbnailUrl(api, _, 400)),
        rights,
        thumbUrl = media.map(thumbnailUrl(api, _, 200)),
        originalUrl = shownBy,
        geography = geo,
        period = period,
        ids = ids,
        withheld = excludedBy(values(c, com.histmersion.plugins.media.Europeana.Field("provider")), values(c, com.histmersion.plugins.media.Europeana.Field("dataProvider")), ex)
      )
    }

  /** Pure: a record completed from the Record API — the licence read AGAIN, from the web resource that is shown
    * (`webResourceEdmRights`), else from the provider's aggregation (`edmRights`): a missing or different licence
    * refuses it, and so does an excluded `dc:type` of any of its proxies.
    */
  def item(
      json: Json,
      rec: MediaItem,
      licences: LicenceReader,
      ex: Exclusions = Exclusions.none
  ): Either[com.histmersion.plugins.media.Europeana.Problem, MediaItem] =
    val c = json.hcursor
    if c.bool("success").contains(false) then
      Left(List(c.str("error"), c.str("message")).flatten.distinct.mkString(": "))
    else
      val obj = c.downField("object")
      val aggs = obj.downField("aggregations").focus.flatMap(_.asArray).toList.flatten.map(_.hcursor)
      val types =
        obj.downField("proxies").focus.flatMap(_.asArray).toList.flatten.map(_.hcursor).flatMap(values(_, com.histmersion.plugins.media.Europeana.Field("dcType")))
      val firstAggregation = excludedType(types, ex) match
        case Some(t) => Left(s"an excluded type ($t)")
        case None    => aggs.headOption.toRight("no aggregation on the record")
      firstAggregation.flatMap { agg =>
        val shownBy = agg.str("edmIsShownBy").orElse(values(agg, "edmIsShownBy").headOption)
        val resource = agg
          .downField("webResources")
          .focus
          .flatMap(_.asArray)
          .toList
          .flatten
          .map(_.hcursor)
          .find(r => shownBy.isDefined && r.str("about") == shownBy)
        val stated =
          resource.flatMap(values(_, "webResourceEdmRights").headOption).orElse(values(agg, "edmRights").headOption)
        rec.rights.flatMap { r =>
          val searched = licences.readUrl(r.licence.value)
          stated.map(licences.readUrl) match
            case None                           => Left("no licence on the full record")
            case Some(l) if l.id != searched.id =>
              Left(s"the full record's licence (${
                  if l.id.isEmpty then "unreadable" else l.id
                }) differs from the search's (${searched.id})")
            case Some(_) => Right(rec.copy(originalUrl = shownBy.orElse(rec.originalUrl)))
        }
      }
