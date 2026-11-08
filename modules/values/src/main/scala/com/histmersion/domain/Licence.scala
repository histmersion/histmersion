package com.histmersion
package domain

import java.util.Locale
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

/** A licence classified into its family: `id` is what is stored and shown, `family` what an allowlist names. Shared by
  * every domain that keeps a licence — a picture's, a source's open-access licence, a dataset's. Pure throughout.
  */
final case class Licence(id: Licence.Id, family: Licence.Family)

object Licence:
  type AllowList = AllowList.T
  object AllowList extends Newtype[String, Pure]
  type Statement = Statement.T
  object Statement extends Newtype[String, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]

  /** A licence nobody can read: never stored. */

  def unknown(statement: Statement): Licence = Licence(Id((statement).value), Family.Unknown)

  /** Pure: the families a config string allows (`"cc0,public-domain,cc-by,cc-by-sa"`). Unknown names and the
    * never-allowed ones are dropped.
    */
  def allowList(csv: AllowList): Set[Family] =
    Option(csv).toList
      .flatMap((_).value.split(",").toList)
      .map(_.trim.toLowerCase(Locale.ROOT))
      .flatMap {
        val refactorFunction = Family.fromWire; (refactorValue) => refactorFunction(Family.Wire(refactorValue))
      }
      .filterNot((refactorCallback2 => ((refactorCallback2.never) == (true))))
      .toSet

  /** Whether a licence may be stored under an allowlist: never one of the never-stored families. */
  def allowed(l: Licence, allow: Set[Family]): Boolean = (!((l.family.never) == (true)) && allow(l.family))

  private val CcRe = """^cc-by(-nc)?(-sa|-nd)?(?:-([0-9](?:\.[0-9])?))?(?:-([a-z]{2,3}))?$""".r

  /** Pure: one licence string — a machine id (Commons' `License`: `cc-by-sa-3.0`, `cc0`, `pd`) or a short name (`CC
    * BY-SA 3.0 de`, `Public domain`) — as a [[Licence]]; [[LicenceFamily.Unknown]] when it is neither.
    */
  def classify(raw: Id): Licence =
    val s = (raw).value.trim.toLowerCase(Locale.ROOT).replaceAll("[\\s_]+", "-")
    s match
      case ""                                                        => unknown(Statement(""))
      case c if c.startsWith("cc0") || c == "cc-zero" || c == "cc-0" =>
        Licence(Id("CC0-1.0"), Family.Cc0)
      case p
          if p == "pd" || p.startsWith("pd-") || p.startsWith("public-domain") || p == "publicdomain" ||
            p.startsWith("pdm") =>
        Licence(Id("public-domain"), Family.PublicDomain)
      case CcRe(nc, variant, version, port) =>
        val wire = Family.CcBy.wire.value + Option(nc).getOrElse("") + Option(variant).getOrElse("")
        val v = Option(version).map(x => if x.contains(".") then x else s"$x.0")
        val id = (wire.toUpperCase(Locale.ROOT) :: v.toList ++ Option(port).map(_.toUpperCase(Locale.ROOT)).toList)
          .mkString("-")
        Licence(Id(id), Family.fromWire(Family.Wire(wire)).getOrElse(Family.Unknown))
      case g if g.startsWith("gfdl") => Licence(Id("GFDL"), Family.Gfdl)
      case a if a == "arr" || a == "all-rights-reserved" || a == "copyrighted" || a.startsWith("©") =>
        Licence(Id(Family.AllRightsReserved.wire.value), Family.AllRightsReserved)
      case other => unknown(Statement(other))

  private val CcUrl =
    """^https?://(?:www\.)?creativecommons\.org/licenses/([a-z-]+)/([0-9](?:\.[0-9])?)(?:/([a-z]{2,3}))?/?(?:deed\.[a-z-]+)?$""".r
  private val PdUrl =
    """^https?://(?:www\.)?creativecommons\.org/publicdomain/(zero|mark)/[0-9.]+/?(?:deed\.[a-z-]+)?$""".r

  /** Pure: a licence given as its Creative Commons URL (Open Context's `dc-terms:license`):
    * `https://creativecommons.org/licenses/by-sa/4.0` → `CC-BY-SA-4.0` (family `cc-by-sa`), `…/publicdomain/zero/1.0/`
    * → CC0, `…/publicdomain/mark/1.0/` → public domain. Anything else — another site's terms page, a malformed URL — is
    * [[LicenceFamily.Unknown]]: never guessed.
    */
  def ofUrl(url: Url): Licence =
    val u = Option((url).value).getOrElse("").trim.toLowerCase(Locale.ROOT)
    u match
      case PdUrl("zero")              => Licence(Id("CC0-1.0"), Family.Cc0)
      case PdUrl(_)                   => Licence(Id("public-domain"), Family.PublicDomain)
      case CcUrl(code, version, port) => classify(Id(s"cc-$code-$version" + Option(port).fold("")("-" + _)))
      case _                          => unknown(Statement(u))

  /** Pure: a statement as a licence — a Creative Commons URL read as one, anything else classified. */
  def ofStatement(stated: Statement): Licence =
    if (stated).value.toLowerCase(Locale.ROOT).matches("^https?://.*") then ofUrl(Url((stated).value))
    else classify(Id((stated).value))

  /** Pure: the first of the candidates that reads as a known licence (the machine id first, then the short name);
    * [[LicenceFamily.Unknown]] with the first non-empty one when none does.
    */
  def parse(machine: Option[Statement], short: Option[Statement]): Licence = firstKnown(machine.toList ++ short.toList)

  /** Pure: the first of the statements that reads as a known licence ([[classify]]); [[LicenceFamily.Unknown]] with the
    * first non-empty one when none does.
    */
  def firstKnown(statements: List[Statement]): Licence =
    val cands = statements.map((_).value.trim).filter(_.nonEmpty)
    cands.iterator
      .map { val refactorFunction2 = classify; (refactorValue3) => refactorFunction2(Id(refactorValue3)) }
      .find(_.family != Family.Unknown)
      .getOrElse(unknown(Statement(cands.headOption.getOrElse(""))))

  /** A licence as it is stored and shown: an SPDX id (`CC-BY-SA-4.0`, `CC0-1.0`), `public-domain`, or — when it reads
    * as no licence known — the statement itself. Classified into its family by the shared `domain.Licence`.
    */
  type Id = Id.T

  object Id extends Newtype[String, Pure]

  /** What a licence allows, as an allowlist names it (`cc-by-sa`): a new version of an allowed licence needs no config
    * change. Two families are never stored, whatever a config says: an unreadable licence and all rights reserved.
    */
  enum Family(val wire: Family.Wire) derives CanEqual:
    case Cc0 extends Family(Family.Wire("cc0"))
    case PublicDomain extends Family(Family.Wire("public-domain"))
    case CcBy extends Family(Family.Wire("cc-by"))
    case CcBySa extends Family(Family.Wire("cc-by-sa"))
    case CcByNc extends Family(Family.Wire("cc-by-nc"))
    case CcByNcSa extends Family(Family.Wire("cc-by-nc-sa"))
    case CcByNd extends Family(Family.Wire("cc-by-nd"))
    case CcByNcNd extends Family(Family.Wire("cc-by-nc-nd"))
    case Gfdl extends Family(Family.Wire("gfdl"))
    case AllRightsReserved extends Family(Family.Wire("all-rights-reserved"))
    case Unknown extends Family(Family.Wire("unknown"))

    /** Whether the licence requires the author to be named: every CC BY variant and the GFDL. */
    def needsAttribution: Boolean = ((wire).value.startsWith((CcBy.wire).value) || this == Gfdl)

    /** The ones that need no credit at all (the credit line is still shown, as a courtesy and a source). */
    def isPublicDomain: Boolean = (this == Cc0 || this == PublicDomain)

    /** Never stored, whatever an allowlist says. */
    def never: Boolean = (this == AllRightsReserved || this == Unknown)

  object Family:

    /** Every family an allowlist may name, in the order the settings document them. */
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    val allowable: List[Family] = values.toList.filterNot((refactorCallback => (refactorCallback.never)))

    private val byWire: Map[Wire, Family] = values.map(f => f.wire -> f).toMap

    def fromWire(s: Wire): Option[Family] = byWire.get(s)
