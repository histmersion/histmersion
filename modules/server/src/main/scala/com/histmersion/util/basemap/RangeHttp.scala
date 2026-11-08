package com.histmersion
package util.basemap

import com.histmersion.util.storage.Reservation
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The ONE seam between the base-map fetch and the network: a test answers from a local file and never opens a socket.
  * Failures are `Left(message)`.
  */
trait RangeHttp:
  /** A small text document (the provider's list of builds). */
  def text(url: RangeHttp.Url): Either[RangeHttp.Problem, RangeHttp.ResponseText]

  /** Bytes `[offset, offset + length)` of `url`, in memory (headers and directories). */
  def bytes(url: RangeHttp.Url, offset: ByteOffset, length: ByteCount): Either[RangeHttp.Problem, (RangeHttp.ResponseBytes, RemoteFile)]

  /** The same, streamed into `to` (tile data, megabytes at a time); the caller commits it or discards it. */
  def download(url: RangeHttp.Url, offset: ByteOffset, length: ByteCount, to: Reservation): Either[RangeHttp.Problem, RemoteFile]

  /** A whole small file (one map tile): `None` when the server has none (404, or 403 from a bucket). */
  def file(url: RangeHttp.Url): Either[RangeHttp.Problem, Option[RangeHttp.ResponseBytes]] = Left(RangeHttp.Problem(s"${url.value}: whole files are not read by this client"))

object RangeHttp:
  type ContentRangeHeader = ContentRangeHeader.T
  object ContentRangeHeader extends RefinedType[String, Pure]
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]
  type ResponseBytes = ResponseBytes.T
  object ResponseBytes extends RefinedType[Array[Byte], Pure]
  type ResponseText = ResponseText.T
  object ResponseText extends RefinedType[String, Pure]
  type StatusCode = StatusCode.T
  object StatusCode extends RefinedType[Int, Pure]
  type Url = Url.T
  object Url extends RefinedType[String, Pure]

  /** Pure: `Content-Range: bytes 0-99/12345` → `(0, 99, Some(12345))`. */


  def contentRange(v: ContentRangeHeader): Option[(ByteOffset, ByteOffset, Option[ByteCount])] =
    val R = """(?i)\s*bytes\s+(\d+)-(\d+)/(\d+|\*)\s*""".r
    val header: String = v.value
    header match
      case R(a, b, total) =>
        for
          first <- a.toLongOption.flatMap(ByteOffset.option)
          last <- b.toLongOption.flatMap(ByteOffset.option)
        yield (first, last, total.toLongOption.flatMap(ByteCount.option))
      case _ => None

  /** Pure: is this answer the range asked for? A 200 (the server ignored the range) is refused — reading a 100 GB
    * planet file whole is exactly what this must never do.
    */
  def check(url: Url, code: StatusCode, range: Option[ContentRangeHeader], offset: ByteOffset, length: ByteCount): Either[Problem, ByteCount] =
    val want: Long = offset.value
    val count: Long = length.value
    if code.value == 200 then
      Left(Problem(s"${url.value} ignores range requests (answered 200 to a range) — refused rather than download the whole file"))
    else if code.value != 206 then Left(Problem(s"${url.value}: HTTP ${code.value}"))
    else
      range.flatMap(contentRange) match
        // a range past the end of the file is answered up to its end
        case Some((a, b, Some(total))) if a.value == want && b.value == math.min(want + count, total.value: Long) - 1 =>
          Right(total)
        case Some((a, b, total)) =>
          Left(
            Problem(
              s"${url.value} answered bytes ${a.value}-${b.value}/${total.fold("*")(_.value.toString)} for $want-${want + count - 1}"
            )
          )
        case None => Left(Problem(s"${url.value} sent no Content-Range"))
