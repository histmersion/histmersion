package com.histmersion
package domain.library.workflows
import com.histmersion.util.json.RawJson.*

import com.histmersion.util.problem.Text.{sentence, sentences}
import com.histmersion.api.json.NewtypeCodecs.given
import io.scalaland.chimney.dsl.*

import com.histmersion.domain.library.acquisition.Ident

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.workflows.PipelineDef

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.Pipeline
import CurrencyCheckRules.CurrencyAction
import com.histmersion.util.json.JsonCodec

import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.api.library.Acquire
import com.histmersion.api.library.Edge
import com.histmersion.domain.automation.goals.GoalService
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.library.sources.SourcesControllers
import com.histmersion.domain.library.Doi
import com.histmersion.api.automation.Queue
import com.histmersion.domain.library.Citekey
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.Api
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** `currency-check <goal>` (docs/research-embedded-agents.md §4.5, §14): is what the goal rests on still accepted? For
  * each of the goal's sources with a DOI: `assess --apply` (a retraction overwrites the status, as `assess` always
  * does) and `followups` (read only). A citing paper that reads as challenging, refuting or superseding becomes an EDGE
  * PROPOSAL when both ends are in the corpus (through the approval gate, with the citation context as evidence — once:
  * a change already proposed or already recorded is not proposed again), or an acquisition item when it is not. No
  * model: the reading is [[Acquire.classify]]'s, so a scheduled check costs nothing. A DRY run assesses without
  * applying, and records the proposals, items and log line it would write.
  *
  * Steps: load · assess · followups · close.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
