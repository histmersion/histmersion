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

  type ValuesOfJsonResult = ValuesOfJsonResult.T
  object ValuesOfJsonResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordRightsPart1 = RecordRightsPart1.T
  object RecordRightsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type PhraseS = PhraseS.T
  object PhraseS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnyOfField = AnyOfField.T
  object AnyOfField extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AnyOfVs = AnyOfVs.T
  object AnyOfVs extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueryResult = QueryResult.T
  object QueryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type QueryName = QueryName.T
  object QueryName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlResult = SearchUrlResult.T
  object SearchUrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlApi = SearchUrlApi.T
  object SearchUrlApi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlName = SearchUrlName.T
  object SearchUrlName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SearchUrlRows = SearchUrlRows.T
  object SearchUrlRows extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LettersS = LettersS.T
  object LettersS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExcludedByResult = ExcludedByResult.T
  object ExcludedByResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExcludedByProviders = ExcludedByProviders.T
  object ExcludedByProviders extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExcludedByDataProviders = ExcludedByDataProviders.T
  object ExcludedByDataProviders extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExcludedTypeResult = ExcludedTypeResult.T
  object ExcludedTypeResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExcludedTypeTypes = ExcludedTypeTypes.T
  object ExcludedTypeTypes extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordUrlResult = RecordUrlResult.T
  object RecordUrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordUrlApi = RecordUrlApi.T
  object RecordUrlApi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordUrlId = RecordUrlId.T
  object RecordUrlId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbnailUrlResult = ThumbnailUrlResult.T
  object ThumbnailUrlResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbnailUrlApi = ThumbnailUrlApi.T
  object ThumbnailUrlApi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbnailUrlMedia = ThumbnailUrlMedia.T
  object ThumbnailUrlMedia extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbnailUrlWidth = ThumbnailUrlWidth.T
  object ThumbnailUrlWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PreviewSourceResult = PreviewSourceResult.T
  object PreviewSourceResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PreviewSourcePreview = PreviewSourcePreview.T
  object PreviewSourcePreview extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShortNameResult = ShortNameResult.T
  object ShortNameResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValuesResult = ValuesResult.T
  object ValuesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValuesField = ValuesField.T
  object ValuesField extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IsUriS = IsUriS.T
  object IsUriS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordsResultPart1 = RecordsResultPart1.T
  object RecordsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordsResultPart2Part1 = RecordsResultPart2Part1.T
  object RecordsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordsApi = RecordsApi.T
  object RecordsApi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordResultPart1 = RecordResultPart1.T
  object RecordResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordApi = RecordApi.T
  object RecordApi extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ItemResultPart1 = ItemResultPart1.T
  object ItemResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

  private def phrase(s: com.histmersion.plugins.media.Europeana.PhraseS) = "\"" + s.replace("\"", " ").trim + "\""
  private def anyOf(field: com.histmersion.plugins.media.Europeana.AnyOfField, vs: List[com.histmersion.plugins.media.Europeana.AnyOfVs]) =
    val ps = vs.map(_.trim).filter(_.nonEmpty)
    if ps.isEmpty then "" else ps.map(phrase).mkString(s" NOT $field:(", " OR ", ")")

  /** Pure: the query — the place as Europeana's aggregated `where` field, without the excluded aggregators (`PROVIDER`)
    * and types (`proxy_dc_type`).
    */
  def query(name: com.histmersion.plugins.media.Europeana.QueryName, ex: Exclusions = Exclusions.none): com.histmersion.plugins.media.Europeana.QueryResult =
    s"where:(${phrase(name)})" + anyOf("PROVIDER", ex.providers) + anyOf("proxy_dc_type", ex.types)

  def searchUrl(api: com.histmersion.plugins.media.Europeana.SearchUrlApi, name: com.histmersion.plugins.media.Europeana.SearchUrlName, rows: com.histmersion.plugins.media.Europeana.SearchUrlRows, ex: Exclusions = Exclusions.none): com.histmersion.plugins.media.Europeana.SearchUrlResult =
    s"${api.stripSuffix("/")}/record/v2/search.json?query=${query(name, ex).enc}&reusability=open&media=true" +
      s"&thumbnail=true&qf=${"TYPE:IMAGE".enc}&profile=rich&rows=${rows.max(1).min(100)}"

  private def letters(s: com.histmersion.plugins.media.Europeana.LettersS) = s.toLowerCase(Locale.ROOT).filter(_.isLetterOrDigit)

  /** Pure: why a record's aggregator or institution excludes it, or `None`. */
  def excludedBy(providers: List[com.histmersion.plugins.media.Europeana.ExcludedByProviders], dataProviders: List[com.histmersion.plugins.media.Europeana.ExcludedByDataProviders], ex: Exclusions): Option[com.histmersion.plugins.media.Europeana.ExcludedByResult] =
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
  def excludedType(types: List[com.histmersion.plugins.media.Europeana.ExcludedTypeTypes], ex: Exclusions): Option[com.histmersion.plugins.media.Europeana.ExcludedTypeResult] =
    val banned = ex.types.map(letters).filter(_.nonEmpty).toSet
    types.find(t => banned(letters(t)))

  /** Pure: the Record API URL of a Europeana id (`/2058611/_kimbl_3_1`), each segment encoded. */
  def recordUrl(api: com.histmersion.plugins.media.Europeana.RecordUrlApi, id: com.histmersion.plugins.media.Europeana.RecordUrlId): com.histmersion.plugins.media.Europeana.RecordUrlResult =
    val path = id.split("/").toList.filter(_.nonEmpty).map(s => s.enc.replace("+", "%20")).mkString("/")
    s"${api.stripSuffix("/")}/record/v2/$path.json"

  /** Pure: the Thumbnail API v3 URL of a media file at `width` (200 or 400). */
  def thumbnailUrl(api: com.histmersion.plugins.media.Europeana.ThumbnailUrlApi, media: com.histmersion.plugins.media.Europeana.ThumbnailUrlMedia, width: com.histmersion.plugins.media.Europeana.ThumbnailUrlWidth): com.histmersion.plugins.media.Europeana.ThumbnailUrlResult =
    val md5 =
      java.security.MessageDigest.getInstance("MD5").digest(media.getBytes(UTF_8)).map(b => f"${b & 0xff}%02x").mkString
    s"${api.stripSuffix("/")}/thumbnail/v3/$width/$md5.jpg"

  /** Pure: the media URL a search's `edmPreview` (a Thumbnail API v2 URL) was made from — its `uri` parameter — or
    * `None` when it is not one.
    */
  def previewSource(preview: com.histmersion.plugins.media.Europeana.PreviewSourcePreview): Option[com.histmersion.plugins.media.Europeana.PreviewSourceResult] =
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
  def shortName(l: Licence.Read): com.histmersion.plugins.media.Europeana.ShortNameResult = l.family match
    case Licence.Read.Cc0          => "CC0"
    case Licence.Read.PublicDomain => "Public Domain Mark"
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
  def values(c: ACursor, field: com.histmersion.plugins.media.Europeana.ValuesField): List[com.histmersion.plugins.media.Europeana.ValuesResult] =
    def ofJson(j: Json): List[com.histmersion.plugins.media.Europeana.ValuesOfJsonResult] =
      j.asString.toList ++ j.asArray.toList.flatten.flatMap(ofJson) ++ j.asObject.toList.flatMap { o =>
        val langs = o.keys.toList.sortBy(k => if k == "en" then 0 else if k == "def" then 1 else 2)
        langs.flatMap(k => o(k).toList.flatMap(ofJson))
      }
    c.downField(field).focus.toList.flatMap(ofJson).map(_.trim).filter(_.nonEmpty).distinct

  private def isUri(s: com.histmersion.plugins.media.Europeana.IsUriS) = s.startsWith("http://") || s.startsWith("https://")

  /** Pure: the items of a search answer; `success: false` is a `Left` with Europeana's message. */
  def records(
      json: Json,
      api: com.histmersion.plugins.media.Europeana.RecordsApi,
      licences: LicenceReader,
      ex: Exclusions = Exclusions.none
  ): Either[com.histmersion.plugins.media.Europeana.RecordsResultPart1, List[Either[com.histmersion.plugins.media.Europeana.RecordsResultPart2Part1, MediaItem]]] =
    val c = json.hcursor
    if c.bool("success").contains(false) then
      Left(List(c.str("error"), c.str("message")).flatten.distinct.mkString(": ") match
        case "" => "the search failed"
        case e  => e)
    else
      c.downField("items").focus.flatMap(_.asArray) match
        case Some(items)                              => Right(items.toList.map(record(_, api, licences, ex)))
        case None if c.long("totalResults").isDefined => Right(Nil)
        case None                                     => Left("no items in the answer")

  /** Pure: one search item; one of an excluded aggregator or institution ([[Exclusions]]) is withheld with that reason.
    */
  def record(
      json: Json,
      api: com.histmersion.plugins.media.Europeana.RecordApi,
      licences: LicenceReader,
      ex: Exclusions = Exclusions.none
  ): Either[com.histmersion.plugins.media.Europeana.RecordResultPart1, MediaItem] =
    val c = json.hcursor
    c.str("id").filter(_.startsWith("/")).toRight("no Europeana id").map { id =>
      val title = values(c, "title").headOption.orElse(values(c, "dcTitleLangAware").headOption).getOrElse(id)
      val creator = values(c, "dcCreator").filterNot(isUri).headOption
      val provider = values(c, "dataProvider").headOption
      val shownBy = values(c, "edmIsShownBy").headOption
      val media = values(c, "edmPreview").headOption.flatMap(previewSource).orElse(shownBy)
      val rights: Either[com.histmersion.plugins.media.Europeana.RecordRightsPart1, MediaRights] = values(c, "rights").headOption match
        case None    => Left("no rights statement on the record")
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
      val spatial = values(c, "dctermsSpatial")
      val geo = spatial.filterNot(isUri).map(Spatial -> _) ++
        (values(c, "edmPlaceLabel") ++ values(c, "edmPlaceLabelLangAware")).distinct.map(Place -> _)
      val ids = (spatial.filter(isUri) ++ values(c, "edmPlace")).flatMap(GazetteerUri.of).distinct
      val years = values(c, "year").flatMap(y => y.trim.toLongOption).filter(_ != 0)
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
        withheld = excludedBy(values(c, "provider"), values(c, "dataProvider"), ex)
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
  ): Either[com.histmersion.plugins.media.Europeana.ItemResultPart1, MediaItem] =
    val c = json.hcursor
    if c.bool("success").contains(false) then
      Left(List(c.str("error"), c.str("message")).flatten.distinct.mkString(": "))
    else
      val obj = c.downField("object")
      val aggs = obj.downField("aggregations").focus.flatMap(_.asArray).toList.flatten.map(_.hcursor)
      val types =
        obj.downField("proxies").focus.flatMap(_.asArray).toList.flatten.map(_.hcursor).flatMap(values(_, "dcType"))
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
