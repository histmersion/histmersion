package com.histmersion.plugins.media

/** One file as Commons' `imageinfo` describes it. `rendition*` is the provider's resized copy at the width asked
  * (`thumburl`; the original itself when that is smaller). `meta` is `extmetadata`, by name.
  */
final case class CommonsFile(
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
