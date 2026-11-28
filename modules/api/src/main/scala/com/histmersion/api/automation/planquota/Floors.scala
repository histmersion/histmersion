package com.histmersion
package api.automation.planquota

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problems
import com.histmersion.util.problem.Problems.traverseAll
import io.github.iltotore.iron.constraint.any.Pure

final case class Floors(byKind: Map[Floors.ByKindKey, Floors.ByKindValue]):
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def text: Floors.Wire = Floors.Wire(byKind.toList.sortBy(_._1).map((k, v) => s"$k=$v").mkString(", "))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def of(limit: Floors.OfLimit, kind: Floors.OfKind, default: Rules.DefaultFloor): Floors.Floor =
    byKind.get(Floors.ByKindKey(s"$limit/$kind")).orElse(byKind.get(Floors.ByKindKey(kind.value))).getOrElse(default)

object Floors:
  type Floor = Floor.T
  object Floor extends Newtype[Int, Pure]
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]
  export Reading.{Kind as OfKind, Limit as OfLimit}

  type ByKindKey = ByKindKey.T
  object ByKindKey extends Newtype[String, Pure]
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type ByKindValue = Floor
  val ByKindValue: Floor.type = Floor

  val Empty: Floors = Floors(Map.empty)
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Either[Problems, Floors] =
    val items = (s).value.split(',').toList.map(_.trim).filter(_.nonEmpty)
    items
      .traverseAll { i =>
        val at = Vector(Problem.Field(s"$i"))
        i.indexOf('=') match
          case -1 => Left(Problem.Invalid.Missing(Problem.Field("percent"), at))
          case n  =>
            val (k, v) = (i.substring(0, n).trim, i.substring(n + 1).trim.stripSuffix("%").trim)
            val floor = v.toIntOption
              .filter(p => p >= 0 && p <= 100)
              .toRight(
                Problem.Invalid.OutOfRange(
                  Problem.TypeName.of(Floor),
                  Problem.Raw(s"$v"),
                  Problem.Invalid.Bounds(Some(Problem.Raw("0")), Some(Problem.Raw("100"))),
                  at
                )
              )
            val window = Either.cond(
              k.nonEmpty && !k.exists(_.isWhitespace),
              ByKindKey(k),
              Problem.Invalid.Missing(Problem.Field("window"), at)
            )
            (window, floor).allOrProblems.map((key, percent) => key -> Floor(percent))
      }
      .map(pairs => Floors(pairs.toMap))
