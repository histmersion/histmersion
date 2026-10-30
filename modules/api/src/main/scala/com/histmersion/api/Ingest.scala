package com.histmersion.api

/** Constants of the ingest surface, shared by the endpoint definitions and the handlers. */
object Ingest:
  /** [[ApiError.code]] for "this exact file is already stored under another citekey" → HTTP 409. */
  val CodeConflict = "conflict"

  /** Assets are content-addressed in practice (a re-conversion rewrites body AND names together), so they can be cached
    * forever.
    */
  val AssetCacheControl = "public, max-age=31536000, immutable"

  /** A provider's picture keeps its uid when a re-import refreshes it (a new version of the file on Commons), so it is
    * cached for a day rather than forever.
    */
  val MediaCacheControl = "public, max-age=86400"

  val KindIngest = com.histmersion.api.JobKind.Ingest.wire
  val KindReocr = com.histmersion.api.JobKind.Reocr.wire

  /** The `Content-Disposition` of `GET /api/sources/{ck}/original`: the stored file is `<sha256>.<ext>`, served as
    * `<citekey>.<ext>` so the format survives the download.
    */
  def originalDisposition(citekey: String, storedName: String): String =
    val ext = storedName.reverse.takeWhile(_ != '.').reverse
    s"""attachment; filename="$citekey.${if ext == storedName then "bin" else ext}""""

  /** The file extension a [[originalDisposition]] names, lower-case (`pdf`, `epub`). */
  def dispositionExtension(disposition: String): Option[String] =
    """filename="?([^";]+)"?""".r
      .findFirstMatchIn(disposition)
      .map(_.group(1).trim)
      .map(_.reverse.takeWhile(_ != '.').reverse.toLowerCase)
      .filter(e => e.nonEmpty && e.length <= 8 && e.forall(_.isLetterOrDigit))
