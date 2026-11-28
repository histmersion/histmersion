package com.histmersion
package api.automation

import com.histmersion.api.automation.agents.ModelRef
import com.histmersion.api.automation.agents.Price
import com.histmersion.api.automation.agents.Usage
import com.histmersion.api.automation.planquota.PoolId
import com.histmersion.api.automation.agents.Billing
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Problem.{Field, Invalid, Raw, TypeName}

import hearth.kindlings.optics.syntax.*

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.WireEnumValues
import com.histmersion.api.WireEnum
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.research.Article
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Citekey
import com.histmersion.api.catalogue.Year
import com.histmersion.api.automation.Creation
import com.histmersion.api.automation.Creation
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.Wording
import com.histmersion.api.time.IsoLocal
import com.histmersion.api.library.Filter
import com.histmersion.api.json.TwoStates

@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.FileSystem, "pending G23 (files only through util.storage)")
object Agents:
  type Action = Action.T
  object Action extends Newtype[String, Pure]

  export Approval.Kind as ApprovalKind
  export Approval.Mode as ApprovalMode
  export Approval.State as ApprovalState
  export Run.State as RunState
  export Step.Status as StepStatus
  export com.histmersion.api.automation.{AgentRole as Role, Pipeline, ProviderKind, TargetKind}

  object Prices:
    type Wire = Wire.T
    object Wire extends Newtype[String, Pure]
    export ModelRef.Model as CostModel

    private[automation] val Million = BigDecimal(1000000)

    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def parse(s: Wire): Either[Problems, Map[ModelRef.Model, Price]] =
      val entries = (s).value.split(";").toList.map(_.trim).filter(_.nonEmpty)
      val parsed = entries.map { e =>
        e.lastIndexOf('=') match
          case -1 => Left(Invalid.Unreadable.here(TypeName.of(Wire), Raw(s"$e"), Vector(Raw("claude-opus=15/75"))))
          case i  =>
            val model = e.substring(0, i).trim
            val nums = e.substring(i + 1).split("/").toList.map(_.trim)
            val dec = nums.map(n => scala.util.Try(BigDecimal(n)).toOption.filter(_ >= 0))
            if model.isEmpty then Left(Invalid.Missing(Field("model"), Vector(Field(s"$e"))))
            else if dec.exists(_.isEmpty) then
              Left(
                Invalid
                  .OutOfRange(TypeName.named[Price], Raw(s"$e"), Invalid.Bounds(Some(Raw("0")), None), Vector.empty)
              )
            else
              dec.flatten match
                case List(in, out)         => Right(ModelRef.Model(model) -> Price(in, out))
                case List(in, out, cr, cw) => Right(ModelRef.Model(model) -> Price(in, out, Some(cr), Some(cw)))
                case _                     =>
                  Left(
                    Invalid.Unreadable
                      .here(TypeName.of(Wire), Raw(s"$e"), Vector(Raw("m=3/15"), Raw("m=3/15/0.3/3.75")))
                  )
      }
      parsed.collect { case Left(m) => m } match
        case Nil  => Right(parsed.collect { case Right(kv) => kv }.toMap)
        case errs => Left(Problems.from(errs.toVector).getOrElse(errs.head))

    /** An unpriced model is an error, never zero: sttp-ai's `PriceTable` counts an unknown model as free, so it would
      * never trip a cost budget.
      */
    @tolerated(LintRule.G47Self, "pending G47 (no FQN noise)")
    def cost(prices: Map[ModelRef.Model, Price], model: Prices.CostModel, u: Usage): Either[Problem, BigDecimal] =
      (prices
        .get(model)
        .toRight(unpriced(model, None))
        .map(_.cost(u)))

    def billing(
        prices: Map[ModelRef.Model, Price],
        m: ModelRef,
        pool: Option[PoolId]
    ): Either[Problem, Billing] =
      pool match
        case Some(p) => Right(Billing.Plan(p))
        case None    =>
          prices
            .get(m.model)
            .map(Billing.Metered(_))
            .toRight(unpriced(m.model, metered(m)))

    /** The model has no price in `agents.prices`: an error, never a price of zero. */
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def unpriced(model: ModelRef.Model, metered: Option[Problem.Config.Metered]): Problem =
      Problem.Config.Unpriced(Problem.Run.Model(s"$model"), metered)

    /** Why a model of a provider that can run on a plan is billed by the token, when it is one. */
    @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def metered(m: ModelRef): Option[Problem.Config.Metered] = m.provider match
      case p @ (ProviderKind.ClaudeCode | ProviderKind.Codex) =>
        Some(Problem.Config.Metered.ApiKey(Problem.Upstream.Name(s"${p.wire}")))
      case ProviderKind.Opencode => Some(Problem.Config.Metered.Opencode)
      case _                     => None

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val JobKind = AgentJobKinds.Agent.kind.value
