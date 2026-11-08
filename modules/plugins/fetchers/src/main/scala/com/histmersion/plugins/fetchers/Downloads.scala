package com.histmersion
package plugins.fetchers

import com.histmersion.plugin.Header

import com.histmersion.plugin.FetchedFile
import com.histmersion.plugin.Plugin
import io.github.iltotore.iron.constraint.any.Pure

/** One download the fetchers make, checked: a copy only when it is a PDF. Any other answer is logged and is no copy. */
final class Downloads(http: Plugin.Http):

  def pdf(
      log: Plugin.Log,
      source: FetchedFile.Source,
      url: Plugin.Http.Url,
      headers: Map[Header.Name, Header.Value] = Map.empty
  ): Option[FetchedFile] =
    http.get(url, headers).map(body => FetchedFile.Bytes(body.value)) match
      case Right(b) if Downloads.looksLikePdf(b) =>
        Some(FetchedFile(source, Some(FetchedFile.Url(url.value)), b, FetchedFile.Extension("pdf")))
      case Right(b) =>
        log.info(
          Plugin.Log.Line(s"$source: $url returned ${b.value.length} bytes that are not a PDF — skipping")
        )
        None
      case Left(e) =>
        log.info(Plugin.Log.Line(s"$source fetch failed ($e)"))
        None

object Downloads:
  type Md5 = Md5.T
  object Md5 extends Newtype[String, Pure]

  /** A PDF that is really a PDF: the magic bytes within the first KiB and a plausible size (the host checks the same).
    */
  def looksLikePdf(b: FetchedFile.Bytes): Boolean =
    b.value.length > 1024 && String(b.value.take(1024), "ISO-8859-1").contains("%PDF")

  /** Hex MD5 of a download, to compare with the one a record states; `None` when the runtime offers no MD5 (the check
    * is then skipped, never failed).
    */
  def md5(b: FetchedFile.Bytes): Option[Md5] =
    scala.util
      .Try(java.security.MessageDigest.getInstance("MD5").digest(b.value).map("%02x".format(_)).mkString)
      .toOption
      .map(h => Md5(h))
