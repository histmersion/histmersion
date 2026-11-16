package com.histmersion
package domain.library.workflows
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.domain.automation.agents.AgentsControllers

import com.histmersion.domain.automation.worklists.{QueueItemId, QueueScope}

import com.histmersion.util.jobs.{BackgroundJob, Job}

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.Write
import com.histmersion.domain.automation.agents.Halt.Stopped
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.library.Acquire.Checks.*
import com.histmersion.api.automation.Queue.QueueAdd
import com.histmersion.domain.library.acquisition.{AcquireCheckRow, AcquireCheckService}
import com.histmersion.domain.AcquireRecheck.{Action, Candidate, Held, JobPlan, Outcome, Settings, Shared, Tally}

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import javax.sql.DataSource
import com.histmersion.api.automation.{AgentsApi, Queue}
import com.histmersion.api.jobs.{JobInfo, JobState}
import com.histmersion.api.Api
import com.histmersion.api.library.Acquire
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.Doi
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.domain.automation.worklists.WorkListControllers
import io.github.iltotore.iron.constraint.any.Pure
import io.scalaland.chimney.dsl.*

/** The acquisition list's LEGAL RE-CHECK (docs/research-process-managers.md "As built: the acquisition list's
  * re-check"), a model-free pass of the managers' tick like the ingest backlog's drain, every
  * `acquire.recheck.pass-minutes` and never while the autopilot drains:
  *
  *   - every OPEN acquisition item that names a DOI the corpus holds with its original is closed (ticked off as
  *     fulfilled by that source; not capped);
  *   - of the others, the DUE ones ([[AcquireRecheck.isDue]]: never checked, checked `every-days` ago, or under another
  *     fetcher generation) are tried — the never-checked first, then the oldest check — through the LEGAL chain only,
  *     whatever `acquire.allow-unauthorised` says ([[AgentUpstream.ingestLegalInto]]), into the metadata-only source
  *     when there is one; a few a pass, `per-day` a day, and under the autopilot's shared daily legal ingest allowance
  *     only what the watchers' backlog leaves, at most `share-percent` of it ([[AcquireRecheck.allowance]]);
  *   - a copy found is converted, and the conversion closes the item (as every import closes the items that name its
  *     DOI); no copy, and the item records when it was checked and what each fetcher answered.
  *
  * One worklog line per pass that did something. [[run]] is the same pass by hand (`histmersion queue acquire
  * recheck`): one item (`id`) and a dry run answer at once; a pass of more than one item, and `all` (every open item
  * naming a DOI, due or not, past every cap), run as a JOB ([[startJob]]) — one at a time, recording each check as it
  * goes, stopped by the drain and resumed by a later tick, like a restart's `interrupted` one; the scheduled passes
  * wait while it runs.
  */
