package com.histmersion
package api.automation.modelchains

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.Agents.ProviderKind
import com.histmersion.api.automation.agents.ModelRef
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Invalid
import com.histmersion.util.problem.Problem.Raw
import com.histmersion.util.problem.Problem.TypeName
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.problem.Problems
import com.histmersion.util.problem.Problem.Field
import com.histmersion.util.problem.Problem.`++`

enum Target:
  case Model(ref: ModelRef)
  case Provider(kind: ProviderKind)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def wire: Target.Wire = this match
    case Model(r)    => Target.Wire(r.wire.value)
    case Provider(k) => Target.Wire(s"${k.wire}:*")

  def covers(m: ModelRef): Boolean = this match
    case Model(r)    => (r == m)
    case Provider(k) => (m.provider == k)

object Target:
  type Spec = Spec.T
  object Spec extends Newtype[String, Pure]
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Spec, known: Set[ProviderKind.Plugin.Name] = Set.empty): Either[Problem, Target] =
    val t = (s).value.trim
    if t.endsWith(":*") then
      val p = t.dropRight(2)
      ProviderKind
        .parse(api.automation.ProviderKind.Wire(p), known)
        .map(Provider(_))
        .toRight(
          Invalid.Unknown
            .here(TypeName.of(ProviderKind.Wire), Raw(s"$p"), ProviderKind.wires(known).map(w => Raw(s"$w")))
        )
    else ModelRef.parse(ModelRef.Wire(t), known).map(Model(_))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parseAllowance(
      target: Target.Spec,
      items: List[Limit.Spec],
      known: Set[ProviderKind.Plugin.Name] = Set.empty
  ): Either[Problems, Allowance] =
    val ts = items.map((_).value.trim).filter(_.nonEmpty)
    val (reserves, limits) = ts.partition(_.toLowerCase(java.util.Locale.ROOT).startsWith("reserve"))
    for
      t <- Target.parse(target, known)
      rs <- reserves match
        case Nil      => Right(Allowance.ReservePct(0))
        case r :: Nil => Allowance.parseReserve(Allowance.ReserveSpec(r.drop("reserve".length)))
        case _ => Left(Invalid.Repeated(TypeName.of(Allowance.ReservePct), Raw("reserve"), Vector(Field(s"${t.wire}"))))
      ls <- limits.map(l => Limit.parse(Limit.Spec(l))).partitionMap(identity) match
        case (Nil, ok) => Right(ok)
        case (errs, _) => Left(errs.reduce[Problems](_ ++ _).at(Field(s"${t.wire}")))
      a <- Allowance.of(t, ls, rs)
    yield a
