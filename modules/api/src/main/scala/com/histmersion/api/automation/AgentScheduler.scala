package com.histmersion
package api.automation

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.jobs.JobKinds
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.api.Wording
import com.histmersion.api.json.TwoStates

object AgentScheduler:
  /** Why the scheduler started, keeps waiting or holds an item: the narration of its decision, not a failure. */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)

  object Code:
    val Person = "person"
    val Held = "held"
    val NotDue = "not-due"
    val Running = "running"
    val Settled = "settled"
    val Paused = "paused"

  /** What a scheduler's wait code means, as the managers' view says it. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  val waitCodes: Map[api.automation.agentscheduler.Waiting.Code, String] = Map(
    {
      val refactorTuple14 = "capacity" -> "No free place";
      (api.automation.agentscheduler.Waiting.Code(refactorTuple14._1), (refactorTuple14._2))
    }, {
      val refactorTuple15 = "pool" -> "Plan is full";
      (api.automation.agentscheduler.Waiting.Code(refactorTuple15._1), (refactorTuple15._2))
    }, {
      val refactorTuple3 = "manager-cap" -> "Manager is full";
      (api.automation.agentscheduler.Waiting.Code(refactorTuple3._1), (refactorTuple3._2))
    }, {
      val refactorTuple4 = "blocked" -> "Blocked";
      (api.automation.agentscheduler.Waiting.Code(refactorTuple4._1), (refactorTuple4._2))
    }, {
      val refactorTuple5 = "busy" -> "Busy";
      (api.automation.agentscheduler.Waiting.Code(refactorTuple5._1), (refactorTuple5._2))
    }, {
      val refactorTuple6 = "budget" -> "Spending cap reached";
      (api.automation.agentscheduler.Waiting.Code(refactorTuple6._1), (refactorTuple6._2))
    }, {
      val refactorTuple7 = "drain" -> "Finishing current work, then pausing";
      (api.automation.agentscheduler.Waiting.Code(refactorTuple7._1), (refactorTuple7._2))
    }, {
      val refactorTuple21 = Code.Person -> "Needs your decision";
      (api.automation.agentscheduler.Waiting.Code(refactorTuple21._1), (refactorTuple21._2))
    }, {
      val refactorTuple22 = Code.Held -> "Held by a person";
      (api.automation.agentscheduler.Waiting.Code(refactorTuple22._1), (refactorTuple22._2))
    }, {
      val refactorTuple23 = Code.NotDue -> "Not due yet";
      (api.automation.agentscheduler.Waiting.Code(refactorTuple23._1), (refactorTuple23._2))
    }, {
      val refactorTuple11 = Code.Running -> "Running";
      (api.automation.agentscheduler.Waiting.Code(refactorTuple11._1), (refactorTuple11._2))
    }, {
      val refactorTuple12 = Code.Settled -> "Settled";
      (api.automation.agentscheduler.Waiting.Code(refactorTuple12._1), (refactorTuple12._2))
    }, {
      val refactorTuple13 = Code.Paused -> "Paused";
      (api.automation.agentscheduler.Waiting.Code(refactorTuple13._1), (refactorTuple13._2))
    }
  )
