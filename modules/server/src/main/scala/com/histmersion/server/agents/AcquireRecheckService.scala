package com.histmersion.server.agents

import com.histmersion.util.jobs.BackgroundJob

import hearth.kindlings.optics.syntax.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.AcquireChecks.*
import com.histmersion.api.Queue.QueueAdd
import com.histmersion.db.{AcquireCheckRepo, ManagerRepo, QueueRepo}
import com.histmersion.util.jobs.JobRepo
import com.histmersion.domain.AcquireRecheck.{Action, Candidate, Held, JobPlan, Outcome, Settings, Shared, Tally}

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import javax.sql.DataSource

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
    rcfg: => RecheckConfig,
    h: com.histmersion.server.Handlers,
    upstream: () => AgentUpstream,
    agents: AgentsHandlers
):

  private def settings: Settings =
    val c = rcfg
    Settings(
      c.enabled,
      c.everyDays.value,
      c.perDay.value,
      c.perPass.value,
      c.passMinutes.value,
      c.sharePercent.value,
      c.retryHours.value
    )

  /** When the last scheduled pass ran (epoch ms; 0 = not since the start). */
  private val lastPass = AtomicLong(0L)

  /** One pass at a time: the managers' tick and a person's run never try the same item twice. A job holds it for as
    * long as it runs.
    */
  private val passing = ReentrantLock()

  /** One re-check job at a time. */
  private val jobSlot = BackgroundJob.Slot()

  /** Is a re-check job running? The scheduled passes wait, and the drain is not drained until it stops. */
  def jobRunning: Boolean = jobSlot.isRunning

  // ── the facts ──────────────────────────────────────────────────────────────────────────────────

  /** One open item with what the rules need: its DOIs (in the order its text names them), the action, its last check.
    */
  final private case class Facts(item: Queue.QueueItem, action: Action, last: Option[AcquireCheckRepo.Row])

  private def facts(only: Option[Int]): List[Facts] =
    val items = QueueRepo.list(ds, Queue.Kind.Acquire, all = false).toList.filter(i => only.forall(_ == i.id))
    val dois = items.map(i => i.id -> orderedDois(i.text)).toMap
    val held = AcquireCheckRepo.heldByDoi(ds, dois.values.flatMap(_.headOption).toList)
    val last = AcquireCheckRepo.of(ds, items.map(_.id))
    items.map(i => Facts(i, AcquireRecheck.action(dois.getOrElse(i.id, Nil), held.get), last.get(i.id)))

  private def orderedDois(text: String): List[String] =
    val lower = text.toLowerCase
    Scheduling.dois(text).toList.sortBy(d => (lower.indexOf(d) match { case -1 => Int.MaxValue; case i => i }, d))

  private def triedToday(now: Instant): (Int, Int) =
    val c = AcquireCheckRepo.countSince(ds, ManagerPolicy.dayStart(now))
    (
      c.getOrElse(Outcome.Fetched.wire, 0) + c.getOrElse(Outcome.NoCopy.wire, 0) + c.getOrElse(Outcome.Failed.wire, 0),
      c.getOrElse(Outcome.Fetched.wire, 0)
    )

  /** The shared daily legal ingest allowance as the re-check sees it (None: the autopilot is off or has no cap). */
  private def shared(now: Instant, fetchedToday: Int): Option[Shared] =
    agents.autopilot
      .sharedIngestLeft(now)
      .map(left =>
        Shared(left, agents.autopilot.ingestCap, ManagerRepo.backlogCounts(ds).values.map(_._1).sum, fetchedToday)
      )

  private def candidate(f: Facts): Candidate = Candidate(f.item.id, f.last.map(_.last))

  private def isDue(c: Candidate, generation: String, now: Instant): Boolean =
    val s = settings
    AcquireRecheck.isDue(c.last, generation, s.everyDays, s.retryHours, now)

  /** The items with a DOI to look up, in [[AcquireRecheck.order]]. */
  private def fetchable(fs: List[Facts]): List[Facts] =
    val byId = fs.map(f => f.item.id -> f).toMap
    AcquireRecheck.order(fs.filter(_.action.isInstanceOf[Action.Fetch]).map(candidate)).flatMap(c => byId.get(c.item))

  /** The items a pass would try, in order: due, the never-checked first, then the oldest check. */
  private def due(fs: List[Facts], generation: String, now: Instant): List[Facts] =
    fetchable(fs).filter(f => isDue(candidate(f), generation, now))

  /** The DOI an item is about (the first its text names). */
  private def doiOf(f: Facts): Option[String] = orderedDois(f.item.text).headOption

  // ── the pass ───────────────────────────────────────────────────────────────────────────────────

  /** From the managers' tick: a pass when on, not draining, and `pass-minutes` passed since the last. */
  def tick(now: Instant): List[String] =
    val s = settings
    if jobSlot.isRunning || agents.drain.draining then Nil
    else
      resumeStopped() match
        case Some(line) => List(line)
        case None       => scheduledPass(s, now)

  private def scheduledPass(s: Settings, now: Instant): List[String] =
    if !s.enabled || !AcquireRecheck.passDue(
        Option(lastPass.get).filter(_ > 0).map(Instant.ofEpochMilli),
        s.passMinutes,
        now
      )
    then Nil
    else
      lastPass.set(now.toEpochMilli)
      pass(RecheckRequest(by = Some("managers")), now).toList.flatMap(r =>
        Option.when(r.closed + r.fetched + r.noCopy + r.failed > 0)(summary(r))
      )

  /** `POST /api/queue/acquire/recheck`: a pass now, by hand — on or off, but never while the autopilot drains (a dry
    * run lists what it would do even then). One item (`id`), a dry run, or a pass that would try at most one item
    * answers at once; any other is started as a job ([[startJob]]), and `all` resumes the `all` job a restart or the
    * drain stopped.
    */
  def run(in: RecheckRequest, now: Instant = Instant.now()): Either[ApiError, RecheckResult] =
    in.id.filter(id =>
      !QueueRepo.get(ds, id).exists(i => i.list == Queue.Kind.Acquire.label && i.status == Queue.Open)
    ) match
      case Some(id)                       => Left(ApiError(s"no open acquisition item #$id", Some(Write.CodeNotFound)))
      case None if in.limit.exists(_ < 0) => Left(ApiError("--limit is 0 or more", Some(Write.CodeInvalid)))
      case None if in.all && in.id.isDefined =>
        Left(ApiError("--all re-checks every open item; --id one of them — not both", Some(Write.CodeInvalid)))
      case None if in.all && in.limit.isDefined =>
        Left(
          ApiError(
            "--all re-checks every open item, past every cap; --limit caps a pass — not both",
            Some(Write.CodeInvalid)
          )
        )
      case None if !in.dryRun && jobSlot.isRunning => Left(busy)
      case None                                    =>
        if !in.dryRun && agents.drain.draining then
          Right(
            RecheckResult(
              dryRun = false,
              note = Some(
                "the autopilot is draining: nothing runs until it is lifted " +
                  "(histmersion agent autopilot undrain); --dry-run lists what a pass would do"
              )
            )
          )
        else if in.dryRun || in.id.isDefined then pass(in, now).toRight(passBusy)
        else h.maintenance.guard("queue/acquire/recheck")(byHand(in, now))

  private def busy: ApiError =
    BackgroundJob.busy("an acquisition list re-check job", jobSlot.current, Some(AgentsApi.CodeConflict))

  private def passBusy: ApiError =
    if jobSlot.isRunning then busy else ApiError("a re-check pass is running already; try again in a moment")

  /** A pass by hand, not a dry run, not one item: a job over every item (`all`, resuming the `all` job that was
    * stopped) or over the due ones the caps allow — or, when that is at most one, the pass at once.
    */
  private def byHand(in: RecheckRequest, now: Instant): Either[ApiError, RecheckResult] =
    val by = in.by.map(_.trim).filter(_.nonEmpty).getOrElse("person")
    val fs = facts(None)
    val base = RecheckResult(dryRun = false, fs.size, fs.count(_.action != Action.NoDoi))
    if in.all then
      val stopped = latestJob()
        .filter(j => AcquireRecheck.resumable(j.status, j.error))
        .flatMap(j => j.detail.flatMap(_.recheck).filter(_.all).flatMap(planOf).map(j -> _))
      val todo = fetchable(fs)
      stopped match
        case Some((j, plan)) =>
          startJob(plan, by, Some(j.id)).map(job =>
            base.copy(
              due = todo.size,
              allowance = todo.size,
              job = Some(job),
              note = Some(s"resumes job ${j.id} (${j.error.getOrElse("stopped")}): the items it checked are skipped")
            )
          )
        case None =>
          startJob(JobPlan(all = true, 0, now), by, None).map(job =>
            base.copy(due = todo.size, allowance = todo.size, job = Some(job))
          )
    else
      val s = settings
      val gen = upstream().fetcherGeneration
      val dueNow = due(fs, gen, now)
      val (tried, fetchedToday) = triedToday(now)
      val upTo = math.min(
        dueNow.size,
        AcquireRecheck.allowance(s.copy(perPass = in.limit.getOrElse(s.perPass)), tried, shared(now, fetchedToday))
      )
      if upTo <= 1 then pass(in, now).toRight(passBusy)
      else
        startJob(JobPlan(all = false, upTo, now), by, None).map(job =>
          base.copy(due = dueNow.size, allowance = upTo, job = Some(job))
        )

  // ── the job ────────────────────────────────────────────────────────────────────────────────────

  /** The newest re-check job. */
  private def latestJob(): Option[JobInfo] =
    JobRepo
      .page(ds, JobRepo.JobQuery(kinds = List(RecheckJob.JobKind)), 1, None, None)
      .toOption
      .flatMap(_._1.headOption)

  private def planOf(d: RecheckJob): Option[JobPlan] =
    scala.util.Try(Instant.parse(d.since)).toOption.map(JobPlan(d.all, d.upTo, _))

  /** From the managers' tick (not draining, no job running): the newest re-check job, when a restart or the drain
    * stopped it, goes on as a new job over what it had not checked yet.
    */
  private def resumeStopped(): Option[String] =
    latestJob().filter(j => AcquireRecheck.resumable(j.status, j.error)).flatMap { j =>
      j.detail.flatMap(_.recheck).flatMap(d => planOf(d).map(d -> _)).flatMap { (d, plan) =>
        startJob(plan, d.by, Some(j.id)) match
          case Right(job) =>
            Some(s"acquire re-check: job ${j.id} (${j.error.getOrElse("stopped")}) resumed as job ${job.id}")
          case Left(e) =>
            scribe.warn(s"acquire re-check: job ${j.id} could not be resumed: ${e.message}")
            None
      }
    }

  /** Start `plan` as a `jobs` row (`histmersion jobs <id>` follows it); `resumes` = the stopped job it takes up.
    */
  private def startJob(plan: JobPlan, by: String, resumes: Option[String]): Either[ApiError, JobInfo] =
    val detail = RecheckJob(plan.all, plan.upTo, plan.since.toString, by, resumes)
    BackgroundJob.start(
      ds,
      RecheckJob.JobKind,
      Some(JobDetail(recheck = Some(detail))),
      jobSlot,
      "an acquisition list re-check job",
      Some(AgentsApi.CodeConflict),
      agents.pins,
      Some(agents.jobStops)
    )((job, progress) => runJob(job.id, plan, by, progress))

  /** The job's body: the items the corpus holds with their text closed first, then each item of
    * [[AcquireRecheck.jobPicks]] tried through the LEGAL chain and recorded before the next — an answer that did not
    * come (a rate limit) is a `failed` check and the job goes on. It stops between two items when the autopilot drains
    * (resumable) or a restore starts.
    */
  private def runJob(jobId: String, plan: JobPlan, by: String, progress: String => Unit): BackgroundJob.Outcome =
    passing.lock()
    try
      val up = upstream()
      if !up.connected then BackgroundJob.Outcome.failed("the acquisition toolset is not connected: nothing tried")
      else
        val gen = up.fetcherGeneration
        progress("closing the items the corpus holds with their text")
        val fs = facts(None)
        val closed = fs
          .collect { case f @ Facts(_, Action.Close(ck), _) => f -> ck }
          .flatMap((f, ck) => close(f, ck, gen, Instant.now(), Some(jobId)))
        val before = AcquireCheckRepo.countSince(ds, plan.since)
        def n(o: Outcome) = before.getOrElse(o.wire, 0)
        val triedByJob = n(Outcome.Fetched) + n(Outcome.NoCopy) + n(Outcome.Failed)
        val byId = fs.map(f => f.item.id -> f).toMap
        val now = Instant.now()
        val picks = AcquireRecheck
          .jobPicks(plan, fetchable(fs).map(candidate), triedByJob, c => isDue(c, gen, now))
          .flatMap(c => byId.get(c.item))
        val start = Tally(
          triedByJob + picks.size,
          triedByJob,
          n(Outcome.Closed),
          n(Outcome.Fetched),
          n(Outcome.NoCopy),
          n(Outcome.Failed)
        )
        progress(AcquireRecheck.progressLine(start))
        @annotation.tailrec
        def loop(left: List[Facts], t: Tally, rows: List[RecheckRow]): (Tally, List[RecheckRow], Option[String]) =
          left match
            case Nil                        => (t, rows, None)
            case _ if agents.drain.draining => (t, rows, Some(AcquireRecheck.StoppedDraining))
            case _ if h.maintenance.active  => (t, rows, Some("a restore started; stopped"))
            case f :: rest                  =>
              if Thread.interrupted() then throw InterruptedException()
              if !QueueRepo.get(ds, f.item.id).exists(_.status == Queue.Open) then
                loop(rest, t.modify(_.total).using(_ - 1), rows)
              else
                val tried = attempt(f, gen, Instant.now())
                val next = t.add(tried.outcome, tried.detail)
                progress(AcquireRecheck.progressLine(next))
                loop(rest, next, tried.row :: rows)
        val (end, rows, stop) = loop(picks, start, Nil)
        val line = AcquireRecheck.progressLine(end)
        val r = RecheckResult(
          dryRun = false,
          due = end.total - start.tried,
          closed = closed.size,
          fetched = rows.count(_.action == Fetched),
          noCopy = rows.count(_.action == NoCopy),
          failed = rows.count(_.action == Failed),
          rows = closed ++ rows.reverse.filter(_.action == Fetched)
        )
        if r.closed + r.fetched + r.noCopy + r.failed > 0 || stop.isDefined then
          val _ = h.ops.queueAdd(
            "log",
            QueueAdd(
              (s"${summary(r)} (by $by, job $jobId" +
                stop.fold(")")(why => s"; $why)")).take(1000),
              job = Some(jobId)
            )
          )
        stop.fold(BackgroundJob.Outcome.done(line))(why => BackgroundJob.Outcome(Some(line), Some(why)))
    finally passing.unlock()

  /** One pass at once (None: another is running). */
  private[agents] def pass(in: RecheckRequest, now: Instant): Option[RecheckResult] =
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
    val dueNow = if in.id.isDefined || in.all then fetchable(fs) else due(fs, gen, now)
    val (tried, fetchedToday) = triedToday(now)
    val room =
      if in.id.isDefined then 1
      else if in.all then dueNow.size
      else AcquireRecheck.allowance(s.copy(perPass = in.limit.getOrElse(s.perPass)), tried, shared(now, fetchedToday))
    val picks = dueNow.take(room)
    val withDoi = fs.count(_.action != Action.NoDoi)
    val noDoi = fs.filter(_.action == Action.NoDoi).map(f => RecheckRow(f.item.id, None, NoDoi, "no DOI in its text"))
    val base = RecheckResult(in.dryRun, fs.size, withDoi, dueNow.size, room)
    val capNote = Option.when(picks.size < dueNow.size && in.id.isEmpty && !in.all)(
      s"${dueNow.size - picks.size} due item(s) wait for the next pass: at most ${in.limit.getOrElse(s.perPass)} a pass, " +
        s"${s.perDay} a day ($tried tried today)" +
        (if shared(now, fetchedToday).isDefined then
           s", and ${s.sharePercent} % of the shared legal ingest allowance after the watchers' backlog"
         else "")
    )
    if in.dryRun then
      val rows = closes.map((f, ck) =>
        RecheckRow(f.item.id, doiOf(f), WouldClose, "in the corpus with its original", Some(ck))
      ) ++
        dueNow.zipWithIndex.map { (f, i) =>
          val into = f.action match { case Action.Fetch(_, into) => into; case _ => None }
          RecheckRow(
            f.item.id,
            doiOf(f),
            if i < picks.size then WouldTry else Due,
            f.last.fold("never checked")(l => s"last ${l.reason}"),
            into
          )
        } ++ (if in.id.isDefined then noDoi else Nil)
      base.copy(rows = rows, note = capNote)
    else if !up.connected && picks.nonEmpty then
      val closed = closes.flatMap((f, ck) => close(f, ck, gen, now))
      base.copy(
        closed = closed.size,
        rows = closed,
        note = Some("the acquisition toolset is not connected: nothing tried")
      )
    else
      val closed = closes.flatMap((f, ck) => close(f, ck, gen, now))
      val outs = picks.map(f => attempt(f, gen, now).row)
      val r = base.copy(
        closed = closed.size,
        fetched = outs.count(_.action == Fetched),
        noCopy = outs.count(_.action == NoCopy),
        failed = outs.count(_.action == Failed),
        rows = closed ++ outs ++ (if in.id.isDefined then noDoi else Nil),
        note = capNote
      )
      if r.closed + r.fetched + r.noCopy + r.failed > 0 then
        val by = in.by.map(_.trim).filter(_.nonEmpty).getOrElse("person")
        val _ = h.ops.queueAdd("log", QueueAdd(s"${summary(r)} (by $by)".take(1000)))
      r

  /** The item ticked off as fulfilled by `citekey` (the one write every import makes), and its check. */
  private def close(
      f: Facts,
      citekey: String,
      generation: String,
      now: Instant,
      job: Option[String] = None
  ): Option[RecheckRow] =
    h.ops.queuePatch(f.item.id, Queue.fulfil(f.item, citekey, job)) match
      case Left(e) =>
        scribe.warn(s"acquire re-check: item #${f.item.id} could not be closed: ${e.message}")
        None
      case Right(_) =>
        AcquireCheckRepo.record(
          ds,
          f.item.id,
          now,
          generation,
          Outcome.Closed,
          AcquireRecheck.reason(now, Outcome.Closed, citekey)
        )
        Some(RecheckRow(f.item.id, doiOf(f), Closed, "in the corpus with its original", Some(citekey)))

  /** One item tried: its row, what it found, and the fetcher's or the chain's own words. */
  final private case class Tried(row: RecheckRow, outcome: Outcome, detail: String)

  /** One item through the legal chain, recorded. */
  private def attempt(f: Facts, generation: String, now: Instant): Tried =
    val (doi, into) = f.action match
      case Action.Fetch(d, i) => (d, i)
      case _                  => ("", None)
    val (outcome, detail, ck) =
      try
        upstream().ingestLegalInto(doi, into) match
          case Right(r) => (Outcome.Fetched, r.pdf.fold("no file")(_.source), Some(r.citekey))
          case Left(m)  => (AcquireRecheck.failure(m), m, into)
      catch
        case e: InterruptedException => throw e
        case e: Exception            => (Outcome.Failed, s"${e.getClass.getSimpleName}: ${e.getMessage}", into)
    val why = AcquireRecheck.reason(now, outcome, detail)
    AcquireCheckRepo.record(ds, f.item.id, now, generation, outcome, why)
    Tried(RecheckRow(f.item.id, Some(doi), outcome.wire, why, ck), outcome, detail)

  private def summary(r: RecheckResult): String =
    def ids(a: String) =
      r.rows.filter(_.action == a).map(row => s"#${row.item}${row.citekey.fold("")(c => s" [[$c]]")}")
    val parts = List(
      Option.when(r.closed > 0)(s"${r.closed} closed (in the corpus already: ${ids(Closed).mkString(", ")})"),
      Option.when(r.fetched > 0)(
        s"${r.fetched} legal copy(ies) found (${ids(Fetched).mkString(", ")}; the conversion closes the item)"
      ),
      Option.when(r.noCopy > 0)(s"${r.noCopy} still without a legal copy"),
      Option.when(r.failed > 0)(s"${r.failed} without an answer (tried again when due)")
    ).flatten
    s"acquire re-check: ${parts.mkString(", ")}; ${math.max(0, r.due - r.fetched - r.noCopy - r.failed)} due item(s) left"

  // ── the status ─────────────────────────────────────────────────────────────────────────────────

  /** The status line's facts (`agent autopilot status`, `about`); readable with agents off. */
  def status(now: Instant = Instant.now()): RecheckStatus =
    val s = settings
    if !s.enabled then RecheckStatus(enabled = false, job = jobLine())
    else
      val gen = upstream().fetcherGeneration
      val fs = facts(None)
      val dueNow = due(fs, gen, now)
      val (tried, fetchedToday) = triedToday(now)
      val week = AcquireCheckRepo.countSince(ds, now.minusSeconds(7L * 86400L))
      val room = AcquireRecheck.allowance(s, tried, shared(now, fetchedToday))
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
              .map(Instant.ofEpochMilli(_).plusSeconds(math.max(1, s.passMinutes).toLong * 60L))
              .filter(_.isAfter(now))
              .fold("at the next managers' tick")(t => s"at ${t.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)}")
          )
      val note =
        if agents.drain.draining then Some("held while the autopilot drains")
        else if !agents.managers.running then Some("it runs on the managers' tick, which is not running on this server")
        else None
      RecheckStatus(
        enabled = true,
        fs.count(_.action != Action.NoDoi),
        dueNow.size,
        tried,
        s.perDay,
        fetchedToday,
        week.getOrElse(Outcome.Closed.wire, 0),
        week.getOrElse(Outcome.Fetched.wire, 0),
        s.everyDays,
        next,
        note,
        jobLine()
      )

  /** The re-check job as the status line names it: the one running and its progress, or the newest when a restart or
    * the drain stopped it (a tick resumes it).
    */
  private def jobLine(): Option[String] =
    latestJob().flatMap { j =>
      if !JobState.parse(j.status).exists(_.isTerminal) then
        Some(s"job ${j.id} running: ${j.progress.getOrElse("starting")}")
      else if AcquireRecheck.resumable(j.status, j.error) then
        Some(
          s"job ${j.id} stopped (${j.error.getOrElse("stopped")}" + j.progress.fold("")(p => s" at $p") + "): " +
            (if agents.drain.draining then "it resumes once the drain is lifted"
             else if agents.managers.running then "it resumes at the next managers' tick"
             else if j.detail.flatMap(_.recheck).exists(_.all) then
               "`histmersion queue acquire recheck --all` resumes it"
             else "the managers' tick resumes it, which is not running on this server")
        )
      else None
    }

  /** What the re-check fetched today: what the shared legal ingest allowance counts besides the watchers'. */
  def fetchedToday(now: Instant): Int = triedToday(now)._2
