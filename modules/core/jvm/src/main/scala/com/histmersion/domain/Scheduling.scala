package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import com.histmersion.api.Agents.RunState
import com.histmersion.domain.AgentProposals.Proposal

/** The pure parts of a goal's schedules and of the two scheduled pipelines
  * (docs/research-embedded-agents.md §14): whether a due schedule may start a run now (the previous
  * run, the daily/monthly cost caps, `max-concurrent-runs`), what an acquisition sweep may queue
  * (never a work already in the corpus or the queue), and what a currency check makes of the papers
  * citing a source. */
object Scheduling:

  /** What the scheduler does with a due schedule. `Skip` moves the schedule on to its next time;
    * `Defer` leaves it due, for the next tick. */
  enum Decision:
    case Start
    case Skip(reason: String)
    case Defer(reason: String)

  /** A run that has not settled: a schedule does not start another beside it. */
  def unsettled(s: RunState): Boolean = !s.isTerminal

  /** Before a scheduled run starts: its previous run must have settled, today's and this month's
    * agent spend must be under the caps (checked BEFORE the start, so a scheduled run never begins
    * past a cap), and fewer than `maxConcurrent` runs may be queued or running. */
  def decide(previous: Option[RunState], spentDay: BigDecimal, spentMonth: BigDecimal, dailyUsd: Double,
      monthlyUsd: Double, active: Int, maxConcurrent: Int): Decision =
    previous.filter(unsettled) match
      case Some(s) => Decision.Skip(s"its previous run is still ${s.wire}")
      case None =>
        if spentDay >= BigDecimal(dailyUsd) then
          Decision.Skip(s"today's agent spend (${AgentsMarkdown.usd(spentDay.toDouble)} USD) reached agents.budgets.daily-usd")
        else if spentMonth >= BigDecimal(monthlyUsd) then
          Decision.Skip(s"this month's agent spend (${AgentsMarkdown.usd(spentMonth.toDouble)} USD) reached agents.budgets.monthly-usd")
        else if active >= math.max(1, maxConcurrent) then
          Decision.Defer(s"$active run(s) already queued or running (agents.max-concurrent-runs)")
        else Decision.Start

  /** The cost cap of a scheduled run: the schedule's own, else the goal's, else the server default. */
  def budget(schedule: Option[Double], goal: Option[Double], default: Double): Double =
    schedule.orElse(goal).filter(_ > 0).getOrElse(default)

  // ── acquisition sweep ───────────────────────────────────────────────────────────────────────────

  private val DoiIn = """10\.\d{4,9}/[-._;()/:A-Za-z0-9<>\[\]]+""".r

  /** Every DOI written in a text (an acquisition item), normalised — how the queue is deduplicated. */
  def dois(text: String): Set[String] =
    DoiIn.findAllIn(Option(text).getOrElse("")).flatMap(Acquire.normalizeDoi).toSet

  /** A candidate a sweep found, as the ranking step sees it. */
  final case class Candidate(doi: String, title: String, year: Option[Int], authors: List[String], venue: Option[String],
      isOa: Boolean, query: String, abstractText: Option[String]) derives KindlingsCodecAsObject

  /** The works a sweep may consider: with a DOI, not retracted, not in the corpus, not already in the
    * acquisition queue (open or done), each once (the first query that found it). Returns the
    * candidates and how many were left out for each reason. */
  def candidates(found: List[(String, WorkRef)], queued: Set[String], max: Int): (List[Candidate], Map[String, Int]) =
    val seen = scala.collection.mutable.LinkedHashMap.empty[String, Candidate] // insertion-ordered dedupe
    val skipped = scala.collection.mutable.Map.empty[String, Int].withDefaultValue(0)
    found.foreach { (q, w) =>
      w.doi.flatMap(Acquire.normalizeDoi) match
        case None                          => skipped("no DOI") += 1
        case Some(_) if w.isRetracted      => skipped("retracted") += 1
        case Some(_) if w.inDb             => skipped("in the corpus") += 1
        case Some(d) if queued.contains(d) => skipped("already queued") += 1
        case Some(d) if seen.contains(d)   => ()
        case Some(d) =>
          seen(d) = Candidate(d, w.title.getOrElse(d), w.year, w.authors.take(4), w.venue, w.isOa, q,
            w.abstractText.map(_.take(600)))
    }
    val all = seen.values.toList
    if all.size > max then skipped("over the sweep limit") += all.size - max
    (all.take(max), skipped.toMap)

  /** The ranking model's answer: which candidates belong to the goal. */
  final case class Pick(doi: String, why: String) derives KindlingsCodecAsObject
  object Pick:
    given sttp.tapir.Schema[Pick] = hearth.kindlings.tapirschemaderivation.KindlingsSchema.derived[Pick].schema
      .modify(_.doi)(_.description("The DOI of a candidate, exactly as listed"))
      .modify(_.why)(_.description("Why it belongs to the goal, in one sentence"))
  final case class Ranking(picks: List[Pick] = Nil) derives KindlingsCodecAsObject
  object Ranking:
    given sttp.tapir.Schema[Ranking] = hearth.kindlings.tapirschemaderivation.KindlingsSchema.derived[Ranking].schema
      .modify(_.picks)(_.description("The candidates that belong to the goal, most important first; leave out the rest"))

  /** A pick must name a listed candidate; a DOI that was not offered is a problem (a model inventing
    * a work), a repeated one is ignored. On the last attempt the valid picks are kept. */
  def checkRanking(r: Ranking, offered: List[Candidate], last: Boolean): Either[List[String], (List[(Candidate, String)], List[String])] =
    val byDoi = offered.map(c => c.doi -> c).toMap
    val parsed = r.picks.map(p => (p, Acquire.normalizeDoi(Option(p.doi).getOrElse("")).flatMap(byDoi.get)))
    val problems = parsed.collect { case (p, None) => s"'${p.doi}' is not one of the listed candidates" }
    val kept = parsed.collect { case (p, Some(c)) => (c, Option(p.why).map(_.trim).getOrElse("")) }.distinctBy(_._1.doi)
    if problems.nonEmpty && !last then Left(problems)
    else Right((kept, problems.map(p => s"rank: dropped — $p")))

  /** A watcher's sweep with auto-ingest on: the open-access picks (`isOa`) are offered to `ingest` in the
    * model's order until `allowed` of them were ingested (one that fails — no legal file — does not count
    * and the next is tried); every closed pick, every failed one and the open ones past the cap stay for
    * the acquisition list. Returns what was ingested, in order. Nothing is offered past the cap. */
  def ingestPicks[A, B](picks: List[A], isOa: A => Boolean, allowed: Int)(ingest: A => Option[B]): List[(A, B)] =
    picks.filter(isOa).foldLeft(Vector.empty[(A, B)]) { (done, p) =>
      if done.size >= allowed then done else ingest(p).fold(done)(b => done :+ (p -> b))
    }.toList

  /** [[ingestPicks]] when the misses matter (a watcher with an ingest backlog): the same order and cap,
    * and what was offered and FAILED, with why — a work with no legal file goes to the acquisition list,
    * any other failure back to the backlog. The open-access picks never offered are the caller's: they
    * wait in the backlog. */
  def ingestOa[A, B](picks: List[A], isOa: A => Boolean, allowed: Int)(ingest: A => Either[String, B]): (List[(A, B)], List[(A, String)]) =
    val (done, failed) = picks.filter(isOa).foldLeft((Vector.empty[(A, B)], Vector.empty[(A, String)])) { case ((ok, ko), p) =>
      if ok.size >= allowed then (ok, ko)
      else ingest(p).fold(why => (ok, ko :+ (p -> why)), b => (ok :+ (p -> b), ko))
    }
    (done.toList, failed.toList)

  /** The acquisition item a picked candidate becomes (the DOI as a resolver URL, so [[dois]] finds it). */
  def acquireText(c: Candidate, goal: String, why: String): String =
    val who  = c.authors.headOption.map(a => if c.authors.size > 1 then s"$a et al." else a)
    val head = List(who, c.year.map(y => s"($y)")).flatten.mkString(" ")
    val oa   = if c.isOa then " (open access)" else ""
    val reason = Option(why).map(_.trim).filter(_.nonEmpty).fold("")(w => s": $w")
    s"${if head.isEmpty then "" else s"$head. "}${c.title}. https://doi.org/${c.doi}$oa — for goal $goal$reason"

  // ── currency check ──────────────────────────────────────────────────────────────────────────────

  /** What a currency check makes of one paper citing `target`: a disagreement edge to propose (both
    * ends in the corpus), a paper to queue for acquisition (it disagrees but is not in the corpus), or
    * nothing (it confirms or merely cites). `supersedes` becomes `superseded_by` from the older source
    * to the newer, the direction the corpus records. Evidence is the citation context: code never
    * invents a quote. */
  enum CurrencyAction:
    case Propose(p: Proposal)
    case Fetch(row: FollowupRow)
    case Ignore

  def currencyAction(target: String, row: FollowupRow): CurrencyAction =
    val kind = row.proposedKind.trim.toLowerCase
    val disagreement = kind == Acquire.EdgeKinds.Refutes || kind == Acquire.EdgeKinds.Challenges ||
      kind == Acquire.EdgeKinds.Supersedes
    if !disagreement || row.citekey.contains(target) then CurrencyAction.Ignore
    else
      row.citekey.filter(_ != target) match
        case None if row.doi.isDefined => CurrencyAction.Fetch(row)
        case None                      => CurrencyAction.Ignore
        case Some(citing) =>
          val context  = row.contexts.map(_.trim).filter(_.nonEmpty).headOption
          val evidence = s"followups: the citing paper reads as '$kind'" +
            (if row.intents.nonEmpty then s" (intents: ${row.intents.mkString(", ")})" else "") +
            context.fold("")(c => s"; context: ${c.take(400)}")
          val (from, edge, to) =
            if kind == Acquire.EdgeKinds.Supersedes then (target, EdgeKind.SupersededBy, citing)
            else (citing, if kind == Acquire.EdgeKinds.Refutes then EdgeKind.Refutes else EdgeKind.Challenges, target)
          CurrencyAction.Propose(Proposal(Agents.ApprovalKind.Edge, from, edgeKind = Some(edge), target = Some(to),
            evidence = evidence))

  /** The acquisition item a disagreeing paper outside the corpus becomes. */
  def acquireText(row: FollowupRow, target: String, goal: String): String =
    val year = row.year.fold("")(y => s" ($y)")
    s"${row.title.getOrElse("untitled")}$year. https://doi.org/${row.doi.getOrElse("")} — " +
      s"${row.proposedKind} [[${target}]] (currency check, goal $goal)"
