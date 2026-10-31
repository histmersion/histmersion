package com.histmersion.plugins.media

import com.histmersion.plugin.*
import com.histmersion.upstreams.MediaText

/** The Commons half every Commons-backed collection shares: what Commons says about each file — licence, author and
  * credit from `imageinfo` + `extmetadata`, read and never assumed, as the item to state — and, for the files to store,
  * their thumbnails in one batch.
  */
final class CommonsFiles(http: PluginHttp, media: MediaSettings, licences: LicenceReader):

  /** `imageinfo` for the titles, in batches; a failed batch is a line in `failed`, and its titles get no answer. */
  def imageInfo(
      titles: List[String],
      width: Int,
      meta: Boolean
  ): (Map[String, Either[String, CommonsFile]], List[String]) =
    val answers = titles
      .grouped(media.commons.batch.max(1))
      .toList
      .map { batch =>
        http
          .askJson(Commons.imageInfoUrl(media.commons.apiUrl, batch, width, media.commons.maxlag, meta))
          .flatMap(Commons.imageInfo)
          .left
          .map(e => s"Commons ${batch.head}…: $e")
      }
    (answers.flatMap(_.toOption).flatMap(_.toList).toMap, answers.flatMap(_.left.toOption))

  /** Each title as the item to state — its licence as Commons states it (the machine id, else the short name, read as
    * the host reads them), the credit line in Commons' own form — and the lines of the batches that failed. A file
    * Commons marks non-free, whose licence is unreadable or all rights reserved, or whose licence requires an author it
    * does not name, is withheld with that reason.
    */
  def items(titles: List[String]): (Map[String, MediaItem], List[String]) =
    val (infos, failed) = imageInfo(titles, media.displayPx, meta = true)
    (titles.map(t => t -> item(t, infos.get(t))).toMap, failed)

  private def item(title: String, answer: Option[Either[String, CommonsFile]]): MediaItem =
    val bare = MediaItem(title, title.stripPrefix("File:"), "", None, Left("no answer from Commons"))
    answer match
      case None              => bare
      case Some(Left(why))   => bare.copy(rights = Left(why))
      case Some(Right(info)) =>
        def field(k: String) = info.meta.get(k).map(MediaText.plain(_)).filter(_.nonEmpty)
        val licence = licences.readFirst(info.meta.get("License").toList ++ field("LicenseShortName").toList)
        val required = info.meta.get("AttributionRequired").exists(_.trim.equalsIgnoreCase("true")) ||
          licence.needsAttribution
        val author = field("Attribution").orElse(field("Artist"))
        val refused =
          if info.meta.get("NonFree").exists(_.trim.equalsIgnoreCase("true")) then Some("marked non-free on Commons")
          else if licence.family == LicenceRead.Unknown then
            Some(if licence.id.isEmpty then "no licence stated" else s"licence not recognised (${licence.id})")
          else if licence.family == LicenceRead.AllRights then Some("all rights reserved")
          else if required && author.isEmpty then Some("no author to credit")
          else None
        val name = field("LicenseShortName").getOrElse(licence.id)
        val rights =
          if refused.isDefined then MediaRights(Licence(licence.id))
          else
            MediaRights(
              Licence(licence.id),
              info.meta
                .get("LicenseUrl")
                .map(_.trim)
                .filter(_.nonEmpty)
                .map(u => if u.startsWith("//") then "https:" + u else u),
              author,
              (author.toList :+ name :+ "via Wikimedia Commons").mkString(", ")
            )
        bare.copy(
          pageUrl = info.pageUrl,
          imageUrl = Some(info.renditionUrl.getOrElse(info.originalUrl)),
          rights = Right(rights),
          originalUrl = Some(info.originalUrl),
          withheld = refused,
          rendition = Some(
            MediaRendition(
              info.mime,
              info.renditionWidth.orElse(info.width),
              info.renditionHeight.orElse(info.height),
              bytes = if info.renditionUrl.isEmpty then info.size else None
            )
          )
        )

  /** The items to store with their thumbnails — Commons' own `thumb-px` renditions, asked in batches. */
  def thumbnails(items: List[MediaItem]): MediaCompleted =
    val (infos, failed) = imageInfo(items.map(_.key), media.thumbPx, meta = false)
    MediaCompleted(
      items.flatMap { it =>
        infos.get(it.key).flatMap(_.toOption).map { i =>
          it.key -> it.copy(
            thumbUrl = i.renditionUrl,
            rendition = it.rendition.map(_.copy(thumbWidth = i.renditionWidth, thumbHeight = i.renditionHeight))
          )
        }
      }.toMap,
      failed
    )
