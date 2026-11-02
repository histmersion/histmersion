package com.histmersion.domain.library.workflows

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.workflows.PipelineDef

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.Pipeline
import CurrencyCheckRules.CurrencyAction
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.syntax.*

import com.histmersion.values.EdgeKind
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.api.library.Acquire

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
final class CurrencyCheckPipeline(
    protected val site: LibrarySite,
    protected val goals: com.histmersion.domain.automation.goals.GoalService,
    cfg: => AgentsConfig,
    upstream: () => AgentUpstream,
    approvals: SourceProposals
) extends PipelineDef
    with GoalSweep:
  val pipeline: Pipeline = Pipeline.CurrencyCheck
  import CurrencyCheckPipeline.*

  def run(ctx: RunCtx): Either[Halt, String] =
    val goal = ctx.run.target
    for
      targets <- ctx.code[List[Target]]("load")(Right(load(goal)))
      assessed <- ctx.code[Assessed]("assess")(Right(assess(ctx, targets)))
      followed <- ctx.code[Followed]("followups")(Right(followups(ctx, goal, targets)))
      line <- ctx.code[String]("close")(
        log(
          ctx,
          s"currency-check goal $goal: ${targets.size} source(s), ${targets.count(_.doi.isDefined)} with a DOI; " +
            s"assess: ${assessed.checked} checked, ${assessed.retracted.size} retracted" +
            assessed.retracted.map(r => s" [[$r]]").mkString +
            s"; followups: ${followed.citing} citing paper(s), ${followed.proposed} edge(s) proposed, " +
            s"${followed.queued} paper(s) queued for acquisition (agent run ${ctx.run.id.take(8)})"
        )
      )
    yield line

  private def load(goal: String): List[Target] =
    goalSources(goal, cfg.scheduler.currencySources)
      .flatMap(ck => site.sources.get(ck, body = false).toOption)
      .map(s => Target(s.source.citekey, s.source.doi.filter(_.trim.nonEmpty)))

  private def assess(ctx: RunCtx, ts: List[Target]): Assessed =
    val results = ts.filter(_.doi.isDefined).map { t =>
      upstream().assess(t.citekey, apply = !ctx.dryRun) match
        case Left(m)  => ctx.warn(s"assess ${t.citekey}: $m"); None
        case Right(a) =>
          if a.isRetracted then ctx.warn(s"assess: [[${t.citekey}]] is RETRACTED — ${a.recommendation}")
          if ctx.dryRun then ctx.write(s"assess [[${t.citekey}]] applied: verdict ${a.verdict}", a.asJson)(())(())
          Some((t.citekey, a.isRetracted))
    }
    Assessed(results.flatten.size, results.flatten.collect { case (ck, true) => ck })

  private def followups(ctx: RunCtx, goal: String, ts: List[Target]): Followed =
    val queued = queuedDois()
    val counts = ts.filter(_.doi.isDefined).map { t =>
      upstream().followups(t.citekey, None, Some(cfg.scheduler.followupsPerSource)) match
        case Left(m)  => ctx.warn(s"followups ${t.citekey}: $m"); (0, 0, 0)
        case Right(r) =>
          val acts = r.followups.map(CurrencyCheckRules.currencyAction(t.citekey, _))
          val proposed = acts.collect { case CurrencyAction.Propose(p) => p }.count { p =>
            val known = ctx.approvalStore.proposedBefore(p.kind, p.citekey, p.status, p.edgeKind, p.target) ||
              site.citations
                .neighbors(p.citekey, p.edgeKind.map(EdgeKind.wireOf).toList)
                .exists(n => n.direction == "out" && p.target.contains(n.citekey))
            !known && (approvals.propose(ctx, p) match
              case SourceProposals.Outcome.Recorded(id, w) => ctx.warn(s"approval $id waits for a person: $w"); true
              case SourceProposals.Outcome.Applied(_)      => true
              case SourceProposals.Outcome.NotProposed(_)  => true
              case SourceProposals.Outcome.Failed(w)       => ctx.warn(s"proposal not applied: $w"); false
              case _                                       => false)
          }
          val fetched = acts.collect { case CurrencyAction.Fetch(row) => row }.count { row =>
            row.doi.flatMap(Acquire.normalizeDoi).exists { doi =>
              queueOnce(ctx, goal, doi, CurrencyCheckRules.acquireText(row, t.citekey, goal), queued) match
                case Left(m)      => ctx.warn(s"followups: $doi not queued — $m"); false
                case Right(added) => added
            }
          }
          (r.followups.size, proposed, fetched)
    }
    Followed(counts.map(_._1).sum, counts.map(_._2).sum, counts.map(_._3).sum)

object CurrencyCheckPipeline:
  final case class Target(citekey: String, doi: Option[String]) derives KindlingsCodecAsObject
  final case class Assessed(checked: Int, retracted: List[String]) derives KindlingsCodecAsObject
  final case class Followed(citing: Int, proposed: Int, queued: Int) derives KindlingsCodecAsObject
