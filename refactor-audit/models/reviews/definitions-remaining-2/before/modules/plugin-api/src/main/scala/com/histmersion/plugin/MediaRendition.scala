package com.histmersion.plugin

/** What a provider says about the file a [[MediaItem]] names, for a host that would otherwise measure it.
  *
  * @param mime
  *   the file's type as the provider states it (used when the download's answer names none)
  * @param width
  *   the width of the file at `imageUrl`, in pixels
  * @param height
  *   its height
  * @param bytes
  *   the size of the file at `imageUrl` when it is the ORIGINAL (no smaller copy exists): the host refuses one larger
  *   than its limit without downloading it
  * @param thumbWidth
  *   the width of the thumbnail at `thumbUrl`
  * @param thumbHeight
  *   its height
  */
final case class MediaRendition(
    mime: String,
    width: Option[Int] = None,
    height: Option[Int] = None,
    bytes: Option[Long] = None,
    thumbWidth: Option[Int] = None,
    thumbHeight: Option[Int] = None
)
