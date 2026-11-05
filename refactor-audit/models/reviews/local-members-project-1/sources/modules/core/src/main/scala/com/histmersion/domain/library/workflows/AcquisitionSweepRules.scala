package com.histmersion.domain.library.workflows

import com.histmersion.domain.library.acquisition.Ident

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

  /** A candidate a sweep found, as the ranking step sees it. */
  type IngestOaLoopResultPart2Part2 = IngestOaLoopResultPart2Part2.T
  object IngestOaLoopResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IngestOaLoopKoPart2 = IngestOaLoopKoPart2.T
  object IngestOaLoopKoPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type CandidatesResultPart2Part1 = CandidatesResultPart2Part1.T
  object CandidatesResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CandidatesResultPart2Part2 = CandidatesResultPart2Part2.T
  object CandidatesResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CandidatesFoundPart1 = CandidatesFoundPart1.T
  object CandidatesFoundPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CandidatesQueued = CandidatesQueued.T
  object CandidatesQueued extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CandidatesMax = CandidatesMax.T
  object CandidatesMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckRankingResultPart1 = CheckRankingResultPart1.T
  object CheckRankingResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckRankingResultPart2Part1Part2 = CheckRankingResultPart2Part1Part2.T
  object CheckRankingResultPart2Part1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckRankingResultPart2Part2 = CheckRankingResultPart2Part2.T
  object CheckRankingResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckRankingLast = CheckRankingLast.T
  object CheckRankingLast extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IngestPicksIsOaResult = IngestPicksIsOaResult.T
  object IngestPicksIsOaResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IngestPicksAllowed = IngestPicksAllowed.T
  object IngestPicksAllowed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IngestOaResultPart2Part2 = IngestOaResultPart2Part2.T
  object IngestOaResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IngestOaIsOaResult = IngestOaIsOaResult.T
  object IngestOaIsOaResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IngestOaAllowed = IngestOaAllowed.T
  object IngestOaAllowed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type IngestOaIngestResultPart1 = IngestOaIngestResultPart1.T
  object IngestOaIngestResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AcquireTextResult = AcquireTextResult.T
  object AcquireTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AcquireTextGoal = AcquireTextGoal.T
  object AcquireTextGoal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AcquireTextWhy = AcquireTextWhy.T
  object AcquireTextWhy extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  final case class Candidate(
    doi: com.histmersion.domain.library.Doi,
    title: com.histmersion.api.library.Title,
    year: Option[com.histmersion.api.catalogue.Year],
    authors: List[com.histmersion.api.library.AuthorName],
    venue: Option[com.histmersion.api.library.Venue],
    isOa: Candidate.IsOa,
    query: Candidate.Query,
    abstractText: Option[Candidate.AbstractText]
  ) derives KindlingsCodecAsObject

  object Candidate:
    type IsOa = IsOa.T
    object IsOa extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Query = Query.T
    object Query extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AbstractText = AbstractText.T
    object AbstractText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The works a sweep may consider: with a DOI, not retracted, not in the corpus, not already in the acquisition queue
    * (open or done), each once (the first query that found it). Returns the candidates and how many were left out for
    * each reason.
    */
  def candidates(found: List[(com.histmersion.domain.library.workflows.AcquisitionSweepRules.CandidatesFoundPart1, WorkRef)], queued: Set[com.histmersion.domain.library.workflows.AcquisitionSweepRules.CandidatesQueued], max: com.histmersion.domain.library.workflows.AcquisitionSweepRules.CandidatesMax): (List[Candidate], Map[com.histmersion.domain.library.workflows.AcquisitionSweepRules.CandidatesResultPart2Part1, com.histmersion.domain.library.workflows.AcquisitionSweepRules.CandidatesResultPart2Part2]) =
    val seen = scala.collection.mutable.LinkedHashMap.empty[String, Candidate] // insertion-ordered dedupe
    val skipped = scala.collection.mutable.Map.empty[String, Int].withDefaultValue(0)
    found.foreach { (q, w) =>
      w.doi.flatMap(d => Ident.doi(d.value)) match
        case None                          => skipped("no DOI") += 1
        case Some(_) if w.isRetracted      => skipped("retracted") += 1
        case Some(_) if w.inDb             => skipped("in the corpus") += 1
        case Some(d) if queued.contains(d) => skipped("already queued") += 1
        case Some(d) if seen.contains(d)   => ()
        case Some(d)                       =>
          seen(d) = Candidate(
            d,
            w.title.getOrElse(com.histmersion.api.library.Title(d)),
            w.year,
            w.authors.take(4),
            w.venue,
            w.isOa,
            q,
            w.abstractText.map(_.take(600))
          )
    }
    val all = seen.values.toList
    if all.size > max then skipped("over the sweep limit") += all.size - max
    (all.take(max), skipped.toMap)

  /** The ranking model's answer: which candidates belong to the goal. */
  final case class Pick(doi: com.histmersion.domain.library.Doi, why: Pick.Why) derives KindlingsCodecAsObject
  object Pick:
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
      last: com.histmersion.domain.library.workflows.AcquisitionSweepRules.CheckRankingLast
  ): Either[List[com.histmersion.domain.library.workflows.AcquisitionSweepRules.CheckRankingResultPart1], (List[(Candidate, com.histmersion.domain.library.workflows.AcquisitionSweepRules.CheckRankingResultPart2Part1Part2)], List[com.histmersion.domain.library.workflows.AcquisitionSweepRules.CheckRankingResultPart2Part2])] =
    val byDoi = offered.map(c => c.doi -> c).toMap
    val parsed = r.picks.map(p => (p, Ident.doi(Option(p.doi).getOrElse("")).flatMap(byDoi.get)))
    val problems = parsed.collect { case (p, None) => s"'${p.doi}' is not one of the listed candidates" }
    val kept = parsed.collect { case (p, Some(c)) => (c, Option(p.why).map(_.trim).getOrElse("")) }.distinctBy(_._1.doi)
    if problems.nonEmpty && !last then Left(problems)
    else Right((kept, problems.map(p => s"rank: dropped — $p")))

  /** A watcher's sweep with auto-ingest on: the open-access picks (`isOa`) are offered to `ingest` in the model's order
    * until `allowed` of them were ingested (one that fails — no legal file — does not count and the next is tried);
    * every closed pick, every failed one and the open ones past the cap stay for the acquisition list. Returns what was
    * ingested, in order. Nothing is offered past the cap.
    */
  def ingestPicks[A, B](picks: List[A], isOa: A => com.histmersion.domain.library.workflows.AcquisitionSweepRules.IngestPicksIsOaResult, allowed: com.histmersion.domain.library.workflows.AcquisitionSweepRules.IngestPicksAllowed)(ingest: A => Option[B]): List[(A, B)] =
    picks
      .filter(isOa)
      .foldLeft(Vector.empty[(A, B)]) { (done, p) =>
        if done.size >= allowed then done else ingest(p).fold(done)(b => done :+ (p -> b))
      }
      .toList

  /** [[ingestPicks]] when the misses matter (a watcher with an ingest backlog): the same order and cap, and what was
    * offered and FAILED, with why — a work with no legal file goes to the acquisition list, any other failure back to
    * the backlog. The open-access picks never offered are the caller's: they wait in the backlog.
    */
  def ingestOa[A, B](picks: List[A], isOa: A => com.histmersion.domain.library.workflows.AcquisitionSweepRules.IngestOaIsOaResult, allowed: com.histmersion.domain.library.workflows.AcquisitionSweepRules.IngestOaAllowed)(
      ingest: A => Either[com.histmersion.domain.library.workflows.AcquisitionSweepRules.IngestOaIngestResultPart1, B]
  ): (List[(A, B)], List[(A, com.histmersion.domain.library.workflows.AcquisitionSweepRules.IngestOaResultPart2Part2)]) =
    @tailrec def loop(rest: List[A], ok: Vector[(A, B)], ko: Vector[(A, com.histmersion.domain.library.workflows.AcquisitionSweepRules.IngestOaLoopKoPart2)]): (List[(A, B)], List[(A, com.histmersion.domain.library.workflows.AcquisitionSweepRules.IngestOaLoopResultPart2Part2)]) =
      rest match
        case p :: tail if ok.size < allowed =>
          ingest(p) match
            case Left(why) => loop(tail, ok, ko :+ (p -> why))
            case Right(b)  => loop(tail, ok :+ (p -> b), ko)
        case _ => (ok.toList, ko.toList)
    loop(picks.filter(isOa), Vector.empty, Vector.empty)

  /** The acquisition item a picked candidate becomes (the DOI as a resolver URL, so
    * [[com.histmersion.domain.Scheduling.dois]] finds it).
    */
  def acquireText(c: Candidate, goal: com.histmersion.domain.library.workflows.AcquisitionSweepRules.AcquireTextGoal, why: com.histmersion.domain.library.workflows.AcquisitionSweepRules.AcquireTextWhy): com.histmersion.domain.library.workflows.AcquisitionSweepRules.AcquireTextResult =
    val who = c.authors.headOption.map(a => if c.authors.size > 1 then s"$a et al." else a)
    val head = List(who, c.year.map(y => s"($y)")).flatten.mkString(" ")
    val oa = if c.isOa then " (open access)" else ""
    val reason = Option(why).map(_.trim).filter(_.nonEmpty).fold("")(w => s": $w")
    s"${if head.isEmpty then "" else s"$head. "}${c.title}. https://doi.org/${c.doi}$oa — for goal $goal$reason"
