package com.histmersion.server.agents

import com.histmersion.core.*
import com.histmersion.core.Agents.{Pipeline, Role}
import com.histmersion.core.GoalPlans.LinkKind
import com.histmersion.core.Queue.QueueAdd
import com.histmersion.core.Scheduling.{Candidate, CurrencyAction, Ranking}
import com.histmersion.db.{AgentRepo, GoalLinkRepo, ManagerRepo}
import com.histmersion.server.Handlers
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.syntax.*

import javax.sql.DataSource

/** What both goal sweeps share: the goal's sources, the DOIs already in the acquisition queue, and
  * adding an item there at most once. */
private[agents] trait GoalSweep:
  protected def h: Handlers
  protected def ds: DataSource

  /** The sources a goal is about: linked directly, and cited by the articles linked to it. */
  protected def goalSources(goal: String, max: Int): List[String] =
    val direct  = GoalLinkRepo.refs(ds, goal, LinkKind.Source)
    val viaArts = GoalLinkRepo.refs(ds, goal, LinkKind.Article).flatMap(a => h.getArticle(a).toOption.toList)
      .flatMap(_.sourcesCited.filter(_.title.isDefined).map(_.citekey))
    (direct ++ viaArts).distinct.take(math.max(0, max))

  /** Every DOI in the acquisition queue (open or done) → its item id. */
  protected def queuedDois(): Map[String, Int] =
    h.ops.queueList("acquire", all = true).toOption.toList.flatMap(_.items)
      .flatMap(i => Scheduling.dois(i.text).map(_ -> i.id)).toMap

  /** Queue `text` for acquisition unless `doi` is already queued; link the item to the goal either
    * way. `Right(true)` = a new item (a dry run: one it would add). Both writes go through the run's
    * gate. */
  protected def queueOnce(ctx: RunCtx, goal: String, doi: String, text: String, queued: Map[String, Int]): Either[String, Boolean] =
    queued.get(doi) match
      case Some(id) =>
        ctx.write(s"link queue item $id to goal $goal", io.circe.Json.obj("goal" -> goal.asJson, "item" -> id.asJson, "doi" -> doi.asJson))(
          { val _ = GoalLinkRepo.link(ds, goal, LinkKind.QueueItem, id.toString, Some(doi)) })(())
        Right(false)
      case None =>
        val item = QueueAdd(text, topic = Some(s"goal $goal"))
        ctx.write(s"acquire item for $doi, linked to goal $goal", item.asJson)(
          h.ops.queueAdd("acquire", item).left.map(_.message).map { added =>
            val _ = GoalLinkRepo.link(ds, goal, LinkKind.QueueItem, added.id.toString, Some(doi))
            true
          })(Right(true))

  protected def log(ctx: RunCtx, line: String): Either[String, String] = Pipelines.log(h, ctx, line)

/** `currency-check <goal>` (docs/research-embedded-agents.md §4.5, §14): is what the goal rests on
  * still accepted? For each of the goal's sources with a DOI: `assess --apply` (a retraction
  * overwrites the status, as `assess` always does) and `followups` (read only). A citing paper that
  * reads as challenging, refuting or superseding becomes an EDGE PROPOSAL when both ends are in the
  * corpus (through the approval gate, with the citation context as evidence — once: a change already
  * proposed or already recorded is not proposed again), or an acquisition item when it is not.
  * No model: the reading is [[Acquire.classify]]'s, so a scheduled check costs nothing. A DRY run
  * assesses without applying, and records the proposals, items and log line it would write.
  *
  * Steps: load · assess · followups · close. */
