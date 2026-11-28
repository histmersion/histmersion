package com.histmersion
package api.catalogue.geocode

import com.histmersion.api.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.catalogue.Geocode
import com.histmersion.api.catalogue.Geocode.Key
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Geocode.key

final case class Attempt(
    name: Attempt.Name,
    within: Option[Attempt.Within] = None,
    approx: Option[Approximation] = None
):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def key: Key = Geocode.key(name.value)

object Attempt:

  type Name = Name.T
  object Name extends Newtype[String, Pure]
  type Within = Within.T
  object Within extends Newtype[String, Pure]

  private val QualifierWords: Set[String] = Set(
    "north",
    "south",
    "east",
    "west",
    "northern",
    "southern",
    "eastern",
    "western",
    "northeast",
    "northwest",
    "southeast",
    "southwest",
    "northeastern",
    "northwestern",
    "southeastern",
    "southwestern",
    "central",
    "upper",
    "lower",
    "inner",
    "outer",
    "greater",
    "coastal",
    "interior",
    "middle",
    "mid",
    "highland",
    "lowland"
  )

  private val Joiners: Set[String] = Set("and", "or")

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def stripQualifier(name: Attempt.Name): Option[(Approximation.Qualifier.Dropped, Attempt.Name)] =
    val words = name.value.trim.split("[\\s\\-]+").toList.filter(_.nonEmpty)
    def isQ(w: String) = QualifierWords.contains(key(w).value)
    @annotation.tailrec
    def go(rest: List[String], dropped: List[String]): (List[String], List[String]) = rest match
      case w :: tail if isQ(w)                                                            => go(tail, dropped :+ w)
      case j :: w :: tail if dropped.nonEmpty && Joiners.contains(key(j).value) && isQ(w) => go(tail, dropped :+ j :+ w)
      case _                                                                              => (dropped, rest)
    val (dropped, rest) = go(words, Nil)
    Option.when(dropped.nonEmpty && rest.nonEmpty && key(rest.mkString(" ")).value.nonEmpty)(
      (Approximation.Qualifier.Dropped(dropped.mkString(" ")), Attempt.Name(rest.mkString(" ")))
    )

  private val Asides = "\\(([^)]*)\\)".r

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def attempts(name: Attempt.Name): List[Attempt] =
    val n = (name).value.trim
    if (key((n))).value.isEmpty then Nil
    else
      val asides = Asides
        .findAllMatchIn(n)
        .map(_.group(1).trim)
        .filter(a => (key((a))).value.nonEmpty)
        .map(a => Attempt(Attempt.Name(a)))
        .toList
      val comma = n.lastIndexOf(',') match
        case i if i > 0 && (key((n.take(i)))).value.nonEmpty && (key((n.drop(i + 1)))).value.nonEmpty =>
          Some((n.take(i).trim, n.drop(i + 1).trim))
        case _ => None
      val head = comma.fold(n)(_._1)
      val qualified =
        stripQualifier(Attempt.Name(head)).map((q, base) =>
          Attempt(base, comma.map(c => Attempt.Within(c._2)), Some(Approximation.Qualifier(q)))
        )
      val all = List(Attempt(Attempt.Name(n))) ++ asides ++
        comma.map((a, in) => Attempt(Attempt.Name(a), Some(Attempt.Within(in)))).toList ++
        qualified.toList ++
        comma
          .map((a, in) =>
            Attempt(Attempt.Name(in), None, Some(Approximation.OnlyWithin(Approximation.OnlyWithin.Missing(a))))
          )
          .toList
      all.distinctBy(a => (a.key, a.within.map(w => key(w.value))))
