package com.histmersion
package plugin

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** A licence AS ITS PROVIDER STATES IT: an SPDX id (`CC-BY-4.0`, `CC0-1.0`), a short name (`CC BY-SA 3.0`,
  * `Public domain`), a Creative Commons URL, or the provider's own words. Never judged by the plugin: the host reads
  * the statement and decides what it allows, so any text is a statement (one the host cannot read is "unknown", which
  * it never stores).
  */
type Licence = Licence.T

object Licence extends Newtype[String, Pure]:

  /** A licence statement as the HOST reads it ([[LicenceReader]]).
    *
    * @param id
    *   what is stored and shown: an SPDX id (`CC-BY-SA-4.0`, `CC0-1.0`), `public-domain`, or the statement itself when
    *   it is not read (family [[Licence.Read.Unknown]])
    * @param family
    *   what an allowlist names: `cc0`, `public-domain`, `cc-by`, `cc-by-sa`, `cc-by-nc`, `cc-by-nc-sa`, `cc-by-nd`,
    *   `cc-by-nc-nd`, `gfdl`, `all-rights-reserved` or `unknown`
    * @param needsAttribution
    *   whether the licence requires the author to be named (every CC BY variant and the GFDL)
    */
  final case class Read(
      id: Read.Id,
      family: Read.Family,
      needsAttribution: Read.NeedsAttribution = Read.NeedsAttribution(false)
  )

  object Read:
    type UnknownStatement = UnknownStatement.T
    object UnknownStatement extends Newtype[String, Pure]

    type Id = Id.T
    object Id extends Newtype[String, Pure]
    type Family = Family.T
    object Family extends Newtype[String, Pure]
    type NeedsAttribution = NeedsAttribution.T
    object NeedsAttribution extends Newtype[Boolean, Pure]

    val Cc0 = "cc0"
    val PublicDomain = "public-domain"
    val AllRights = "all-rights-reserved"
    val Unknown = "unknown"

    /** A statement nothing reads. */
    def unknown(statement: UnknownStatement): Read = Read(Id((statement).value), Family(Unknown))
