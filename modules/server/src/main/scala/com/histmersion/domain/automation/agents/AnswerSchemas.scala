package com.histmersion.domain.automation.agents

import com.histmersion.domain.{HintProposals, PlanGoal, Scheduling}
import com.histmersion.api.Agents.ProbeAnswer
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.ResponseSchema

/** The typed answer of every LLM step the engine's own workflows run, one value each (the domains' are their
  * contributions' — today `domain.library.workflows.LibraryAnswers` and `ResearchAnswers`). A step names its schema
  * here rather than deriving it at the call, so what a provider is sent can be checked over ALL of them
  * (AnswerSchemasSuite: the strict form Codex needs, a `null` decoding as an absent field) — and that suite fails when
  * a `ResponseSchema.derived` appears anywhere else.
  */
object AnswerSchemas:
  val probe: ResponseSchema[ProbeAnswer] = ResponseSchema.derived[ProbeAnswer]()
  val plan: ResponseSchema[PlanGoal.PlanAnswer] = ResponseSchema.derived[PlanGoal.PlanAnswer]()
  val ranking: ResponseSchema[Scheduling.Ranking] = ResponseSchema.derived[Scheduling.Ranking]()
  val hintEffect: ResponseSchema[HintProposals.HintAnswer] = ResponseSchema.derived[HintProposals.HintAnswer]()

  /** The engine's step schemas, by name. */
  val all: List[(String, ResponseSchema[?])] = List(
    "probe" -> probe,
    "plan" -> plan,
    "ranking" -> ranking,
    "hint-effect" -> hintEffect
  )
