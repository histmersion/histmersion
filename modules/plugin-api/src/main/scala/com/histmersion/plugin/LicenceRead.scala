package com.histmersion.plugin

/** A licence statement as the HOST reads it ([[LicenceReader]]).
  *
  * @param id
  *   what is stored and shown: an SPDX id (`CC-BY-SA-4.0`, `CC0-1.0`), `public-domain`, or the statement itself when it
  *   is not read (family [[LicenceRead.Unknown]])
  * @param family
  *   what an allowlist names: `cc0`, `public-domain`, `cc-by`, `cc-by-sa`, `cc-by-nc`, `cc-by-nc-sa`, `cc-by-nd`,
  *   `cc-by-nc-nd`, `gfdl`, `all-rights-reserved` or `unknown`
  * @param needsAttribution
  *   whether the licence requires the author to be named (every CC BY variant and the GFDL)
  */
final case class LicenceRead(id: String, family: String, needsAttribution: Boolean = false)

object LicenceRead:
  val Cc0 = "cc0"
  val PublicDomain = "public-domain"
  val AllRights = "all-rights-reserved"
  val Unknown = "unknown"

  /** A statement nothing reads. */
  def unknown(statement: String): LicenceRead = LicenceRead(statement, Unknown)
