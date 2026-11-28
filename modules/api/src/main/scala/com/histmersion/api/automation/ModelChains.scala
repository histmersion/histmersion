package com.histmersion
package api.automation

import com.histmersion.api.automation.agents.ModelRef
import com.histmersion.api.automation.modelchains.LimitState
import com.histmersion.api.automation.modelchains.Measure

import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Problem.{Field, Invalid, Raw, TypeName}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.Wording
import com.histmersion.api.json.TwoStates

object ModelChains:
  object Chain:
    type Spec = Spec.T
    object Spec extends Newtype[String, Pure]

  import Agents.ProviderKind

  /** One model is a chain of one, so a `roles.*` setting written before chains existed still reads. A model id never
    * contains `>`.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parseChain(
      s: Chain.Spec,
      known: Set[ProviderKind.Plugin.Name] = Set.empty
  ): Either[Problems, List[ModelRef]] =
    val parts = (s).value.split('>').toList.map(_.trim)
    if parts.forall(_.isEmpty) then Right(Nil)
    else
      (parseModels((parts).map(refactorValue => ModelRef.Model.Spec(refactorValue)), known)).left.map(refactorError =>
        refactorError
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parseModels(
      refs: List[ModelRef.Model.Spec],
      known: Set[ProviderKind.Plugin.Name] = Set.empty
  ): Either[Problems, List[ModelRef]] =
    val parsed = refs.map((_).value.trim).filter(_.nonEmpty).map(r => ModelRef.parse(ModelRef.Wire(r), known))
    parsed.collect { case Left(m) => m } match
      case Nil =>
        val ms = parsed.collect { case Right(m) => m }
        val dup = ms.diff(ms.distinct).map(_.wire).distinct
        Problems
          .from(dup.toVector.map(d => Invalid.Repeated.here(TypeName.named[ModelRef], Raw(s"$d"))))
          .toLeft(ms)
      case errs => Left(Problems.from(errs.toVector).getOrElse(errs.head))

  def chainText(ms: List[ModelRef]): String = (ms.map(_.wire).mkString(" > "))

  def verdict(ss: List[LimitState]): (Boolean, Option[LimitState.FreesAt]) =
    val blocked = ss.filterNot((refactorCallback => ((refactorCallback.usable) == LimitState.Usable.Usable)))
    if blocked.isEmpty then ((true), None)
    else ((false), if blocked.exists(_.freesAt.isEmpty) then None else Some(blocked.flatMap(_.freesAt).max))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def headroom(ss: List[LimitState]): (Option[LimitState.FreesAt], Option[BigDecimal]) =
    val tok = ss.filter(_.limit.measure == Measure.Tokens).map(_.headroom).minOption.map(_.toLong)
    val usd = ss.filter(_.limit.measure == Measure.Usd).map(_.headroom).minOption
    ((tok).map(refactorValue5 => LimitState.FreesAt(refactorValue5)), usd)
