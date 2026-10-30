package com.histmersion.server.agents

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Agents.Pipeline
import com.histmersion.api.GoalPlans.LinkKind
import com.histmersion.domain.Scheduling.{Candidate, Ranking}
import com.histmersion.db.{GoalLinkRepo, ManagerRepo}
import com.histmersion.server.Handlers
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.syntax.*

import javax.sql.DataSource
import com.histmersion.api.VocabularyCodecs.given

/** `acquisition-sweep <goal>` (docs/research-embedded-agents.md §4.5, §14): code searches OpenAlex with the goal's
  * linked queries (its title when it has none) and drops every work the corpus or the acquisition queue already has;
  * the planner model ranks what is left against the goal (no tools, the candidates as data, a pick that names an
  * unlisted DOI is refused); code queues the picks as acquisition items linked to the goal. Nothing is ingested —
  * acquiring stays a person's call — unless the run comes from a watcher that OPTED IN (`RunParams.autoIngest`,
  * docs/research-process-managers.md §4 (c)): then at most that many of the open-access picks are ingested through the
  * LEGAL chain only (never an unauthorised fetcher; a work with no legal file is not stored and is queued instead),
  * each linked to the goal and recorded as an `ingested` event of the watcher (what its daily cap counts).
  *
  * Steps: discover · rank · [ingest] · queue · close. The ingest step exists only when auto-ingest was allowed, so a
  * run without it has exactly the steps it always had. A DRY run records the items, the ingests (and the log line) it
  * would make.
  *
  * A watcher's run with an INGEST BACKLOG (`RunParams.oaBacklog`, docs/research-process-managers.md "As built: the
  * ingest backlog"): discover · rank · [ingest] · backlog · queue · close — the open-access picks past the allowance
  * (and the ones that failed for another reason than a missing legal file) wait in the watcher's backlog
  * ([[OaBacklog.add]]) for a later day; the acquisition list gets only the closed picks and the ones with no legal
  * file.
  */
