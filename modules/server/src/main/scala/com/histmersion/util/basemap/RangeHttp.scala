package com.histmersion.util.basemap

import com.histmersion.util.storage.Reservation

/** The ONE seam between the base-map fetch and the network: a test answers from a local file and never opens a socket.
  * Failures are `Left(message)`.
  */
trait RangeHttp:
  /** A small text document (the provider's list of builds). */
  def text(url: com.histmersion.util.basemap.RangeHttp.TextUrl): Either[com.histmersion.util.basemap.RangeHttp.TextResultPart1, com.histmersion.util.basemap.RangeHttp.TextResultPart2]

  /** Bytes `[offset, offset + length)` of `url`, in memory (headers and directories). */
  def bytes(url: com.histmersion.util.basemap.RangeHttp.BytesUrl, offset: com.histmersion.util.basemap.RangeHttp.BytesOffset, length: com.histmersion.util.basemap.RangeHttp.BytesLength): Either[com.histmersion.util.basemap.RangeHttp.BytesResultPart1, (Array[Byte], RemoteFile)]

  /** The same, streamed into `to` (tile data, megabytes at a time); the caller commits it or discards it. */
  def download(url: com.histmersion.util.basemap.RangeHttp.DownloadUrl, offset: com.histmersion.util.basemap.RangeHttp.DownloadOffset, length: com.histmersion.util.basemap.RangeHttp.DownloadLength, to: Reservation): Either[com.histmersion.util.basemap.RangeHttp.DownloadResultPart1, RemoteFile]

  /** A whole small file (one map tile): `None` when the server has none (404, or 403 from a bucket). */
  def file(url: com.histmersion.util.basemap.RangeHttp.FileUrl): Either[com.histmersion.util.basemap.RangeHttp.FileResultPart1, Option[Array[Byte]]] = Left(s"$url: whole files are not read by this client")

object RangeHttp:

  /** Pure: `Content-Range: bytes 0-99/12345` → `(0, 99, Some(12345))`. */
  type TextResultPart1 = TextResultPart1.T
  object TextResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextResultPart2 = TextResultPart2.T
  object TextResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TextUrl = TextUrl.T
  object TextUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesResultPart1 = BytesResultPart1.T
  object BytesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesUrl = BytesUrl.T
  object BytesUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesOffset = BytesOffset.T
  object BytesOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BytesLength = BytesLength.T
  object BytesLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadResultPart1 = DownloadResultPart1.T
  object DownloadResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadUrl = DownloadUrl.T
  object DownloadUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadOffset = DownloadOffset.T
  object DownloadOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DownloadLength = DownloadLength.T
  object DownloadLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileResultPart1 = FileResultPart1.T
  object FileResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FileUrl = FileUrl.T
  object FileUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContentRangeResultPart1 = ContentRangeResultPart1.T
  object ContentRangeResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContentRangeResultPart2 = ContentRangeResultPart2.T
  object ContentRangeResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContentRangeResultPart3 = ContentRangeResultPart3.T
  object ContentRangeResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ContentRangeV = ContentRangeV.T
  object ContentRangeV extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckResultPart1 = CheckResultPart1.T
  object CheckResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckResultPart2 = CheckResultPart2.T
  object CheckResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckUrl = CheckUrl.T
  object CheckUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckCode = CheckCode.T
  object CheckCode extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckRange = CheckRange.T
  object CheckRange extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckOffset = CheckOffset.T
  object CheckOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckLength = CheckLength.T
  object CheckLength extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def contentRange(v: com.histmersion.util.basemap.RangeHttp.ContentRangeV): Option[(com.histmersion.util.basemap.RangeHttp.ContentRangeResultPart1, com.histmersion.util.basemap.RangeHttp.ContentRangeResultPart2, Option[com.histmersion.util.basemap.RangeHttp.ContentRangeResultPart3])] =
    val R = """(?i)\s*bytes\s+(\d+)-(\d+)/(\d+|\*)\s*""".r
    v match
      case R(a, b, total) => Some((a.toLong, b.toLong, total.toLongOption))
      case _              => None

  /** Pure: is this answer the range asked for? A 200 (the server ignored the range) is refused — reading a 100 GB
    * planet file whole is exactly what this must never do.
    */
  def check(url: com.histmersion.util.basemap.RangeHttp.CheckUrl, code: com.histmersion.util.basemap.RangeHttp.CheckCode, range: Option[com.histmersion.util.basemap.RangeHttp.CheckRange], offset: com.histmersion.util.basemap.RangeHttp.CheckOffset, length: com.histmersion.util.basemap.RangeHttp.CheckLength): Either[com.histmersion.util.basemap.RangeHttp.CheckResultPart1, com.histmersion.util.basemap.RangeHttp.CheckResultPart2] =
    if code == 200 then
      Left(s"$url ignores range requests (answered 200 to a range) — refused rather than download the whole file")
    else if code != 206 then Left(s"$url: HTTP $code")
    else
      range.flatMap(contentRange) match
        // a range past the end of the file is answered up to its end
        case Some((a, b, Some(total))) if a == offset && b == math.min(offset + length, total) - 1 => Right(total)
        case Some((a, b, total))                                                                   =>
          Left(s"$url answered bytes $a-$b/${total.getOrElse("*")} for $offset-${offset + length - 1}")
        case None => Left(s"$url sent no Content-Range")
