package com.histmersion.plugins.fetchers

import com.histmersion.plugin.{FetchedFile, PluginHttp, PluginLog}

/** One download the fetchers make, checked: a copy only when it is a PDF. Any other answer is logged and is no copy. */
final class Downloads(http: PluginHttp):

  def pdf(log: PluginLog, source: String, url: String, headers: Map[String, String] = Map.empty): Option[FetchedFile] =
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
  def looksLikePdf(b: Array[Byte]): Boolean =
    b.length > 1024 && String(b.take(1024), "ISO-8859-1").contains("%PDF")

  /** Hex MD5 of a download, to compare with the one a record states; `None` when the runtime offers no MD5 (the check
    * is then skipped, never failed).
    */
  def md5(b: Array[Byte]): Option[String] =
    scala.util.Try(java.security.MessageDigest.getInstance("MD5").digest(b).map("%02x".format(_)).mkString).toOption
