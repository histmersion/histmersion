package com.histmersion
package api.automation.agents

import com.histmersion.util.problem.Problem
import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.agents.WouldWrite
import com.histmersion.api.automation.agents.Usage
import io.github.iltotore.iron.constraint.any.Pure

@tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
final case class StepInfo(
    seq: StepInfo.Seq,
    name: StepInfo.Name,
    kind: StepInfo.Kind,
    attempt: StepInfo.Attempt,
    status: StepInfo.Status,
    model: Option[StepInfo.Model] = None,
    promptHash: Option[StepInfo.PromptHash] = None,
    usage: Usage = Usage.Zero,
    costUsd: StepInfo.CostUsd = StepInfo.CostUsd(0.0),
    error: Option[Problem] = None,
    output: Option[StepInfo.Output] = None,
    startedAt: StepInfo.StartedAt = StepInfo.StartedAt(""),
    finishedAt: StepInfo.FinishedAt = StepInfo.FinishedAt(""),
    actualModel: Option[StepInfo.ActualModel] = None,
    wouldWrite: List[WouldWrite] = Nil,
    firstCallTokens: Option[StepInfo.FirstCallTokens] = None,
    turns: Option[StepInfo.Turns] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema:
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def modelText: String = (model, actualModel) match
    case (Some(m), Some(a)) if StepInfo.ActualModel((m).value.drop((m).value.indexOf(':') + 1)) != a => (s"$m → $a")
    case (Some(m), _)                                                                                => ((m).value)
    case (None, Some(a))                                                                             => ((a).value)
    case (None, None)                                                                                => ("")

object StepInfo:

  type Seq = Seq.T
  object Seq extends Newtype[Int, Pure]
  type Name = Name.T
  object Name extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]
  type Attempt = Attempt.T
  object Attempt extends Newtype[Int, Pure]
  type Status = Status.T
  object Status extends Newtype[String, Pure]
  type Model = Model.T
  object Model extends Newtype[String, Pure]
  type PromptHash = PromptHash.T
  object PromptHash extends Newtype[String, Pure]
  type CostUsd = CostUsd.T
  object CostUsd extends Newtype[Double, Pure]
  type Output = Output.T
  object Output extends Newtype[String, Pure]
  type StartedAt = StartedAt.T
  object StartedAt extends Newtype[String, Pure]
  type FinishedAt = FinishedAt.T
  object FinishedAt extends Newtype[String, Pure]
  type ActualModel = ActualModel.T
  object ActualModel extends Newtype[String, Pure]
  type FirstCallTokens = FirstCallTokens.T
  object FirstCallTokens extends Newtype[Long, Pure]
  type Turns = Turns.T
  object Turns extends Newtype[Int, Pure]