@tolerated(LintRule.Repository, "pending G11 (a repository stays inside its feature)")
final class CurrencyCheckPipeline(
    protected val site: LibrarySite,
    protected val goals: GoalService,
    cfg: => AgentsConfig,
    upstream: () => AgentUpstream,
    approvals: SourceProposals
) extends PipelineDef
    with GoalSweep:
  val pipeline: Pipeline = Pipeline.CurrencyCheck
  import CurrencyCheckPipeline.*

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def run(ctx: RunCtx)(using WritePermit)(using Creator.New): Either[Halt, PipelineDef.Summary] =
    val goal = goalOf(ctx)
    for
      targets <- ctx.code[List[Target]](AgentRepo.StepRecord.Name("load"))(Right(load(goal)))
      assessed <- ctx.code[Assessed](AgentRepo.StepRecord.Name("assess"))(Right(assess(ctx, targets)))
      followed <- ctx.code[Followed](AgentRepo.StepRecord.Name("followups"))(Right(followups(ctx, goal, targets)))
      line <- ctx.code[String](AgentRepo.StepRecord.Name("close"))(
        log(
          ctx,
          s"currency-check goal $goal: ${targets.size} source(s), ${targets.count(_.doi.isDefined)} with a DOI; " +
            s"assess: ${assessed.checked} checked, ${assessed.retracted.size} retracted" +
            assessed.retracted.map(r => s" [[$r]]").mkString +
            s"; followups: ${followed.citing} citing paper(s), ${followed.proposed} edge(s) proposed, " +
            s"${followed.queued} paper(s) queued for acquisition (agent run ${ctx.run.id.value.take(8)})"
        )
      )
    yield PipelineDef.Summary(line)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def load(goal: Goal.Slug): List[Target] =
    goalSources(goal, GoalSweep.Limit(cfg.scheduler.currencySources.value))
      .map(site.sources.canonical)
      .flatMap(ck =>
        site.sources
          .get(ck, body = SourcesControllers.GetBody.WithoutBody)
          .left
          .map(Api.Error(_))
          .toOption
          .map(s => Target(ck, s.source.doi.filter(_.value.trim.nonEmpty).flatMap(d => Doi.option(d.value.trim))))
      )

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def assess(ctx: RunCtx, ts: List[Target])(using WritePermit)(using Creator.New): Assessed =
    val results = ts.filter(_.doi.isDefined).map { t =>
      upstream().assess(
        t.citekey,
        apply = (if !ctx.dryRun then AgentUpstream.AssessApply.Apply else AgentUpstream.AssessApply.DryRun)
      ) match
        case Left(m)  => ctx.warn(s"assess ${t.citekey}: ${m.sentences.mkString("; ")}"); None
        case Right(a) =>
          if a.isRetracted.isRetracted then ctx.warn(s"assess: [[${t.citekey}]] is RETRACTED — ${a.recommendation}")
          if ctx.dryRun then ctx.write(s"assess [[${t.citekey}]] applied: verdict ${a.verdict}", a.asJson)(())(())
          Some((t.citekey, a.isRetracted.isRetracted))
    }
    Assessed(Assessed.Checked(results.flatten.size), results.flatten.collect { case (ck, true) => ck })

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def followups(ctx: RunCtx, goal: Goal.Slug, ts: List[Target])(using
      WritePermit
  )(using Creator.New): Followed =
    val queued = queuedDois()
    val counts = ts.filter(_.doi.isDefined).map { t =>
      upstream().followups(
        AgentUpstream.FollowupsId(t.citekey.value),
        None,
        Some(FollowupsInput.Limit(cfg.scheduler.followupsPerSource.value))
      ) match
        case Left(m)  => ctx.warn(s"followups ${t.citekey}: ${m.sentences.mkString("; ")}"); (0, 0, 0)
        case Right(r) =>
          val acts = r.followups.map(CurrencyCheckRules.currencyAction(t.citekey, _))
          val proposed = acts.collect { case CurrencyAction.Propose(p) => p }.count { p =>
            val known = approvals.proposedBefore(p) ||
              site.citations
                .neighbors(
                  p.citekey,
                  p.edgeKind.map(k => domain.library.Edge.Kind.Wire(Edge.Kind.wireOf(k).value)).toList
                )
                .exists(n => n.direction.value == "out" && p.target.exists(_.value == n.citekey.value))
            !known && (approvals.propose(ctx, p) match
              case SourceProposals.Outcome.Recorded(id, w) => ctx.warn(s"approval $id waits for a person: $w"); true
              case SourceProposals.Outcome.Applied(_)      => true
              case SourceProposals.Outcome.NotProposed(_)  => true
              case SourceProposals.Outcome.Failed(w)       => ctx.warn(s"proposal not applied: ${w.sentence}"); false
              case _                                       => false)
          }
          val fetched = acts.collect { case CurrencyAction.Fetch(row) => row }.count { row =>
            row.doi.flatMap(d => Ident.doi(Ident.Written(d.value))).exists { doi =>
              queueOnce(
                ctx,
                goal,
                doi,
                Queue.QueueItem.Text(CurrencyCheckRules.acquireText(row, t.citekey, goal).value),
                queued
              ) match
                case Left(m)      => ctx.warn(s"followups: $doi not queued — $m"); false
                case Right(added) => added
            }
          }
          (r.followups.size, proposed, fetched)
    }
    Followed(
      Followed.Citing(counts.map(_._1).sum),
      Followed.Proposed(counts.map(_._2).sum),
      Followed.Queued(counts.map(_._3).sum)
    )

object CurrencyCheckPipeline:
  final case class Target(citekey: Citekey, doi: Option[Doi]) derives JsonCodec
  final case class Assessed(checked: Assessed.Checked, retracted: List[Assessed.Retracted]) derives JsonCodec

  object Assessed:
    type Checked = Checked.T
    object Checked extends Newtype[Int, Pure]
    @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
    type Retracted = Citekey
    val Retracted: Citekey.type = Citekey
  final case class Followed(citing: Followed.Citing, proposed: Followed.Proposed, queued: Followed.Queued)
      derives JsonCodec

  object Followed:
    type Citing = Citing.T
    object Citing extends Newtype[Int, Pure]
    type Proposed = Proposed.T
    object Proposed extends Newtype[Int, Pure]
    type Queued = Queued.T
    object Queued extends Newtype[Int, Pure]
