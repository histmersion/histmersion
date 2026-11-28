package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.util.problem.Problem
import com.histmersion.util.problem.Problem.Field
import com.histmersion.util.problem.Problem.Invalid
import com.histmersion.util.problem.Problem.Raw
import com.histmersion.util.problem.Problem.TypeName
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.AgentRole
import com.histmersion.Newtype

final case class ModelOverride(role: ModelOverride.Role, model: ModelOverride.Model)
    derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def text: ModelOverride.Wire = ModelOverride.Wire(s"$role=$model")

object ModelOverride:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  export AgentRole.Wire as Role
  type Model = Model.T
  object Model extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Either[Problem, ModelOverride] =
    (s).value.indexOf('=') match
      case -1 =>
        Left(
          Invalid.Unreadable
            .here(TypeName.of(Wire), Raw(s"${(s).value.trim}"), Vector(Raw("reviewer=claude-code:opus")))
        )
      case i =>
        val (r, m) =
          ((s).value.substring(0, i).trim.toLowerCase(java.util.Locale.ROOT), (s).value.substring(i + 1).trim)
        if r.isEmpty then Left(Invalid.Missing(Field("role"), Vector(Field(s"${(s).value.trim}"))))
        else if m.isEmpty then Left(Invalid.Missing(Field("model"), Vector(Field(s"${(s).value.trim}"))))
        else Right(ModelOverride(AgentRole.Wire(r), Model(m)))
