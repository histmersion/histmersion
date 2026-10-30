package com.histmersion.domain

import io.circe.Json

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

/** Wikimedia Commons, read through the action API's `prop=imageinfo` with `extmetadata`. Pure parsing, URL building and
  * the licence/credit decision; the fetch is the server's.
  */
object Commons:

  /** One file as `imageinfo` describes it. `rendition*` is the provider's resized copy at the width asked (`thumburl`;
    * the original itself when that is smaller). `meta` is `extmetadata`, by name.
    */
  final case class FileInfo(
      title: String,
      pageUrl: String,
      originalUrl: String,
      renditionUrl: Option[String],
      renditionWidth: Option[Int],
      renditionHeight: Option[Int],
      mime: String,
      width: Option[Int],
      height: Option[Int],
      size: Option[Long],
      meta: Map[String, String]
  )

  /** What the licence requires beside the picture, read from `extmetadata` and never assumed. */
  final case class Rights(
      licence: MediaLicence.Licence,
      licenceName: String,
      licenceUrl: Option[String],
      author: Option[String],
      credit: String
  )

  /** The `extmetadata` fields read — and asked for, so the answer stays small. */
  val MetaFields: List[String] = List(
    "LicenseShortName",
    "License",
    "LicenseUrl",
    "Artist",
    "Credit",
    "Attribution",
    "AttributionRequired",
    "Copyrighted",
    "NonFree",
    "UsageTerms",
    "Restrictions",
    "ObjectName"
  )

  /** Pure: the canonical title of a file name as Wikidata gives it (`Lions-Gate-Mycenae.jpg` →
    * `File:Lions-Gate-Mycenae.jpg`): underscores are spaces, and the first letter is upper case.
    */
  def fileTitle(name: String): String =
    val n = name.trim.stripPrefix("File:").stripPrefix("file:").replace('_', ' ').replaceAll("\\s+", " ").trim
    if n.isEmpty then "" else "File:" + n.head.toUpper + n.tail

  private def enc(s: String): String = URLEncoder.encode(s, UTF_8)

  /** Pure: the `imageinfo` URL for a batch of titles, with the rendition `width`; `meta` adds the licence fields (the
    * thumbnail call needs only the URL). `maxlag` 0 = not sent.
    */
  def imageInfoUrl(api: String, titles: List[String], width: Int, maxlag: Int, meta: Boolean): String =
    val props = if meta then "url|size|mime|extmetadata" else "url|size|mime"
    val ext = if meta then s"&iiextmetadatafilter=${enc(MetaFields.mkString("|"))}&iiextmetadatalanguage=en" else ""
    val lag = if maxlag > 0 then s"&maxlag=$maxlag" else ""
    s"$api?action=query&format=json&formatversion=2&prop=imageinfo&iiprop=${enc(props)}&iiurlwidth=$width" +
      s"$ext&titles=${enc(titles.mkString("|"))}$lag"

  /** Pure: the files of an `imageinfo` answer, keyed by the title ASKED for (the API's `normalized` list maps an asked
    * title to its canonical one). A file Commons does not have is `Left("missing on Commons")`; an API error is `Left`
    * for the whole answer.
    */
  def imageInfo(json: Json): Either[String, Map[String, Either[String, FileInfo]]] =
    val c = json.hcursor
    c.downField("error").focus match
      case Some(err) =>
        Left(
          s"${err.hcursor.get[String]("code").getOrElse("error")}: ${err.hcursor.get[String]("info").getOrElse("")}".trim
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
          val entry: Either[String, FileInfo] =
            if h.downField("missing").focus.isDefined || h.downField("invalid").focus.isDefined || info.focus.isEmpty
            then Left("missing on Commons")
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
                    .map(k -> _)
                }
                .toMap
              (for
                url <- i.get[String]("url").toOption
                page <- i.get[String]("descriptionurl").toOption
              yield FileInfo(
                title,
                page,
                MediaText.stripTracking(url),
                i.get[String]("thumburl").toOption.map(MediaText.stripTracking),
                i.get[Int]("thumbwidth").toOption,
                i.get[Int]("thumbheight").toOption,
                i.get[String]("mime").getOrElse("application/octet-stream"),
                i.get[Int]("width").toOption,
                i.get[Int]("height").toOption,
                i.get[Long]("size").toOption,
                meta
              ))
                .toRight("no file URL in the answer")
          (title :: normalized.getOrElse(title, Nil)).map(_ -> entry)
        }
        Right(pages.toMap)

  /** Pure: what the licence of a file requires, from its `extmetadata` — or why it cannot be used: a file Commons marks
    * non-free, a licence that is unreadable or all-rights-reserved, a licence that requires attribution with no author
    * to name. The credit line is Commons' own suggested form, "Author, Licence, via Wikimedia Commons" (the author is
    * left out for a public-domain file that names none). Whether the provider's allowlist takes the licence is
    * [[MediaLicence.allowed]].
    */
  def rights(meta: Map[String, String]): Either[String, Rights] =
    def field(k: String) = meta.get(k).map(MediaText.plain(_)).filter(_.nonEmpty)
    val licence = MediaLicence.parse(meta.get("License").map(_.trim), field("LicenseShortName"))
    val required = meta.get("AttributionRequired").exists(_.trim.equalsIgnoreCase("true")) ||
      MediaLicence.needsAttribution(licence.family)
    val author = field("Attribution").orElse(field("Artist"))
    if meta.get("NonFree").exists(_.trim.equalsIgnoreCase("true")) then Left("marked non-free on Commons")
    else if licence.family == MediaLicence.Unknown then
      Left(if licence.id.isEmpty then "no licence stated" else s"licence not recognised (${licence.id})")
    else if licence.family == MediaLicence.AllRights then Left("all rights reserved")
    else if required && author.isEmpty then Left("no author to credit")
    else
      val name = field("LicenseShortName").getOrElse(licence.id)
      val credit = (author.toList :+ name :+ "via Wikimedia Commons").mkString(", ")
      Right(
        Rights(
          licence,
          name,
          meta
            .get("LicenseUrl")
            .map(_.trim)
            .filter(_.nonEmpty)
            .map(u => if u.startsWith("//") then "https:" + u else u),
          author,
          credit
        )
      )
