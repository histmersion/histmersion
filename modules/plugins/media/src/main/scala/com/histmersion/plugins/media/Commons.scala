package com.histmersion
package plugins.media

import com.histmersion.upstreams.media.MediaText
import io.circe.Json
import com.histmersion.plugin.*
import com.histmersion.upstreams.landing.RepositoryLanding
import com.histmersion.upstreams.wikidata.{WikidataEntities, WikidataItem, WikidataStatement}
import com.histmersion.plugin.Plugin.Config.given
import hearth.kindlings.sconfigderivation.ConfigReader
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** Wikimedia Commons, read through the action API's `prop=imageinfo` with `extmetadata`: URL building and parsing.
  * Pure.
  */
object Commons:
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]

  /** The `extmetadata` fields read — and asked for, so the answer stays small. */

  type MetaFields = MetaFieldsValue
  type MetaFieldsValue = MetaFieldsValue.T
  object MetaFieldsValue extends RefinedType[String, Pure]
  type ImageInfoUrlMeta = ImageInfoUrlMeta.T
  object ImageInfoUrlMeta extends RefinedType[Boolean, Pure]

  val MetaFields: List[MetaFields] = List(
    MetaFieldsValue("LicenseShortName"),
    MetaFieldsValue("License"),
    MetaFieldsValue("LicenseUrl"),
    MetaFieldsValue("Artist"),
    MetaFieldsValue("Credit"),
    MetaFieldsValue("Attribution"),
    MetaFieldsValue("AttributionRequired"),
    MetaFieldsValue("Copyrighted"),
    MetaFieldsValue("NonFree"),
    MetaFieldsValue("UsageTerms"),
    MetaFieldsValue("Restrictions"),
    MetaFieldsValue("ObjectName")
  )

  /** Pure: the canonical title of a file name as Wikidata gives it (`Lions-Gate-Mycenae.jpg` →
    * `File:Lions-Gate-Mycenae.jpg`): underscores are spaces, and the first letter is upper case.
    */
  def fileTitle(name: File.Title): File.Title =
    val n = name.value.trim.stripPrefix("File:").stripPrefix("file:").replace('_', ' ').replaceAll("\\s+", " ").trim
    if n.isEmpty then File.Title("") else File.Title("File:" + n.head.toUpper + n.tail)

  /** Pure: the `imageinfo` URL for a batch of titles, with the rendition `width`; `meta` adds the licence fields (the
    * thumbnail call needs only the URL). `maxlag` 0 = not sent.
    */
  def imageInfoUrl(api: Settings.ApiUrl, titles: List[File.Title], width: File.Width, maxlag: Settings.Maxlag, meta: ImageInfoUrlMeta): Plugin.Http.GetUrl =
    val props = if meta.value then "url|size|mime|extmetadata" else "url|size|mime"
    val ext = if meta.value then s"&iiextmetadatafilter=${MetaFields.map(_.value).mkString("|").enc}&iiextmetadatalanguage=en" else ""
    val lag = if maxlag.value > 0 then s"&maxlag=${maxlag.value}" else ""
    Plugin.Http.GetUrl(
      s"${api.value}?action=query&format=json&formatversion=2&prop=imageinfo&iiprop=${props.enc}&iiurlwidth=${width.value}" +
        s"$ext&titles=${titles.map(_.value).mkString("|").enc}$lag"
    )

  private def untracked(url: String): String =
    MediaText.stripTracking(RepositoryLanding.Candidate.Url(url)).value

  /** Pure: the files of an `imageinfo` answer, keyed by the title ASKED for (the API's `normalized` list maps an asked
    * title to its canonical one). A file Commons does not have is `Left("missing on Commons")`; an API error is `Left`
    * for the whole answer.
    */
  def imageInfo(json: Json): Either[Problem, Map[File.Title, Either[Problem, File]]] =
    val c = json.hcursor
    c.downField("error").focus match
      case Some(err) =>
        Left(
          Problem(
            s"${err.hcursor.get[String]("code").getOrElse("error")}: ${err.hcursor.get[String]("info").getOrElse("")}".trim
          )
        )
      case None =>
        val q = c.downField("query")
        val normalized = q
          .downField("normalized")
          .focus
          .flatMap(_.asArray)
          .toList
          .flatten
          .flatMap { n =>
            for f <- n.hcursor.get[String]("from").toOption; t <- n.hcursor.get[String]("to").toOption yield t -> f
          }
          .groupMap(_._1)(_._2)
        val pages = q.downField("pages").focus.flatMap(_.asArray).toList.flatten.flatMap { p =>
          val h = p.hcursor
          val title = h.get[String]("title").getOrElse("")
          val info = h.downField("imageinfo").downArray
          val entry: Either[Problem, File] =
            if h.downField("missing").focus.isDefined || h.downField("invalid").focus.isDefined || info.focus.isEmpty
            then Left(Problem("missing on Commons"))
            else
              val i = info
              val meta = i
                .downField("extmetadata")
                .focus
                .flatMap(_.asObject)
                .toList
                .flatMap(_.toList)
                .flatMap { (k, v) =>
                  v.hcursor
                    .downField("value")
                    .focus
                    .flatMap(j =>
                      j.asString
                        .orElse(j.asNumber.map(_.toString))
                        .orElse(j.asBoolean.map(_.toString))
                    )
                    .map(v => File.MetaKey(k) -> File.MetaValue(v))
                }
                .toMap
              (for
                url <- i.get[String]("url").toOption
                page <- i.get[String]("descriptionurl").toOption
              yield File(
                File.Title(title),
                File.PageUrl(page),
                File.OriginalUrl(untracked(url)),
                i.get[String]("thumburl").toOption.map(u => File.RenditionUrl(untracked(u))),
                i.get[Int]("thumbwidth").toOption.map(File.RenditionWidth(_)),
                i.get[Int]("thumbheight").toOption.map(File.RenditionHeight(_)),
                File.Mime(i.get[String]("mime").getOrElse("application/octet-stream")),
                i.get[Int]("width").toOption.map(File.Width(_)),
                i.get[Int]("height").toOption.map(File.Height(_)),
                i.get[Long]("size").toOption.map(File.Size(_)),
                meta
              ))
                .toRight(Problem("no file URL in the answer"))
          (title :: normalized.getOrElse(title, Nil)).map(_ -> entry)
        }
        Right(pages.map((t, e) => File.Title(t) -> e).toMap)

  /** `depicts`: pictures that DEPICT an event, from Wikimedia Commons: for each event with a Wikidata item,
    *   1. the works its item lists as `P1299 depicted by` (a painting, a relief) — each work's own image (P18) — when
    *      `media.depicts.depicted-by` is on; then
    *   2. the Commons files whose structured data says `P180 depicts` the item ([[CommonsDepicts]]);
    * at most `media.depicts.per-event`, in role `depiction`. Licence, author and credit come from Commons as for every
    * Commons picture ([[CommonsFiles]]); the host judges them against `media.depicts.licences`.
    */
  final class Depictions(media: MediaSettings, licences: LicenceReader) extends MediaCollection:
    val title = MediaCollection.TitleResult("Commons depictions")
    override val description = MediaCollection.DescriptionResult(
      "Pictures that depict events: Wikimedia Commons files whose structured data says P180 'depicts' the event's " +
        "Wikidata item, and the images of the works its item lists as P1299 'depicted by'; licence read from Commons"
    )
    val role = MediaItem.Role(MediaCollection.Depiction)
    override def matchRule = MediaMatchRule.Own
    override def asks = Set(MediaTarget.Events)
    override def requestIntervalMs = MediaCollection.RequestIntervalMsResult(media.requestIntervalMs.value)

    private def entitiesUrl(ids: List[WikidataId], wikidata: WikidataSettings): Plugin.Http.GetUrl =
      Plugin.Http.GetUrl(
        WikidataEntities
          .url(
            WikidataEntities.Api(wikidata.apiUrl.value),
            ids.map(i => WikidataItem.Id(i.value)),
            WikidataEntities.Maxlag(wikidata.maxlag.value)
          )
          .value
      )

    def search(place: MediaPlace, http: Plugin.Http) = Left(MediaCollection.SearchResultPart1("the depictions are about events"))

    override def searchTargets(places: List[MediaPlace], events: List[MediaEvent], http: Plugin.Http): MediaFound =
      val asked = events.flatMap(e => e.wikidata.map(q => (e, q)))
      val wikidata = media.wikidata
      val qids = asked.map(_._2).distinct

      // 1. P1299 on the events' items, then P18 of the works named
      val byItem =
        if !media.depicts.depictedBy.value || asked.isEmpty then Nil
        else
          qids.grouped(wikidata.batch.value.max(1)).toList.map { ids =>
            ids -> http.askJson(entitiesUrl(ids, wikidata)).flatMap { j =>
              j.hcursor.downField("error").focus.fold(Right(j))(_ => Left("Wikidata error"))
            }
          }
      val depictedBy = byItem.flatMap(_._2.toOption).map(Depicts.depictedBy).flatMap(_.toList).toMap
      val works = depictedBy.values.flatten.toList.distinct
      val workAnswers = works.grouped(wikidata.batch.value.max(1)).toList.map { ids =>
        ids -> http
          .askJson(entitiesUrl(ids, wikidata))
          .flatMap(WikidataEntities.items(_, Set(WikidataStatement.Property("P18"))))
      }
      val workImage: Map[WikidataId, File.Title] = workAnswers
        .flatMap(_._2.toOption)
        .flatMap(_.toList)
        .flatMap((q, item) =>
          WikidataStatement
            .best(item.statements, WikidataStatement.BestPer(1))
            .headOption
            .map(st => WikidataId(q.value) -> fileTitle(File.Title(st.file.value)))
        )
        .toMap

      // 2. Commons' P180 depicts, one search per event
      val searches = asked.map { (e, q) =>
        (
          e,
          q,
          http
            .askJson(Depicts.searchUrl(media.commons.apiUrl, q, media.depicts.searchLimit, media.commons.maxlag))
            .flatMap(Depicts.titles)
        )
      }
      val depicts = searches.collect { case (e, _, Right(ts)) => e.id -> ts }.toMap

      val found = asked.flatMap { (e, q) =>
        val fromWorks = depictedBy
          .getOrElse(q, Nil)
          .flatMap(workImage.get)
          .filter(_.value.nonEmpty)
          .map(f => (Depicts.DepictedBy, f))
        val fromFiles = depicts.getOrElse(e.id, Nil).map(f => (Depicts.Depicts, fileTitle(f)))
        (fromWorks ++ fromFiles).distinctBy(_._2).take(media.depicts.perEvent.value.max(0)).zipWithIndex.map {
          case ((p, f), i) => (e, q, p, f, i)
        }
      }
      val (files, judged) = Files(http, media, licences).items(found.map(_._4).distinct)
      val unanswered = byItem.collect { case (ids, Left(_)) => ids }.flatten.toSet ++
        searches.collect { case (_, q, Left(_)) => q }
      MediaFound(
        uses = found.map((e, q, p, f, i) =>
          MediaUse(
            eventTarget(e),
            files(f),
            MediaUse.Rank(i),
            Some(MediaUse.Statement(p)),
            Some(q),
            note = Some(MediaUse.Note(s"$p ${if p == Depicts.Depicts then "depicts" else "depicted by"} ${q.value}"))
          )
        ),
        asked = asked.map((e, _) => eventTarget(e)),
        unanswered = asked.filter((_, q) => unanswered(q)).map((e, _) => eventTarget(e)).toSet,
        read = MediaFound.Read(depictedBy.size + workImage.size),
        failed = (byItem.collect { case (ids, Left(err)) => s"Wikidata ${ids.head.value}…: $err" } ++
          workAnswers.collect { case (ids, Left(err)) => s"Wikidata works ${ids.head.value}…: $err" } ++
          searches.collect { case (e, q, Left(err)) => s"Commons search for ${e.id.value} (${q.value}): $err" })
          .map(MediaFound.Failed(_)) ++ judged.map(j => MediaFound.Failed(j.value))
      )

    private def eventTarget(e: MediaEvent): MediaTarget.Id =
      MediaTarget.Id(MediaTarget.Events, MediaTarget.Id.Value(e.id.value))

    override def completeAll(items: List[MediaItem], http: Plugin.Http): MediaCompleted =
      Files(http, media, licences).thumbnails(items)


  /** Wikimedia Commons files that DEPICT an event: structured data's `P180 depicts` searched by CirrusSearch's
    * documented `haswbstatement` keyword through the action API (no login), and Wikidata's `P1299 depicted by` on the
    * event's own item (a work whose own image, P18, is the picture). URL building and parsing. Pure.
    */
  object Depicts:
    type Problem = Problem.T
    object Problem extends RefinedType[String, Pure]

    val Depicts = "P180"
    val DepictedBy = "P1299"

    def searchUrl(api: Settings.ApiUrl, qid: WikidataId, limit: DepictsSettings.SearchLimit, maxlag: Settings.Maxlag): Plugin.Http.GetUrl =
      val lag = if maxlag.value > 0 then s"&maxlag=${maxlag.value}" else ""
      Plugin.Http.GetUrl(
        s"${api.value}?action=query&format=json&formatversion=2&list=search&srnamespace=6&srlimit=${limit.value.max(1)}" +
          s"&srsearch=${s"haswbstatement:$Depicts=${qid.value}".enc}$lag"
      )

    /** Pure: the file titles of a search answer, in its order; an API error is a `Left`. */
    def titles(json: Json): Either[Problem, List[File.Title]] =
      val c = json.hcursor
      c.downField("error").focus match
        case Some(err) =>
          Left(
            Problem(
              s"${err.hcursor.get[String]("code").getOrElse("error")}: ${err.hcursor.get[String]("info").getOrElse("")}".trim
            )
          )
        case None =>
          Right(
            c.downField("query")
              .downField("search")
              .focus
              .flatMap(_.asArray)
              .toList
              .flatten
              .flatMap(_.hcursor.get[String]("title").toOption)
              .filter(_.startsWith("File:"))
              .map(File.Title(_))
          )

    /** Pure: the items an entity names under `P1299 depicted by`, per entity asked, best rank first. */
    def depictedBy(json: Json): Map[WikidataId, List[WikidataId]] =
      json.hcursor
        .downField("entities")
        .focus
        .flatMap(_.asObject)
        .toList
        .flatMap(_.toList)
        .map { (key, ent) =>
          val sts = ent.hcursor.downField("claims").downField(DepictedBy).focus.flatMap(_.asArray).toList.flatten
          val ids = sts.flatMap { st =>
            val s = st.hcursor
            val rank = s.get[String]("rank").getOrElse("normal")
            Option
              .when(rank != "deprecated")(
                s.downField("mainsnak")
                  .downField("datavalue")
                  .downField("value")
                  .get[String]("id")
                  .toOption
              )
              .flatten
              .filter(_.matches("Q[1-9][0-9]*"))
              .map(id => (if rank == "preferred" then 0 else 1, id))
          }
          WikidataId(key) -> ids.sortBy(_._1).map(_._2).distinct.map(WikidataId(_))
        }
        .toMap

  /** One file as Commons' `imageinfo` describes it. `rendition*` is the provider's resized copy at the width asked
    * (`thumburl`; the original itself when that is smaller). `meta` is `extmetadata`, by name.
    */
  final case class File(
      title: File.Title,
      pageUrl: File.PageUrl,
      originalUrl: File.OriginalUrl,
      renditionUrl: Option[File.RenditionUrl],
      renditionWidth: Option[File.RenditionWidth],
      renditionHeight: Option[File.RenditionHeight],
      mime: File.Mime,
      width: Option[File.Width],
      height: Option[File.Height],
      size: Option[File.Size],
      meta: Map[File.MetaKey, File.MetaValue]
  )

  object File:
    type Title = Title.T
    object Title extends RefinedType[String, Pure]
    type PageUrl = PageUrl.T
    object PageUrl extends RefinedType[String, Pure]
    type OriginalUrl = OriginalUrl.T
    object OriginalUrl extends RefinedType[String, Pure]
    type RenditionUrl = RenditionUrl.T
    object RenditionUrl extends RefinedType[String, Pure]
    type RenditionWidth = RenditionWidth.T
    object RenditionWidth extends RefinedType[Int, Pure]
    type RenditionHeight = RenditionHeight.T
    object RenditionHeight extends RefinedType[Int, Pure]
    type Mime = Mime.T
    object Mime extends RefinedType[String, Pure]
    type Width = Width.T
    object Width extends RefinedType[Int, Pure]
    type Height = Height.T
    object Height extends RefinedType[Int, Pure]
    type Size = Size.T
    object Size extends RefinedType[Long, Pure]
    type MetaKey = MetaKey.T
    object MetaKey extends RefinedType[String, Pure]
    type MetaValue = MetaValue.T
    object MetaValue extends RefinedType[String, Pure]

  /** The Commons half every Commons-backed collection shares: what Commons says about each file — licence, author and
    * credit from `imageinfo` + `extmetadata`, read and never assumed, as the item to state — and, for the files to
    * store, their thumbnails in one batch.
    */
  final class Files(http: Plugin.Http, media: MediaSettings, licences: LicenceReader):

    /** `imageinfo` for the titles, in batches; a failed batch is a line in `failed`, and its titles get no answer. */
    def imageInfo(
        titles: List[File.Title],
        width: File.Width,
        meta: ImageInfoUrlMeta
    ): (Map[File.Title, Either[Problem, File]], List[Problem]) =
      val answers = titles
        .grouped(media.commons.batch.value.max(1))
        .toList
        .map { batch =>
          http
            .askJson(imageInfoUrl(media.commons.apiUrl, batch, width, media.commons.maxlag, meta))
            .flatMap(Commons.imageInfo)
            .left
            .map(e => Problem(s"Commons ${batch.head.value}…: $e"))
        }
      (answers.flatMap(_.toOption).flatMap(_.toList).toMap, answers.flatMap(_.left.toOption))

    /** Each title as the item to state — its licence as Commons states it (the machine id, else the short name, read as
      * the host reads them), the credit line in Commons' own form — and the lines of the batches that failed. A file
      * Commons marks non-free, whose licence is unreadable or all rights reserved, or whose licence requires an author
      * it does not name, is withheld with that reason.
      */
    def items(titles: List[File.Title]): (Map[File.Title, MediaItem], List[Problem]) =
      val (infos, failed) = imageInfo(titles, File.Width(media.displayPx.value), ImageInfoUrlMeta(true))
      (titles.map(t => t -> item(t, infos.get(t))).toMap, failed)

    private def item(title: File.Title, answer: Option[Either[Problem, File]]): MediaItem =
      val bare = MediaItem(
        MediaItem.Key(title.value),
        MediaItem.Title(title.value.stripPrefix("File:")),
        MediaItem.PageUrl(""),
        None,
        Left(MediaItem.RightsPart1("no answer from Commons"))
      )
      answer match
        case None              => bare
        case Some(Left(why))   => bare.copy(rights = Left(MediaItem.RightsPart1(why.value)))
        case Some(Right(info)) =>
          def field(k: File.MetaKey): Option[String] =
            info.meta.get(k).map(v => MediaText.plain(MediaText.Html(v.value)).value).filter(_.nonEmpty)
          val licence = licences.readFirst(
            (info.meta.get(File.MetaKey("License")).map(_.value).toList ++ field(File.MetaKey("LicenseShortName")).toList)
              .map(LicenceReader.ReadFirstStatements(_))
          )
          val required = info.meta.get(File.MetaKey("AttributionRequired")).exists(_.value.trim.equalsIgnoreCase("true")) ||
            licence.needsAttribution.value
          val author = field(File.MetaKey("Attribution")).orElse(field(File.MetaKey("Artist")))
          val refused =
            if info.meta.get(File.MetaKey("NonFree")).exists(_.value.trim.equalsIgnoreCase("true")) then Some("marked non-free on Commons")
            else if licence.family.value == Licence.Read.Unknown then
              Some(if licence.id.value.isEmpty then "no licence stated" else s"licence not recognised (${licence.id.value})")
            else if licence.family.value == Licence.Read.AllRights then Some("all rights reserved")
            else if required && author.isEmpty then Some("no author to credit")
            else None
          val name = field(File.MetaKey("LicenseShortName")).getOrElse(licence.id.value)
          val rights =
            if refused.isDefined then MediaRights(Licence(licence.id.value))
            else
              MediaRights(
                Licence(licence.id.value),
                info.meta
                  .get(File.MetaKey("LicenseUrl"))
                  .map(_.value.trim)
                  .filter(_.nonEmpty)
                  .map(u => MediaRights.LicenceUrl(if u.startsWith("//") then "https:" + u else u)),
                author.map(MediaRights.Author(_)),
                MediaRights.Credit((author.toList :+ name :+ "via Wikimedia Commons").mkString(", "))
              )
          bare.copy(
            pageUrl = MediaItem.PageUrl(info.pageUrl.value),
            imageUrl = Some(MediaItem.ImageUrl(info.renditionUrl.map(_.value).getOrElse(info.originalUrl.value))),
            rights = Right(rights),
            originalUrl = Some(MediaItem.OriginalUrl(info.originalUrl.value)),
            withheld = refused.map(MediaItem.Withheld(_)),
            rendition = Some(
              MediaRendition(
                MediaRendition.Mime(info.mime.value),
                info.renditionWidth.map(_.value).orElse(info.width.map(_.value)).map(MediaRendition.Width(_)),
                info.renditionHeight.map(_.value).orElse(info.height.map(_.value)).map(MediaRendition.Height(_)),
                bytes = if info.renditionUrl.isEmpty then info.size.map(s => MediaRendition.Bytes(s.value)) else None
              )
            )
          )

    /** The items to store with their thumbnails — Commons' own `thumb-px` renditions, asked in batches. */
    def thumbnails(items: List[MediaItem]): MediaCompleted =
      val (infos, failed) = imageInfo(items.map(i => File.Title(i.key.value)), File.Width(media.thumbPx.value), ImageInfoUrlMeta(false))
      MediaCompleted(
        items.flatMap { it =>
          infos.get(File.Title(it.key.value)).flatMap(_.toOption).map { i =>
            it.key -> it.copy(
              thumbUrl = i.renditionUrl.map(u => MediaItem.ThumbUrl(u.value)),
              rendition = it.rendition.map(
                _.copy(
                  thumbWidth = i.renditionWidth.map(w => MediaRendition.ThumbWidth(w.value)),
                  thumbHeight = i.renditionHeight.map(h => MediaRendition.ThumbHeight(h.value))
                )
              )
            )
          }
        }.toMap,
        failed.map(f => MediaCompleted.Failed(f.value))
      )

  /** `histmersion.media.commons`: the action API, titles per `imageinfo` call, and `maxlag`. */
  final case class Settings(apiUrl: Settings.ApiUrl, batch: Settings.Batch, maxlag: Settings.Maxlag) derives ConfigReader

  object Settings:
    type ApiUrl = ApiUrl.T
    object ApiUrl extends RefinedType[String, Pure]
    type Batch = Batch.T
    object Batch extends RefinedType[Int, Pure]
    type Maxlag = Maxlag.T
    object Maxlag extends RefinedType[Int, Pure]
