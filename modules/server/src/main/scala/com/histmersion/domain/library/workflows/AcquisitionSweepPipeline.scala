package com.histmersion
package domain.library.workflows
import com.histmersion.util.json.RawJson.*
import com.github.plokhotnyuk.jsoniter_scala.core.JsonKeyCodec
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.domain.automation.agents.AgentRepo.StepRecord
import com.histmersion.domain.automation.agents.Checked
import com.histmersion.domain.automation.agents.Halt
import com.histmersion.domain.automation.agents.Prompt
import com.histmersion.domain.automation.agents.RunCtx
import com.histmersion.domain.automation.agents.StepAgent
import com.histmersion.api.automation.Goal.Plans.GoalLink
import com.histmersion.domain.automation.workflows.PipelineDef
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Doi
import com.histmersion.domain.library.search.Search

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.Pipeline
import AcquisitionSweepRules.{Candidate, Ranking}
import com.histmersion.util.json.JsonCodec

import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.StoredIdCodecs.given
import com.histmersion.api.automation.{AgentsMarkdown, Managers, Queue}
import com.histmersion.api.library.{Candidates, Source, WorkRef}
import com.histmersion.domain.automation.agents.AgentsControllers
import com.histmersion.domain.automation.goals.GoalService
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.automation.Run
import com.histmersion.domain.automation.managers.ManagerContext
import io.github.iltotore.iron.constraint.any.Pure

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
    protected val site: LibrarySite,
    agents: AgentsControllers,
    cfg: => AgentsConfig,
    upstream: () => AgentUpstream,
    backlog: OaBacklog
) extends PipelineDef
    with GoalSweep:
  val pipeline: Pipeline = Pipeline.AcquisitionSweep
  protected def goals: GoalService = agents.goals
  import AcquisitionSweepPipeline.*

  def run(ctx: RunCtx): Either[Halt, PipelineDef.Summary] =
    val goal = goalOf(ctx)
    for
      found <- ctx.code[Found](StepRecord.Name("discover"))(discover(ctx, goal))
      line <-
        if found.candidates.isEmpty then
          ctx.code[String](StepRecord.Name("close"))(
            log(
              ctx,
              s"acquisition-sweep goal $goal: ${found.queries.size} query(ies), nothing new " +
                s"(${skippedText(found.skipped)}) (agent run ${ctx.run.id.value.take(8)})"
            )
          )
        else
          val allowed = AcquisitionSweepRules.Allowance(ctx.run.params.autoIngest.fold(0)(_.value))
          for
            picks <- ctx.llm[Ranking, List[Picked]](
              StepRecord.Name("rank"),
              StepAgent.AcquisitionRanker,
              Prompt.UserText(rankInput(goal, found)),
              LibraryAnswers.ranking
            )((r, last) =>
              AcquisitionSweepRules
                .checkRanking(r, found.candidates, AcquisitionSweepRules.LastAttempt(last.value))
                .map((ps, ws) => Checked(ps.map((c, why) => Picked(c, why)), ws.map(d => Checked.Warnings(d.value))))
            )
            // the stored candidates the ranking saw are SEEN, its picks QUEUED: a later sweep shows neither again
            _ <-
              if found.stored.isEmpty then Right(0)
              else ctx.code[Int](StepRecord.Name("seen"))(Right(markSeen(ctx, found, picks)))
            backed = ctx.run.params.oaBacklog.exists(_.value) && ctx.run.managerId.isDefined
            line <-
              if backed then withBacklog(ctx, goal, found, picks, allowed)
              else withoutBacklog(ctx, goal, found, picks, allowed)
          yield line
    yield PipelineDef.Summary(line)

  /** The steps after the ranking as they always were: the open-access picks within the allowance ingested, every other
    * pick queued for acquisition.
    */
  private def withoutBacklog(
      ctx: RunCtx,
      goal: Goal.Slug,
      found: Found,
      picks: List[Picked],
      allowed: AcquisitionSweepRules.Allowance
  ): Either[Halt, String] =
    for
      took <-
        if allowed.value > 0 then
          ctx.code[List[Ingested]](StepRecord.Name("ingest"))(Right(ingest(ctx, goal, picks, allowed)))
        else Right(Nil)
      added <- ctx.code[Int](StepRecord.Name("queue"))(
        Right(queue(ctx, goal, picks.filterNot(p => took.exists(_.doi == p.candidate.doi))))
      )
      line <- ctx.code[String](StepRecord.Name("close"))(
        log(
          ctx,
          s"acquisition-sweep goal $goal: ${found.queries.size} query(ies), " +
            s"${found.candidates.size} new candidate(s), ${picks.size} picked, " +
            (if allowed.value > 0 then
               s"${took.size} ingested (legal open access${took.map(i => s" [[${i.citekey}]]").mkString}), "
             else "") +
            s"$added queued for acquisition (${skippedText(found.skipped)}) (agent run ${ctx.run.id.value.take(8)})"
        )
      )
    yield line

  /** The steps after the ranking for a watcher with an ingest backlog: the open-access picks within today's allowance
    * ingested; the rest of them — and the ones whose ingest failed for another reason than a missing legal file —
    * parked in the backlog; the closed picks and the ones with no legal file queued for acquisition.
    */
  private def withBacklog(
      ctx: RunCtx,
      goal: Goal.Slug,
      found: Found,
      picks: List[Picked],
      allowed: AcquisitionSweepRules.Allowance
  ): Either[Halt, String] =
    for
      tried <-
        if allowed.value > 0 then ctx.code[Tried](StepRecord.Name("ingest"))(Right(ingestOa(ctx, goal, picks, allowed)))
        else Right(Tried())
      noCopy = tried.failed.filter(_.noCopy).map(_.doi).toSet
      waiting = picks.filter(p =>
        p.candidate.isOa.value && !tried.ingested.exists(_.doi == p.candidate.doi) && !noCopy(p.candidate.doi)
      )
      parked <- ctx.code[Parked](StepRecord.Name("backlog"))(Right(park(ctx, goal, waiting)))
      added <- ctx.code[Int](StepRecord.Name("queue"))(
        Right(queue(ctx, goal, picks.filter(p => !p.candidate.isOa.value || noCopy(p.candidate.doi))))
      )
      line <- ctx.code[String](StepRecord.Name("close"))(
        log(
          ctx,
          s"acquisition-sweep goal $goal: ${found.queries.size} query(ies), " +
            s"${found.candidates.size} new candidate(s), ${picks.size} picked, " +
            s"${tried.ingested.size} ingested (legal open access${tried.ingested.map(i => s" [[${i.citekey}]]").mkString}), " +
            s"${parked.waiting} to the ingest backlog (legal open access past today's allowance)" +
            (if parked.known.value > 0 then s", ${parked.known} in the corpus or on the acquisition list already"
             else "") +
            s", $added queued for acquisition (${skippedText(found.skipped)}) (agent run ${ctx.run.id.value.take(8)})"
        )
      )
    yield line

  private def skippedText(m: Map[Problem, AcquisitionSweepRules.SkipCount]): String =
    if m.isEmpty then "none left out"
    else m.toList.sortBy(_._1.sentence).map((k, n) => s"$n ${k.sentence}").mkString(", ")

  /** The DOIs in a watcher's ingest backlog that this goal found already (a pick another goal found joins that entry,
    * so it is offered to this goal's ranking too).
    */
  private def backlogged(goal: Goal.Slug): Set[Doi] =
    val apiGoal = com.histmersion.api.automation.Goal.Slug(goal.value)
    val watcher = Managers.watchId(com.histmersion.api.automation.Goal.Ref(goal.value))
    agents.managerStore
      .backlogOpen()
      .filter(i => i.state.oa.exists(_.goals.contains(apiGoal)) || i.managerId == watcher)
      .map(i => Doi.assume(i.target.value))
      .toSet

  /** The candidates the deterministic search stored for the goal (#3761): its watches read now from their watermarks
    * (no model), then the unseen ones; the live search below only when that search is not connected (a fixture).
    */
  private def discover(ctx: RunCtx, goal: Goal.Slug): Either[String, Found] =
    upstream().sweepCandidates(goal, AgentUpstream.SweepCandidatesDryRun(ctx.dryRun)) match
      case Left(_)   => liveDiscover(ctx, goal)
      case Right(sw) =>
        sw.watches
          .filter(_.lastError.isDefined)
          .foreach(w =>
            ctx.warn(s"candidates: ${w.index} ${w.kind} '${w.subject}' not read — ${w.lastError.fold("")(_.sentence)}")
          )
        for
          g <- agents.host.goal(goal).left.map(_.message.sentence)
          rows <- upstream()
            .candidates(goal, Candidates.State.New.wire, AgentUpstream.Limit(cfg.scheduler.sweepCandidates.value))
            .left
            .map(_.sentence)
        yield
          val queued = queuedDois().keySet ++ backlogged(goal)
          val (gone, open) = rows.partition(c => queued.contains(Doi.assume(c.doi.value)))
          val cands = open.map(c =>
            Candidate(
              Doi.assume(c.doi.value),
              c.title,
              c.year,
              c.authors.take(4),
              c.venue,
              WorkRef.IsOa(c.isOa.value),
              Candidate.Query(c.foundBy.headOption.fold("the candidate search")(_.value)),
              c.abstractText.map(a => WorkRef.AbstractText(a.value.take(600)))
            )
          )
          val queries = sw.watches
            .filter(_.kind == Candidates.WatchKind.Query.wire)
            .map(w => Search.Query(w.subject.value))
            .distinct
          Found(
            g.title,
            g.notes,
            queries,
            cands,
            sw.skipped.map((k, n) =>
              Problem.Unclassified(Legacy.Text(k.value)) -> AcquisitionSweepRules.SkipCount(n.value)
            ) ++
              Option
                .when(gone.nonEmpty)(
                  Problem.Unclassified(Legacy.Text("already queued")) -> AcquisitionSweepRules.SkipCount(gone.size)
                )
                .toMap,
            stored = open.map(c => Doi.assume(c.doi.value) -> c.id).toMap
          )

  /** The stored candidates shown to the ranking: the picks marked queued, the rest seen. */
  private def markSeen(ctx: RunCtx, found: Found, picks: List[Picked]): Int =
    val picked = picks.map(_.candidate.doi).toSet
    val (q, s) = found.stored.toList.partition((doi, _) => picked.contains(doi))
    ctx.write(
      s"candidates of goal ${ctx.run.target}: ${q.size} queued, ${s.size} seen",
      Json.obj("queued" -> q.map(_._2).asJson, "seen" -> s.map(_._2).asJson)
    )(
      upstream().markCandidates(q.map(_._2), Candidates.State.Queued.wire).fold(_ => 0, _.value) +
        upstream().markCandidates(s.map(_._2), Candidates.State.Seen.wire).fold(_ => 0, _.value)
    )(q.size + s.size)

  /** OpenAlex searched live with the goal's queries (the sweep as it was before the candidate search). */
  private def liveDiscover(ctx: RunCtx, goal: Goal.Slug): Either[String, Found] =
    agents.host.goal(goal).left.map(_.message.sentence).map { g =>
      val linked = goals.refs(goal, Goal.LinkKind.Query).map(r => Search.Query(r.value))
      val queries = Some(linked).filter(_.nonEmpty).getOrElse(List(Search.Query(g.title.value)))
      val found = queries.flatMap { q =>
        upstream().discover(q, AgentUpstream.Limit(cfg.scheduler.discoverPerQuery.value)) match
          case Left(m)  => ctx.warn(s"discover '$q': ${m.sentence}"); Nil
          case Right(r) => r.works.map(w => Candidate.Query(q.value) -> w)
      }
      val (cands, skipped) =
        AcquisitionSweepRules.candidates(
          found,
          queuedDois().keySet ++ backlogged(goal),
          AcquisitionSweepRules.Limit(cfg.scheduler.sweepCandidates.value)
        )
      Found(g.title, g.notes, queries, cands, skipped)
    }

  private def rankInput(goal: Goal.Slug, f: Found): String =
    val list = f.candidates
      .map { c =>
        val who = c.authors.headOption.fold("")(a => s"$a${if c.authors.size > 1 then " et al." else ""}, ")
        s"- doi ${c.doi} — ${c.title} ($who${c.year.fold("n.d.")(_.toString)}${c.venue.fold("")(v => s", $v")})" +
          s" · found by \"${c.query}\"" + c.abstractText.fold("")(a => s"\n  abstract: $a")
      }
      .mkString("\n")
    s"Pick the candidates worth acquiring for this goal.\n\n" +
      Prompt.data("goal", s"slug: $goal\ntitle: ${f.title}\nnotes: ${f.notes.fold("none")(_.value)}") + "\n\n" +
      Prompt.data("candidates", list)

  /** The watcher's auto-ingest: the open-access picks, in the model's order, until `allowed` were ingested, each
    * through the LEGAL chain only. One that has no legal file (or fails) is left for the queue step, and the next
    * open-access pick is tried.
    */
  private def ingest(
      ctx: RunCtx,
      goal: Goal.Slug,
      picks: List[Picked],
      allowed: AcquisitionSweepRules.Allowance
  ): List[Ingested] =
    AcquisitionSweepRules
      .ingestPicks(picks, (p: Picked) => p.candidate.isOa.value, allowed) { p =>
        ingestOne(ctx, goal, p) match
          case Left(m) =>
            ctx.warn(s"ingest: ${p.candidate.doi} not ingested, queued instead — ${m.sentence}")
            None
          case Right(i) => Some(i)
      }
      .map(_._2)

  /** One open-access pick through the legal chain: linked to the goal and recorded as an `ingested` event of the
    * watcher (what its daily cap counts). Left = why not.
    */
  private def ingestOne(
      ctx: RunCtx,
      goal: Goal.Slug,
      p: Picked
  ): Either[Problem, Ingested] =
    val doi = p.candidate.doi
    ctx.write(
      s"ingest $doi (legal open access only) for goal $goal",
      Json.obj("doi" -> doi.asJson, "title" -> p.candidate.title.asJson, "why" -> p.why.asJson)
    )(upstream().ingestLegal(doi).map { r =>
      val _ =
        goals.link(
          goal,
          Goal.LinkKind.Source,
          GoalLink.Ref(r.citekey.value),
          Some(GoalLink.Note(s"ingested by a watcher sweep: ${p.why.sentence}".take(500)))
        )
      ctx.run.managerId.foreach(mid =>
        agents.managerStore.event(
          mid,
          ctx.run.managerItem,
          Some(Run.Id.assume(ctx.run.id.value)),
          Managers.ManagerEvent.Kind(Managers.Feed.Ingested),
          Managers.ManagerEvent.Text(
            s"[[${r.citekey}]] ingested (legal open access, ${r.pdf.fold("no file")(_.source.value)}): ${p.candidate.title} — https://doi.org/$doi"
          )
        )
      )
      Ingested(doi, Citekey.assume(r.citekey.value))
    })(Right(Ingested(doi, Citekey.assume(s"(${AgentsMarkdown.DryRunNote})"))))

  /** [[ingest]] for a watcher with a backlog: what failed is kept too (a missing legal file → the acquisition list;
    * anything else → the backlog, tried again on a later day).
    */
  private def ingestOa(
      ctx: RunCtx,
      goal: Goal.Slug,
      picks: List[Picked],
      allowed: AcquisitionSweepRules.Allowance
  ): Tried =
    val (ok, ko) =
      AcquisitionSweepRules.ingestOa(picks, (p: Picked) => p.candidate.isOa.value, allowed)(ingestOne(ctx, goal, _))
    ko.foreach((p, why) => ctx.warn(s"ingest: ${p.candidate.doi} not ingested — ${why.sentence}"))
    Tried(
      ok.map(_._2),
      ko.map((p, why) =>
        Failed(
          p.candidate.doi,
          Problem.Unclassified(Legacy.Text(why.sentence.take(500))),
          IngestBacklog.isNoLegalCopy(why)
        )
      )
    )

  /** The open-access picks not ingested now, into the watcher's ingest backlog (deduplicated against the corpus, the
    * acquisition list and every watcher's backlog).
    */
  private def park(ctx: RunCtx, goal: Goal.Slug, picks: List[Picked]): Parked =
    val mid = ctx.run.managerId.getOrElse(Managers.watchId(com.histmersion.api.automation.Goal.Ref(goal.value)))
    val placed = picks.map { p =>
      val c = p.candidate
      val w = Managers.OaWork(
        com.histmersion.api.library.Doi(c.doi.value),
        c.title,
        c.year,
        c.authors,
        c.venue,
        List(com.histmersion.api.automation.Goal.Slug(goal.value)),
        p.why,
        Some(Managers.OaWork.FoundBy(ctx.run.id.value))
      )
      ctx.write(
        s"ingest backlog of $mid: ${c.doi} for goal $goal",
        Json.obj("doi" -> c.doi.asJson, "title" -> c.title.asJson, "why" -> p.why.asJson)
      )(backlog.add(mid, goal, w))(IngestBacklog.Placement.New)
    }
    Parked(
      Parked.Waiting(placed.count {
        case IngestBacklog.Placement.New | IngestBacklog.Placement.JoinBacklog(_) => true
        case _                                                                    => false
      }),
      Parked.Known(placed.count {
        case IngestBacklog.Placement.InCorpus(_) | IngestBacklog.Placement.JoinAcquire(_) => true
        case _                                                                            => false
      })
    )

  private def queue(ctx: RunCtx, goal: Goal.Slug, picks: List[Picked]): Int =
    val queued = queuedDois()
    picks.count { p =>
      val text = Queue.QueueItem.Text(AcquisitionSweepRules.acquireText(p.candidate, goal, p.why).value)
      queueOnce(ctx, goal, p.candidate.doi, text, queued) match
        case Left(m)      => ctx.warn(s"queue: ${p.candidate.doi} not queued — $m"); false
        case Right(added) => added
    }

