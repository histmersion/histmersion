package com.histmersion.plugins.media

import com.histmersion.upstreams.MediaText
import io.circe.Json

/** Wikimedia Commons, read through the action API's `prop=imageinfo` with `extmetadata`: URL building and parsing.
  * Pure.
  */
object Commons:

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

  /** Pure: the `imageinfo` URL for a batch of titles, with the rendition `width`; `meta` adds the licence fields (the
    * thumbnail call needs only the URL). `maxlag` 0 = not sent.
    */
  def imageInfoUrl(api: String, titles: List[String], width: Int, maxlag: Int, meta: Boolean): String =
    val props = if meta then "url|size|mime|extmetadata" else "url|size|mime"
    val ext = if meta then s"&iiextmetadatafilter=${MetaFields.mkString("|").enc}&iiextmetadatalanguage=en" else ""
    val lag = if maxlag > 0 then s"&maxlag=$maxlag" else ""
    s"$api?action=query&format=json&formatversion=2&prop=imageinfo&iiprop=${props.enc}&iiurlwidth=$width" +
      s"$ext&titles=${titles.mkString("|").enc}$lag"

  /** Pure: the files of an `imageinfo` answer, keyed by the title ASKED for (the API's `normalized` list maps an asked
    * title to its canonical one). A file Commons does not have is `Left("missing on Commons")`; an API error is `Left`
    * for the whole answer.
    */
  def imageInfo(json: Json): Either[String, Map[String, Either[String, CommonsFile]]] =
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
          val entry: Either[String, CommonsFile] =
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
              yield CommonsFile(
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
