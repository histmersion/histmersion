package com.histmersion.domain

import com.histmersion.api.Plural
import com.histmersion.api.automation.ManagersMarkdown
import com.histmersion.api.given

import com.histmersion.api.automation.Managers.*

import java.time.{Duration, Instant}
import com.histmersion.values.{ApprovalState, ManagerKind, ManagerState, RunState}

/** The pure half of the process managers (docs/research-process-managers.md §3.4): what to do with an item after its
  * run settled, the order items are dispatched in, how many runs a manager may start, the backoff, and the hold
  * releases. No I/O: the supervisor reads the facts and writes the result.
  */
object ManagerPolicy:

  /** A manager's settings in effect (the saved config over `agents.managers.<kind>`). The review manager uses them all;
    * the enrich and seeding managers their run limits, failures and backoff (their items run once each: no rounds, no
    * refill).
    */
  final case class ReviewSettings(
      maxRuns: Int,
      maxOpen: Int,
      rounds: Int,
      maxRunsPerArticle: Int,
      noProgressRuns: Int,
      reopenLimit: Int,
      cooldownMinutes: Int,
      lowWater: Int,
      refillBatch: Int,
      maxFailures: Int,
      backoffMinutes: Int,
      maxBackoffMinutes: Int,
      staleAfterDays: Int = 0,
      inferFilter: Boolean = false
  )

  object ReviewSettings:
    /** The settings of a manager of kind `k`. */
    def forKind(k: ManagerKind, c: ManagerConfig, m: ManagerDefaults): ReviewSettings =
      val maxRuns = math.max(0, c.maxRuns.getOrElse(simple(k, m).maxRuns))
      ReviewSettings(
        maxRuns,
        math.max(maxRuns, c.maxOpen.getOrElse(2 * maxRuns)),
        1,
        1,
        1,
        1,
        math.max(0, c.cooldownMinutes.getOrElse(0)),
        0,
        0,
        math.max(1, c.maxFailures.getOrElse(m.maxFailures)),
        math.max(1, m.backoffMinutes),
        math.max(1, m.maxBackoffMinutes)
      )

    private def simple(k: ManagerKind, m: ManagerDefaults): ManagerRunDefaults = k match
      case ManagerKind.Review => m.review
      case ManagerKind.Enrich => m.enrich
      case ManagerKind.Watch  => m.watch
      case _                  => m.seed

    /** The defaults of a manager of kind `k` (a kind whose defaults say more builds them itself). */
    def defaultsFor(k: ManagerKind, m: ManagerDefaults): ManagerConfig =
      val s = simple(k, m)
      ManagerConfig(
        maxRuns = Some(s.maxRuns),
        maxOpen = Some(2 * s.maxRuns),
        maxFailures = Some(m.maxFailures),
        dailyUsd = dailyCap(ManagerConfig(), m)
      )

  // ── the cron (phase 3) ─────────────────────────────────────────────────────────────────────────

  /** What a manager's cron does at a tick. */
  enum CronStep:
    /** Nothing: no cron, or not due yet. */
    case Idle

    /** Not scheduled yet: record its first firing, fire nothing. */
    case Schedule(next: Option[Instant])

    /** Due: fire now, then next at `next` (None: it never fires again). */
    case Fire(next: Option[Instant])

  /** An active manager's cron at `now`, given when it was due (`nextTick`). A firing missed while the manager was
    * paused (or the server down) fires ONCE when it is next looked at, never once per missed time.
    */
  def cronStep(cron: Option[Cron], nextTick: Option[Instant], now: Instant): CronStep =
    cron match
      case None    => CronStep.Idle
      case Some(c) =>
        nextTick match
          case None                       => CronStep.Schedule(c.next(now))
          case Some(t) if !t.isAfter(now) => CronStep.Fire(c.next(now))
          case _                          => CronStep.Idle

  /** A cron a person sets: it parses, and it fires no more often than every `minIntervalMinutes`
    * (`agents.scheduler.min-interval-minutes`).
    */
  def checkCron(text: String, now: Instant, minIntervalMinutes: Int): Either[String, Cron] =
    Cron.parse(text).flatMap { c =>
      c.shortestGapMinutes(now).filter(_ < minIntervalMinutes) match
        case Some(gap) =>
          Left(
            s"'$text' would fire every $gap minute(s); the least is $minIntervalMinutes " +
              "(agents.scheduler.min-interval-minutes)"
          )
        case None => Right(c)
    }

  /** A manager's own daily cap in effect: its `dailyUsd`, else `agents.managers.daily-usd`; 0 = none. */
  def dailyCap(c: ManagerConfig, m: ManagerDefaults): Option[Double] =
    c.dailyUsd.orElse(Option(m.dailyUsd)).filter(_ > 0)

  /** Why a manager starts nothing more today, if it does not: its runs' metered spend this UTC day reached its daily
    * cap. Plan-billed calls cost 0 USD, so this caps metered spend only.
    */
  def dailyBlock(cap: Option[Double], spentToday: BigDecimal): Option[String] =
    cap
      .filter(c => spentToday >= BigDecimal(c))
      .map(c =>
        s"budget: its spend today (${ManagersMarkdown.usd(spentToday.toDouble)} USD, metered calls only) reached its daily-usd " +
          s"(${ManagersMarkdown.usd(c)}); it starts again tomorrow (UTC)"
      )

  /** The start of `now`'s UTC day (what a daily cap counts from). */
  def dayStart(now: Instant): Instant = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS)

  /** What the next step of an item is, once its run settled. */
  enum Next:
    /** Run it again (not before `notBefore`). */
    case Again(notBefore: Option[Instant], state: ItemState, note: String)

    /** It reached its end. */
    case Done(outcome: String, state: ItemState)

    /** It stops here; `task` is the backlog line a person acts on (None: nothing to act on). */
    case Stop(outcome: String, task: Option[String], state: ItemState)

    /** Its run failed: try again after the backoff (`failures` counted). */
    case Retry(notBefore: Instant, failures: Int, note: String)

    /** Its run left a proposal for a person (approval `approval`): the item is held until it is decided, then settled
      * with the decision — never run again for it.
      */
    case Wait(approval: Long, note: String)

  /** `base · 2^(failures-1)` minutes, at most `max`. */
  def backoff(baseMinutes: Int, maxMinutes: Int, failures: Int): Duration =
    val exp = math.min(30, math.max(0, failures - 1))
    val mins = math.min(maxMinutes.toLong, baseMinutes.toLong * (1L << exp))
    Duration.ofMinutes(math.max(1L, mins))

  // ── the one-run items (infer-filter, extend, enrich, propose, compile) ───────────────────────────

  /** What a DONE run of a one-run item left, as the supervisor reads it: its work is finished (with the outcome), it
    * waits for a person's decision on approval `id`, or it ended without doing its work (`task`: what a person should
    * look at, if anything).
    */
  enum StepFacts:
    case Finished(outcome: String)
    case Waiting(approval: Long, what: String)
    case NotDone(outcome: String, task: Option[String])

    /** Its work was refused by a check that a new run may pass (a draft the autopilot's checks rejected): run it again
      * after the backoff, counted as a failure, stopped with a task after `maxFailures`.
      */
    case Redo(outcome: String)

  /** Evaluate a one-run item (`label` = its kind, for the texts) after its run settled `status`: done → finished, held
    * for an approval, or stopped; failed → retried after the backoff, stopped with a task after `maxFailures`;
    * cancelled → stopped. `facts` are read only for a done run.
    */
  def evaluateStep(
      s: ReviewSettings,
      label: String,
      target: String,
      failures: Int,
      state: ItemState,
      run: String,
      status: RunState,
      error: Option[String],
      facts: => StepFacts,
      now: Instant
  ): Next =
    status match
      case RunState.Done =>
        facts match
          case StepFacts.Finished(o)      => Next.Done(o, state)
          case StepFacts.Waiting(id, w)   => Next.Wait(id, w)
          case StepFacts.NotDone(o, task) => Next.Stop(o, task.map(_.take(2000)), state)
          case StepFacts.Redo(o)          =>
            val n = failures + 1
            if n >= s.maxFailures then
              Next.Stop(
                s"stopped: $o (${Plural.count(n, "attempt")})",
                Some(
                  s"$label manager item stopped [[$target]]: $o, ${Plural.count(n, "attempt")} (agent run ${run.take(8)})"
                    .take(2000)
                ),
                state
              )
            else
              Next.Retry(
                now.plus(backoff(s.backoffMinutes, s.maxBackoffMinutes, n)),
                n,
                s"$o ($n of ${s.maxFailures}); again"
              )
      case RunState.Cancelled => Next.Stop("stopped: its run was cancelled", None, state)
      case _                  =>
        val n = failures + 1
        val why = error.filter(_.nonEmpty).fold("")(e => s": ${e.take(300)}")
        if n >= s.maxFailures then
          Next.Stop(
            s"stopped: ${Plural.count(n, "failed run")}$why",
            Some(
              s"$label manager item stopped [[$target]] after ${Plural.count(n, "failed run")}$why (agent run ${run.take(8)})"
            ),
            state
          )
        else
          Next.Retry(
            now.plus(backoff(s.backoffMinutes, s.maxBackoffMinutes, n)),
            n,
            s"run failed ($n of ${s.maxFailures})$why"
          )

  /** How an approval a held item waits for was decided, as its outcome (None: still open). */
  def decided(state: String, result: Option[String]): Option[String] =
    ApprovalState.parse(state).filter(_ != ApprovalState.Open).map { st =>
      st.wire + result.filter(_.nonEmpty).fold("")(r => s": ${r.take(300)}")
    }

  /** Must an item of `kind` on `target` wait — an article's review while an infer-filter item for the same article is
    * still open (pending, held or active), a goal's article proposal while its plan-goal item is? `open` = the
    * manager's open items (kind, target).
    */
  def waitsFor(kind: String, target: String, open: List[(String, String)]): Boolean =
    (kind == ArticleReview && open.contains((InferFilter, target))) ||
      (kind == ProposeArticles && open.contains((PlanGoalItem, target)))

  /** The item kinds whose target is a goal. */
  val GoalKinds: Set[String] = Set(PlanGoalItem, ProposeArticles, AcquisitionSweep, CurrencyCheck)

  /** Must an item of `kind` (whose pipeline is `pipeline`) wait for the unsettled runs on its target (their pipelines)?
    * On an article or a source: any of them (one writer at a time). On a goal — its plan, its article proposal, its
    * sweeps — only a run of the same pipeline: they write different things (the plan and queries, an approval, sources
    * and acquisition items), so a plan waiting days for a person does not stop its watcher, nor a sweep the proposal
    * (which waits for the plan through [[waitsFor]]).
    */
  def blocks(kind: String, pipeline: String, unsettled: Seq[String]): Boolean =
    if GoalKinds(kind) then unsettled.contains(pipeline) else unsettled.nonEmpty

  // ── dispatch ───────────────────────────────────────────────────────────────────────────────────

  /** What dispatch needs of an item. */
  final case class Candidate(id: Long, target: String, priority: Int, notBefore: Option[Instant])

  /** May it start now? */
  def due(c: Candidate, now: Instant): Boolean = c.notBefore.forall(!_.isAfter(now))

  /** The dispatch order within one manager: priority (highest first), then the earliest `notBefore` (none first), then
    * the oldest.
    */
  def order(cs: List[Candidate]): List[Candidate] =
    cs.sortBy(c => (-c.priority, c.notBefore.map(_.toEpochMilli).getOrElse(Long.MinValue), c.id))

  /** How many runs a manager may start now: the global room (`maxConcurrent` minus every agent run queued or running, a
    * person's too), its own (`maxRuns` minus its queued/running; `maxOpen` minus every unsettled one, paused and
    * waiting included), and its pools' (`poolCap`, the calls its models' plan pools take at once). Never negative.
    */
  def capacity(
      maxConcurrent: Int,
      activeAll: Int,
      maxRuns: Int,
      mineRunning: Int,
      maxOpen: Int,
      mineOpen: Int,
      poolCap: Option[Int]
  ): Int =
    List(
      maxConcurrent - activeAll,
      maxRuns - mineRunning,
      maxOpen - mineOpen,
      poolCap.fold(Int.MaxValue)(_ - mineRunning)
    ).min.max(0)

  /** Several managers sharing the global room: one item from each in turn (in the order given), each within its own
    * `cap`, until `room` is used or nothing is left.
    */
  def roundRobin[M, A](queues: List[(M, Int, List[A])], room: Int): List[(M, A)] =
    @annotation.tailrec
    def go(qs: List[(M, Int, List[A])], left: Int, acc: Vector[(M, A)]): Vector[(M, A)] =
      val live = qs.filter((_, cap, as) => cap > 0 && as.nonEmpty)
      if left <= 0 || live.isEmpty then acc
      else
        val take = live.take(left)
        val picked = take.map((m, _, as) => (m, as.head))
        val rest = qs.map { case q @ (m, cap, as) =>
          if take.exists(_._1 == m) then (m, cap - 1, as.tail) else q
        }
        go(rest, left - take.size, acc ++ picked)
    go(queues, math.max(0, room), Vector.empty).toList

  /** Is a hold over: its date passed, or the source it waits for exists. A hold with neither waits for a person's
    * release.
    */
  def released(h: Hold, now: Instant, sourceExists: String => Boolean): Boolean =
    h.until
      .flatMap(parseInstant)
      .exists(!_.isAfter(now)) || h.untilSource.map(_.trim).filter(_.nonEmpty).exists(sourceExists)

  /** `2026-11-01` (UTC midnight) or an ISO instant. */
  def parseInstant(s: String): Option[Instant] =
    val t = s.trim
    scala.util
      .Try(Instant.parse(t))
      .toOption
      .orElse(scala.util.Try(java.time.LocalDate.parse(t).atStartOfDay(java.time.ZoneOffset.UTC).toInstant).toOption)

  /** The status line a manager shows. */
  def statusNote(
      state: ManagerState,
      starved: Option[String],
      capacity: Int,
      pending: Int,
      due: Int,
      inFlight: Int,
      budget: Option[String]
  ): String =
    if state == ManagerState.Stopped then "stopped"
    else if state == ManagerState.Paused then
      (if inFlight > 0 then s"paused (${Plural.count(inFlight, "run")} in flight)" else "paused"
    )
    else
      budget.orElse(starved.map(s => s"starved: $s")).getOrElse {
        if pending == 0 && inFlight == 0 then "idle"
        else if due > 0 && capacity == 0 then
          s"at capacity (${Plural.count(inFlight, "run")} in flight, $pending pending)"
        else if pending > 0 then
          s"$pending pending" + (if inFlight > 0 then s", ${Plural.count(inFlight, "run")} in flight" else "")
        else s"${Plural.count(inFlight, "run")} in flight"
      }

  // ── hints ──────────────────────────────────────────────────────────────────────────────────────

  /** Does a structured hint effect name this target (by slug, or by one of its tags/topics/goals)? */
  def matches(e: HintEffect, target: String, tags: List[String], topics: List[String], goals: List[String]): Boolean =
    def eq(a: String, b: String) = a.trim.equalsIgnoreCase(b.trim)
    e.targets.exists(eq(_, target)) || e.tag.exists(t => tags.exists(eq(_, t))) ||
    e.topic.exists(t => topics.exists(eq(_, t))) || e.goal.exists(g => goals.exists(eq(_, g)))

  /** The notes a run is given: the manager's live standing hints and unconsumed once-hints, manager-wide or about this
    * item, oldest first.
    */
  def notesFor(hints: List[ManagerHint], itemId: Long): List[ManagerHint] =
    hints.filter(h => h.retiredAt.isEmpty && h.itemId.forall(_ == itemId) && h.text.trim.nonEmpty).sortBy(_.id)
