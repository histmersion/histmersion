package com.histmersion
package domain

import java.util.Locale
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

final case class Licence(id: Licence.Id, family: Licence.Family)

object Licence:
  type AllowList = AllowList.T
  object AllowList extends Newtype[String, Pure]
  type Statement = Statement.T
  object Statement extends Newtype[String, Pure]
  type Url = Url.T
  object Url extends Newtype[String, Pure]

  def unknown(statement: Statement): Licence = Licence(Id((statement).value), Family.Unknown)

  def allowList(csv: AllowList): Set[Family] =
    Option(csv).toList
      .flatMap((_).value.split(",").toList)
      .map(_.trim.toLowerCase(Locale.ROOT))
      .flatMap {
        val refactorFunction = Family.fromWire; (refactorValue) => refactorFunction(Family.Wire(refactorValue))
      }
      .filterNot((refactorCallback2 => ((refactorCallback2.never) == (true))))
      .toSet

  def allowed(l: Licence, allow: Set[Family]): Boolean = (!((l.family.never) == (true)) && allow(l.family))

  private val CcRe = """^cc-by(-nc)?(-sa|-nd)?(?:-([0-9](?:\.[0-9])?))?(?:-([a-z]{2,3}))?$""".r

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

  def ofUrl(url: Url): Licence =
    val u = Option((url).value).getOrElse("").trim.toLowerCase(Locale.ROOT)
    u match
      case PdUrl("zero")              => Licence(Id("CC0-1.0"), Family.Cc0)
      case PdUrl(_)                   => Licence(Id("public-domain"), Family.PublicDomain)
      case CcUrl(code, version, port) => classify(Id(s"cc-$code-$version" + Option(port).fold("")("-" + _)))
      case _                          => unknown(Statement(u))

  def ofStatement(stated: Statement): Licence =
    if (stated).value.toLowerCase(Locale.ROOT).matches("^https?://.*") then ofUrl(Url((stated).value))
    else classify(Id((stated).value))

  def parse(machine: Option[Statement], short: Option[Statement]): Licence = firstKnown(machine.toList ++ short.toList)

  def firstKnown(statements: List[Statement]): Licence =
    val cands = statements.map((_).value.trim).filter(_.nonEmpty)
    cands.iterator
      .map { val refactorFunction2 = classify; (refactorValue3) => refactorFunction2(Id(refactorValue3)) }
      .find(_.family != Family.Unknown)
      .getOrElse(unknown(Statement(cands.headOption.getOrElse(""))))

  type Id = Id.T

  object Id extends Newtype[String, Pure]

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

    def needsAttribution: Boolean = ((wire).value.startsWith((CcBy.wire).value) || this == Gfdl)

    def isPublicDomain: Boolean = (this == Cc0 || this == PublicDomain)

    def never: Boolean = (this == AllRightsReserved || this == Unknown)

  object Family:

    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]

    val allowable: List[Family] = values.toList.filterNot((refactorCallback => (refactorCallback.never)))

    private val byWire: Map[Wire, Family] = values.map(f => f.wire -> f).toMap

    def fromWire(s: Wire): Option[Family] = byWire.get(s)
