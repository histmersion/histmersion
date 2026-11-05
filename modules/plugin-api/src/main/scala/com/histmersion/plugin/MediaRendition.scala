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
    mime: MediaRendition.Mime,
    width: Option[MediaRendition.Width] = None,
    height: Option[MediaRendition.Height] = None,
    bytes: Option[MediaRendition.Bytes] = None,
    thumbWidth: Option[MediaRendition.ThumbWidth] = None,
    thumbHeight: Option[MediaRendition.ThumbHeight] = None
)

object MediaRendition:
  type Mime = Mime.T
  object Mime extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Width = Width.T
  object Width extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Height = Height.T
  object Height extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Bytes = Bytes.T
  object Bytes extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbWidth = ThumbWidth.T
  object ThumbWidth extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ThumbHeight = ThumbHeight.T
  object ThumbHeight extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
