package com.histmersion
package domain.automation.managers
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.util.problem.{Problem, Problems}

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.database.Transactor

import com.augustnagro.magnum.connect

import com.histmersion.domain.automation.agents.{
  AgentRepo,
  AnswerSchemas,
  ApprovalService,
  Checked,
  Halt,
  Prompt,
  RunCtx,
  StepAgent
}
import com.histmersion.domain.automation.approvals.ApprovalQuery
import com.histmersion.domain.automation.workflows.{PipelineDef, Pipelines}

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.{ApprovalKind, Pipeline}
import com.histmersion.domain.HintProposals.{HintAnswer, Proposed}
import com.histmersion.api.automation.Managers.*
import com.histmersion.domain.automation.managers.ManagerRepo
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.util.json.JsonCodec

import javax.sql.DataSource
import com.histmersion.api.automation.{AgentsMarkdown, ManagersMarkdown}
import com.histmersion.util.jobs.Job
import com.histmersion.api.automation.Goal
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.automation.Managers
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{LintRule, tolerated}

/** `hint-effect <manager>` (docs/research-process-managers.md §3.6, phase 3): a person's hint, in words, read by the
  * planner model (the `hint-reader` definition: no tools — the hint, the manager's items and its settings are given as
  * data), which proposes the structured action it asks for. Code checks the proposal ([[HintProposals.check]]: an
  * action it knows, targets that are the manager's items, a date that parses, settings that parse; a failing answer
  * goes back with why) and records it as a `hint-effect` approval the run does NOT wait for — NOTHING is applied until
  * a person approves it (then [[ManagersControllers.applyProposal]]). Started by a person (`suggest` on a hint).
  *
  * Steps: load · propose · record · close. A DRY run records the approval it would open.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.Repository, "pending G11 (a repository stays inside its feature)")
final class HintEffectPipeline(
    workLists: WorkListControllers,
    store: ApprovalService,
    ds: DataSource,
    cfg: => AgentsConfig
) extends PipelineDef:
  val pipeline: Pipeline = Pipeline.HintEffect
  import HintEffectPipeline.*

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def run(ctx: RunCtx)(using WritePermit)(using Creator.New): Either[Halt, PipelineDef.Summary] =
    val mid = ManagerInfo.Id(ctx.run.target.value)
    for
      loaded <- ctx.code[Loaded](AgentRepo.StepRecord.Name("load"))(
        load(mid, ctx.run.params.hint.map(h => ManagerHint.Id(h.value)))
      )
      proposed <- ctx.llm[HintAnswer, Proposed](
        AgentRepo.StepRecord.Name("propose"),
        StepAgent.HintReader,
        Prompt.UserText(input(loaded)),
        AnswerSchemas.hintEffect
      )((a, last) =>
        HintProposals
          .check(
            a,
            t => loaded.known.contains(t),
            loaded.itemTarget.map(t => HintProposals.Item(t.value)),
            HintProposals.Last(last.value)
          )
          .map((e, ws) =>
            Checked(
              Proposed(e, Managers.Rationale(Option(a.why).fold("")(w => s"$w".trim)), ws),
              ws.map(w => Checked.Warnings(w.value))
            )
          )
      )
      line <- ctx.code[String](AgentRepo.StepRecord.Name("record"))(
        ctx.byAnswer(AgentRepo.StepRecord.Name("propose"))(record(ctx, loaded, proposed))
      )
      closed <- ctx.code[String](AgentRepo.StepRecord.Name("close"))(
        Pipelines
          .log(
            workLists,
            ctx,
            Pipelines.LogLine(
              s"hint-effect ${loaded.managerId.value} hint #${loaded.hint.value}: $line " +
                s"(agent run ${ctx.run.id.value.take(8)})"
            )
          )
          .map(_.value)
      )
    yield PipelineDef.Summary(closed)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def load(mid: ManagerInfo.Id, hint: Option[ManagerHint.Id]): Either[Problems, Loaded] =
    for
      m <- connect(ds)(ManagerRepo.get(mid))
        .toRight(Problem.NotFound.Entity(Problem.TypeName.of(ManagerInfo.Id), Problem.Raw.of(mid)))
      id <- hint.toRight(Problem.Invalid.Missing.here(Problem.Field("RunParams.hint")))
      ht <- Transactor(ds)
        .read(ManagerRepo.hint(id))
        .filter(_.managerId == m.id)
        .toRight(Problem.NotFound.Entity(Problem.TypeName.of(ManagerHint.Id), Problem.Raw.of(id)))
    yield
      val (items, _) = Transactor(ds).read(ManagerRepo.itemPage(m.id, None, Offset(0), ItemsShown))
      val lines =
        items.map(i =>
          Loaded.Items(s"${i.target.value} (${i.kind.value}, ${i.status.value}, priority ${i.priority.value})")
        )
      val itemTarget = ht.itemId.flatMap(i => connect(ds)(ManagerRepo.item(ManagerItem.Id(i.value)))).map(_.target)
      Loaded(
        m.id,
        m.kind,
        m.goal,
        ht.id,
        ht.text,
        itemTarget,
        lines,
        (items.map(_.target.value) ++ m.goal.toList.map(_.value)).distinct.map(HintProposals.Item(_)),
        Loaded.Settings(ManagersMarkdown.configLine(m.config))
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  private def input(l: Loaded): String =
    val manager =
      s"id: ${l.managerId.value}\nkind: ${l.kind.value}\nresearch area: ${l.goal.fold("none (library-wide)")(_.value)}\n" +
        s"its settings now: ${l.settings.value}" + l.itemTarget.fold("")(t =>
          s"\nthe hint is about the item: ${t.value}"
        )
    s"Propose the action this hint asks the manager to take, if any.\n\n${Prompt.data("hint", l.text.value)}\n\n" +
      s"${Prompt.data("manager", manager)}\n\n" +
      Prompt.data("items", if l.items.isEmpty then "none" else l.items.map(x => s"- ${x.value}").mkString("\n")) +
      s"\n\nSettings a `config` action may change (key=value): $SettingKeys"

  /** The proposal, through the write gate: an open `hint-effect` approval about the manager (and the manager's event).
    * Nothing passed or asked for: nothing is recorded.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def record(ctx: RunCtx, l: Loaded, p: Proposed)(using
      WritePermit
  )(using Creator.New): Either[Problems, String] =
    p.effect match
      case None =>
        Right(if p.problems.isEmpty then "the hint asks for no structured action"
        else s"nothing proposed: ${p.problems.map(_.value).mkString("; ").take(600)}")
      case Some(e) =>
        val by = s"model (hint-effect run ${ctx.run.id.value.take(8)})"
        val hp = HintProposal(
          l.managerId,
          HintProposal.Hint(l.hint.value),
          HintProposal.Text(l.text.value),
          e,
          HintProposal.By(by),
          p.why
        )
        val what = ManagersMarkdown.effect(e)
        ctx.write(
          s"${ApprovalKind.HintEffect.wire.value} approval for ${l.managerId.value} hint #${l.hint.value}",
          hp.asJson
        ) {
          val a = store.openSubject(
            Some(Job.Id.assume(ctx.run.id.value)),
            ApprovalKind.HintEffect,
            ApprovalQuery.Subject(l.managerId.value),
            ApprovalService.Payload(hp.asJson.noSpaces)
          )
          Transactor(ds).write(
            ManagerRepo.event(
              l.managerId,
              None,
              Some(ctx.run.id),
              ManagerEvent.Kind(Feed.Hint),
              ManagerEvent.Text(
                s"hint #${l.hint.value}: a model proposed $what — approval #${a.id.value} waits for a person"
              )
            )
          )
          Right(s"approval ${a.id.value} waits for a person: $what")
        }(Right(s"no approval opened (${AgentsMarkdown.DryRunNote}): $what"))

object HintEffectPipeline:
  /** Items the model is shown (the most urgent first). */
  val ItemsShown: PageSize = PageSize(60)

  /** The keys a `config` action may name (what [[ManagerConfig.parse]] reads). */
  val SettingKeys =
    "maxRuns, maxOpen, rounds, maxRunsPerArticle, noProgressRuns, reopenLimit, cooldownMinutes, lowWater, " +
      "refillBatch, maxFailures, budgetUsd, includeTags, includeGoal, staleAfterDays, inferFilter, minSources, maxArticles, " +
      "sweeps (acquisition,currency), autoIngest, autoIngestPerDay, dailyUsd"

  /** The manager and the hint as the step sees them (checkpointed). `known` = what a target may name. */
  final case class Loaded(
      managerId: ManagerInfo.Id,
      kind: ManagerInfo.Kind,
      goal: Option[Goal.Slug],
      hint: ManagerHint.Id,
      text: ManagerHint.Text,
      itemTarget: Option[ManagerItem.Target],
      items: List[Loaded.Items],
      known: List[HintProposals.Item],
      settings: Loaded.Settings
  ) derives JsonCodec

  object Loaded:
    type Items = Items.T
    object Items extends Newtype[String, Pure]
    type Settings = Settings.T
    object Settings extends Newtype[String, Pure]
