package com.histmersion
package util.basemap
import com.histmersion.util.problem.{External, Problem}

import com.histmersion.util.storage.Reservation
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** The ONE seam between the base-map fetch and the network: a test answers from a local file and never opens a socket.
  * Failures are `Left(message)`.
  */
trait RangeHttp:
  /** A small text document (the provider's list of builds). */
  def text(url: RangeHttp.Url): Either[Problem, RangeHttp.ResponseText]

  /** Bytes `[offset, offset + length)` of `url`, in memory (headers and directories). */
  def bytes(
      url: RangeHttp.Url,
      offset: ByteOffset,
      length: ByteCount
  ): Either[Problem, (RangeHttp.ResponseBytes, RemoteFile)]

  /** The same, streamed into `to` (tile data, megabytes at a time); the caller commits it or discards it. */
  def download(
      url: RangeHttp.Url,
      offset: ByteOffset,
      length: ByteCount,
      to: Reservation
  ): Either[Problem, RemoteFile]

  /** A whole small file (one map tile): `None` when the server has none (404, or 403 from a bucket). */
  def file(url: RangeHttp.Url): Either[Problem, Option[RangeHttp.ResponseBytes]] = Left(
    Problem.Refused.Policy(Problem.Refused.Rule.RangesOnly)
  )

object RangeHttp:
  type ContentRangeHeader = ContentRangeHeader.T
  object ContentRangeHeader extends Newtype[String, Pure]
  type ResponseBytes = ResponseBytes.T
  object ResponseBytes extends Newtype[Array[Byte], Pure]:
    extension (bytes: ResponseBytes) def asTile: PmTiles.TileBytes = PmTiles.TileBytes(bytes.value)
  type ResponseText = ResponseText.T
  object ResponseText extends Newtype[String, Pure]
  type StatusCode = StatusCode.T
  object StatusCode extends Newtype[Int, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]:
    extension (url: Url)
      /** The upstream a failed request names: the URL's host (the URL itself when it has none). */
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def upstream: Problem.Upstream.Name =
        Problem.Upstream.Name(
          s"${scala.util.Try(java.net.URI.create(s"$url").getHost).toOption.flatMap(Option(_)).getOrElse(s"$url")}"
        )

      /** The URL as a failed request names it. */
      @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
      def address: Problem.Upstream.Url = Problem.Upstream.Url(s"$url")

  /** Pure: `Content-Range: bytes 0-99/12345` → `(0, 99, Some(12345))`. */

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
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
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def check(
      url: Url,
      code: StatusCode,
      range: Option[ContentRangeHeader],
      offset: ByteOffset,
      length: ByteCount
  ): Either[Problem, ByteCount] =
    val want: Long = offset.value
    val count: Long = length.value
    if code.value == 200 then
      Left(
        Problem.Upstream.BadReply.here(
          url.upstream,
          url.address,
          Problem.Upstream.Shape.Range,
          External.Reported.of("HTTP 200: the whole file, refused rather than downloaded")
        )
      )
    else if code.value != 206 then
      Left(
        Problem.Upstream.Http
          .here(url.upstream, url.address, Problem.Upstream.Status(code.value), External.ReplyExcerpt(""))
      )
    else
      range.flatMap(contentRange) match
        // a range past the end of the file is answered up to its end
        case Some((a, b, Some(total))) if a.value == want && b.value == math.min(want + count, total.value: Long) - 1 =>
          Right(total)
        case Some((a, b, total)) =>
          Left(
            Problem.Upstream.BadReply.here(
              url.upstream,
              url.address,
              Problem.Upstream.Shape.Range,
              External.Reported.of(
                s"bytes ${a.value}-${b.value}/${total.fold("*")(_.value.toString)} for $want-${want + count - 1}"
              )
            )
          )
        case None =>
          Left(
            Problem.Upstream.BadReply
              .here(url.upstream, url.address, Problem.Upstream.Shape.Range, External.Reported.of("no Content-Range"))
          )
