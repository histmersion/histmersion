package com.histmersion.server.agents

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.AcquireChecks.*
import com.histmersion.api.Queue.QueueAdd
import com.histmersion.db.{AcquireCheckRepo, ManagerRepo, QueueRepo}
import com.histmersion.domain.AcquireRecheck.{Action, Held, Outcome, Settings, Shared}

import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import javax.sql.DataSource

/** The acquisition list's LEGAL RE-CHECK (docs/research-process-managers.md "As built: the acquisition
  * list's re-check"), a model-free pass of the managers' tick like the ingest backlog's drain, every
  * `acquire.recheck.pass-minutes` and never while the autopilot drains:
  *
  *  - every OPEN acquisition item that names a DOI the corpus holds with its original is closed (ticked off
  *    as fulfilled by that source; not capped);
  *  - of the others, the DUE ones ([[AcquireRecheck.isDue]]: never checked, checked `every-days` ago, or
  *    under another fetcher generation) are tried — the never-checked first, then the oldest check — through
  *    the LEGAL chain only, whatever `acquire.allow-unauthorised` says ([[AgentUpstream.ingestLegalInto]]),
  *    into the metadata-only source when there is one; a few a pass, `per-day` a day, and under the
  *    autopilot's shared daily legal ingest allowance only what the watchers' backlog leaves, at most
  *    `share-percent` of it ([[AcquireRecheck.allowance]]);
  *  - a copy found is converted, and the conversion closes the item (as every import closes the items that
  *    name its DOI); no copy, and the item records when it was checked and what each fetcher answered.
  *
  * One worklog line per pass that did something. [[run]] is the same pass by hand
  * (`histmersion queue acquire recheck`). */
