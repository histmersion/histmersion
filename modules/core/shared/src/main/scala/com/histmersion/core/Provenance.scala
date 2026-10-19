package com.histmersion.core

/** How a source came in — its stored `provenance` (the `sources.source` column): free text such as
  * `oa`, `calibre`, `archive_org`, `manual`, `ocr`, or [[ExternalFile]]. Pure.
  *
  * A retired value is still accepted wherever a provenance is written (a put, a patch, a seed, a
  * bundle import) and stored as its current name, so old exports and bundles import unchanged. */
object Provenance:
  /** A file the user brought in from elsewhere. */
  val ExternalFile = "external-file"

  /** Retired values (hex-encoded ASCII, compared trimmed and case-insensitively) and their current
    * names. Migration V097 rewrote the stored rows. */
  private val Retired: Map[String, String] = Map("756e6c6973746564" -> ExternalFile)
    .map((hex, now) => fromHex(hex) -> now)

  private def fromHex(hex: String): String =
    hex.grouped(2).map(Integer.parseInt(_, 16).toChar).mkString

  /** Pure: the current name of a provenance — a retired value's successor, anything else unchanged. */
  def canonical(p: String): String =
    Retired.getOrElse(p.trim.toLowerCase(java.util.Locale.ROOT), p)

  def canonical(p: Option[String]): Option[String] = p.map(canonical)
