package com.histmersion.domain.research.workflows

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
  type RoundResult = RoundResult.T
  object RoundResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RoundName = RoundName.T
  object RoundName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RoundPrefix = RoundPrefix.T
  object RoundPrefix extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SameMajorResult = SameMajorResult.T
  object SameMajorResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  val ProblemSimilarity = 0.5

  private def round(name: com.histmersion.domain.research.workflows.ReviewComparison.RoundName, prefix: com.histmersion.domain.research.workflows.ReviewComparison.RoundPrefix): Option[com.histmersion.domain.research.workflows.ReviewComparison.RoundResult] =
    Option.when(name.startsWith(prefix))(name.stripPrefix(prefix).toIntOption).flatten

  private def reviewSide(d: Run): ReviewSide =
    val merges = d.steps.map(_.name).distinct.flatMap(n => round(n, "merge-").map(_ -> n)).sortBy(_._1)
    val last = merges.lastOption.flatMap((_, n) => CompareRuns.output[Merged](d, n))
    val first = CompareRuns.output[Merged](d, "merge-1")
    val reports = List("rules-1", "cold-reader-1", "skeptic-1").flatMap(n => CompareRuns.output[Report](d, n))
    val mech = CompareRuns.output[List[Finding]](d, "mechanical-1")
    val reviewers = reports.map(r =>
      ReviewerSide(r.reviewer.wire, Some(r.verdict.wire), r.findings.count(_.major), r.findings.count(!_.major))
    ) ++ mech.toList.map(fs =>
      ReviewerSide(ReviewArticle.Reviewer.Mechanical.wire, None, fs.count(_.major), fs.count(!_.major))
    )
    val kinds = first.toList
      .flatMap(_.findings)
      .groupMapReduce(f => Option(f.kind).map(_.trim).filter(_.nonEmpty).getOrElse("other"))(_ => 1)(_ + _)
      .toList
      .sortBy((k, n) => (-n, k))
      .map(KindCount(_, _))
    ReviewSide(
      last.map(_.verdict.wire),
      merges.lastOption.fold(0)(_._1),
      reviewers,
      kinds,
      first.toList.flatMap(_.majors.map(_.line))
    )

  override def review(runs: List[Run]): List[ReviewSide] = runs.map(reviewSide)

  /** The same major finding, as two runs' reviewers worded it: the same `where` (normalised) and a similar problem, or
    * the same quoted passage.
    */
  def sameMajor(a: Finding, b: Finding): com.histmersion.domain.research.workflows.ReviewComparison.SameMajorResult =
    val (qa, qb) =
      (ComparedText.normalise(Option(a.quote).getOrElse("")), ComparedText.normalise(Option(b.quote).getOrElse("")))
    val sameWhere =
      ComparedText.normalise(Option(a.where).getOrElse("")) == ComparedText.normalise(Option(b.where).getOrElse(""))
    (qa.nonEmpty && qa == qb) || (sameWhere && CompareRuns.similarity(a.problem, b.problem) >= ProblemSimilarity)

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
    val majors = runs.map(d => d.run.id -> CompareRuns.output[Merged](d, "merge-1").toList.flatMap(_.majors))
    for
      (i, j) <- majors.indices.toList.combinations(2).toList.map(p => (p(0), p(1)))
      (ra, ma) = majors(i)
      (rb, mb) = majors(j)
    yield
      val pairs = overlap(ma, mb)
      MajorOverlap(ra, rb, ma.size, mb.size, pairs.size, pairs.map((x, y) => s"${x.id} ↔ ${y.id}"))
