package com.histmersion.api

object Ingest:
  val CodeConflict = "conflict"

  val AssetCacheControl = "public, max-age=31536000, immutable"

  val MediaCacheControl = "public, max-age=86400"

  val KindIngest = com.histmersion.api.JobKindsAdapter.Ingest.value
  val KindReocr = com.histmersion.api.JobKindsAdapter.Reocr.value

  def originalDisposition(citekey: String, storedName: String): String =
    val ext = storedName.reverse.takeWhile(_ != '.').reverse
    s"""attachment; filename="$citekey.${if ext == storedName then "bin" else ext}""""

  def dispositionExtension(disposition: String): Option[String] =
    """filename="?([^";]+)"?""".r
      .findFirstMatchIn(disposition)
      .map(_.group(1).trim)
      .map(_.reverse.takeWhile(_ != '.').reverse.toLowerCase)
      .filter(e => e.nonEmpty && e.length <= 8 && e.forall(_.isLetterOrDigit))
