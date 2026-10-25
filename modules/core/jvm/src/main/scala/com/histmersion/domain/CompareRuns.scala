package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.Agents.{RunDetail, StepInfo, StepStatus, Usage}
import com.histmersion.api.RunCompare.*
import io.circe.Decoder
import io.circe.parser.decode
import com.histmersion.values.{Pipeline, RunState}

/** `agent compare` (#992): the pure half. Reads two or more runs' recorded steps (their FULL outputs)
  * and lays them side by side; `billing` names how a configured model is paid for (a plan pool or a
  * price — the server knows, from its config). Nothing is judged: counts, tokens, times, and what one
  * run produced that the others did not. */
object CompareRuns:

  /** A rejected findings answer's quote failures are counted from the verifier's own words. */
  private val QuoteFailure = "the quote is not in the text"

  /** Two major findings are the same one when they sit at the same `where` and their problems share at
    * least this much of their words (Jaccard over the normalised words), or quote the same passage. */
  val ProblemSimilarity = 0.5

  def compare(runs: List[RunDetail], billing: String => Option[String]): Either[String, Comparison] =
    for
      _ <- Either.cond(runs.size >= 2, (), "give at least two runs to compare")
      _ <- Either.cond(runs.map(_.run.id).distinct.size == runs.size, (), "a run is given twice")
      first = runs.head.run
      _ <- runs.find(_.run.pipeline != first.pipeline).map(o =>
             s"run ${o.run.id.take(8)} is ${o.run.pipeline}, run ${first.id.take(8)} ${first.pipeline}: compare runs of one pipeline").toLeft(())
      _ <- runs.find(_.run.target != first.target).map(o =>
             s"run ${o.run.id.take(8)} is on ${o.run.target}, run ${first.id.take(8)} on ${first.target}: compare runs on one target").toLeft(())
    yield
      val pipeline = Pipeline.parse(first.pipeline)
      val names    = unionInOrder(runs.map(_.steps.map(_.name).distinct))
      val rows     = names.map(n => StepRow(n, runs.map(d => Option(d.steps.filter(_.name == n)).filter(_.nonEmpty).map(stepSide(_, billing)))))
      val notes    = runs.collect {
        case d if d.run.status != RunState.Done.wire =>
          s"run ${d.run.id.take(8)} is ${d.run.status}, not done: its outputs are what it had so far"
      }
      val enrich = if pipeline.contains(Pipeline.EnrichSource) then enrichSides(runs) else Nil
      val review = if pipeline.contains(Pipeline.ReviewArticle) then runs.map(reviewSide) else Nil
      val overlaps = if pipeline.contains(Pipeline.ReviewArticle) then majorOverlaps(runs) else Nil
      Comparison(first.pipeline, first.target, runs.map(runSide), rows, enrich, review, overlaps, notes)

  // ── steps and totals ───────────────────────────────────────────────────────────────────────────

  /** Every list's names in one order: the first list's, each later name placed after the name it
    * follows in its own list (a run with more rounds or windows slots its extra steps in). */
  def unionInOrder(lists: List[List[String]]): List[String] =
    lists.foldLeft(Vector.empty[String]) { (acc, names) =>
      names.foldLeft((acc, -1)) { case ((a, pos), n) =>
        a.indexOf(n) match
          case -1 => (a.patch(pos + 1, List(n), 0), pos + 1)
          case i  => (a, i)
      }._1
    }.toList

  /** A step's duration in whole seconds (the store keeps seconds); 0 when a time is unreadable. */
  def seconds(s: StepInfo): Long =
    (for
      a <- scala.util.Try(java.time.Instant.parse(s.startedAt)).toOption
      b <- scala.util.Try(java.time.Instant.parse(s.finishedAt)).toOption
    yield math.max(0L, b.getEpochSecond - a.getEpochSecond)).getOrElse(0L)

  private def sum(us: List[Usage]): Usage = us.foldLeft(Usage.Zero)(_ + _)

  def stepSide(attempts: List[StepInfo], billing: String => Option[String]): StepSide =
    val models = attempts.flatMap(_.model).distinct
    StepSide(models, attempts.flatMap(_.actualModel).distinct, models.flatMap(billing).distinct,
      sum(attempts.map(_.usage)), attempts.map(_.costUsd).sum, attempts.map(seconds).sum, attempts.size,
      attempts.count(_.status == StepStatus.Failed.wire), attempts.flatMap(_.firstCallTokens).headOption,
      Option.when(attempts.exists(_.turns.isDefined))(attempts.flatMap(_.turns).sum))

  private def runSide(d: RunDetail): RunSide =
    val r = d.run
    RunSide(r.id, r.status, r.dryRun, r.model, r.params.overrides, sum(d.steps.map(_.usage)), d.steps.map(_.costUsd).sum,
      d.steps.map(seconds).sum, d.steps.size, d.steps.count(_.status == StepStatus.Failed.wire))

  /** The checkpoint of step `name`: its last finished attempt's output, decoded. */
  private def output[O: Decoder](d: RunDetail, name: String): Option[O] =
    d.steps.filter(s => s.name == name && s.status == StepStatus.Done.wire).lastOption
      .flatMap(_.output).flatMap(decode[O](_).toOption)

  // ── enrich-source ──────────────────────────────────────────────────────────────────────────────

  import EnrichSource.{Classified, Findings, Geocoded, VerifiedFindings}

  private def isFindings(name: String) = name.startsWith("findings-")

  /** The quote check over every attempt of every findings step: (checked, passed). A finished attempt
    * says it itself; a rejected one holds the rejected answer, and the verifier's reasons say which
    * quotes failed. An attempt with no readable answer checked nothing. */
  def quoteCheck(steps: List[StepInfo]): (Int, Int) =
    steps.filter(s => isFindings(s.name)).foldLeft((0, 0)) { case ((checked, passed), s) =>
      if s.status == StepStatus.Done.wire then
        s.output.flatMap(decode[VerifiedFindings](_).toOption).fold((checked, passed)) { v =>
          val n = v.proposed.getOrElse(v.findings.size)
          (checked + n, passed + n - v.quoteFailed.getOrElse(0))
        }
      else
        s.output.flatMap(decode[Findings](_).toOption).fold((checked, passed)) { f =>
          val n     = f.findings.size
          val fails = math.min(n, s.error.fold(0)(e => e.split(java.util.regex.Pattern.quote(QuoteFailure), -1).length - 1))
          (checked + n, passed + n - fails)
        }
    }

  /** The claims a run's enrichment ended with: the geocoded findings, else what the findings steps kept. */
  private def claims(d: RunDetail): List[String] =
    output[Geocoded](d, "geocode").map(_.findings.map(_.claim)).getOrElse(
      d.steps.map(_.name).distinct.filter(isFindings).flatMap(n => output[VerifiedFindings](d, n).toList.flatMap(_.findings.map(_.claim))))

  private def enrichSides(runs: List[RunDetail]): List[EnrichSide] =
    val all = runs.map(d => claims(d).map(c => AgentVerify.normalise(c) -> c))
    runs.zip(all).zipWithIndex.map { case ((d, mine), i) =>
      val others = all.patch(i, Nil, 1).flatten.map(_._1).toSet
      val geo    = output[Geocoded](d, "geocode")
      val cls    = output[Classified](d, "classify")
      val (checked, passed) = quoteCheck(d.steps)
      EnrichSide(
        findings = mine.size,
        quotesChecked = checked, quotesPassed = passed,
        placesAsked = geo.fold(0)(_.choices.size), placesResolved = geo.fold(0)(_.choices.count(_.placeId.isDefined)),
        datedFindings = geo.fold(0)(_.findings.count(f => f.periodStart.isDefined || f.periodEnd.isDefined)),
        periodStart = cls.flatMap(_.period).map(_.start), periodEnd = cls.flatMap(_.period).map(_.end),
        summaryChars = cls.fold(0)(_.summary.trim.length),
        notesChars = output[String](d, "notes").fold(0)(_.trim.length),
        uniqueClaims = mine.filterNot((n, _) => others.contains(n)).map(_._2).distinct)
    }

  // ── review-article ─────────────────────────────────────────────────────────────────────────────

  import ReviewArticle.{Finding, Merged, Report}

  private def round(name: String, prefix: String): Option[Int] =
    Option.when(name.startsWith(prefix))(name.stripPrefix(prefix).toIntOption).flatten

  private def reviewSide(d: RunDetail): ReviewSide =
    val merges = d.steps.map(_.name).distinct.flatMap(n => round(n, "merge-").map(_ -> n)).sortBy(_._1)
    val last   = merges.lastOption.flatMap((_, n) => output[Merged](d, n))
    val first  = output[Merged](d, "merge-1")
    val reports = List("rules-1", "cold-reader-1", "skeptic-1").flatMap(n => output[Report](d, n))
    val mech   = output[List[Finding]](d, "mechanical-1")
    val reviewers = reports.map(r => ReviewerSide(r.reviewer.wire, Some(r.verdict.wire), r.findings.count(_.major),
      r.findings.count(!_.major))) ++ mech.toList.map(fs =>
      ReviewerSide(ReviewArticle.Reviewer.Mechanical.wire, None, fs.count(_.major), fs.count(!_.major)))
    val kinds = first.toList.flatMap(_.findings).groupMapReduce(f => Option(f.kind).map(_.trim).filter(_.nonEmpty).getOrElse("other"))(_ => 1)(_ + _)
      .toList.sortBy((k, n) => (-n, k)).map(KindCount(_, _))
    ReviewSide(last.map(_.verdict.wire), merges.lastOption.fold(0)(_._1), reviewers, kinds, first.toList.flatMap(_.majors.map(_.line)))

  private def words(s: String): Set[String] =
    AgentVerify.normalise(Option(s).getOrElse("")).split("[^\\p{L}\\p{N}]+").filter(_.length > 2).toSet

  /** Jaccard similarity of two texts' words (0 when both have none). */
  def similarity(a: String, b: String): Double =
    val (x, y) = (words(a), words(b))
    if x.isEmpty && y.isEmpty then 0.0 else (x intersect y).size.toDouble / (x union y).size

  /** The same major finding, as two runs' reviewers worded it: the same `where` (normalised) and a
    * similar problem, or the same quoted passage. */
  def sameMajor(a: Finding, b: Finding): Boolean =
    val (qa, qb) = (AgentVerify.normalise(Option(a.quote).getOrElse("")), AgentVerify.normalise(Option(b.quote).getOrElse("")))
    val sameWhere = AgentVerify.normalise(Option(a.where).getOrElse("")) == AgentVerify.normalise(Option(b.where).getOrElse(""))
    (qa.nonEmpty && qa == qb) || (sameWhere && similarity(a.problem, b.problem) >= ProblemSimilarity)

  /** Each pair of runs: how many of round 1's majors match one-to-one (greedily, in order). */
  def overlap(a: List[Finding], b: List[Finding]): List[(Finding, Finding)] =
    a.foldLeft((List.empty[(Finding, Finding)], b)) { case ((acc, left), f) =>
      left.find(sameMajor(f, _)) match
        case Some(g) => (acc :+ (f -> g), left.filterNot(_ eq g))
        case None    => (acc, left)
    }._1

  private def majorOverlaps(runs: List[RunDetail]): List[MajorOverlap] =
    val majors = runs.map(d => d.run.id -> output[Merged](d, "merge-1").toList.flatMap(_.majors))
    for
      (i, j) <- majors.indices.toList.combinations(2).toList.map(p => (p(0), p(1)))
      (ra, ma) = majors(i)
      (rb, mb) = majors(j)
    yield
      val pairs = overlap(ma, mb)
      MajorOverlap(ra, rb, ma.size, mb.size, pairs.size, pairs.map((x, y) => s"${x.id} ↔ ${y.id}"))
