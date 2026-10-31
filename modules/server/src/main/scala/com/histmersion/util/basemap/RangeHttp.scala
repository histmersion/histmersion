package com.histmersion.util.basemap

import com.histmersion.util.storage.Reservation

/** The ONE seam between the base-map fetch and the network: a test answers from a local file and never opens a socket.
  * Failures are `Left(message)`.
  */
trait RangeHttp:
  /** A small text document (the provider's list of builds). */
  def text(url: String): Either[String, String]

  /** Bytes `[offset, offset + length)` of `url`, in memory (headers and directories). */
  def bytes(url: String, offset: Long, length: Long): Either[String, (Array[Byte], RemoteFile)]

  /** The same, streamed into `to` (tile data, megabytes at a time); the caller commits it or discards it. */
  def download(url: String, offset: Long, length: Long, to: Reservation): Either[String, RemoteFile]

  /** A whole small file (one map tile): `None` when the server has none (404, or 403 from a bucket). */
  def file(url: String): Either[String, Option[Array[Byte]]] = Left(s"$url: whole files are not read by this client")

object RangeHttp:

  /** Pure: `Content-Range: bytes 0-99/12345` → `(0, 99, Some(12345))`. */
  def contentRange(v: String): Option[(Long, Long, Option[Long])] =
    val R = """(?i)\s*bytes\s+(\d+)-(\d+)/(\d+|\*)\s*""".r
    v match
      case R(a, b, total) => Some((a.toLong, b.toLong, total.toLongOption))
      case _              => None

  /** Pure: is this answer the range asked for? A 200 (the server ignored the range) is refused — reading a 100 GB
    * planet file whole is exactly what this must never do.
    */
  def check(url: String, code: Int, range: Option[String], offset: Long, length: Long): Either[String, Long] =
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
