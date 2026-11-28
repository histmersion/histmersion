package com.histmersion
package domain.library.workflows
import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Problem.{Content, Raw}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.domain.library.Doi

import scala.annotation.tailrec
import com.histmersion.api.library.{Acquire, WorkRef}
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Title
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Source
import com.histmersion.domain.automation.Goal
import com.histmersion.lint.{tolerated, LintRule}

/** The pure rules of an acquisition sweep (docs/research-embedded-agents.md §14): what a sweep may offer the ranking
  * model (never a work already in the corpus or the queue), the model's answer and its check, which open-access picks
  * are ingested within an allowance, and the acquisition item a pick becomes.
  */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
object AcquisitionSweepRules:

  /** Why a candidate belongs to the goal, in the ranking model's words: what it said, not a failure. */
  type Rationale = Rationale.T
  object Rationale extends Newtype[String, Pure]

  type Limit = Limit.T
  object Limit extends Newtype[Int, Pure]
  type Allowance = Allowance.T
  object Allowance extends Newtype[Int, Pure]
  type LastAttempt = LastAttempt.T
  object LastAttempt extends Newtype[Boolean, Pure]
  type SkipCount = SkipCount.T
  object SkipCount extends Newtype[Int, Pure]

  /** Why a found work is not offered to the ranking: the account of the sweep's choice, not a failure. */
  enum LeftOut derives CanEqual:
    case NoDoi, Retracted, InCorpus, AlreadyQueued, OverLimit

    /** The reason as the sweep's report counts it. */
    def said: String = this match
      case NoDoi         => "no DOI"
      case Retracted     => "retracted"
      case InCorpus      => "in the corpus"
      case AlreadyQueued => "already queued"
      case OverLimit     => "over the sweep limit"
  type Dropped = Dropped.T
  object Dropped extends Newtype[String, Pure]
  type AcquireText = AcquireText.T
  object AcquireText extends Newtype[String, Pure]

  /** A candidate a sweep found, as the ranking step sees it. */
  final case class Candidate(
      doi: Doi,
      title: Title,
      year: Option[Year],
      authors: List[AuthorName],
      venue: Option[Source.Venue],
      isOa: WorkRef.IsOa,
      query: Candidate.Query,
      abstractText: Option[WorkRef.AbstractText]
  )

  object Candidate:
    type Query = Query.T
    object Query extends Newtype[String, Pure]

  /** The works a sweep may consider: with a DOI, not retracted, not in the corpus, not already in the acquisition queue
    * (open or done), each once (the first query that found it). Returns the candidates and how many were left out for
    * each reason.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def candidates(
      found: List[(Candidate.Query, WorkRef)],
      queued: Set[Doi],
      max: Limit
  ): (List[Candidate], Map[LeftOut, SkipCount]) =
    val seen = scala.collection.mutable.LinkedHashMap.empty[Doi, Candidate] // insertion-ordered dedupe
    val skipped = scala.collection.mutable.Map.empty[LeftOut, Int].withDefaultValue(0)
    found.foreach { (q, w) =>
      w.doi.flatMap(d => Doi.normalize(d.value)) match
        case None                                 => skipped(LeftOut.NoDoi) += 1
        case Some(_) if w.isRetracted.isRetracted => skipped(LeftOut.Retracted) += 1
        case Some(_) if w.inDb.inDb               => skipped(LeftOut.InCorpus) += 1
        case Some(d) if queued.contains(d)        => skipped(LeftOut.AlreadyQueued) += 1
        case Some(d) if seen.contains(d)          => ()
        case Some(d)                              =>
          seen(d) = Candidate(
            d,
            w.title.getOrElse(Title(d.value)),
            w.year,
            w.authors.take(4),
            w.venue,
            w.isOa,
            q,
            w.abstractText.map(a => WorkRef.AbstractText(a.value.take(600)))
          )
    }
    val all = seen.values.toList
    if all.size > max.value then skipped(LeftOut.OverLimit) += all.size - max.value
    (all.take(max.value), skipped.toMap.map((reason, count) => reason -> SkipCount(count)))

  /** The ranking model's answer: which candidates belong to the goal. */
  final case class Pick(doi: Pick.ListedDoi, why: Rationale)
  object Pick:
    type ListedDoi = ListedDoi.T
    object ListedDoi extends Newtype[String, Pure]
  final case class Ranking(picks: List[Pick] = Nil)

  /** A pick must name a listed candidate; a DOI that was not offered is a problem (a model inventing a work), a
    * repeated one is ignored. On the last attempt the valid picks are kept.
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def checkRanking(
      r: Ranking,
      offered: List[Candidate],
      last: LastAttempt
  ): Either[List[Problem], (List[(Candidate, Rationale)], List[Dropped])] =
    val byDoi = offered.map(c => c.doi -> c).toMap
    val parsed = r.picks.map(p => (p, Doi.normalize(p.doi.value).flatMap(byDoi.get)))
    val problems = parsed.collect { case (p, None) =>
      Content.Answer(Content.Requirement.ListedCandidate(Raw.of(p.doi)))
    }
    val kept = parsed
      .collect { case (p, Some(c)) =>
        (c, Rationale(Option(p.why).fold("")(w => s"$w".trim)))
      }
      .distinctBy(_._1.doi)
    if problems.nonEmpty && !last.value then Left(problems)
    else Right((kept, problems.map(p => Dropped(s"rank: dropped — ${p.sentence}"))))

  /** A watcher's sweep with auto-ingest on: the open-access picks (`isOa`) are offered to `ingest` in the model's order
    * until `allowed` of them were ingested (one that fails — no legal file — does not count and the next is tried);
    * every closed pick, every failed one and the open ones past the cap stay for the acquisition list. Returns what was
    * ingested, in order. Nothing is offered past the cap.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def ingestPicks[A, B](picks: List[A], isOa: A => Boolean, allowed: Allowance)(ingest: A => Option[B]): List[(A, B)] =
    picks
      .filter(isOa)
      .foldLeft(Vector.empty[(A, B)]) { (done, p) =>
        if done.size >= allowed.value then done else ingest(p).fold(done)(b => done :+ (p -> b))
      }
      .toList

  /** [[ingestPicks]] when the misses matter (a watcher with an ingest backlog): the same order and cap, and what was
    * offered and FAILED, with why — a work with no legal file goes to the acquisition list, any other failure back to
    * the backlog. The open-access picks never offered are the caller's: they wait in the backlog.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def ingestOa[A, B](picks: List[A], isOa: A => Boolean, allowed: Allowance)(
      ingest: A => Either[Problems, B]
  ): (List[(A, B)], List[(A, Problems)]) =
    @tailrec def loop(
        rest: List[A],
        ok: Vector[(A, B)],
        ko: Vector[(A, Problems)]
    ): (List[(A, B)], List[(A, Problems)]) =
      rest match
        case p :: tail if ok.size < allowed.value =>
          ingest(p) match
            case Left(why) => loop(tail, ok, ko :+ (p -> why))
            case Right(b)  => loop(tail, ok :+ (p -> b), ko)
        case _ => (ok.toList, ko.toList)
    loop(picks.filter(isOa), Vector.empty, Vector.empty)

  /** The acquisition item a picked candidate becomes (the DOI as a resolver URL, so
    * [[com.histmersion.domain.Scheduling.dois]] finds it).
    */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def acquireText(c: Candidate, goal: Goal.Slug, why: Rationale): AcquireText =
    val who = c.authors.headOption.map(a => if c.authors.size > 1 then s"$a et al." else a.value)
    val head = List(who, c.year.map(y => s"($y)")).flatten.mkString(" ")
    val oa = if c.isOa.isOa then " (open access)" else ""
    val reason = Option(why).map(w => s"$w".trim).filter(_.nonEmpty).fold("")(w => s": $w")
    AcquireText(
      s"${if head.isEmpty then "" else s"$head. "}${c.title}. https://doi.org/${c.doi}$oa — for goal $goal$reason"
    )