object AcquisitionSweepPipeline:
  given JsonKeyCodec[Problem] = JsonCodec.key[Problem](_.sentence, k => Right(Problem.Unclassified(Legacy.Text(k))))
  given JsonKeyCodec[Doi] = JsonCodec.key[Doi](_.value, k => Right((Doi.assume(_))(k)))

  /** What an acquisition sweep picked (its `rank` step's checked answer). */
  def picked(c: ManagerContext, run: Run.Id): List[Picked] =
    c.stepOutput(run, StepRecord.Name("rank"))
      .flatMap(o => RawJson.decode[List[Picked]](o.value).toOption)
      .getOrElse(Nil)

  /** `stored`: the candidates' ids in the store (#3761), by DOI — none when the live search found them. */
  final case class Found(
      title: com.histmersion.api.automation.Goal.Title,
      notes: Option[com.histmersion.api.automation.Goal.Notes],
      queries: List[Search.Query],
      candidates: List[Candidate],
      skipped: Map[Problem, AcquisitionSweepRules.SkipCount],
      stored: Map[Doi, Source.Candidate.Id] = Map.empty
  ) derives JsonCodec

  final case class Picked(candidate: Candidate, why: Problem) derives JsonCodec

  /** A pick the watcher's auto-ingest brought in (`citekey` the new source's). */
  final case class Ingested(doi: Doi, citekey: Citekey) derives JsonCodec

  /** An open-access pick whose ingest failed: why, and whether the legal chain has no file for it. */
  final case class Failed(doi: Doi, why: Problem, noCopy: Boolean) derives JsonCodec

  /** What the ingest step of a run with a backlog did. */
  final case class Tried(ingested: List[Ingested] = Nil, failed: List[Failed] = Nil) derives JsonCodec

  /** What the backlog step did: picks waiting in a backlog (new entries and ones joined), and picks the corpus or the
    * acquisition list had already.
    */
  final case class Parked(waiting: Parked.Waiting, known: Parked.Known) derives JsonCodec

  object Parked:
    type Waiting = Waiting.T
    object Waiting extends Newtype[Int, Pure]
    type Known = Known.T
    object Known extends Newtype[Int, Pure]
