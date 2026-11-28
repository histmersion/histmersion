package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.Agents.ProviderKind
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Field
import com.histmersion.util.problem.Problem.Invalid
import com.histmersion.util.problem.Problem.Raw
import com.histmersion.util.problem.Problem.TypeName
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api
import com.histmersion.Newtype

final case class ModelRef(provider: ProviderKind, model: ModelRef.Model) derives CanEqual:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def wire: ModelRef.Wire = ModelRef.Wire(s"${provider.wire}:$model")

object ModelRef:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  type Model = Model.T
  object Model extends Newtype[String, Pure]:
    type Spec = Spec.T
    object Spec extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(
      s: Wire,
      known: Set[api.automation.ProviderKind.Plugin.Name] = Set.empty
  ): Either[Problem, ModelRef] =
    val t = (s).value.trim
    t.indexOf(':') match
      case -1 =>
        Left(
          Invalid.Unreadable.here(TypeName.of(Wire), Raw(s"$t"), Vector(Raw("claude-code:opus"), Raw("openai:gpt-5")))
        )
      case i =>
        val (p, m) = (t.substring(0, i), t.substring(i + 1).trim)
        ProviderKind.parse(ProviderKind.Wire(p), known) match
          case None =>
            Left(
              Invalid.Unknown.here(
                TypeName.of(ProviderKind.Wire),
                Raw(s"$p"),
                ProviderKind.wires(known).map(w => Raw(s"$w"))
              )
            )
          case Some(_) if m.isEmpty => Left(Invalid.Missing(Field("model"), Vector(Field(s"$t"))))
          case Some(k)              => Right(ModelRef(k, Model(m)))
