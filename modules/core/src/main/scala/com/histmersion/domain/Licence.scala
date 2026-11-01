package com.histmersion.domain

import com.histmersion.values.LicenceId

import java.util.Locale

/** A licence classified into its family: `id` is what is stored and shown, `family` what an allowlist names. Shared by
  * every domain that keeps a licence — a picture's, a source's open-access licence, a dataset's. Pure throughout.
  */
final case class Licence(id: LicenceId, family: LicenceFamily)

object Licence:

  /** A licence nobody can read: never stored. */
  def unknown(statement: String): Licence = Licence(LicenceId(statement), LicenceFamily.Unknown)

  /** Pure: the families a config string allows (`"cc0,public-domain,cc-by,cc-by-sa"`). Unknown names and the
    * never-allowed ones are dropped.
    */
  def allowList(csv: String): Set[LicenceFamily] =
    Option(csv).toList
      .flatMap(_.split(",").toList)
      .map(_.trim.toLowerCase(Locale.ROOT))
      .flatMap(LicenceFamily.fromWire)
      .filterNot(_.never)
      .toSet

  /** Whether a licence may be stored under an allowlist: never one of the never-stored families. */
  def allowed(l: Licence, allow: Set[LicenceFamily]): Boolean = !l.family.never && allow(l.family)

  private val CcRe = """^cc-by(-nc)?(-sa|-nd)?(?:-([0-9](?:\.[0-9])?))?(?:-([a-z]{2,3}))?$""".r

  /** Pure: one licence string — a machine id (Commons' `License`: `cc-by-sa-3.0`, `cc0`, `pd`) or a short name (`CC
    * BY-SA 3.0 de`, `Public domain`) — as a [[Licence]]; [[LicenceFamily.Unknown]] when it is neither.
    */
  def classify(raw: String): Licence =
    val s = raw.trim.toLowerCase(Locale.ROOT).replaceAll("[\\s_]+", "-")
    s match
      case ""                                                        => unknown("")
      case c if c.startsWith("cc0") || c == "cc-zero" || c == "cc-0" => Licence(LicenceId("CC0-1.0"), LicenceFamily.Cc0)
      case p
          if p == "pd" || p.startsWith("pd-") || p.startsWith("public-domain") || p == "publicdomain" ||
            p.startsWith("pdm") =>
        Licence(LicenceId("public-domain"), LicenceFamily.PublicDomain)
      case CcRe(nc, variant, version, port) =>
        val wire = LicenceFamily.CcBy.wire + Option(nc).getOrElse("") + Option(variant).getOrElse("")
        val v = Option(version).map(x => if x.contains(".") then x else s"$x.0")
        val id = (wire.toUpperCase(Locale.ROOT) :: v.toList ++ Option(port).map(_.toUpperCase(Locale.ROOT)).toList)
          .mkString("-")
        Licence(LicenceId(id), LicenceFamily.fromWire(wire).getOrElse(LicenceFamily.Unknown))
      case g if g.startsWith("gfdl") => Licence(LicenceId("GFDL"), LicenceFamily.Gfdl)
      case a if a == "arr" || a == "all-rights-reserved" || a == "copyrighted" || a.startsWith("©") =>
        Licence(LicenceId(LicenceFamily.AllRightsReserved.wire), LicenceFamily.AllRightsReserved)
      case other => unknown(other)

  private val CcUrl =
    """^https?://(?:www\.)?creativecommons\.org/licenses/([a-z-]+)/([0-9](?:\.[0-9])?)(?:/([a-z]{2,3}))?/?(?:deed\.[a-z-]+)?$""".r
  private val PdUrl =
    """^https?://(?:www\.)?creativecommons\.org/publicdomain/(zero|mark)/[0-9.]+/?(?:deed\.[a-z-]+)?$""".r

  /** Pure: a licence given as its Creative Commons URL (Open Context's `dc-terms:license`):
    * `https://creativecommons.org/licenses/by-sa/4.0` → `CC-BY-SA-4.0` (family `cc-by-sa`), `…/publicdomain/zero/1.0/`
    * → CC0, `…/publicdomain/mark/1.0/` → public domain. Anything else — another site's terms page, a malformed URL — is
    * [[LicenceFamily.Unknown]]: never guessed.
    */
  def ofUrl(url: String): Licence =
    val u = Option(url).getOrElse("").trim.toLowerCase(Locale.ROOT)
    u match
      case PdUrl("zero")              => Licence(LicenceId("CC0-1.0"), LicenceFamily.Cc0)
      case PdUrl(_)                   => Licence(LicenceId("public-domain"), LicenceFamily.PublicDomain)
      case CcUrl(code, version, port) => classify(s"cc-$code-$version" + Option(port).fold("")("-" + _))
      case _                          => unknown(u)

  /** Pure: a statement as a licence — a Creative Commons URL read as one, anything else classified. */
  def ofStatement(stated: String): Licence =
    if stated.toLowerCase(Locale.ROOT).matches("^https?://.*") then ofUrl(stated) else classify(stated)

  /** Pure: the first of the candidates that reads as a known licence (the machine id first, then the short name);
    * [[LicenceFamily.Unknown]] with the first non-empty one when none does.
    */
  def parse(machine: Option[String], short: Option[String]): Licence = firstKnown(machine.toList ++ short.toList)

  /** Pure: the first of the statements that reads as a known licence ([[classify]]); [[LicenceFamily.Unknown]] with
    * the first non-empty one when none does.
    */
  def firstKnown(statements: List[String]): Licence =
    val cands = statements.map(_.trim).filter(_.nonEmpty)
    cands.iterator
      .map(classify)
      .find(_.family != LicenceFamily.Unknown)
      .getOrElse(unknown(cands.headOption.getOrElse("")))
