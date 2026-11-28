package com.histmersion
package plugins.media
import com.histmersion.util.json.RawJson
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.JsonCodec.given
import com.histmersion.util.json.Cursor
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.Problem

import com.histmersion.plugin.*

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{atBoundary, BoundaryKind}
import com.histmersion.lint.{tolerated, LintRule}

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
@atBoundary(BoundaryKind.Json, "reads the europeana collection's JSON answers into media records")
object Europeana:

  /** The plugin's id: what its problems name. */
  val Name = "media-europeana"
  type Clause = Clause.T
  object Clause extends Newtype[String, Pure]
  type Field = Field.T
  object Field extends Newtype[String, Pure]
  type Preview = Preview.T
  object Preview extends Newtype[String, Pure]
  type Source = Source.T
  object Source extends Newtype[String, Pure]

  val KeyHeader = "X-Api-Key"
  val Spatial = "dcterms:spatial"
  val Place = "edm:Place"

  /** The records an import refuses whatever place they name: those of an aggregator (`PROVIDER`, its name exactly, any
    * case), of an institution whose name contains one of `dataProviders` (any case), or whose `dc:type` is one of
    * `types` (letters compared, any case: `Preserved Specimen` = `PreservedSpecimen`). The aggregators and types are
    * excluded in the search as well; a type is read again from the Record API, since a search item does not carry it.
    */
  final case class Exclusions(
      providers: List[Exclusions.Providers],
      dataProviders: List[Exclusions.DataProviders],
      types: List[Exclusions.Types]
  )
  object Exclusions:
    type Providers = Providers.T
    object Providers extends Newtype[String, Pure]
    type DataProviders = DataProviders.T
    object DataProviders extends Newtype[String, Pure]
    type Types = Types.T
    object Types extends Newtype[String, Pure]

    val none: Exclusions = Exclusions(Nil, Nil, Nil)

  private def phrase(s: String) = "\"" + s.replace("\"", " ").trim + "\""
  private def anyOf(field: String, vs: List[String]) =
    val ps = vs.map(_.trim).filter(_.nonEmpty)
    if ps.isEmpty then "" else ps.map(phrase).mkString(s" NOT $field:(", " OR ", ")")

  /** Pure: the query — the place as Europeana's aggregated `where` field, without the excluded aggregators (`PROVIDER`)
    * and types (`proxy_dc_type`).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def query(name: MediaPlace.Label, ex: Exclusions = Exclusions.none): CollectionQuery.Text =
    CollectionQuery.Text(
      s"where:(${phrase(name.value)})" + anyOf("PROVIDER", ex.providers.map(_.value)) + anyOf(
        "proxy_dc_type",
        ex.types.map(_.value)
      )
    )

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def searchUrl(
      api: CollectionSettings.ApiUrl,
      name: MediaPlace.Label,
      rows: CollectionSettings.Candidates,
      ex: Exclusions = Exclusions.none
  ): Plugin.Http.Url =
    Plugin.Http.Url(
      s"${api.value.stripSuffix("/")}/record/v2/search.json?query=${query(name, ex).value.enc}&reusability=open&media=true" +
        s"&thumbnail=true&qf=${"TYPE:IMAGE".enc}&profile=rich&rows=${rows.value.max(1).min(100)}"
    )

  private def letters(s: String) = s.toLowerCase(Locale.ROOT).filter(_.isLetterOrDigit)

  /** Pure: why a record's aggregator or institution excludes it, or `None`. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def excludedBy(
      providers: List[Exclusions.Providers],
      dataProviders: List[Exclusions.DataProviders],
      ex: Exclusions
  ): Option[Clause] =
    providers
      .find(p => ex.providers.exists(_.value.trim.equalsIgnoreCase(p.value)))
      .map(p => Clause(s"an excluded provider (${p.value})"))
      .orElse(
        dataProviders
          .find { d =>
            val name = d.value.toLowerCase(Locale.ROOT)
            ex.dataProviders.map(_.value.trim.toLowerCase(Locale.ROOT)).exists(f => f.nonEmpty && name.contains(f))
          }
          .map(d => Clause(s"an excluded data provider (${d.value})"))
      )

  /** Pure: the first of a record's `dc:type` values that is excluded, or `None`. */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def excludedType(types: List[Exclusions.Types], ex: Exclusions): Option[Exclusions.Types] =
    val banned = ex.types.map(t => letters(t.value)).filter(_.nonEmpty).toSet
    types.find(t => banned(letters(t.value)))

  /** Pure: the Record API URL of a Europeana id (`/2058611/_kimbl_3_1`), each segment encoded. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def recordUrl(api: CollectionSettings.ApiUrl, id: MediaItem.Key): Plugin.Http.Url =
    val path = id.value.split("/").toList.filter(_.nonEmpty).map(s => s.enc.replace("+", "%20")).mkString("/")
    Plugin.Http.Url(s"${api.value.stripSuffix("/")}/record/v2/$path.json")

  /** Pure: the Thumbnail API v3 URL of a media file at `width` (200 or 400). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def thumbnailUrl(api: CollectionSettings.ApiUrl, media: Source, width: MediaRendition.Width): Plugin.Http.Url =
    val md5 =
      java.security.MessageDigest
        .getInstance("MD5")
        .digest(media.value.getBytes(UTF_8))
        .map(b => f"${b & 0xff}%02x")
        .mkString
    Plugin.Http.Url(s"${api.value.stripSuffix("/")}/thumbnail/v3/${width.value}/$md5.jpg")

  /** Pure: the media URL a search's `edmPreview` (a Thumbnail API v2 URL) was made from — its `uri` parameter — or
    * `None` when it is not one.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def previewSource(preview: Preview): Option[Source] =
    scala.util
      .Try(java.net.URI.create(preview.value.trim))
      .toOption
      .flatMap(u => Option(u.getRawQuery))
      .toList
      .flatMap(_.split("&"))
      .map(_.split("=", 2))
      .collectFirst { case Array("uri", v) =>
        java.net.URLDecoder.decode(v, UTF_8)
      }
      .filter(_.nonEmpty)
      .map(Source(_))

  /** Pure: a licence's short name for the credit line (`CC-BY-SA-4.0` → `CC BY-SA 4.0`). */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def shortName(l: Licence.Read): Licence = l.family.value match
    case Licence.Read.Cc0          => Licence("CC0")
    case Licence.Read.PublicDomain => Licence("Public Domain Mark")
    case _                         =>
      val parts = l.id.value.split("-").toList
      val (name, rest) = parts.span(p => !p.headOption.exists(_.isDigit))
      Licence((name match
        case "CC" :: kinds => ("CC " + kinds.mkString("-")) :: rest
        case other         => other ++ rest
      ).mkString(" "))

  /** Pure: the values of a field that may be a string, an array of strings, a LangMap (`{"en": [..]}`) or an array of
    * LangMaps (`[{"def": "Athens"}]`) — English and language-less values first.
    */
  extension (c: Cursor)
    /** Europeana's own words when it answers `success: false`. */
    private def errorOf: Option[String] =
      Option(List(c.str("error"), c.str("message")).flatten.distinct.mkString(": ")).filter(_.nonEmpty)

    @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
    private def langValues(field: Field): List[String] =
      def ofJson(j: Json): List[String] =
        j.asString.toList ++ j.asArray.toList.flatten.flatMap(ofJson) ++ j.asObject.toList.flatMap { o =>
          val langs = o.keys.toList.sortBy(k => if k == "en" then 0 else if k == "def" then 1 else 2)
          langs.flatMap(k => o(k).toList.flatMap(ofJson))
        }
      c.downField(field.value).focus.toList.flatMap(ofJson).map(_.trim).filter(_.nonEmpty).distinct

  private def isUri(s: String) = s.startsWith("http://") || s.startsWith("https://")

  /** Pure: the items of a search answer; `success: false` is a `Left` with Europeana's message. */
  def records(
      json: Json,
      api: CollectionSettings.ApiUrl,
      licences: LicenceReader,
      ex: Exclusions = Exclusions.none
  ): Either[Problem, List[Either[Problem, MediaItem]]] =
    val c = json.cursor
    if c.bool("success").contains(false) then
      Left(MediaPlugin.said(Name, Problem.Plugin.Claim.Failed, c.errorOf.getOrElse("the search failed")))
    else
      c.downField("items").focus.flatMap(_.asArray) match
        case Some(items)                              => Right(items.toList.map(record(_, api, licences, ex)))
        case None if c.long("totalResults").isDefined => Right(Nil)
        case None                                     => Left(MediaPlugin.missing("items"))

  /** Pure: one search item; one of an excluded aggregator or institution ([[Exclusions]]) is withheld with that reason.
    */
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def record(
      json: Json,
      api: CollectionSettings.ApiUrl,
      licences: LicenceReader,
      ex: Exclusions = Exclusions.none
  ): Either[Problem, MediaItem] =
    val c = json.cursor
    c.str("id").filter(_.startsWith("/")).toRight(MediaPlugin.missing("id")).map { id =>
      val title =
        c.langValues(Field("title")).headOption.orElse(c.langValues(Field("dcTitleLangAware")).headOption).getOrElse(id)
      val creator = c.langValues(Field("dcCreator")).filterNot(isUri).headOption
      val provider = c.langValues(Field("dataProvider")).headOption
      val shownBy = c.langValues(Field("edmIsShownBy")).headOption
      val media =
        c.langValues(Field("edmPreview"))
          .headOption
          .flatMap(v => previewSource(Preview(v)).map(_.value))
          .orElse(shownBy)
      val rights: Either[Problem, MediaRights] = c.langValues(Field("rights")).headOption match
        case None    => Left(MediaPlugin.rejected(Name, id, Problem.Plugin.Refusal.NoLicence))
        case Some(u) =>
          val lic = licences.readUrl(LicenceReader.ReadUrlUrl(u))
          if lic.family.value == Licence.Read.Unknown then
            Left(
              Problem.Invalid
                .Unknown(Problem.TypeName.named[Licence], Problem.Raw(u), Vector.empty, Vector(Problem.Field(id)))
            )
          else
            Right(
              MediaRights(
                Licence(u),
                Some(MediaRights.LicenceUrl(u)),
                creator.map(MediaRights.Author(_)),
                MediaRights.Credit(
                  (List(Some(title), creator, provider).flatten ++ List("via Europeana", shortName(lic).value))
                    .mkString(", ")
                )
              )
            )
      val spatial = c.langValues(Field("dctermsSpatial"))
      val geo = (spatial.filterNot(isUri).map(Spatial -> _) ++
        (c.langValues(Field("edmPlaceLabel")) ++ c.langValues(Field("edmPlaceLabelLangAware"))).distinct
          .map(Place -> _))
        .map((k, v) => (MediaItem.Geography.Field(k), MediaItem.Geography.Value(v)))
      val ids = (spatial.filter(isUri) ++ c.langValues(Field("edmPlace")))
        .flatMap(u => GazetteerUri.of(GazetteerPlace.Id(u)))
        .distinct
      val years = c.langValues(Field("year")).flatMap(y => y.trim.toLongOption).filter(_ != 0)
      val period = Option.when(years.nonEmpty)(()).flatMap(_ => Period.span(years.min, years.max))
      MediaItem(
        MediaItem.Key(id.stripPrefix("/")),
        MediaItem.Title(title),
        MediaItem.PageUrl(s"https://www.europeana.eu/item$id"),
        media.map(m => MediaItem.ImageUrl(thumbnailUrl(api, Source(m), MediaRendition.Width(400)).value)),
        rights,
        thumbUrl = media.map(m => MediaItem.ThumbUrl(thumbnailUrl(api, Source(m), MediaRendition.Width(200)).value)),
        originalUrl = shownBy.map(MediaItem.OriginalUrl(_)),
        geography = geo,
        period = period,
        ids = ids,
        withheld = excludedBy(
          c.langValues(Field("provider")).map(Exclusions.Providers(_)),
          c.langValues(Field("dataProvider")).map(Exclusions.DataProviders(_)),
          ex
        ).map(w => MediaItem.Withheld(w.value))
      )
    }

  /** Pure: a record completed from the Record API — the licence read AGAIN, from the web resource that is shown
    * (`webResourceEdmRights`), else from the provider's aggregation (`edmRights`): a missing or different licence
    * refuses it, and so does an excluded `dc:type` of any of its proxies.
    */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G45Boundary, "pending G45 (boundaries lift type classes instead of hand .value)")
  def item(
      json: Json,
      rec: MediaItem,
      licences: LicenceReader,
      ex: Exclusions = Exclusions.none
  ): Either[Problem, MediaItem] =
    val c = json.cursor
    if c.bool("success").contains(false) then
      Left(MediaPlugin.said(Name, Problem.Plugin.Claim.Failed, c.errorOf.getOrElse("the record failed")))
    else
      val obj = c.downField("object")
      val aggs = obj.downField("aggregations").focus.flatMap(_.asArray).toList.flatten.map(_.cursor)
      val types =
        obj
          .downField("proxies")
          .focus
          .flatMap(_.asArray)
          .toList
          .flatten
          .map(_.cursor)
          .flatMap(_.langValues(Field("dcType")))
          .map(Exclusions.Types(_))
      val firstAggregation: Either[Problem, Cursor] = excludedType(types, ex) match
        case Some(t) => Left(MediaPlugin.said(Name, Problem.Plugin.Claim.Rejected, s"an excluded type ($t)"))
        case None    => aggs.headOption.toRight(MediaPlugin.missing("aggregations"))
      firstAggregation.flatMap { agg =>
        val shownBy = agg.str("edmIsShownBy").orElse(agg.langValues(Field("edmIsShownBy")).headOption)
        val resource = agg
          .downField("webResources")
          .focus
          .flatMap(_.asArray)
          .toList
          .flatten
          .map(_.cursor)
          .find(r => shownBy.isDefined && r.str("about") == shownBy)
        val stated =
          resource
            .flatMap(_.langValues(Field("webResourceEdmRights")).headOption)
            .orElse(agg.langValues(Field("edmRights")).headOption)
        rec.rights.flatMap { r =>
          val searched = licences.readUrl(LicenceReader.ReadUrlUrl(r.licence.value))
          stated.map(u => licences.readUrl(LicenceReader.ReadUrlUrl(u))) match
            case None => Left(MediaPlugin.rejected(Name, s"${rec.key}", Problem.Plugin.Refusal.NoLicence))
            case Some(l) if l.id != searched.id =>
              Left(
                MediaPlugin.said(
                  Name,
                  Problem.Plugin.Claim.Rejected,
                  s"the full record's licence (${if l.id.value.isEmpty then "unreadable" else l.id.value}) differs " +
                    s"from the search's (${searched.id.value})"
                )
              )
            case Some(_) => Right(rec.copy(originalUrl = shownBy.map(MediaItem.OriginalUrl(_)).orElse(rec.originalUrl)))
        }
      }
