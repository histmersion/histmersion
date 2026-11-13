package com.histmersion
package domain.research.workflows
import com.histmersion.util.json.JsonCodec.given

import com.histmersion.domain.{CompareRuns, ReviewArticle}
import com.histmersion.domain.CompareRuns.{KindCount, MajorOverlap, ReviewerSide, ReviewSide, Run}
import com.histmersion.domain.ReviewArticle.{Finding, Merged, Report}
import com.histmersion.domain.automation.agents.OutputComparison
import com.histmersion.util.text.ComparedText

import scala.annotation.tailrec

/** review-article's runs compared by their outputs (`agent compare`, #992): the last round's verdict, the rounds, round
  * 1 per reviewer and by kind, its major findings, and the ones two runs share.
  */
object ReviewComparison extends OutputComparison:

  /** Two major findings are the same one when they sit at the same `where` and their problems share at least this much
    * of their words (Jaccard over the normalised words), or quote the same passage.
    */
  val ProblemSimilarity = 0.5

  private def round(name: CompareRuns.OutputName, prefix: String): Option[Int] =
    Option.when(name.value.startsWith(prefix))(name.value.stripPrefix(prefix).toIntOption).flatten

  private def reviewSide(d: Run): ReviewSide =
    val merges = d.steps.map(_.name).distinct.flatMap(n => round(n, "merge-").map(_ -> n)).sortBy(_._1)
    val last = merges.lastOption.flatMap((_, n) => CompareRuns.output[Merged](d, n))
    val first = CompareRuns.output[Merged](d, CompareRuns.OutputName("merge-1"))
    val reports = List("rules-1", "cold-reader-1", "skeptic-1").flatMap(n =>
      CompareRuns.output[Report](d, CompareRuns.OutputName(n))
    )
    val mech = CompareRuns.output[List[Finding]](d, CompareRuns.OutputName("mechanical-1"))
    val reviewers = reports.map(r =>
      ReviewerSide(
        ReviewerSide.Reviewer(r.reviewer.wire.value),
        Some(ReviewerSide.Verdict(r.verdict.wire.value)),
        ReviewerSide.Majors(r.findings.count(_.major)),
        ReviewerSide.Minors(r.findings.count(!_.major))
      )
    ) ++ mech.toList.map(fs =>
      ReviewerSide(
        ReviewerSide.Reviewer(ReviewArticle.Reviewer.Mechanical.wire.value),
        None,
        ReviewerSide.Majors(fs.count(_.major)),
        ReviewerSide.Minors(fs.count(!_.major))
      )
    )
    val kinds = first.toList
      .flatMap(_.findings)
      .groupMapReduce(f => Option(f.kind).map(_.value.trim).filter(_.nonEmpty).getOrElse("other"))(_ => 1)(_ + _)
      .toList
      .sortBy((k, n) => (-n, k))
      .map((k, n) => KindCount(KindCount.Kind(k), KindCount.Count(n)))
    ReviewSide(
      last.map(m => ReviewSide.Verdict(m.verdict.wire.value)),
      ReviewSide.Rounds(merges.lastOption.fold(0)(_._1)),
      reviewers,
      kinds,
      first.toList.flatMap(_.majors.map(m => ReviewSide.Majors(m.line)))
    )

  override def review(runs: List[Run]): List[ReviewSide] = runs.map(reviewSide)

  /** The same major finding, as two runs' reviewers worded it: the same `where` (normalised) and a similar problem, or
    * the same quoted passage.
    */
  def sameMajor(a: Finding, b: Finding): Boolean =
    val (qa, qb) =
      (
        ComparedText.normalise(ComparedText.NormaliseS(Option(a.quote).fold("")(_.value))),
        ComparedText.normalise(ComparedText.NormaliseS(Option(b.quote).fold("")(_.value)))
      )
    val sameWhere =
      ComparedText.normalise(ComparedText.NormaliseS(Option(a.where).fold("")(_.value))) ==
        ComparedText.normalise(ComparedText.NormaliseS(Option(b.where).fold("")(_.value)))
    (qa.value.nonEmpty && qa == qb) ||
    (sameWhere && CompareRuns
      .similarity(CompareRuns.Text(a.problem.value), CompareRuns.Text(b.problem.value))
      .value >= ProblemSimilarity)

  /** Each pair of runs: how many of round 1's majors match one-to-one (greedily, in order). */
  def overlap(a: List[Finding], b: List[Finding]): List[(Finding, Finding)] =
    @tailrec def loop(
        rest: List[Finding],
        acc: List[(Finding, Finding)],
        left: List[Finding]
    ): List[(Finding, Finding)] =
      rest match
        case Nil       => acc.reverse
        case f :: tail =>
          left.find(sameMajor(f, _)) match
            case Some(g) => loop(tail, (f -> g) :: acc, left.filterNot(_ eq g))
            case None    => loop(tail, acc, left)
    loop(a, Nil, b)

  override def overlaps(runs: List[Run]): List[MajorOverlap] =
    val majors = runs.map(d =>
      d.run.id -> CompareRuns.output[Merged](d, CompareRuns.OutputName("merge-1")).toList.flatMap(_.majors)
    )
    for
      (i, j) <- majors.indices.toList.combinations(2).toList.map(p => (p(0), p(1)))
      (ra, ma) = majors(i)
      (rb, mb) = majors(j)
    yield
      val pairs = overlap(ma, mb)
      MajorOverlap(
        MajorOverlap.A(ra.value),
        MajorOverlap.B(rb.value),
        MajorOverlap.AMajors(ma.size),
        MajorOverlap.BMajors(mb.size),
        MajorOverlap.Shared(pairs.size),
        pairs.map((x, y) => MajorOverlap.Pairs(s"${x.id} ↔ ${y.id}"))
      )
