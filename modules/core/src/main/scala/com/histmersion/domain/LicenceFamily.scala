package com.histmersion.domain

/** What a licence allows, as an allowlist names it (`cc-by-sa`): a new version of an allowed licence needs no config
  * change. Two families are never stored, whatever a config says: an unreadable licence and all rights reserved.
  */
enum LicenceFamily(val wire: String):
  case Cc0 extends LicenceFamily("cc0")
  case PublicDomain extends LicenceFamily("public-domain")
  case CcBy extends LicenceFamily("cc-by")
  case CcBySa extends LicenceFamily("cc-by-sa")
  case CcByNc extends LicenceFamily("cc-by-nc")
  case CcByNcSa extends LicenceFamily("cc-by-nc-sa")
  case CcByNd extends LicenceFamily("cc-by-nd")
  case CcByNcNd extends LicenceFamily("cc-by-nc-nd")
  case Gfdl extends LicenceFamily("gfdl")
  case AllRightsReserved extends LicenceFamily("all-rights-reserved")
  case Unknown extends LicenceFamily("unknown")

  /** Whether the licence requires the author to be named: every CC BY variant and the GFDL. */
  def needsAttribution: Boolean = wire.startsWith(LicenceFamily.CcBy.wire) || this == LicenceFamily.Gfdl

  /** The ones that need no credit at all (the credit line is still shown, as a courtesy and a source). */
  def isPublicDomain: Boolean = this == LicenceFamily.Cc0 || this == LicenceFamily.PublicDomain

  /** Never stored, whatever an allowlist says. */
  def never: Boolean = this == LicenceFamily.AllRightsReserved || this == LicenceFamily.Unknown

object LicenceFamily:

  /** Every family an allowlist may name, in the order the settings document them. */
  val allowable: List[LicenceFamily] = values.toList.filterNot(_.never)

  private val byWire: Map[String, LicenceFamily] = values.map(f => f.wire -> f).toMap

  def fromWire(s: String): Option[LicenceFamily] = byWire.get(s)