final class AcquireRecheckService(ds: DataSource, rcfg: => RecheckConfig, h: com.histmersion.server.Handlers,
    upstream: () => AgentUpstream, agents: AgentsHandlers):

  private def settings: Settings =
    val c = rcfg
    Settings(c.enabled, c.everyDays, c.perDay, c.perPass, c.passMinutes, c.sharePercent)

  /** When the last scheduled pass ran (epoch ms; 0 = not since the start). */
  private val lastPass = AtomicLong(0L)

  /** One pass at a time: the managers' tick and a person's run never try the same item twice. */
  private val passing = ReentrantLock()

  // ── the facts ──────────────────────────────────────────────────────────────────────────────────

  /** One open item with what the rules need: its DOIs (in the order its text names them), the action, its
    * last check. */
  private final case class Facts(item: Queue.QueueItem, action: Action, last: Option[AcquireCheckRepo.Row])

  private def facts(only: Option[Int]): List[Facts] =
    val items = QueueRepo.list(ds, Queue.Kind.Acquire, all = false).toList.filter(i => only.forall(_ == i.id))
    val dois  = items.map(i => i.id -> orderedDois(i.text)).toMap
    val held  = AcquireCheckRepo.heldByDoi(ds, dois.values.flatMap(_.headOption).toList)
    val last  = AcquireCheckRepo.of(ds, items.map(_.id))
    items.map(i => Facts(i, AcquireRecheck.action(dois.getOrElse(i.id, Nil), held.get), last.get(i.id)))

  private def orderedDois(text: String): List[String] =
    val lower = text.toLowerCase
    Scheduling.dois(text).toList.sortBy(d => (lower.indexOf(d) match { case -1 => Int.MaxValue; case i => i }, d))

  private def triedToday(now: Instant): (Int, Int) =
    val c = AcquireCheckRepo.countSince(ds, ManagerPolicy.dayStart(now))
    (c.getOrElse(Outcome.Fetched.wire, 0) + c.getOrElse(Outcome.NoCopy.wire, 0) + c.getOrElse(Outcome.Failed.wire, 0),
      c.getOrElse(Outcome.Fetched.wire, 0))

  /** The shared daily legal ingest allowance as the re-check sees it (None: the autopilot is off or has no cap). */
  private def shared(now: Instant, fetchedToday: Int): Option[Shared] =
    agents.autopilot.sharedIngestLeft(now).map(left =>
      Shared(left, agents.autopilot.ingestCap, ManagerRepo.backlogCounts(ds).values.map(_._1).sum, fetchedToday))

  /** The items a pass would try, in order: due, the never-checked first, then the oldest check. */
  private def due(fs: List[Facts], generation: String, now: Instant): List[Facts] =
    fs.filter(f => f.action.isInstanceOf[Action.Fetch] &&
        AcquireRecheck.isDue(f.last.map(_.last), generation, settings.everyDays, now))
      .sortBy(f => (f.last.isDefined, f.last.fold(0L)(_.at.toEpochMilli), f.item.id))

  /** The DOI an item is about (the first its text names). */
  private def doiOf(f: Facts): Option[String] = orderedDois(f.item.text).headOption

  // ── the pass ───────────────────────────────────────────────────────────────────────────────────

  /** From the managers' tick: a pass when on, not draining, and `pass-minutes` passed since the last. */
  def tick(now: Instant): List[String] =
    val s = settings
    if !s.enabled || agents.drain.draining || !AcquireRecheck.passDue(Option(lastPass.get).filter(_ > 0).map(Instant.ofEpochMilli),
        s.passMinutes, now) then Nil
    else
      lastPass.set(now.toEpochMilli)
      pass(RecheckRequest(by = Some("managers")), now).toList.flatMap(r =>
        Option.when(r.closed + r.fetched + r.noCopy + r.failed > 0)(summary(r)))

  /** `POST /api/queue/acquire/recheck`: a pass now, by hand — on or off, but never while the autopilot
    * drains (a dry run lists what it would do even then). */
  def run(in: RecheckRequest, now: Instant = Instant.now()): Either[ApiError, RecheckResult] =
    in.id.filter(id => !QueueRepo.get(ds, id).exists(i => i.list == Queue.Kind.Acquire.label && i.status == Queue.Open)) match
      case Some(id) => Left(ApiError(s"no open acquisition item #$id", Some(Write.CodeNotFound)))
      case None if in.limit.exists(_ < 0) => Left(ApiError("--limit is 0 or more", Some(Write.CodeInvalid)))
      case None =>
        if !in.dryRun && agents.drain.draining then
          Right(RecheckResult(dryRun = false, note = Some("the autopilot is draining: nothing runs until it is lifted " +
            "(histmersion agent autopilot undrain); --dry-run lists what a pass would do")))
        else pass(in, now).toRight(ApiError("a re-check pass is running already; try again in a moment"))

  /** One pass (None: another is running). */
  private def pass(in: RecheckRequest, now: Instant): Option[RecheckResult] =
    if !passing.tryLock() then None
    else
      try Some(passNow(in, now))
      finally passing.unlock()

  private def passNow(in: RecheckRequest, now: Instant): RecheckResult =
    val s    = settings
    val up   = upstream()
    val gen  = up.fetcherGeneration
    val fs   = facts(in.id)
    val closes = fs.collect { case f @ Facts(_, Action.Close(ck), _) => f -> ck }
    val dueNow = if in.id.isDefined then fs.filter(_.action.isInstanceOf[Action.Fetch]) else due(fs, gen, now)
    val (tried, fetchedToday) = triedToday(now)
    val room =
      if in.id.isDefined then 1
      else AcquireRecheck.allowance(s.copy(perPass = in.limit.getOrElse(s.perPass)), tried, shared(now, fetchedToday))
    val picks   = dueNow.take(room)
    val withDoi = fs.count(_.action != Action.NoDoi)
    val noDoi   = fs.filter(_.action == Action.NoDoi).map(f => RecheckRow(f.item.id, None, NoDoi, "no DOI in its text"))
    val base = RecheckResult(in.dryRun, fs.size, withDoi, dueNow.size, room)
    val capNote = Option.when(picks.size < dueNow.size && in.id.isEmpty)(
      s"${dueNow.size - picks.size} due item(s) wait for the next pass: at most ${in.limit.getOrElse(s.perPass)} a pass, " +
        s"${s.perDay} a day (${tried} tried today)" +
        (if shared(now, fetchedToday).isDefined then s", and ${s.sharePercent} % of the shared legal ingest allowance after the watchers' backlog" else ""))
    if in.dryRun then
      val rows = closes.map((f, ck) => RecheckRow(f.item.id, doiOf(f), WouldClose, "in the corpus with its original", Some(ck))) ++
        dueNow.zipWithIndex.map { (f, i) =>
          val into = f.action match { case Action.Fetch(_, into) => into; case _ => None }
          RecheckRow(f.item.id, doiOf(f), if i < picks.size then WouldTry else Due,
            f.last.fold("never checked")(l => s"last ${l.reason}"), into)
        } ++ (if in.id.isDefined then noDoi else Nil)
      base.copy(rows = rows, note = capNote)
    else if !up.connected && picks.nonEmpty then
      val closed = closes.flatMap((f, ck) => close(f, ck, gen, now))
      base.copy(closed = closed.size, rows = closed, note = Some("the acquisition toolset is not connected: nothing tried"))
    else
      val closed = closes.flatMap((f, ck) => close(f, ck, gen, now))
      val outs   = picks.map(f => attempt(f, gen, now))
      val r = base.copy(closed = closed.size, fetched = outs.count(_.action == Fetched), noCopy = outs.count(_.action == NoCopy),
        failed = outs.count(_.action == Failed), rows = closed ++ outs ++ (if in.id.isDefined then noDoi else Nil), note = capNote)
      if r.closed + r.fetched + r.noCopy + r.failed > 0 then
        val by = in.by.map(_.trim).filter(_.nonEmpty).getOrElse("person")
        val _ = h.ops.queueAdd("log", QueueAdd(s"${summary(r)} (by $by)".take(1000)))
      r

  /** The item ticked off as fulfilled by `citekey` (the one write every import makes), and its check. */
  private def close(f: Facts, citekey: String, generation: String, now: Instant): Option[RecheckRow] =
    h.ops.queuePatch(f.item.id, Queue.fulfil(f.item, citekey)) match
      case Left(e) =>
        scribe.warn(s"acquire re-check: item #${f.item.id} could not be closed: ${e.message}")
        None
      case Right(_) =>
        AcquireCheckRepo.record(ds, f.item.id, now, generation, Outcome.Closed, AcquireRecheck.reason(now, Outcome.Closed, citekey))
        Some(RecheckRow(f.item.id, doiOf(f), Closed, "in the corpus with its original", Some(citekey)))

  /** One item through the legal chain, recorded. */
  private def attempt(f: Facts, generation: String, now: Instant): RecheckRow =
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
    RecheckRow(f.item.id, Some(doi), outcome.wire, why, ck)

  private def summary(r: RecheckResult): String =
    def ids(a: String) = r.rows.filter(_.action == a).map(row => s"#${row.item}${row.citekey.fold("")(c => s" [[$c]]")}")
    val parts = List(
      Option.when(r.closed > 0)(s"${r.closed} closed (in the corpus already: ${ids(Closed).mkString(", ")})"),
      Option.when(r.fetched > 0)(s"${r.fetched} legal copy(ies) found (${ids(Fetched).mkString(", ")}; the conversion closes the item)"),
      Option.when(r.noCopy > 0)(s"${r.noCopy} still without a legal copy"),
      Option.when(r.failed > 0)(s"${r.failed} without an answer (tried again when due)")).flatten
    s"acquire re-check: ${parts.mkString(", ")}; ${math.max(0, r.due - r.fetched - r.noCopy - r.failed)} due item(s) left"

  // ── the status ─────────────────────────────────────────────────────────────────────────────────

  /** The status line's facts (`agent autopilot status`, `about`); readable with agents off. */
  def status(now: Instant = Instant.now()): RecheckStatus =
    val s = settings
    if !s.enabled then RecheckStatus(enabled = false)
    else
      val gen  = upstream().fetcherGeneration
      val fs   = facts(None)
      val dueNow = due(fs, gen, now)
      val (tried, fetchedToday) = triedToday(now)
      val week = AcquireCheckRepo.countSince(ds, now.minusSeconds(7L * 86400L))
      val room = AcquireRecheck.allowance(s, tried, shared(now, fetchedToday))
      val next: Option[String] =
        if dueNow.isEmpty then
          AcquireRecheck.nextDue(fs.filter(_.action.isInstanceOf[Action.Fetch]).flatMap(_.last.map(_.last)), gen, s.everyDays)
            .map(t => s"when the next item falls due, ${t.truncatedTo(java.time.temporal.ChronoUnit.MINUTES)}")
        else if room <= 0 then Some(s"tomorrow (UTC): today's tries or the re-check's share of the allowance are spent")
        else Some(Option(lastPass.get).filter(_ > 0).map(Instant.ofEpochMilli(_).plusSeconds(math.max(1, s.passMinutes).toLong * 60L))
          .filter(_.isAfter(now)).fold("at the next managers' tick")(t => s"at ${t.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)}"))
      val note =
        if agents.drain.draining then Some("held while the autopilot drains")
        else if !agents.managers.running then Some("it runs on the managers' tick, which is not running on this server")
        else None
      RecheckStatus(enabled = true, fs.count(_.action != Action.NoDoi), dueNow.size, tried, s.perDay, fetchedToday,
        week.getOrElse(Outcome.Closed.wire, 0), week.getOrElse(Outcome.Fetched.wire, 0), s.everyDays, next, note)

  /** What the re-check fetched today: what the shared legal ingest allowance counts besides the watchers'. */
  def fetchedToday(now: Instant): Int = triedToday(now)._2
