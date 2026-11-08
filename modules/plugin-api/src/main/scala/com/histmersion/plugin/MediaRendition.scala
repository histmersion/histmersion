package com.histmersion
package plugin

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

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
    mime: MediaRendition.Mime,
    width: Option[MediaRendition.Width] = None,
    height: Option[MediaRendition.Height] = None,
    bytes: Option[MediaRendition.Bytes] = None,
    thumbWidth: Option[MediaRendition.ThumbWidth] = None,
    thumbHeight: Option[MediaRendition.ThumbHeight] = None
)

object MediaRendition:
  type Mime = Mime.T
  object Mime extends RefinedType[String, Pure]
  type Width = Width.T
  object Width extends RefinedType[Int, Pure]
  type Height = Height.T
  object Height extends RefinedType[Int, Pure]
  type Bytes = Bytes.T
  object Bytes extends RefinedType[Long, Pure]
  type ThumbWidth = ThumbWidth.T
  object ThumbWidth extends RefinedType[Int, Pure]
  type ThumbHeight = ThumbHeight.T
  object ThumbHeight extends RefinedType[Int, Pure]
