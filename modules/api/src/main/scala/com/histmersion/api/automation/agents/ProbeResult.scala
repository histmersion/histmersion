package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.agents.Usage
import com.histmersion.api.automation.agents.ProbeAnswer
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates

final case class ProbeResult(
    model: ProbeResult.Model,
    answer: Option[ProbeAnswer],
    rawAnswer: ProbeResult.RawAnswer,
    matchesRecord: ProbeResult.MatchesRecord,
    toolCalls: List[ProbeResult.ToolCalls],
    iterations: ProbeResult.Iterations,
    finish: ProbeResult.Finish,
    usage: Usage,
    costUsd: Option[ProbeResult.CostUsd],
    millis: ProbeResult.Millis,
    problems: List[ProbeResult.Problems] = Nil,
    attempts: ProbeResult.Attempts = ProbeResult.Attempts(1)
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object ProbeResult:
  type Model = Model.T
  object Model extends Newtype[String, Pure]
  type RawAnswer = RawAnswer.T
  object RawAnswer extends Newtype[String, Pure]
  enum MatchesRecord derives CanEqual:
    case Matches, Differs
  object MatchesRecord extends TwoStates[MatchesRecord]:
    extension (matchesRecord: MatchesRecord) def matchesRecord: Boolean = matchesRecord == Matches
  type ToolCalls = ToolCalls.T
  object ToolCalls extends Newtype[String, Pure]
  type Iterations = Iterations.T
  object Iterations extends Newtype[Int, Pure]
  type Finish = Finish.T
  object Finish extends Newtype[String, Pure]
  type CostUsd = CostUsd.T
  object CostUsd extends Newtype[Double, Pure]
  type Millis = Millis.T
  object Millis extends Newtype[Long, Pure]
  type Problems = Problems.T
  object Problems extends Newtype[String, Pure]
  type Attempts = Attempts.T
  object Attempts extends Newtype[Int, Pure]