final class CurrencyCheckPipeline(protected val h: Handlers, protected val ds: DataSource, cfg: AgentsConfig,
    upstream: () => AgentUpstream, approvals: Approvals) extends PipelineDef with GoalSweep:
  val pipeline: Pipeline = Pipeline.CurrencyCheck
  import CurrencyCheckPipeline.*

  def run(ctx: RunCtx): Either[Halt, String] =
    val goal = ctx.run.target
    for
      targets  <- ctx.code[List[Target]]("load")(Right(load(goal)))
      assessed <- ctx.code[Assessed]("assess")(Right(assess(ctx, targets)))
      followed <- ctx.code[Followed]("followups")(Right(followups(ctx, goal, targets)))
      line     <- ctx.code[String]("close")(log(ctx,
                    s"currency-check goal $goal: ${targets.size} source(s), ${targets.count(_.doi.isDefined)} with a DOI; " +
                    s"assess: ${assessed.checked} checked, ${assessed.retracted.size} retracted" +
                    assessed.retracted.map(r => s" [[${r}]]").mkString +
                    s"; followups: ${followed.citing} citing paper(s), ${followed.proposed} edge(s) proposed, " +
                    s"${followed.queued} paper(s) queued for acquisition (agent run ${ctx.run.id.take(8)})"))
    yield line

  private def load(goal: String): List[Target] =
    goalSources(goal, cfg.scheduler.currencySources).flatMap(ck => h.getSource(ck, body = false).toOption)
      .map(s => Target(s.source.citekey, s.source.doi.filter(_.trim.nonEmpty)))

  private def assess(ctx: RunCtx, ts: List[Target]): Assessed =
    val results = ts.filter(_.doi.isDefined).map { t =>
      upstream().assess(t.citekey, apply = !ctx.dryRun) match
        case Left(m) => ctx.warn(s"assess ${t.citekey}: $m"); None
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
        case Left(m) => ctx.warn(s"followups ${t.citekey}: $m"); (0, 0, 0)
        case Right(r) =>
          val acts = r.followups.map(Scheduling.currencyAction(t.citekey, _))
          val proposed = acts.collect { case CurrencyAction.Propose(p) => p }.count { p =>
            val known = AgentRepo.proposedBefore(ds, p) ||
              h.neighbors(p.citekey, p.edgeKind.map(EdgeKind.wireOf).toList).exists(n => n.direction == "out" && p.target.contains(n.citekey))
            !known && (approvals.propose(ctx, p) match
              case Approvals.Outcome.Recorded(id, w) => ctx.warn(s"approval $id waits for a person: $w"); true
              case Approvals.Outcome.Applied(_)      => true
              case Approvals.Outcome.NotProposed(_)  => true
              case Approvals.Outcome.Failed(w)       => ctx.warn(s"proposal not applied: $w"); false
              case _                                 => false)
          }
          val fetched = acts.collect { case CurrencyAction.Fetch(row) => row }.count { row =>
            row.doi.flatMap(Acquire.normalizeDoi).exists { doi =>
              queueOnce(ctx, goal, doi, Scheduling.acquireText(row, t.citekey, goal), queued) match
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

/** `acquisition-sweep <goal>` (docs/research-embedded-agents.md §4.5, §14): code searches OpenAlex
  * with the goal's linked queries (its title when it has none) and drops every work the corpus or the
  * acquisition queue already has; the planner model ranks what is left against the goal (no tools,
  * the candidates as data, a pick that names an unlisted DOI is refused); code queues the picks as
  * acquisition items linked to the goal. Nothing is ingested — acquiring stays a person's call — unless
  * the run comes from a watcher that OPTED IN (`RunParams.autoIngest`, docs/research-process-managers.md
  * §4 (c)): then at most that many of the open-access picks are ingested through the LEGAL chain only
  * (never the unauthorised mirror; a work with no legal file is not stored and is queued instead), each linked to the
  * goal and recorded as an `ingested` event of the watcher (what its daily cap counts).
  *
  * Steps: discover · rank · [ingest] · queue · close. The ingest step exists only when auto-ingest was
  * allowed, so a run without it has exactly the steps it always had. A DRY run records the items, the
  * ingests (and the log line) it would make. */
final class AcquisitionSweepPipeline(protected val h: Handlers, protected val ds: DataSource, cfg: AgentsConfig,
    upstream: () => AgentUpstream) extends PipelineDef with GoalSweep:
  val pipeline: Pipeline = Pipeline.AcquisitionSweep
  import AcquisitionSweepPipeline.*

  def run(ctx: RunCtx): Either[Halt, String] =
    val goal = ctx.run.target
    for
      found <- ctx.code[Found]("discover")(discover(ctx, goal))
      line  <-
        if found.candidates.isEmpty then
          ctx.code[String]("close")(log(ctx, s"acquisition-sweep goal $goal: ${found.queries.size} query(ies), nothing new " +
            s"(${skippedText(found.skipped)}) (agent run ${ctx.run.id.take(8)})"))
        else
          val allowed = ctx.run.params.autoIngest.getOrElse(0)
          for
            picks  <- ctx.llm[Ranking, List[Picked]]("rank", StepAgent.AcquisitionRanker, rankInput(goal, found),
                        AnswerSchemas.ranking)((r, last) =>
                        Scheduling.checkRanking(r, found.candidates, last).map((ps, ws) =>
                          Checked(ps.map((c, why) => Picked(c, why)), ws)))
            took   <- if allowed > 0 then ctx.code[List[Ingested]]("ingest")(Right(ingest(ctx, goal, picks, allowed)))
                      else Right(Nil)
            added  <- ctx.code[Int]("queue")(Right(queue(ctx, goal, picks.filterNot(p => took.exists(_.doi == p.candidate.doi)))))
            line   <- ctx.code[String]("close")(log(ctx, s"acquisition-sweep goal $goal: ${found.queries.size} query(ies), " +
                        s"${found.candidates.size} new candidate(s), ${picks.size} picked, " +
                        (if allowed > 0 then s"${took.size} ingested (legal open access${took.map(i => s" [[${i.citekey}]]").mkString}), "
                         else "") +
                        s"$added queued for acquisition (${skippedText(found.skipped)}) (agent run ${ctx.run.id.take(8)})"))
          yield line
    yield line

  private def skippedText(m: Map[String, Int]): String =
    if m.isEmpty then "none left out" else m.toList.sortBy(_._1).map((k, n) => s"$n $k").mkString(", ")

  private def discover(ctx: RunCtx, goal: String): Either[String, Found] =
    h.getGoal(goal).left.map(_.message).map { g =>
      val queries = Some(GoalLinkRepo.refs(ds, goal, LinkKind.Query)).filter(_.nonEmpty).getOrElse(List(g.title))
      val found = queries.flatMap { q =>
        upstream().discover(q, cfg.scheduler.discoverPerQuery) match
          case Left(m)  => ctx.warn(s"discover '$q': $m"); Nil
          case Right(r) => r.works.map(q -> _)
      }
      val (cands, skipped) = Scheduling.candidates(found, queuedDois().keySet, cfg.scheduler.sweepCandidates)
      Found(g.title, g.notes, queries, cands, skipped)
    }

  private def rankInput(goal: String, f: Found): String =
    val list = f.candidates.map { c =>
      val who = c.authors.headOption.fold("")(a => s"$a${if c.authors.size > 1 then " et al." else ""}, ")
      s"- doi ${c.doi} — ${c.title} (${who}${c.year.fold("n.d.")(_.toString)}${c.venue.fold("")(v => s", $v")})" +
        s" · found by \"${c.query}\"" + c.abstractText.fold("")(a => s"\n  abstract: $a")
    }.mkString("\n")
    s"Pick the candidates worth acquiring for this goal.\n\n" +
      Prompt.data("goal", s"slug: $goal\ntitle: ${f.title}\nnotes: ${f.notes.getOrElse("none")}") + "\n\n" +
      Prompt.data("candidates", list)

  /** The watcher's auto-ingest: the open-access picks, in the model's order, until `allowed` were ingested,
    * each through the LEGAL chain only. One that has no legal file (or fails) is left for the queue step,
    * and the next open-access pick is tried. */
  private def ingest(ctx: RunCtx, goal: String, picks: List[Picked], allowed: Int): List[Ingested] =
    Scheduling.ingestPicks(picks, (p: Picked) => p.candidate.isOa, allowed) { p =>
      val doi = p.candidate.doi
      ctx.write(s"ingest $doi (legal open access, no unauthorised mirror) for goal $goal", io.circe.Json.obj("doi" -> doi.asJson,
          "title" -> p.candidate.title.asJson, "why" -> p.why.asJson))(
        upstream().ingestLegal(doi) match
          case Left(m) =>
            ctx.warn(s"ingest: $doi not ingested, queued instead — $m")
            None
          case Right(r) =>
            val _ = GoalLinkRepo.link(ds, goal, LinkKind.Source, r.citekey, Some(s"ingested by a watcher sweep: ${p.why}".take(500)))
            ctx.run.managerId.foreach(mid => ManagerRepo.event(ds, mid, ctx.run.managerItem, Some(ctx.run.id), Managers.Feed.Ingested,
              s"[[${r.citekey}]] ingested (legal open access, ${r.pdf.fold("no file")(_.source)}): ${p.candidate.title} — https://doi.org/$doi"))
            Some(Ingested(doi, r.citekey)))(
        Some(Ingested(doi, s"(${AgentsMarkdown.DryRunNote})")))
    }.map(_._2)

  private def queue(ctx: RunCtx, goal: String, picks: List[Picked]): Int =
    val queued = queuedDois()
    picks.count { p =>
      queueOnce(ctx, goal, p.candidate.doi, Scheduling.acquireText(p.candidate, goal, p.why), queued) match
        case Left(m)      => ctx.warn(s"queue: ${p.candidate.doi} not queued — $m"); false
        case Right(added) => added
    }

object AcquisitionSweepPipeline:
  final case class Found(title: String, notes: Option[String], queries: List[String], candidates: List[Candidate],
      skipped: Map[String, Int]) derives KindlingsCodecAsObject
  final case class Picked(candidate: Candidate, why: String) derives KindlingsCodecAsObject
  /** A pick the watcher's auto-ingest brought in (`citekey` the new source's). */
  final case class Ingested(doi: String, citekey: String) derives KindlingsCodecAsObject
