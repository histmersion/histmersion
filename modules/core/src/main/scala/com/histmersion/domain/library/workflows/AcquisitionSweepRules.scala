package com.histmersion.domain.library.workflows

import com.histmersion.domain.library.Doi

import scala.annotation.tailrec
import com.histmersion.api.library.{Acquire, WorkRef}
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given

import hearth.kindlings.circederivation.KindlingsCodecAsObject

/** The pure rules of an acquisition sweep (docs/research-embedded-agents.md §14): what a sweep may offer the ranking
  * model (never a work already in the corpus or the queue), the model's answer and its check, which open-access picks
  * are ingested within an allowance, and the acquisition item a pick becomes.
  */
object AcquisitionSweepRules:

  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Allowance = Allowance.T
  object Allowance extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LastAttempt = LastAttempt.T
  object LastAttempt extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SkipReason = SkipReason.T
  object SkipReason extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SkipCount = SkipCount.T
  object SkipCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Dropped = Dropped.T
  object Dropped extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IngestFailure = IngestFailure.T
  object IngestFailure extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AcquireText = AcquireText.T
  object AcquireText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** A candidate a sweep found, as the ranking step sees it. */
  final case class Candidate(
    doi: Doi,
    title: com.histmersion.api.library.Title,
    year: Option[com.histmersion.api.catalogue.Year],
    authors: List[com.histmersion.api.library.AuthorName],
    venue: Option[com.histmersion.api.library.Source.Venue],
    isOa: WorkRef.IsOa,
    query: Candidate.Query,
    abstractText: Option[WorkRef.AbstractText]
  ) derives KindlingsCodecAsObject

  object Candidate:
    type Query = Query.T
    object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The works a sweep may consider: with a DOI, not retracted, not in the corpus, not already in the acquisition queue
    * (open or done), each once (the first query that found it). Returns the candidates and how many were left out for
    * each reason.
    */
  def candidates(found: List[(Candidate.Query, WorkRef)], queued: Set[Doi], max: Limit): (List[Candidate], Map[SkipReason, SkipCount]) =
    val seen = scala.collection.mutable.LinkedHashMap.empty[Doi, Candidate] // insertion-ordered dedupe
    val skipped = scala.collection.mutable.Map.empty[String, Int].withDefaultValue(0)
    found.foreach { (q, w) =>
      w.doi.flatMap(d => Doi.normalize(d.value)) match
        case None                          => skipped("no DOI") += 1
        case Some(_) if w.isRetracted.value => skipped("retracted") += 1
        case Some(_) if w.inDb.value        => skipped("in the corpus") += 1
        case Some(d) if queued.contains(d) => skipped("already queued") += 1
        case Some(d) if seen.contains(d)   => ()
        case Some(d)                       =>
          seen(d) = Candidate(
            d,
            w.title.getOrElse(com.histmersion.api.library.Title(d.value)),
            w.year,
            w.authors.take(4),
            w.venue,
            w.isOa,
            q,
            w.abstractText.map(a => WorkRef.AbstractText(a.value.take(600)))
          )
    }
    val all = seen.values.toList
    if all.size > max.value then skipped("over the sweep limit") += all.size - max.value
    (all.take(max.value), skipped.toMap.map((reason, count) => SkipReason(reason) -> SkipCount(count)))

  /** The ranking model's answer: which candidates belong to the goal. */
  final case class Pick(doi: Pick.DoiText, why: Pick.Why) derives KindlingsCodecAsObject
  object Pick:
    type DoiText = DoiText.T
    object DoiText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Why = Why.T
    object Why extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

    given sttp.tapir.Schema[Pick] = hearth.kindlings.tapirschemaderivation.KindlingsSchema
      .derived[Pick]
      .schema
      .modify(_.doi)(_.description("The DOI of a candidate, exactly as listed"))
      .modify(_.why)(_.description("Why it belongs to the goal, in one sentence"))
  final case class Ranking(picks: List[Pick] = Nil) derives KindlingsCodecAsObject
  object Ranking:
    given sttp.tapir.Schema[Ranking] = hearth.kindlings.tapirschemaderivation.KindlingsSchema
      .derived[Ranking]
      .schema
      .modify(_.picks)(
        _.description("The candidates that belong to the goal, most important first; leave out the rest")
      )

  /** A pick must name a listed candidate; a DOI that was not offered is a problem (a model inventing a work), a
    * repeated one is ignored. On the last attempt the valid picks are kept.
    */
  def checkRanking(
      r: Ranking,
      offered: List[Candidate],
      last: LastAttempt
  ): Either[List[Problem], (List[(Candidate, Pick.Why)], List[Dropped])] =
    val byDoi = offered.map(c => c.doi -> c).toMap
    val parsed = r.picks.map(p => (p, Doi.normalize(p.doi.value).flatMap(byDoi.get)))
    val problems = parsed.collect { case (p, None) => Problem(s"'${p.doi}' is not one of the listed candidates") }
    val kept = parsed.collect { case (p, Some(c)) => (c, Pick.Why(Option(p.why).map(_.value.trim).getOrElse(""))) }.distinctBy(_._1.doi)
    if problems.nonEmpty && !last.value then Left(problems)
    else Right((kept, problems.map(p => Dropped(s"rank: dropped — $p"))))

  /** A watcher's sweep with auto-ingest on: the open-access picks (`isOa`) are offered to `ingest` in the model's order
    * until `allowed` of them were ingested (one that fails — no legal file — does not count and the next is tried);
    * every closed pick, every failed one and the open ones past the cap stay for the acquisition list. Returns what was
    * ingested, in order. Nothing is offered past the cap.
    */
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
  def ingestOa[A, B](picks: List[A], isOa: A => Boolean, allowed: Allowance)(
      ingest: A => Either[IngestFailure, B]
  ): (List[(A, B)], List[(A, IngestFailure)]) =
    @tailrec def loop(rest: List[A], ok: Vector[(A, B)], ko: Vector[(A, IngestFailure)]): (List[(A, B)], List[(A, IngestFailure)]) =
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
  def acquireText(c: Candidate, goal: com.histmersion.domain.automation.Goal.Slug, why: Pick.Why): AcquireText =
    val who = c.authors.headOption.map(a => if c.authors.size > 1 then s"$a et al." else a.value)
    val head = List(who, c.year.map(y => s"($y)")).flatten.mkString(" ")
    val oa = if c.isOa.value then " (open access)" else ""
    val reason = Option(why).map(_.value.trim).filter(_.nonEmpty).fold("")(w => s": $w")
    AcquireText(s"${if head.isEmpty then "" else s"$head. "}${c.title}. https://doi.org/${c.doi}$oa — for goal $goal$reason")
