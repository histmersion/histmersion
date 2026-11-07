package com.histmersion.plugins.fetchers

import com.histmersion.plugin.FetchedFile
import com.histmersion.plugin.Plugin

/** One download the fetchers make, checked: a copy only when it is a PDF. Any other answer is logged and is no copy. */
final class Downloads(http: Plugin.Http):

  def pdf(log: Plugin.Log, source: com.histmersion.plugin.FetchedFile.Source, url: com.histmersion.plugin.Plugin.Http.GetUrl, headers: Map[com.histmersion.plugin.Plugin.Http.GetHeadersPart1, com.histmersion.plugin.Plugin.Http.GetHeadersPart2] = Map.empty): Option[FetchedFile] =
    http.get(url, headers).map(body => FetchedFile.Bytes(body.value)) match
      case Right(b) if Downloads.looksLikePdf(b) => Some(FetchedFile(source, Some(com.histmersion.plugin.FetchedFile.Url(url.value)), b, com.histmersion.plugin.FetchedFile.Extension("pdf")))
      case Right(b)                              =>
        log.info(Plugin.Log.InfoMessage(s"$source: $url returned ${b.value.length} bytes that are not a PDF — skipping"))
        None
      case Left(e) =>
        log.info(Plugin.Log.InfoMessage(s"$source fetch failed ($e)"))
        None

object Downloads:
  type Md5 = Md5.T
  object Md5 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A PDF that is really a PDF: the magic bytes within the first KiB and a plausible size (the host checks the same).
    */
  def looksLikePdf(b: com.histmersion.plugin.FetchedFile.Bytes): Boolean =
    b.value.length > 1024 && String(b.value.take(1024), "ISO-8859-1").contains("%PDF")

  /** Hex MD5 of a download, to compare with the one a record states; `None` when the runtime offers no MD5 (the check
    * is then skipped, never failed).
    */
  def md5(b: com.histmersion.plugin.FetchedFile.Bytes): Option[com.histmersion.plugins.fetchers.Downloads.Md5] =
    scala.util.Try(java.security.MessageDigest.getInstance("MD5").digest(b.value).map("%02x".format(_)).mkString).toOption.map(h => Md5(h))
