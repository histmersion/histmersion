package com.histmersion.domain

import java.util.Locale

/** Licences of pictures from outside providers (#1446), as data.
  *
  * A licence is stored as its SPDX id (`CC-BY-SA-3.0`, `CC0-1.0`) or `public-domain`, and judged by its FAMILY
  * (`cc-by-sa`): a provider's allowlist names families, so a new version of an allowed licence needs no config change.
  * Two families are never stored whatever a config says — an unreadable licence and all-rights-reserved. Pure
  * throughout.
  */
object MediaLicence:

  /** `id` is what is stored and shown; `family` is what an allowlist names. */
  final case class Licence(id: String, family: String)

  val Cc0 = "cc0"
  val PublicDomain = "public-domain"
  val CcBy = "cc-by"
  val CcBySa = "cc-by-sa"
  val CcByNc = "cc-by-nc"
  val CcByNcSa = "cc-by-nc-sa"
  val CcByNd = "cc-by-nd"
  val CcByNcNd = "cc-by-nc-nd"
  val Gfdl = "gfdl"
  val AllRights = "all-rights-reserved"
  val Unknown = "unknown"

  /** Every family an allowlist may name. */
  val Families: List[String] = List(Cc0, PublicDomain, CcBy, CcBySa, CcByNc, CcByNcSa, CcByNd, CcByNcNd, Gfdl)

  /** Never stored, whatever the allowlist says. */
  val Never: Set[String] = Set(AllRights, Unknown)

  /** Pure: the families a config string allows (`"cc0,public-domain,cc-by,cc-by-sa"`). Unknown names and the
    * never-allowed ones are dropped.
    */
  def allowList(csv: String): Set[String] =
    Option(csv).toList
      .flatMap(_.split(",").toList)
      .map(_.trim.toLowerCase(Locale.ROOT))
      .filter(Families.contains)
      .toSet -- Never

  def allowed(l: Licence, allow: Set[String]): Boolean = !Never(l.family) && allow(l.family)

  /** Does the family require the author to be named? Every CC BY variant and the GFDL do. */
  def needsAttribution(family: String): Boolean = family.startsWith(CcBy) || family == Gfdl

  /** The ones that need no credit at all (the credit line is still shown, as a courtesy and a source). */
  def isPublicDomain(family: String): Boolean = family == Cc0 || family == PublicDomain

  private val CcRe = """^cc-by(-nc)?(-sa|-nd)?(?:-([0-9](?:\.[0-9])?))?(?:-([a-z]{2,3}))?$""".r

  /** Pure: one licence string — a machine id (Commons' `License`: `cc-by-sa-3.0`, `cc0`, `pd`) or a short name (`CC
    * BY-SA 3.0 de`, `Public domain`) — as a [[Licence]]; [[Unknown]] when it is neither.
    */
  def classify(raw: String): Licence =
    val s = raw.trim.toLowerCase(Locale.ROOT).replaceAll("[\\s_]+", "-")
    s match
      case ""                                                        => Licence("", Unknown)
      case c if c.startsWith("cc0") || c == "cc-zero" || c == "cc-0" => Licence("CC0-1.0", Cc0)
      case p
          if p == "pd" || p.startsWith("pd-") || p.startsWith("public-domain") || p == "publicdomain" ||
            p.startsWith("pdm") =>
        Licence("public-domain", PublicDomain)
      case CcRe(nc, variant, version, port) =>
        val family = CcBy + Option(nc).getOrElse("") + Option(variant).getOrElse("")
        val v = Option(version).map(x => if x.contains(".") then x else s"$x.0")
        val id = (family.toUpperCase(Locale.ROOT) :: v.toList ++ Option(port).map(_.toUpperCase(Locale.ROOT)).toList)
          .mkString("-")
        Licence(id, family)
      case g if g.startsWith("gfdl") => Licence("GFDL", Gfdl)
      case a if a == "arr" || a == "all-rights-reserved" || a == "copyrighted" || a.startsWith("©") =>
        Licence(AllRights, AllRights)
      case other => Licence(other, Unknown)

  private val CcUrl =
    """^https?://(?:www\.)?creativecommons\.org/licenses/([a-z-]+)/([0-9](?:\.[0-9])?)(?:/([a-z]{2,3}))?/?(?:deed\.[a-z-]+)?$""".r
  private val PdUrl =
    """^https?://(?:www\.)?creativecommons\.org/publicdomain/(zero|mark)/[0-9.]+/?(?:deed\.[a-z-]+)?$""".r

  /** Pure: a licence given as its Creative Commons URL (Open Context's `dc-terms:license`):
    * `https://creativecommons.org/licenses/by-sa/4.0` → `CC-BY-SA-4.0` (family `cc-by-sa`), `…/publicdomain/zero/1.0/`
    * → CC0, `…/publicdomain/mark/1.0/` → public domain. Anything else — another site's terms page, a malformed URL — is
    * [[Unknown]]: never guessed.
    */
  def ofUrl(url: String): Licence =
    val u = Option(url).getOrElse("").trim.toLowerCase(Locale.ROOT)
    u match
      case PdUrl("zero")              => Licence("CC0-1.0", Cc0)
      case PdUrl(_)                   => Licence("public-domain", PublicDomain)
      case CcUrl(code, version, port) => classify(s"cc-$code-$version" + Option(port).fold("")("-" + _))
      case _                          => Licence(u, Unknown)

  /** Pure: the first of the candidates that reads as a known licence (the machine id first, then the short name);
    * [[Unknown]] with the first non-empty one when none does.
    */
  def parse(machine: Option[String], short: Option[String]): Licence =
    val cands = (machine.toList ++ short.toList).map(_.trim).filter(_.nonEmpty)
    cands.iterator
      .map(classify)
      .find(_.family != Unknown)
      .getOrElse(Licence(cands.headOption.getOrElse(""), Unknown))
