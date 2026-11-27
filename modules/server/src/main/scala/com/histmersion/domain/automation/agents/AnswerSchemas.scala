package com.histmersion
package domain.automation.agents
import com.histmersion.domain.automation.agents.SttpAiJson.given

import com.histmersion.domain.{HintProposals, PlanGoal}
import com.histmersion.api.automation.Agents.ProbeAnswer
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.ResponseSchema
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{tolerated, LintRule}

/** The typed answer of every LLM step the engine's own workflows run, one value each (the domains' are their
  * contributions' — today `domain.library.workflows.LibraryAnswers` and `ResearchAnswers`). A step names its schema
  * here rather than deriving it at the call, so what a provider is sent can be checked over ALL of them
  * (AnswerSchemasSuite: the strict form Codex needs, a `null` decoding as an absent field) — and that suite fails when
  * a `ResponseSchema.derived` appears anywhere else.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object AnswerSchemas:
  type SchemaName = SchemaName.T
  object SchemaName extends Newtype[String, Pure]

  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val probe: ResponseSchema[ProbeAnswer] = ResponseSchema.derived[ProbeAnswer]()
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val plan: ResponseSchema[PlanGoal.PlanAnswer] = ResponseSchema.derived[PlanGoal.PlanAnswer]()
  @tolerated(LintRule.Derives, "pending G18 (derives instead of hand-written givens)")
  val hintEffect: ResponseSchema[HintProposals.HintAnswer] = ResponseSchema.derived[HintProposals.HintAnswer]()

  /** The engine's step schemas, by name. */
  val all: List[(SchemaName, ResponseSchema[?])] = List(
    SchemaName("probe") -> probe,
    SchemaName("plan") -> plan,
    SchemaName("hint-effect") -> hintEffect
  )