final class AcquireRecheckService(
    ds: DataSource,
    checks: AcquireCheckService,
    rcfg: => RecheckConfig,
    site: LibrarySite,
    upstream: () => AgentUpstream,
    agents: AgentsControllers
):

  import AcquireRecheckService.{By, Line}

  private def settings: Settings =
    val c = rcfg
    Settings(
      Settings.Enabled(c.enabled.value),
      Settings.EveryDays(c.everyDays.value),
      Settings.PerDay(c.perDay.value),
      Settings.PerPass(c.perPass.value),
      Settings.PassMinutes(c.passMinutes.value),
      Settings.SharePercent(c.sharePercent.value),
      Settings.RetryHours(c.retryHours.value)
    )

  /** When the last scheduled pass ran (epoch ms; 0 = not since the start). */
  private val lastPass = AtomicLong(0L)

  /** One pass at a time: the managers' tick and a person's run never try the same item twice. A job holds it for as
    * long as it runs.
    */
  private val passing = ReentrantLock()

  /** One re-check job at a time. */
  private val jobSlot = BackgroundJob.Slot()

  private val jobKind: Job.Kind = Job.Kind.assume(RecheckJob.JobKind)

  /** Is a re-check job running? The scheduled passes wait, and the drain is not drained until it stops. */
  def jobRunning: Boolean = jobSlot.isRunning

  private def apiCk(c: Citekey): com.histmersion.api.library.Citekey =
    com.histmersion.api.library.Citekey(c.value)

  private def apiDoi(d: Doi): com.histmersion.api.library.Doi =
    com.histmersion.api.library.Doi(d.value)

  private def jobIdOf(job: JobInfo): Job.Id = Job.Id.assume(job.id.value)

  private def stopReason(j: JobInfo): String = j.error.fold("stopped")(_.sentence)

  private def resumable(j: JobInfo): Boolean =
    j.status == JobState.Failed.wire && j.error.exists(e => Stopped.from(e).cause.resumable)

  private def countOf(counts: Map[AcquireRecheck.Outcome.Wire, RowCount], o: Outcome): Int =
    counts.get(o.wire).fold(0)(_.value)

  private def error(message: String): Api.Error = Api.Error(Problem.Unclassified(Legacy.Text(message)))

  // ── the facts ──────────────────────────────────────────────────────────────────────────────────

  /** One open item with what the rules need: its DOIs (in the order its text names them), the action, its last check.
    */
  final private case class Facts(item: Queue.QueueItem, action: Action, last: Option[AcquireCheckRow])

  private def facts(only: Option[RecheckRequest.Id]): List[Facts] =
    val items =
      site.queues.list(Queue.Kind.Acquire, QueueScope.Open).toList.filter(i => only.forall(_.value == i.id.value))
    val dois = items.map(i => i.id.value -> orderedDois(i.text)).toMap
    val held = checks.heldByDoi(dois.values.flatMap(_.headOption).toList)
    val last = checks.of(items.map(_.id.transformInto[AcquireCheckRow.Id]))
    items.map(i =>
      Facts(
        i,
        AcquireRecheck.action(dois.getOrElse(i.id.value, Nil), held.get),
        last.get(i.id.transformInto[AcquireCheckRow.Id])
      )
    )

  private def orderedDois(text: Queue.QueueItem.Text): List[Doi] =
    val lower = text.value.toLowerCase
    Scheduling
      .dois(text.value)
      .toList
      .sortBy(d => (lower.indexOf(d.value) match { case -1 => Int.MaxValue; case i => i }, d.value))

  /** How many items were tried today, and how many of them fetched a copy. */
  private def triedToday(now: Instant): (Int, Int) =
    val c = checks.countSince(ManagerPolicy.dayStart(now))
    (
      countOf(c, Outcome.Fetched) + countOf(c, Outcome.NoCopy) + countOf(c, Outcome.Failed),
      countOf(c, Outcome.Fetched)
    )

  /** The shared daily legal ingest allowance as the re-check sees it (None: the autopilot is off or has no cap). */
  private def shared(now: Instant, fetchedToday: Int): Option[Shared] =
    agents.autopilot
      .sharedIngestLeft(now)
      .map(left =>
        Shared(
          Shared.Left(left.value),
          Shared.Cap(agents.autopilot.ingestCap.value),
          Shared.BacklogPending(agents.managerStore.backlogCounts().values.map(_._1.value).sum),
          Shared.FetchedToday(fetchedToday)
        )
      )

  private def candidate(f: Facts): Candidate = Candidate(Candidate.Item(f.item.id.value), f.last.map(_.last))

  private def isDue(c: Candidate, generation: AcquireRecheck.LastCheck.Generation, now: Instant): Boolean =
    val s = settings
    AcquireRecheck.isDue(c.last, generation, s.everyDays, s.retryHours, now)

  /** The items with a DOI to look up, in [[AcquireRecheck.order]]. */
  private def fetchable(fs: List[Facts]): List[Facts] =
    val byId = fs.map(f => f.item.id.value -> f).toMap
    AcquireRecheck
      .order(fs.filter(_.action.isInstanceOf[Action.Fetch]).map(candidate))
      .flatMap(c => byId.get(c.item.value))

  /** The items a pass would try, in order: due, the never-checked first, then the oldest check. */
  private def due(fs: List[Facts], generation: AcquireRecheck.LastCheck.Generation, now: Instant): List[Facts] =
    fetchable(fs).filter(f => isDue(candidate(f), generation, now))

  /** The DOI an item is about (the first its text names). */
  private def doiOf(f: Facts): Option[Doi] = orderedDois(f.item.text).headOption

  // ── the pass ───────────────────────────────────────────────────────────────────────────────────

  /** From the managers' tick: a pass when on, not draining, and `pass-minutes` passed since the last. */
  def tick(now: Instant): List[Line] =
    val s = settings
    if jobSlot.isRunning || agents.drain.draining then Nil
    else
      resumeStopped() match
        case Some(line) => List(line)
        case None       => scheduledPass(s, now)

  private def scheduledPass(s: Settings, now: Instant): List[Line] =
    if !s.enabled.value || !AcquireRecheck.passDue(
        Option(lastPass.get).filter(_ > 0).map(Instant.ofEpochMilli),
        s.passMinutes,
        now
      )
    then Nil
    else
      lastPass.set(now.toEpochMilli)
      pass(RecheckRequest(by = Some(RecheckRequest.By("managers"))), now).toList.flatMap(r =>
        Option.when(r.closed.value + r.fetched.value + r.noCopy.value + r.failed.value > 0)(Line(summary(r)))
      )

  /** `POST /api/queue/acquire/recheck`: a pass now, by hand — on or off, but never while the autopilot drains (a dry
    * run lists what it would do even then). One item (`id`), a dry run, or a pass that would try at most one item
    * answers at once; any other is started as a job ([[startJob]]), and `all` resumes the `all` job a restart or the
    * drain stopped.
    */
  def run(in: RecheckRequest, now: Instant = Instant.now(agents.clock)): Either[Api.Error, RecheckResult] =
    in.id.filter(id =>
      !site.queues
        .get(QueueItemId(id.value))
        .exists(i => i.list == Queue.Kind.Acquire.label && i.status.value == Queue.Open)
    ) match
      case Some(id) =>
        Left(
          Api.Error(
            Problem.Unclassified(Legacy.Text(s"no open acquisition item #$id")),
            Some(Api.Error.Code(Write.CodeNotFound))
          )
        )
      case None if in.limit.exists(_.value < 0) =>
        Left(Write.invalid(Problem.Unclassified(Legacy.Text("--limit is 0 or more"))))
      case None if in.all.value && in.id.isDefined =>
        Left(
          Write.invalid(
            Problem.Unclassified(Legacy.Text("--all re-checks every open item; --id one of them — not both"))
          )
        )
      case None if in.all.value && in.limit.isDefined =>
        Left(
          Write.invalid(
            Problem.Unclassified(
              Legacy.Text("--all re-checks every open item, past every cap; --limit caps a pass — not both")
            )
          )
        )
      case None if !in.dryRun.value && jobSlot.isRunning => Left(busy)
      case None                                          =>
        if !in.dryRun.value && agents.drain.draining then
          Right(
            RecheckResult(
              dryRun = RecheckResult.DryRun(false),
              note = Some(
                RecheckResult.Note(
                  "the autopilot is draining: nothing runs until it is lifted " +
                    "(histmersion agent autopilot undrain); --dry-run lists what a pass would do"
                )
              )
            )
          )
        else if in.dryRun.value || in.id.isDefined then pass(in, now).toRight(passBusy)
        else site.maintenance.guard(WriteGate.OperationName("queue/acquire/recheck"))(byHand(in, now))

  private def busy: Api.Error =
    BackgroundJob.busy(
      BackgroundJob.Work("an acquisition list re-check job"),
      jobSlot.current,
      Some(Api.Error.Code(AgentsApi.CodeConflict))
    )

  private def passBusy: Api.Error =
    if jobSlot.isRunning then busy else error("a re-check pass is running already; try again in a moment")

  /** A pass by hand, not a dry run, not one item: a job over every item (`all`, resuming the `all` job that was
    * stopped) or over the due ones the caps allow — or, when that is at most one, the pass at once.
    */
  private def byHand(in: RecheckRequest, now: Instant): Either[Api.Error, RecheckResult] =
    val by = By(in.by.map(_.value.trim).filter(_.nonEmpty).getOrElse("person"))
    val fs = facts(None)
    val base = RecheckResult(
      dryRun = RecheckResult.DryRun(false),
      RecheckResult.Open(fs.size),
      RecheckResult.WithDoi(fs.count(_.action != Action.NoDoi))
    )
    if in.all.value then
      val stopped = latestJob()
        .filter(resumable)
        .flatMap(j =>
          j.detail.flatMap(Acquire.Checks.RecheckJob.Detail.of).filter(_.all.value).flatMap(planOf).map(j -> _)
        )
      val todo = fetchable(fs)
      stopped match
        case Some((j, plan)) =>
          startJob(plan, by, Some(jobIdOf(j))).map(job =>
            base
              .modify(_.due)
              .setTo(RecheckResult.Due(todo.size))
              .modify(_.allowance)
              .setTo(RecheckResult.Allowance(todo.size))
              .modify(_.job)
              .setTo(Some(job))
              .modify(_.note)
              .setTo(
                Some(RecheckResult.Note(s"resumes job ${j.id} (${stopReason(j)}): the items it checked are skipped"))
              )
          )
        case None =>
          startJob(JobPlan(all = JobPlan.All(true), JobPlan.UpTo(0), now), by, None).map(job =>
            base
              .modify(_.due)
              .setTo(RecheckResult.Due(todo.size))
              .modify(_.allowance)
              .setTo(RecheckResult.Allowance(todo.size))
              .modify(_.job)
              .setTo(Some(job))
          )
    else
      val s = settings
      val gen = upstream().fetcherGeneration
      val dueNow = due(fs, gen, now)
      val (tried, fetchedToday) = triedToday(now)
      val upTo = math.min(
        dueNow.size,
        AcquireRecheck
          .allowance(
            s.modify(_.perPass).setTo(in.limit.fold(s.perPass)(l => Settings.PerPass(l.value))),
            AcquireRecheck.CheckedToday(tried),
            shared(now, fetchedToday)
          )
          .value
      )
      if upTo <= 1 then pass(in, now).toRight(passBusy)
      else
        startJob(JobPlan(all = JobPlan.All(false), JobPlan.UpTo(upTo), now), by, None).map(job =>
          base
            .modify(_.due)
            .setTo(RecheckResult.Due(dueNow.size))
            .modify(_.allowance)
            .setTo(RecheckResult.Allowance(upTo))
            .modify(_.job)
            .setTo(Some(job))
        )

  // ── the job ────────────────────────────────────────────────────────────────────────────────────

  /** The newest re-check job. */
  private def latestJob(): Option[JobInfo] =
    site.jobs.newest(jobKind)

  private def planOf(d: RecheckJob): Option[JobPlan] =
    scala.util
      .Try(Instant.parse(d.since.value))
      .toOption
      .map(JobPlan(JobPlan.All(d.all.value), JobPlan.UpTo(d.upTo.value.toInt), _))

  /** From the managers' tick (not draining, no job running): the newest re-check job, when a restart or the drain
    * stopped it, goes on as a new job over what it had not checked yet.
    */
  private def resumeStopped(): Option[Line] =
    latestJob().filter(resumable).flatMap { j =>
      j.detail.flatMap(Acquire.Checks.RecheckJob.Detail.of).flatMap(d => planOf(d).map(d -> _)).flatMap { (d, plan) =>
        startJob(plan, By(d.by.value), Some(jobIdOf(j))) match
          case Right(job) =>
            Some(Line(s"acquire re-check: job ${j.id} (${stopReason(j)}) resumed as job ${job.id}"))
          case Left(e) =>
            scribe.warn(s"acquire re-check: job ${j.id} could not be resumed: ${e.problems.sentences.mkString("; ")}")
            None
      }
    }

  /** Start `plan` as a `jobs` row (`histmersion jobs <id>` follows it); `resumes` = the stopped job it takes up.
    */
  private def startJob(plan: JobPlan, by: By, resumes: Option[Job.Id]): Either[Api.Error, JobInfo] =
    val detail = RecheckJob(
      RecheckJob.All(plan.all.value),
      RecheckJob.UpTo(plan.upTo.value.toLong),
      RecheckJob.Since(plan.since.toString),
      RecheckJob.By(by.value),
      resumes.map(j => RecheckJob.Resumes(j.value))
    )
    BackgroundJob.start(
      ds,
      jobKind,
      Some(Acquire.Checks.RecheckJob.Detail(Some(detail))),
      jobSlot,
      BackgroundJob.Work("an acquisition list re-check job"),
      Some(Api.Error.Code(AgentsApi.CodeConflict)),
      agents.pins,
      Some(agents.jobStops)
    )((job, progress) => runJob(jobIdOf(job), plan, by, progress))

  /** The job's body: the items the corpus holds with their text closed first, then each item of
    * [[AcquireRecheck.jobPicks]] tried through the LEGAL chain and recorded before the next — an answer that did not
    * come (a rate limit) is a `failed` check and the job goes on. It stops between two items when the autopilot drains
    * (resumable) or a restore starts.
    */
  private def runJob(jobId: Job.Id, plan: JobPlan, by: By, progress: JobInfo.Progress => Unit): BackgroundJob.Outcome =
    passing.lock()
    try
      val up = upstream()
      if !up.connected then
        BackgroundJob.Outcome.failed(
          Problem.Unclassified(Legacy.Text("the acquisition toolset is not connected: nothing tried"))
        )
      else
        val gen = up.fetcherGeneration
        def report(line: AcquireRecheck.ProgressLine): Unit = progress(JobInfo.Progress(line.value))
        progress(JobInfo.Progress("closing the items the corpus holds with their text"))
        val fs = facts(None)
        val closed = fs
          .collect { case f @ Facts(_, Action.Close(ck), _) => f -> ck }
          .flatMap((f, ck) => close(f, ck, gen, Instant.now(agents.clock), Some(jobId)))
        val before = checks.countSince(plan.since)
        def n(o: Outcome) = countOf(before, o)
        val triedByJob = n(Outcome.Fetched) + n(Outcome.NoCopy) + n(Outcome.Failed)
        val byId = fs.map(f => f.item.id.value -> f).toMap
        val now = Instant.now(agents.clock)
        val picks = AcquireRecheck
          .jobPicks(plan, fetchable(fs).map(candidate), Tally.Tried(triedByJob), c => isDue(c, gen, now))
          .flatMap(c => byId.get(c.item.value))
        val start = Tally(
          Tally.Total(triedByJob + picks.size),
          Tally.Tried(triedByJob),
          Tally.Closed(n(Outcome.Closed)),
          Tally.Fetched(n(Outcome.Fetched)),
          Tally.NoCopy(n(Outcome.NoCopy)),
          Tally.Failed(n(Outcome.Failed))
        )
        report(AcquireRecheck.progressLine(start))
        @annotation.tailrec
        def loop(left: List[Facts], t: Tally, rows: List[RecheckRow]): (Tally, List[RecheckRow], Option[String]) =
          left match
            case Nil                          => (t, rows, None)
            case _ if agents.drain.draining   => (t, rows, Some(Stopped.DrainingText))
            case _ if site.maintenance.active => (t, rows, Some("a restore started; stopped"))
            case f :: rest                    =>
              if Thread.interrupted() then throw InterruptedException()
              if !site.queues.get(QueueItemId(f.item.id.value)).exists(_.status.value == Queue.Open) then
                loop(rest, t.modify(_.total).using(total => Tally.Total(total.value - 1)), rows)
              else
                val tried = attempt(f, gen, Instant.now(agents.clock))
                val next = t.add(tried.outcome, AcquireRecheck.Detail(tried.detail))
                report(AcquireRecheck.progressLine(next))
                loop(rest, next, tried.row :: rows)
        val (end, rows, stop) = loop(picks, start, Nil)
        val line = AcquireRecheck.progressLine(end)
        val r = RecheckResult(
          dryRun = RecheckResult.DryRun(false),
          due = RecheckResult.Due(end.total.value - start.tried.value),
          closed = RecheckResult.Closed(closed.size),
          fetched = RecheckResult.Fetched(rows.count(_.action.value == Fetched)),
          noCopy = RecheckResult.NoCopy(rows.count(_.action.value == NoCopy)),
          failed = RecheckResult.Failed(rows.count(_.action.value == Failed)),
          rows = closed ++ rows.reverse.filter(_.action.value == Fetched)
        )
        if r.closed.value + r.fetched.value + r.noCopy.value + r.failed.value > 0 || stop.isDefined then
          val _ = site.workLists.add(
            WorkListControllers.ListName("log"),
            QueueAdd(
              Queue.QueueItem.Text(
                (s"${summary(r)} (by $by, job $jobId" +
                  stop.fold(")")(why => s"; $why)")).take(1000)
              ),
              job = Some(JobInfo.Id(jobId.value))
            )
          )
        stop.fold(BackgroundJob.Outcome.done(BackgroundJob.Outcome.Summary(line.value)))(why =>
          BackgroundJob.Outcome(
            Some(BackgroundJob.Outcome.Summary(line.value)),
            Some(Problem.Unclassified(Legacy.Text(why)))
          )
        )
    finally passing.unlock()

  /** One pass at once (None: another is running). */
  private[histmersion] def pass(in: RecheckRequest, now: Instant): Option[RecheckResult] =
    if !passing.tryLock() then None
    else
      try Some(passNow(in, now))
      finally passing.unlock()

  private def passNow(in: RecheckRequest, now: Instant): RecheckResult =
    val s = settings
    val up = upstream()
    val gen = up.fetcherGeneration
    val fs = facts(in.id)
    val closes = fs.collect { case f @ Facts(_, Action.Close(ck), _) => f -> ck }
    val dueNow = if in.id.isDefined || in.all.value then fetchable(fs) else due(fs, gen, now)
    val (tried, fetchedToday) = triedToday(now)
    val perPass = in.limit.fold(s.perPass.value)(_.value)
    val room =
      if in.id.isDefined then 1
      else if in.all.value then dueNow.size
      else
        AcquireRecheck
          .allowance(
            s.modify(_.perPass).setTo(Settings.PerPass(perPass)),
            AcquireRecheck.CheckedToday(tried),
            shared(now, fetchedToday)
          )
          .value
    val picks = dueNow.take(room)
    val withDoi = fs.count(_.action != Action.NoDoi)
    val noDoi = fs
      .filter(_.action == Action.NoDoi)
      .map(f =>
        RecheckRow(
          RecheckRow.Item(f.item.id.value),
          None,
          RecheckRow.Action(NoDoi),
          RecheckRow.Detail("no DOI in its text")
        )
      )
    val base = RecheckResult(
      RecheckResult.DryRun(in.dryRun.value),
      RecheckResult.Open(fs.size),
      RecheckResult.WithDoi(withDoi),
      RecheckResult.Due(dueNow.size),
      RecheckResult.Allowance(room)
    )
    val capNote = Option.when(picks.size < dueNow.size && in.id.isEmpty && !in.all.value)(
      RecheckResult.Note(
        s"${dueNow.size - picks.size} due item(s) wait for the next pass: at most $perPass a pass, " +
          s"${s.perDay} a day ($tried tried today)" +
          (if shared(now, fetchedToday).isDefined then
             s", and ${s.sharePercent} % of the shared legal ingest allowance after the watchers' backlog"
           else "")
      )
    )
    if in.dryRun.value then
      val rows = closes.map((f, ck) =>
        RecheckRow(
          RecheckRow.Item(f.item.id.value),
          doiOf(f).map(apiDoi),
          RecheckRow.Action(WouldClose),
          RecheckRow.Detail("in the corpus with its original"),
          Some(apiCk(ck))
        )
      ) ++
        dueNow.zipWithIndex.map { (f, i) =>
          val into = f.action match { case Action.Fetch(_, into) => into; case _ => None }
          RecheckRow(
            RecheckRow.Item(f.item.id.value),
            doiOf(f).map(apiDoi),
            RecheckRow.Action(if i < picks.size then WouldTry else Due),
            RecheckRow.Detail(f.last.fold("never checked")(l => s"last ${l.reason}")),
            into.map(apiCk)
          )
        } ++ (if in.id.isDefined then noDoi else Nil)
      base.modify(_.rows).setTo(rows).modify(_.note).setTo(capNote)
    else if !up.connected && picks.nonEmpty then
      val closed = closes.flatMap((f, ck) => close(f, ck, gen, now))
      base
        .modify(_.closed)
        .setTo(RecheckResult.Closed(closed.size))
        .modify(_.rows)
        .setTo(closed)
        .modify(_.note)
        .setTo(Some(RecheckResult.Note("the acquisition toolset is not connected: nothing tried")))
    else
      val closed = closes.flatMap((f, ck) => close(f, ck, gen, now))
      val outs = picks.map(f => attempt(f, gen, now).row)
      val r = base
        .modify(_.closed)
        .setTo(RecheckResult.Closed(closed.size))
        .modify(_.fetched)
        .setTo(RecheckResult.Fetched(outs.count(_.action.value == Fetched)))
        .modify(_.noCopy)
        .setTo(RecheckResult.NoCopy(outs.count(_.action.value == NoCopy)))
        .modify(_.failed)
        .setTo(RecheckResult.Failed(outs.count(_.action.value == Failed)))
        .modify(_.rows)
        .setTo(closed ++ outs ++ (if in.id.isDefined then noDoi else Nil))
        .modify(_.note)
        .setTo(capNote)
      if r.closed.value + r.fetched.value + r.noCopy.value + r.failed.value > 0 then
        val by = in.by.map(_.value.trim).filter(_.nonEmpty).getOrElse("person")
        val _ = site.workLists.add(
          WorkListControllers.ListName("log"),
          QueueAdd(Queue.QueueItem.Text(s"${summary(r)} (by $by)".take(1000)))
        )
      r

  /** The item ticked off as fulfilled by `citekey` (the one write every import makes), and its check. */
  private def close(
      f: Facts,
      citekey: Citekey,
      generation: AcquireRecheck.LastCheck.Generation,
      now: Instant,
      job: Option[Job.Id] = None
  ): Option[RecheckRow] =
    site.workLists.patch(
      QueueItemId(f.item.id.value),
      Queue.fulfil(f.item, apiCk(citekey), job.map(j => Queue.QueueItem.ClosedBy(j.value)))
    ) match
      case Left(e) =>
        scribe.warn(s"acquire re-check: item #${f.item.id} could not be closed: ${e.problems.sentences.mkString("; ")}")
        None
      case Right(_) =>
        checks.record(
          f.item.id.transformInto[AcquireCheckRow.Id],
          now,
          generation,
          Outcome.Closed,
          AcquireRecheck.reason(now, Outcome.Closed, AcquireRecheck.Detail(citekey.value))
        )
        Some(
          RecheckRow(
            RecheckRow.Item(f.item.id.value),
            doiOf(f).map(apiDoi),
            RecheckRow.Action(Closed),
            RecheckRow.Detail("in the corpus with its original"),
            Some(apiCk(citekey))
          )
        )

  /** One item tried: its row, what it found, and the fetcher's or the chain's own words. */
  final private case class Tried(row: RecheckRow, outcome: Outcome, detail: String)

  /** One item through the legal chain, recorded. */
  private def attempt(f: Facts, generation: AcquireRecheck.LastCheck.Generation, now: Instant): Tried =
    val (doi, into) = f.action match
      case Action.Fetch(d, i) => (Some(d), i)
      case _                  => (None, None)
    val (outcome, detail, ck, noCopy) = doi match
      case None    => (Outcome.Failed, "no DOI in its text", None, None)
      case Some(d) =>
        try
          upstream().ingestLegalInto(d, into) match
            case Right(r) => (Outcome.Fetched, r.pdf.fold("no file")(_.source.value), Some(r.citekey), None)
            case Left(m)  =>
              AcquireRecheck.failure(m) match
                case Outcome.NoCopy => (Outcome.NoCopy, m.sentence, into.map(apiCk), Some(m))
                case outcome        => (outcome, m.sentence, into.map(apiCk), None)
        catch
          case e: InterruptedException => throw e
          case e: Exception            =>
            (Outcome.Failed, s"${e.getClass.getSimpleName}: ${e.getMessage}", into.map(apiCk), None)
    val why =
      noCopy.fold(AcquireRecheck.reason(now, outcome, AcquireRecheck.Detail(detail)))(AcquireRecheck.noCopy(now, _))
    checks.record(f.item.id.transformInto[AcquireCheckRow.Id], now, generation, outcome, why)
    Tried(
      RecheckRow(
        RecheckRow.Item(f.item.id.value),
        doi.map(apiDoi),
        RecheckRow.Action(outcome.wire.value),
        RecheckRow.Detail(why.said),
        ck
      ),
      outcome,
      detail
    )

  private def summary(r: RecheckResult): String =
    def ids(a: String) =
      r.rows.filter(_.action.value == a).map(row => s"#${row.item}${row.citekey.fold("")(c => s" [[$c]]")}")
    val parts = List(
      Option.when(r.closed.value > 0)(s"${r.closed} closed (in the corpus already: ${ids(Closed).mkString(", ")})"),
      Option.when(r.fetched.value > 0)(
        s"${r.fetched} legal copy(ies) found (${ids(Fetched).mkString(", ")}; the conversion closes the item)"
      ),
      Option.when(r.noCopy.value > 0)(s"${r.noCopy} still without a legal copy"),
      Option.when(r.failed.value > 0)(s"${r.failed} without an answer (tried again when due)")
    ).flatten
    s"acquire re-check: ${parts.mkString(", ")}; ${math.max(0, r.due.value - r.fetched.value - r.noCopy.value - r.failed.value)} due item(s) left"

  // ── the status ─────────────────────────────────────────────────────────────────────────────────

  /** The status line's facts (`agent autopilot status`, `about`); readable with agents off. */
  def status(now: Instant = Instant.now(agents.clock)): RecheckStatus =
    val s = settings
    if !s.enabled.value then
      RecheckStatus(enabled = RecheckStatus.Enabled(false), job = jobLine().map(RecheckStatus.Job(_)))
    else
      val gen = upstream().fetcherGeneration
      val fs = facts(None)
      val dueNow = due(fs, gen, now)
      val (tried, fetchedToday) = triedToday(now)
      val week = checks.countSince(now.minusSeconds(7L * 86400L))
      val room = AcquireRecheck.allowance(s, AcquireRecheck.CheckedToday(tried), shared(now, fetchedToday)).value
      val next: Option[String] =
        if dueNow.isEmpty then
          AcquireRecheck
            .nextDue(
              fs.filter(_.action.isInstanceOf[Action.Fetch]).flatMap(_.last.map(_.last)),
              gen,
              s.everyDays,
              s.retryHours
            )
            .map(t => s"when the next item falls due, ${t.truncatedTo(java.time.temporal.ChronoUnit.MINUTES)}")
        else if room <= 0 then Some(s"tomorrow (UTC): today's tries or the re-check's share of the allowance are spent")
        else
          Some(
            Option(lastPass.get)
              .filter(_ > 0)
              .map(Instant.ofEpochMilli(_).plusSeconds(math.max(1, s.passMinutes.value).toLong * 60L))
              .filter(_.isAfter(now))
              .fold("at the next managers' tick")(t => s"at ${t.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)}")
          )
      val note =
        if agents.drain.draining then Some("held while the autopilot drains")
        else if !agents.managers.running then Some("it runs on the managers' tick, which is not running on this server")
        else None
      RecheckStatus(
        enabled = RecheckStatus.Enabled(true),
        RecheckStatus.WithDoi(fs.count(_.action != Action.NoDoi)),
        RecheckStatus.Due(dueNow.size),
        RecheckStatus.TriedToday(tried),
        RecheckStatus.PerDay(s.perDay.value),
        RecheckStatus.FetchedToday(fetchedToday),
        RecheckStatus.ClosedWeek(countOf(week, Outcome.Closed)),
        RecheckStatus.FetchedWeek(countOf(week, Outcome.Fetched)),
        RecheckStatus.EveryDays(s.everyDays.value),
        next.map(RecheckStatus.NextPass(_)),
        note.map(RecheckStatus.Note(_)),
        jobLine().map(RecheckStatus.Job(_))
      )

  /** The re-check job as the status line names it: the one running and its progress, or the newest when a restart or
    * the drain stopped it (a tick resumes it).
    */
  private def jobLine(): Option[String] =
    latestJob().flatMap { j =>
      if !JobState.parse(j.status).exists(_.isTerminal) then
        Some(s"job ${j.id} running: ${j.progress.fold("starting")(_.value)}")
      else if resumable(j) then
        Some(
          s"job ${j.id} stopped (${stopReason(j)}" + j.progress.fold("")(p => s" at ${p.value}") + "): " +
            (if agents.drain.draining then "it resumes once the drain is lifted"
             else if agents.managers.running then "it resumes at the next managers' tick"
             else if j.detail.flatMap(Acquire.Checks.RecheckJob.Detail.of).exists(_.all.value) then
               "`histmersion queue acquire recheck --all` resumes it"
             else "the managers' tick resumes it, which is not running on this server")
        )
      else None
    }

  /** What the re-check fetched today: what the shared legal ingest allowance counts besides the watchers'. */
  def fetchedToday(now: Instant): RowCount = RowCount(triedToday(now)._2)

object AcquireRecheckService:
  type By = By.T
  object By extends Newtype[String, Pure]

  /** One worklog line a pass wrote. */
  type Line = Line.T
  object Line extends Newtype[String, Pure]