final class AcquisitionSweepPipeline(
    protected val h: Handlers,
    protected val ds: DataSource,
    cfg: => AgentsConfig,
    upstream: () => AgentUpstream,
    backlog: OaBacklog
) extends PipelineDef
    with GoalSweep:
  val pipeline: Pipeline = Pipeline.AcquisitionSweep
  import AcquisitionSweepPipeline.*

  def run(ctx: RunCtx): Either[Halt, String] =
    val goal = ctx.run.target
    for
      found <- ctx.code[Found]("discover")(discover(ctx, goal))
      line <-
        if found.candidates.isEmpty then
          ctx.code[String]("close")(
            log(
              ctx,
              s"acquisition-sweep goal $goal: ${found.queries.size} query(ies), nothing new " +
                s"(${skippedText(found.skipped)}) (agent run ${ctx.run.id.take(8)})"
            )
          )
        else
          val allowed = ctx.run.params.autoIngest.getOrElse(0)
          for
            picks <- ctx.llm[Ranking, List[Picked]](
              "rank",
              StepAgent.AcquisitionRanker,
              rankInput(goal, found),
              AnswerSchemas.ranking
            )((r, last) =>
              Scheduling
                .checkRanking(r, found.candidates, last)
                .map((ps, ws) => Checked(ps.map((c, why) => Picked(c, why)), ws))
            )
            // the stored candidates the ranking saw are SEEN, its picks QUEUED: a later sweep shows neither again
            _ <- if found.stored.isEmpty then Right(0) else ctx.code[Int]("seen")(Right(markSeen(ctx, found, picks)))
            backed = ctx.run.params.oaBacklog.contains(true) && ctx.run.managerId.isDefined
            line <-
              if backed then withBacklog(ctx, goal, found, picks, allowed)
              else withoutBacklog(ctx, goal, found, picks, allowed)
          yield line
    yield line

  /** The steps after the ranking as they always were: the open-access picks within the allowance ingested, every other
    * pick queued for acquisition.
    */
  private def withoutBacklog(
      ctx: RunCtx,
      goal: String,
      found: Found,
      picks: List[Picked],
      allowed: Int
  ): Either[Halt, String] =
    for
      took <-
        if allowed > 0 then ctx.code[List[Ingested]]("ingest")(Right(ingest(ctx, goal, picks, allowed)))
        else Right(Nil)
      added <- ctx.code[Int]("queue")(
        Right(queue(ctx, goal, picks.filterNot(p => took.exists(_.doi == p.candidate.doi))))
      )
      line <- ctx.code[String]("close")(
        log(
          ctx,
          s"acquisition-sweep goal $goal: ${found.queries.size} query(ies), " +
            s"${found.candidates.size} new candidate(s), ${picks.size} picked, " +
            (if allowed > 0 then
               s"${took.size} ingested (legal open access${took.map(i => s" [[${i.citekey}]]").mkString}), "
             else "") +
            s"$added queued for acquisition (${skippedText(found.skipped)}) (agent run ${ctx.run.id.take(8)})"
        )
      )
    yield line

  /** The steps after the ranking for a watcher with an ingest backlog: the open-access picks within today's allowance
    * ingested; the rest of them — and the ones whose ingest failed for another reason than a missing legal file —
    * parked in the backlog; the closed picks and the ones with no legal file queued for acquisition.
    */
  private def withBacklog(
      ctx: RunCtx,
      goal: String,
      found: Found,
      picks: List[Picked],
      allowed: Int
  ): Either[Halt, String] =
    for
      tried <-
        if allowed > 0 then ctx.code[Tried]("ingest")(Right(ingestOa(ctx, goal, picks, allowed))) else Right(Tried())
      noCopy = tried.failed.filter(_.noCopy).map(_.doi).toSet
      waiting = picks.filter(p =>
        p.candidate.isOa && !tried.ingested.exists(_.doi == p.candidate.doi) && !noCopy(p.candidate.doi)
      )
      parked <- ctx.code[Parked]("backlog")(Right(park(ctx, goal, waiting)))
      added <- ctx.code[Int]("queue")(
        Right(queue(ctx, goal, picks.filter(p => !p.candidate.isOa || noCopy(p.candidate.doi))))
      )
      line <- ctx.code[String]("close")(
        log(
          ctx,
          s"acquisition-sweep goal $goal: ${found.queries.size} query(ies), " +
            s"${found.candidates.size} new candidate(s), ${picks.size} picked, " +
            s"${tried.ingested.size} ingested (legal open access${tried.ingested.map(i => s" [[${i.citekey}]]").mkString}), " +
            s"${parked.waiting} to the ingest backlog (legal open access past today's allowance)" +
            (if parked.known > 0 then s", ${parked.known} in the corpus or on the acquisition list already" else "") +
            s", $added queued for acquisition (${skippedText(found.skipped)}) (agent run ${ctx.run.id.take(8)})"
        )
      )
    yield line

  private def skippedText(m: Map[String, Int]): String =
    if m.isEmpty then "none left out" else m.toList.sortBy(_._1).map((k, n) => s"$n $k").mkString(", ")

  /** The DOIs in a watcher's ingest backlog that this goal found already (a pick another goal found joins that entry,
    * so it is offered to this goal's ranking too).
    */
  private def backlogged(goal: String): Set[String] =
    ManagerRepo
      .backlogOpen(ds)
      .filter(i => i.state.oa.exists(_.goals.contains(goal)) || i.managerId == Managers.watchId(goal))
      .map(_.target)
      .toSet

  /** The candidates the deterministic search stored for the goal (#3761): its watches read now from their watermarks
    * (no model), then the unseen ones; the live search below only when that search is not connected (a fixture).
    */
  private def discover(ctx: RunCtx, goal: String): Either[String, Found] =
    upstream().sweepCandidates(goal, ctx.dryRun) match
      case Left(_)   => liveDiscover(ctx, goal)
      case Right(sw) =>
        sw.watches
          .filter(_.lastError.isDefined)
          .foreach(w =>
            ctx.warn(s"candidates: ${w.index} ${w.kind} '${w.subject}' not read — ${w.lastError.getOrElse("")}")
          )
        for
          g <- h.getGoal(goal).left.map(_.message)
          rows <- upstream().candidates(goal, Candidates.State.New.wire, cfg.scheduler.sweepCandidates)
        yield
          val queued = queuedDois().keySet ++ backlogged(goal)
          val (gone, open) = rows.partition(c => queued.contains(c.doi))
          val cands = open.map(c =>
            Candidate(
              c.doi,
              c.title,
              c.year,
              c.authors.take(4),
              c.venue,
              c.isOa,
              c.foundBy.headOption.getOrElse("the candidate search"),
              c.abstractText.map(_.take(600))
            )
          )
          val queries = sw.watches.filter(_.kind == Candidates.WatchKind.Query.wire).map(_.subject).distinct
          Found(
            g.title,
            g.notes,
            queries,
            cands,
            sw.skipped ++ Option.when(gone.nonEmpty)("already queued" -> gone.size).toMap,
            stored = open.map(c => c.doi -> c.id).toMap
          )

  /** The stored candidates shown to the ranking: the picks marked queued, the rest seen. */
  private def markSeen(ctx: RunCtx, found: Found, picks: List[Picked]): Int =
    val picked = picks.map(_.candidate.doi).toSet
    val (q, s) = found.stored.toList.partition((doi, _) => picked.contains(doi))
    ctx.write(
      s"candidates of goal ${ctx.run.target}: ${q.size} queued, ${s.size} seen",
      io.circe.Json.obj("queued" -> q.map(_._2).asJson, "seen" -> s.map(_._2).asJson)
    )(
      upstream().markCandidates(q.map(_._2), Candidates.State.Queued.wire).getOrElse(0) +
        upstream().markCandidates(s.map(_._2), Candidates.State.Seen.wire).getOrElse(0)
    )(q.size + s.size)

  /** OpenAlex searched live with the goal's queries (the sweep as it was before the candidate search). */
  private def liveDiscover(ctx: RunCtx, goal: String): Either[String, Found] =
    h.getGoal(goal).left.map(_.message).map { g =>
      val queries = Some(GoalLinkRepo.refs(ds, goal, LinkKind.Query)).filter(_.nonEmpty).getOrElse(List(g.title))
      val found = queries.flatMap { q =>
        upstream().discover(q, cfg.scheduler.discoverPerQuery) match
          case Left(m)  => ctx.warn(s"discover '$q': $m"); Nil
          case Right(r) => r.works.map(q -> _)
      }
      val (cands, skipped) =
        Scheduling.candidates(found, queuedDois().keySet ++ backlogged(goal), cfg.scheduler.sweepCandidates)
      Found(g.title, g.notes, queries, cands, skipped)
    }

  private def rankInput(goal: String, f: Found): String =
    val list = f.candidates
      .map { c =>
        val who = c.authors.headOption.fold("")(a => s"$a${if c.authors.size > 1 then " et al." else ""}, ")
        s"- doi ${c.doi} — ${c.title} ($who${c.year.fold("n.d.")(_.toString)}${c.venue.fold("")(v => s", $v")})" +
          s" · found by \"${c.query}\"" + c.abstractText.fold("")(a => s"\n  abstract: $a")
      }
      .mkString("\n")
    s"Pick the candidates worth acquiring for this goal.\n\n" +
      Prompt.data("goal", s"slug: $goal\ntitle: ${f.title}\nnotes: ${f.notes.getOrElse("none")}") + "\n\n" +
      Prompt.data("candidates", list)

  /** The watcher's auto-ingest: the open-access picks, in the model's order, until `allowed` were ingested, each
    * through the LEGAL chain only. One that has no legal file (or fails) is left for the queue step, and the next
    * open-access pick is tried.
    */
  private def ingest(ctx: RunCtx, goal: String, picks: List[Picked], allowed: Int): List[Ingested] =
    Scheduling
      .ingestPicks(picks, (p: Picked) => p.candidate.isOa, allowed) { p =>
        ingestOne(ctx, goal, p) match
          case Left(m) =>
            ctx.warn(s"ingest: ${p.candidate.doi} not ingested, queued instead — $m")
            None
          case Right(i) => Some(i)
      }
      .map(_._2)

  /** One open-access pick through the legal chain: linked to the goal and recorded as an `ingested` event of the
    * watcher (what its daily cap counts). Left = why not.
    */
  private def ingestOne(ctx: RunCtx, goal: String, p: Picked): Either[String, Ingested] =
    val doi = p.candidate.doi
    ctx.write(
      s"ingest $doi (legal open access only) for goal $goal",
      io.circe.Json.obj("doi" -> doi.asJson, "title" -> p.candidate.title.asJson, "why" -> p.why.asJson)
    )(upstream().ingestLegal(doi).map { r =>
      val _ = GoalLinkRepo
        .link(ds, goal, LinkKind.Source, r.citekey, Some(s"ingested by a watcher sweep: ${p.why}".take(500)))
      ctx.run.managerId.foreach(mid =>
        ManagerRepo.event(
          ds,
          mid,
          ctx.run.managerItem,
          Some(ctx.run.id),
          Managers.Feed.Ingested,
          s"[[${r.citekey}]] ingested (legal open access, ${r.pdf.fold("no file")(_.source)}): ${p.candidate.title} — https://doi.org/$doi"
        )
      )
      Ingested(doi, r.citekey)
    })(Right(Ingested(doi, s"(${AgentsMarkdown.DryRunNote})")))

  /** [[ingest]] for a watcher with a backlog: what failed is kept too (a missing legal file → the acquisition list;
    * anything else → the backlog, tried again on a later day).
    */
  private def ingestOa(ctx: RunCtx, goal: String, picks: List[Picked], allowed: Int): Tried =
    val (ok, ko) = Scheduling.ingestOa(picks, (p: Picked) => p.candidate.isOa, allowed)(ingestOne(ctx, goal, _))
    ko.foreach((p, why) => ctx.warn(s"ingest: ${p.candidate.doi} not ingested — $why"))
    Tried(ok.map(_._2), ko.map((p, why) => Failed(p.candidate.doi, why.take(500), IngestBacklog.isNoLegalCopy(why))))

  /** The open-access picks not ingested now, into the watcher's ingest backlog (deduplicated against the corpus, the
    * acquisition list and every watcher's backlog).
    */
  private def park(ctx: RunCtx, goal: String, picks: List[Picked]): Parked =
    val mid = ctx.run.managerId.getOrElse(Managers.watchId(goal))
    val placed = picks.map { p =>
      val c = p.candidate
      val w = Managers.OaWork(c.doi, c.title, c.year, c.authors, c.venue, List(goal), p.why, Some(ctx.run.id))
      ctx.write(
        s"ingest backlog of $mid: ${c.doi} for goal $goal",
        io.circe.Json.obj("doi" -> c.doi.asJson, "title" -> c.title.asJson, "why" -> p.why.asJson)
      )(backlog.add(mid, goal, w))(IngestBacklog.Placement.New)
    }
    Parked(
      placed.count {
        case IngestBacklog.Placement.New | IngestBacklog.Placement.JoinBacklog(_) => true
        case _                                                                    => false
      },
      placed.count {
        case IngestBacklog.Placement.InCorpus(_) | IngestBacklog.Placement.JoinAcquire(_) => true
        case _                                                                            => false
      }
    )

  private def queue(ctx: RunCtx, goal: String, picks: List[Picked]): Int =
    val queued = queuedDois()
    picks.count { p =>
      queueOnce(ctx, goal, p.candidate.doi, Scheduling.acquireText(p.candidate, goal, p.why), queued) match
        case Left(m)      => ctx.warn(s"queue: ${p.candidate.doi} not queued — $m"); false
        case Right(added) => added
    }

object AcquisitionSweepPipeline:
  /** `stored`: the candidates' ids in the store (#3761), by DOI — none when the live search found them. */
  final case class Found(
      title: String,
      notes: Option[String],
      queries: List[String],
      candidates: List[Candidate],
      skipped: Map[String, Int],
      stored: Map[String, Long] = Map.empty
  ) derives KindlingsCodecAsObject
  final case class Picked(candidate: Candidate, why: String) derives KindlingsCodecAsObject

  /** A pick the watcher's auto-ingest brought in (`citekey` the new source's). */
  final case class Ingested(doi: String, citekey: String) derives KindlingsCodecAsObject

  /** An open-access pick whose ingest failed: why, and whether the legal chain has no file for it. */
  final case class Failed(doi: String, why: String, noCopy: Boolean) derives KindlingsCodecAsObject

  /** What the ingest step of a run with a backlog did. */
  final case class Tried(ingested: List[Ingested] = Nil, failed: List[Failed] = Nil) derives KindlingsCodecAsObject

  /** What the backlog step did: picks waiting in a backlog (new entries and ones joined), and picks the corpus or the
    * acquisition list had already.
    */
  final case class Parked(waiting: Int, known: Int) derives KindlingsCodecAsObject
