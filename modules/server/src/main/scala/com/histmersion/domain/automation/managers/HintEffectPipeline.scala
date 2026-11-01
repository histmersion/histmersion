package com.histmersion.domain.automation.managers

import com.histmersion.domain.automation.agents.{AnswerSchemas, Checked, Halt, Prompt, RunCtx, StepAgent}
import com.histmersion.domain.automation.workflows.{PipelineDef, Pipelines}

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Agents.{ApprovalKind, Pipeline}
import com.histmersion.domain.HintProposals.{HintAnswer, Proposed}
import com.histmersion.api.Managers.*
import com.histmersion.db.ManagerRepo
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.api.VocabularyCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.syntax.*

import javax.sql.DataSource

/** `hint-effect <manager>` (docs/research-process-managers.md §3.6, phase 3): a person's hint, in words, read by the
  * planner model (the `hint-reader` definition: no tools — the hint, the manager's items and its settings are given as
  * data), which proposes the structured action it asks for. Code checks the proposal ([[HintProposals.check]]: an
  * action it knows, targets that are the manager's items, a date that parses, settings that parse; a failing answer
  * goes back with why) and records it as a `hint-effect` approval the run does NOT wait for — NOTHING is applied until
  * a person approves it (then [[ManagersHandlers.applyProposal]]). Started by a person (`suggest` on a hint).
  *
  * Steps: load · propose · record · close. A DRY run records the approval it would open.
  */
final class HintEffectPipeline(
    workLists: WorkListControllers,
    store: com.histmersion.domain.automation.agents.ApprovalService,
    ds: DataSource,
    cfg: => AgentsConfig
) extends PipelineDef:
  val pipeline: Pipeline = Pipeline.HintEffect
  import HintEffectPipeline.*

  def run(ctx: RunCtx): Either[Halt, String] =
    val mid = ctx.run.target
    for
      loaded <- ctx.code[Loaded]("load")(load(mid, ctx.run.params.hint))
      proposed <- ctx.llm[HintAnswer, Proposed](
        "propose",
        StepAgent.HintReader,
        input(loaded),
        AnswerSchemas.hintEffect
      )((a, last) =>
        HintProposals
          .check(a, t => loaded.known.contains(t), loaded.itemTarget, last)
          .map((e, ws) => Checked(Proposed(e, Option(a.why).map(_.trim).getOrElse(""), ws), ws))
      )
      line <- ctx.code[String]("record")(record(ctx, loaded, proposed))
      closed <- ctx.code[String]("close")(
        Pipelines.log(
          workLists,
          ctx,
          s"hint-effect ${loaded.managerId} hint #${loaded.hint}: $line " +
            s"(agent run ${ctx.run.id.take(8)})"
        )
      )
    yield closed

  private def load(mid: String, hint: Option[Long]): Either[String, Loaded] =
    for
      m <- ManagerRepo.get(ds, mid).toRight(s"no manager '$mid'")
      id <- hint.toRight("the run names no hint (RunParams.hint)")
      ht <- ManagerRepo.hint(ds, id).filter(_.managerId == m.id).toRight(s"manager $mid has no hint #$id")
    yield
      val (items, _) = ManagerRepo.items(ds, m.id, None, 0, ItemsShown)
      val lines = items.map(i => s"${i.target} (${i.kind}, ${i.status}, priority ${i.priority})")
      val itemTarget = ht.itemId.flatMap(ManagerRepo.item(ds, _)).map(_.target)
      Loaded(
        m.id,
        m.kind,
        m.goal,
        ht.id,
        ht.text,
        itemTarget,
        lines,
        (items.map(_.target) ++ m.goal.toList).distinct,
        ManagersMarkdown.configLine(m.config)
      )

  private def input(l: Loaded): String =
    val manager = s"id: ${l.managerId}\nkind: ${l.kind}\nresearch area: ${l.goal.getOrElse("none (library-wide)")}\n" +
      s"its settings now: ${l.settings}" + l.itemTarget.fold("")(t => s"\nthe hint is about the item: $t")
    s"Propose the action this hint asks the manager to take, if any.\n\n${Prompt.data("hint", l.text)}\n\n" +
      s"${Prompt.data("manager", manager)}\n\n" +
      Prompt.data("items", if l.items.isEmpty then "none" else l.items.map(x => s"- $x").mkString("\n")) +
      s"\n\nSettings a `config` action may change (key=value): $SettingKeys"

  /** The proposal, through the write gate: an open `hint-effect` approval about the manager (and the manager's event).
    * Nothing passed or asked for: nothing is recorded.
    */
  private def record(ctx: RunCtx, l: Loaded, p: Proposed): Either[String, String] =
    p.effect match
      case None =>
        Right(if p.problems.isEmpty then "the hint asks for no structured action"
        else s"nothing proposed: ${p.problems.mkString("; ").take(600)}")
      case Some(e) =>
        val by = s"model (hint-effect run ${ctx.run.id.take(8)})"
        val hp = HintProposal(l.managerId, l.hint, l.text, e, by, p.why)
        val what = ManagersMarkdown.effect(e)
        ctx.write(s"${ApprovalKind.HintEffect.wire} approval for ${l.managerId} hint #${l.hint}", hp.asJson) {
          val a = store.openSubject(Some(ctx.run.id), ApprovalKind.HintEffect, l.managerId, hp.asJson.noSpaces)
          ManagerRepo.event(
            ds,
            l.managerId,
            None,
            Some(ctx.run.id),
            Feed.Hint,
            s"hint #${l.hint}: a model proposed $what — approval #${a.id} waits for a person"
          )
          Right(s"approval ${a.id} waits for a person: $what")
        }(Right(s"no approval opened (${AgentsMarkdown.DryRunNote}): $what"))

object HintEffectPipeline:
  /** Items the model is shown (the most urgent first). */
  val ItemsShown = 60

  /** The keys a `config` action may name (what [[ManagerConfig.parse]] reads). */
  val SettingKeys =
    "maxRuns, maxOpen, rounds, maxRunsPerArticle, noProgressRuns, reopenLimit, cooldownMinutes, lowWater, " +
      "refillBatch, maxFailures, budgetUsd, includeTags, includeGoal, staleAfterDays, inferFilter, minSources, maxArticles, " +
      "sweeps (acquisition,currency), autoIngest, autoIngestPerDay, dailyUsd"

  /** The manager and the hint as the step sees them (checkpointed). `known` = what a target may name. */
  final case class Loaded(
      managerId: String,
      kind: String,
      goal: Option[String],
      hint: Long,
      text: String,
      itemTarget: Option[String],
      items: List[String],
      known: List[String],
      settings: String
  ) derives KindlingsCodecAsObject
