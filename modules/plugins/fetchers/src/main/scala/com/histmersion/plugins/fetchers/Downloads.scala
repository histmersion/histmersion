package com.histmersion.plugins.fetchers

import com.histmersion.plugin.FetchedFile
import com.histmersion.plugin.Plugin

/** One download the fetchers make, checked: a copy only when it is a PDF. Any other answer is logged and is no copy. */
final class Downloads(http: Plugin.Http):

  def pdf(log: Plugin.Log, source: com.histmersion.plugins.fetchers.Downloads.PdfSource, url: com.histmersion.plugins.fetchers.Downloads.PdfUrl, headers: Map[com.histmersion.plugins.fetchers.Downloads.PdfHeadersPart1, com.histmersion.plugins.fetchers.Downloads.PdfHeadersPart2] = Map.empty): Option[FetchedFile] =
    http.get(url, headers) match
      case Right(b) if Downloads.looksLikePdf(b) => Some(FetchedFile(source, Some(url), b, "pdf"))
      case Right(b)                              =>
        log.info(s"$source: $url returned ${b.length} bytes that are not a PDF — skipping")
        None
      case Left(e) =>
        log.info(s"$source fetch failed ($e)")
        None

object Downloads:
  /** A PDF that is really a PDF: the magic bytes within the first KiB and a plausible size (the host checks the same).
    */
  type PdfSource = PdfSource.T
  object PdfSource extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PdfUrl = PdfUrl.T
  object PdfUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PdfHeadersPart1 = PdfHeadersPart1.T
  object PdfHeadersPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PdfHeadersPart2 = PdfHeadersPart2.T
  object PdfHeadersPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LooksLikePdfResult = LooksLikePdfResult.T
  object LooksLikePdfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Md5Result = Md5Result.T
  object Md5Result extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def looksLikePdf(b: Array[Byte]): com.histmersion.plugins.fetchers.Downloads.LooksLikePdfResult =
    b.length > 1024 && String(b.take(1024), "ISO-8859-1").contains("%PDF")

  /** Hex MD5 of a download, to compare with the one a record states; `None` when the runtime offers no MD5 (the check
    * is then skipped, never failed).
    */
  def md5(b: Array[Byte]): Option[com.histmersion.plugins.fetchers.Downloads.Md5Result] =
    scala.util.Try(java.security.MessageDigest.getInstance("MD5").digest(b).map("%02x".format(_)).mkString).toOption
