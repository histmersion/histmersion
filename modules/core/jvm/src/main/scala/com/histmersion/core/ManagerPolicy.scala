package com.histmersion.core

import com.histmersion.core.Agents.RunState
import com.histmersion.core.Managers.*
import com.histmersion.core.ReviewLedger.Ledger

import java.time.{Duration, Instant}

/** The pure half of the process managers (docs/research-process-managers.md §3.4): what to do with an
  * item after its run settled, the order items are dispatched in, how many runs a manager may start,
  * the backoff, and the hold releases. No I/O: the supervisor reads the facts and writes the result. */
object ManagerPolicy:

  /** The review manager's settings in effect (the saved config over `agents.managers.review`). */
  final case class ReviewSettings(maxRuns: Int, maxOpen: Int, rounds: Int, maxRunsPerArticle: Int, noProgressRuns: Int,
      reopenLimit: Int, cooldownMinutes: Int, lowWater: Int, refillBatch: Int, maxFailures: Int,
      backoffMinutes: Int, maxBackoffMinutes: Int)

  object ReviewSettings:
    def of(c: ManagerConfig, d: ReviewManagerConfig, m: AgentManagersConfig): ReviewSettings =
      val maxRuns = math.max(0, c.maxRuns.getOrElse(d.maxRuns))
      ReviewSettings(maxRuns, math.max(maxRuns, c.maxOpen.getOrElse(2 * maxRuns)), math.max(1, c.rounds.getOrElse(d.rounds)),
        math.max(1, c.maxRunsPerArticle.getOrElse(d.maxRunsPerArticle)), math.max(1, c.noProgressRuns.getOrElse(d.noProgressRuns)),
        math.max(1, c.reopenLimit.getOrElse(d.reopenLimit)), math.max(0, c.cooldownMinutes.getOrElse(d.cooldownMinutes)),
        math.max(0, c.lowWater.getOrElse(d.lowWater)), math.max(0, c.refillBatch.getOrElse(d.refillBatch)),
        math.max(1, c.maxFailures.getOrElse(m.maxFailures)), math.max(1, m.backoffMinutes), math.max(1, m.maxBackoffMinutes))

    /** The defaults as a config (what `effective` shows for an unset key). */
    def defaults(d: ReviewManagerConfig, m: AgentManagersConfig): ManagerConfig =
      ManagerConfig(maxRuns = Some(d.maxRuns), maxOpen = Some(2 * d.maxRuns), rounds = Some(d.rounds),
        maxRunsPerArticle = Some(d.maxRunsPerArticle), noProgressRuns = Some(d.noProgressRuns),
        reopenLimit = Some(d.reopenLimit), cooldownMinutes = Some(d.cooldownMinutes), lowWater = Some(d.lowWater),
        refillBatch = Some(d.refillBatch), maxFailures = Some(m.maxFailures))

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

  /** What a settled run of the review manager left: the ledger's open MAJORs, the most often
    * reopened, the escalated ones, whether every open MAJOR is a `missing-critique` finding, and the
    * open pairs of the article's critique checklist now (None when not known). */
  final case class ReviewFacts(openMajors: Int, maxReopened: Int, escalated: Int, onlyMissingCritiques: Boolean,
      openPairs: Option[Int] = None)

  object ReviewFacts:
    /** The facts of `l`, with the checklist's open pairs counted by the caller. */
    def of(l: Ledger, openPairs: Option[Int] = None): ReviewFacts =
      val majors = l.openMajors
      ReviewFacts(majors.size, majors.map(_.reopened).maxOption.getOrElse(0), majors.count(_.finding.escalated),
        majors.nonEmpty && majors.forall(e => ReviewArticle.isMissingCritique(e.finding)), openPairs)

  /** The open MAJORs over the last `n` runs went from `before` (when the window's first run was
    * dispatched) to `after` without going down. */
  def noProgress(history: List[RunMark], n: Int): Boolean =
    val window = history.takeRight(n)
    window.size >= n && {
      val start = window.head.before.getOrElse(Int.MaxValue)
      window.last.after >= start
    }

  /** The open pairs of the critique checklist did not go down over the last `n` runs: from
    * `pairsBefore` of the window's first run to `pairsAfter` of its last. Unknown counts (a run
    * evaluated before the checklist existed) are no evidence of a stall. */
  def pairsStalled(history: List[RunMark], n: Int): Boolean =
    val window = history.takeRight(n)
    window.size >= n && window.head.pairsBefore.zip(window.last.pairsAfter).exists((start, end) => end >= start)

  /** Evaluate a review item after its run `run` settled `status` (a terminal state). `attempts` = the
    * runs it has had (this one included); `facts` = what the ledger says now (None for a failed or
    * cancelled run); `taskText` = the ledger's own task line for a stop. The stop rules, in order:
    * omission churn, a finding escalated or reopened `reopenLimit` times, `maxRunsPerArticle` runs, no
    * progress over `noProgressRuns` runs.
    *
    * OMISSION CHURN (§6.8 of docs/research-review-convergence.md): every open MAJOR is a missing-critique
    * finding AND the open pairs of the critique checklist did not go down over `noProgressRuns` runs.
    * While those pairs go down the runs are finishing a closed list, so neither churn nor the open-MAJOR
    * count stops the item (a missing-critique MAJOR per open pair keeps that count flat by design);
    * `maxRunsPerArticle` still does. */
  def evaluateReview(s: ReviewSettings, slug: String, attempts: Int, failures: Int, state: ItemState, run: String,
      status: RunState, error: Option[String], facts: Option[ReviewFacts], taskText: Option[String], now: Instant): Next =
    status match
      case RunState.Done =>
        val f    = facts.getOrElse(ReviewFacts(0, 0, 0, onlyMissingCritiques = false))
        val mark = RunMark(run, state.before, f.openMajors, f.maxReopened, f.escalated, f.onlyMissingCritiques,
          state.pairsBefore, f.openPairs)
        val st   = state.copy(history = state.history :+ mark, before = None, pairsBefore = None)
        val churn = f.onlyMissingCritiques && pairsStalled(st.history, s.noProgressRuns)
        val pairs = f.openPairs.fold("")(n => s", ${Plural.count(n, "open critique pair")}")
        def stop(why: String) =
          val task = taskText.fold(s"review manager stopped [[$slug]]: $why (agent run ${run.take(8)})")(t =>
            s"$t — review manager stopped: $why")
          Next.Stop(s"stopped: $why", Some(task.take(2000)), st)
        if f.openMajors == 0 then Next.Done(s"converged after ${Plural.count(attempts, "run")}", st)
        else if churn then
          stop(s"omission churn — every open MAJOR (${f.openMajors}) is a missing-critique finding and the open critique " +
            s"pairs did not go down over ${Plural.count(s.noProgressRuns, "run")} (now ${f.openPairs.getOrElse(0)})")
        else if f.escalated > 0 then stop(s"${Plural.count(f.escalated, "MAJOR")} raised again after the fixer rejected it (escalated)")
        else if f.maxReopened >= s.reopenLimit then stop(s"a finding was reopened ${f.maxReopened} times (reopen-limit ${s.reopenLimit})")
        else if attempts >= s.maxRunsPerArticle then
          stop(s"${Plural.count(f.openMajors, "open MAJOR")} after ${Plural.count(attempts, "run")} (max-runs-per-article ${s.maxRunsPerArticle})")
        else if !f.onlyMissingCritiques && noProgress(st.history, s.noProgressRuns) then
          stop(s"the open MAJORs did not go down over ${Plural.count(s.noProgressRuns, "run")} (now ${f.openMajors})")
        else
          Next.Again(Option.when(s.cooldownMinutes > 0)(now.plusSeconds(s.cooldownMinutes.toLong * 60L)), st,
            s"${Plural.count(f.openMajors, "open MAJOR")}$pairs after run ${attempts}; again" +
              (if s.cooldownMinutes > 0 then s" after a ${s.cooldownMinutes}-minute cooldown" else ""))
      case RunState.Cancelled =>
        Next.Stop("stopped: its run was cancelled", None, state.copy(before = None))
      case _ => // failed (or anything else terminal)
        val n = failures + 1
        val why = error.filter(_.nonEmpty).fold("")(e => s": ${e.take(300)}")
        if n >= s.maxFailures then
          Next.Stop(s"stopped: ${Plural.count(n, "failed run")}$why",
            Some(s"review manager stopped [[$slug]] after ${Plural.count(n, "failed run")}$why (agent run ${run.take(8)})"),
            state.copy(before = None))
        else Next.Retry(now.plus(backoff(s.backoffMinutes, s.maxBackoffMinutes, n)), n, s"run failed ($n of ${s.maxFailures})$why")

  /** `base · 2^(failures-1)` minutes, at most `max`. */
  def backoff(baseMinutes: Int, maxMinutes: Int, failures: Int): Duration =
    val exp  = math.min(30, math.max(0, failures - 1))
    val mins = math.min(maxMinutes.toLong, baseMinutes.toLong * (1L << exp))
    Duration.ofMinutes(math.max(1L, mins))

  // ── dispatch ───────────────────────────────────────────────────────────────────────────────────

  /** What dispatch needs of an item. */
  final case class Candidate(id: Long, target: String, priority: Int, notBefore: Option[Instant])

  /** May it start now? */
  def due(c: Candidate, now: Instant): Boolean = c.notBefore.forall(!_.isAfter(now))

  /** The dispatch order within one manager: priority (highest first), then the earliest `notBefore`
    * (none first), then the oldest. */
  def order(cs: List[Candidate]): List[Candidate] =
    cs.sortBy(c => (-c.priority, c.notBefore.map(_.toEpochMilli).getOrElse(Long.MinValue), c.id))

  /** How many runs a manager may start now: the global room (`maxConcurrent` minus every agent run
    * queued or running, a person's too), its own (`maxRuns` minus its queued/running; `maxOpen` minus
    * every unsettled one, paused and waiting included), and its pools' (`poolCap`, the calls its models'
    * plan pools take at once). Never negative. */
  def capacity(maxConcurrent: Int, activeAll: Int, maxRuns: Int, mineRunning: Int, maxOpen: Int, mineOpen: Int,
      poolCap: Option[Int]): Int =
    List(maxConcurrent - activeAll, maxRuns - mineRunning, maxOpen - mineOpen,
      poolCap.fold(Int.MaxValue)(_ - mineRunning)).min.max(0)

  /** Several managers sharing the global room: one item from each in turn (in the order given), each
    * within its own `cap`, until `room` is used or nothing is left. */
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

  /** Is a hold over: its date passed, or the source it waits for exists. A hold with neither waits for
    * a person's release. */
  def released(h: Hold, now: Instant, sourceExists: String => Boolean): Boolean =
    h.until.flatMap(parseInstant).exists(!_.isAfter(now)) || h.untilSource.map(_.trim).filter(_.nonEmpty).exists(sourceExists)

  /** `2026-11-01` (UTC midnight) or an ISO instant. */
  def parseInstant(s: String): Option[Instant] =
    val t = s.trim
    scala.util.Try(Instant.parse(t)).toOption
      .orElse(scala.util.Try(java.time.LocalDate.parse(t).atStartOfDay(java.time.ZoneOffset.UTC).toInstant).toOption)

  /** The status line a manager shows. */
  def statusNote(state: State, starved: Option[String], capacity: Int, pending: Int, due: Int, inFlight: Int,
      budget: Option[String]): String =
    if state == State.Stopped then "stopped"
    else if state == State.Paused then (if inFlight > 0 then s"paused (${Plural.count(inFlight, "run")} in flight)" else "paused")
    else budget.orElse(starved.map(s => s"starved: $s")).getOrElse {
      if pending == 0 && inFlight == 0 then "idle"
      else if due > 0 && capacity == 0 then s"at capacity (${Plural.count(inFlight, "run")} in flight, $pending pending)"
      else if pending > 0 then s"$pending pending" + (if inFlight > 0 then s", ${Plural.count(inFlight, "run")} in flight" else "")
      else s"${Plural.count(inFlight, "run")} in flight"
    }

  // ── hints ──────────────────────────────────────────────────────────────────────────────────────

  /** Does a structured hint effect name this target (by slug, or by one of its tags/topics/goals)? */
  def matches(e: HintEffect, target: String, tags: List[String], topics: List[String], goals: List[String]): Boolean =
    def eq(a: String, b: String) = a.trim.equalsIgnoreCase(b.trim)
    e.targets.exists(eq(_, target)) || e.tag.exists(t => tags.exists(eq(_, t))) ||
      e.topic.exists(t => topics.exists(eq(_, t))) || e.goal.exists(g => goals.exists(eq(_, g)))

  /** The notes a run is given: the manager's live standing hints and unconsumed once-hints, manager-wide
    * or about this item, oldest first. */
  def notesFor(hints: List[ManagerHint], itemId: Long): List[ManagerHint] =
    hints.filter(h => h.retiredAt.isEmpty && h.itemId.forall(_ == itemId) && h.text.trim.nonEmpty).sortBy(_.id)
