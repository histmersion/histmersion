package com.histmersion.domain.library.findings
import com.histmersion.api.exchange.MergeIssue
import com.histmersion.api.exchange.MergeDecision

import com.histmersion.api.exchange.{IssueCondition, IssueDetail, IssueField, IssueSide, MergeIssue}
import com.histmersion.domain.MergeFold
import com.histmersion.domain.MergePlanner
import com.histmersion.domain.MergeSettings
import com.histmersion.domain.library.sources.MergeText
import com.histmersion.domain.library.sources.SourceMatcher
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.exchange.BundlePlan
import com.histmersion.util.data.exchange.PairQuestions
import com.histmersion.util.data.exchange.PlanFolds
import com.histmersion.util.data.exchange.PlanRelabels
import com.histmersion.util.data.exchange.PlanRun
import com.histmersion.util.data.exchange.SourceFate
import com.histmersion.api.library.Confidence

import hearth.kindlings.optics.syntax.*
import java.util.UUID
import scala.annotation.tailrec
import scala.collection.mutable
import com.histmersion.api.exchange.Bundle

/** How a merge plan treats findings (docs/research-merge-import.md §5.4): a finding belongs to its source; inside a
  * pair of sources that are (or may be) one, each of the bundle's findings is matched with at most one here — the same
  * claim folds, a near duplicate is a question (kept apart by default) —, the rest arrive under the survivor's citekey,
  * numbered after its last; a source renamed takes its findings along; a finding added under an id another finding has
  * here arrives under the next free one.
  */
final class FindingPlan(in: MergePlanner.Input) extends PlanRun:
  import FindingPlan.*

  private val km = MergeDecision.Kind
  private val findingSource = in.foreign.findings.map(f => f.uid -> f.sourceUid).toMap
  private val localFindingsOf = in.local.findings.groupBy(_.sourceUid)
  private val nearIssue = mutable.Map.empty[UUID, String] // the bundle's finding → its question
  private val findingMatches = mutable.Map.empty[UUID, List[Match]] // the bundle's source → its findings' matches
  private val findingLine = mutable.Map.empty[UUID, BundleEntities => com.histmersion.api.exchange.Bundle.Finding]
  private val assignedIds = mutable.Set.from(in.local.findings.map(_.id))
  private def findingIn(es: BundleEntities, u: UUID) = es.findings.find(_.uid == u).get

  override def ownerSource(foreign: UUID, kind: Bundle.Kind): Option[UUID] =
    if kind == Bundle.Kind.Finding then findingSource.get(foreign) else None

  // rows of the bundle that already have their own uid here are not matched again inside a pair
  override def pairFirst(
      right: com.histmersion.api.exchange.Bundle.Source,
      left: com.histmersion.api.exchange.Bundle.Source,
      cond: Option[IssueCondition],
      cfg: MergeSettings,
      questions: PairQuestions
  ): Unit =
    val ms = matches(
      localFindingsOf.getOrElse(left.uid, Nil),
      in.foreign.findings.filter(f => f.sourceUid == right.uid && !questions.here(questions.toLocal(f.uid))),
      cfg.findingWithinKm,
      cfg.findingNearAtLeast
    )
    findingMatches(right.uid) = ms
    ms.foreach {
      case Match.Near(rf, lf, j) =>
        val id = MergePlanner.issueId(
          MergeIssue.Kind.FindingNearDuplicate,
          Bundle.Kind.Finding,
          List(rf.uid),
          List(lf.uid),
          questions.rightRev(rf.uid),
          questions.localRev(lf.uid)
        )
        nearIssue(rf.uid) = id
        questions.ask(
          MergeIssue(
            id,
            MergeIssue.Kind.FindingNearDuplicate,
            MergeIssue.Severity.Default,
            Bundle.Kind.Finding,
            s"near-duplicate findings (claim words alike, Jaccard ${SourceMatcher.fixed(j, 2)})",
            Some(IssueSide(lf.id, lf.uid, List(MergeIssue.Detail.preview(lf.claim)))),
            Some(IssueSide(rf.id, rf.uid, List(MergeIssue.Detail.preview(rf.claim)))),
            List("by default both are kept; merge makes them one finding"),
            List(km.Both, km.Merge),
            Some(com.histmersion.api.exchange.MergeDecision.of(km.Both)),
            cond,
            detail =
              Some(
                IssueDetail(
                  List(
                    IssueField("claim", Some(lf.claim), Some(rf.claim)),
                    IssueField(
                      "period",
                      lf.period.map(x => MergeIssue.Detail.rangeText(Some(x))),
                      rf.period.map(x => MergeIssue.Detail.rangeText(Some(x)))
                    ),
                    IssueField("region", lf.region, rf.region),
                    IssueField(
                      "confidence",
                      lf.confidence.map(_.toString.toLowerCase),
                      rf.confidence.map(_.toString.toLowerCase)
                    )
                  )
                )
              )
          ),
          Set.empty
        )
      case Match.Same(rf, lf) if rf.confidence.isDefined && lf.confidence.isDefined && rf.confidence != lf.confidence =>
        questions.info(
          s"finding ${lf.id}: the same claim as the bundle's ${rf.id} at another confidence — the more cautious is kept"
        )
      case _ => ()
    }

  override def pairIssue(kind: MergeIssue.Kind): Boolean = kind == MergeIssue.Kind.FindingNearDuplicate

  // the same claim folds, a near duplicate folds when decided `merge`, the rest arrive under the survivor's citekey,
  // numbered after its last
  override def foldSources(
      right: com.histmersion.api.exchange.Bundle.Source,
      left: com.histmersion.api.exchange.Bundle.Source,
      citekey: String,
      folds: PlanFolds
  ): Boolean =
    val ms = findingMatches.getOrElse(right.uid, Nil)
    def merging(rf: com.histmersion.api.exchange.Bundle.Finding) =
      nearIssue.get(rf.uid).flatMap(folds.decisionIn).exists(_.kind == km.Merge)
    val renumbered =
      localFindingsOf.getOrElse(left.uid, Nil).map(f => s"$citekey#${f.id.dropWhile(_ != '#').drop(1)}").toSet
    val incoming = ms.collect {
      case Match.Unmatched(rf)                  => rf
      case Match.Near(rf, _, _) if !merging(rf) => rf
    }
    val ids = nextIds(citekey, assignedIds.toSet ++ renumbered, incoming.size)
    assignedIds ++= ids
    incoming.zip(ids).foreach((rf, id) => findingLine(rf.uid) = es => findingIn(es, rf.uid).modify(_.id).setTo(id))
    ms.foreach {
      case Match.Same(rf, lf) =>
        folds.fold(rf.uid, lf.uid, Bundle.Kind.Finding); findingLine(lf.uid) = _ => fold(lf, rf)
        folds.count(_.modify(_.findingsMerged).using(_ + 1))
      case Match.Near(rf, lf, _) if merging(rf) =>
        folds.fold(rf.uid, lf.uid, Bundle.Kind.Finding); findingLine(lf.uid) = _ => fold(lf, rf)
        folds.count(_.modify(_.findingsMerged).using(_ + 1))
      case _ => ()
    }
    false

  override def renamed(source: UUID, citekey: String): Unit =
    in.foreign.findings
      .filter(_.sourceUid == source)
      .foreach(f =>
        findingLine(f.uid) =
          es => findingIn(es, f.uid).modify(_.id).setTo(s"$citekey#${f.id.dropWhile(_ != '#').drop(1)}")
      )

  // a finding id a different finding has here: the bundle's is renumbered (a source whose citekey collides is renamed
  // as a whole, its findings with it)
  override def relabelFirst(fates: Map[UUID, SourceFate], relabels: PlanRelabels): Unit =
    val colliding: Set[UUID] = in.foreign.sources
      .filter(s =>
        fates.get(s.uid).exists {
          case SourceFate.New | SourceFate.Gate(_, _) => true
          case _                                      => false
        } && (in.labels.citekeys.contains(s.citekey) || in.labels.aliases.contains(s.citekey))
      )
      .map(_.uid)
      .toSet
    relabels.rows
      .filter(r =>
        r.decision == BundlePlan.Decision.Insert && r.kind == Bundle.Kind.Finding &&
          !findingLine.contains(r.foreignUid) && !findingSource.get(r.foreignUid).exists(colliding)
      )
      .foreach { r =>
        val f = relabels.localized.findings.find(_.uid == r.uid).get
        in.labels.findings.get(f.id).filter(_ != f.uid).foreach { _ =>
          val ck = f.id.takeWhile(_ != '#')
          val id = nextIds(ck, assignedIds.toSet, 1).head
          assignedIds += id
          findingLine(r.foreignUid) = es => findingIn(es, r.foreignUid).modify(_.id).setTo(id)
          relabels.info(s"finding ${f.id}: that id is another finding's here — the bundle's arrives as $id")
          relabels.relabelled(s"finding ${f.id} → $id")
        }
      }

  override def lines(es: BundleEntities, es0: BundleEntities): BundleEntities =
    es.modify(_.findings)
      .setTo(
        MergePlanner
          .replaced(es0.findings, _.uid, findingLine, es0, (_: com.histmersion.api.exchange.Bundle.Finding) => true)
      )

object FindingPlan:

  /** How a bundle finding of a merged pair relates to this collection's findings of the survivor. */
  enum Match:
    /** The same claim (and a compatible time and place): one finding. */
    case Same(right: com.histmersion.api.exchange.Bundle.Finding, left: com.histmersion.api.exchange.Bundle.Finding)

    /** Nearly the same claim at an overlapping time: kept apart unless the user says `merge`. */
    case Near(
        right: com.histmersion.api.exchange.Bundle.Finding,
        left: com.histmersion.api.exchange.Bundle.Finding,
        jaccard: Double
    )

    /** Nothing here like it: a new finding of the survivor. */
    case Unmatched(right: com.histmersion.api.exchange.Bundle.Finding)

  private def text(o: Option[String]): Option[String] = o.map(_.trim).filter(_.nonEmpty)

  /** The claim as compared: folded, stopwords out, de-duplicated, sorted. */
  def claimKey(claim: String): String = MergeText.significant(MergeText.tokens(claim)).distinct.sorted.mkString(" ")

  private def claimWords(claim: String): Set[String] = MergeText.significant(MergeText.tokens(claim)).toSet

  /** Great-circle distance in km. */
  def km(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double =
    val (p1, p2) = (math.toRadians(lat1), math.toRadians(lat2))
    val dp = p2 - p1
    val dl = math.toRadians(lon2 - lon1)
    val a = math.pow(math.sin(dp / 2), 2) + math.cos(p1) * math.cos(p2) * math.pow(math.sin(dl / 2), 2)
    6371.0 * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

  /** The same place, as far as two findings say: points within `withinKm`, or the same region name (fingerprint), or
    * one of them says nothing about where.
    */
  def samePlace(
      a: com.histmersion.api.exchange.Bundle.Finding,
      b: com.histmersion.api.exchange.Bundle.Finding,
      withinKm: Double
  ): Boolean =
    val points = for { x <- a.lon; y <- a.lat; u <- b.lon; v <- b.lat } yield km(x, y, u, v) <= withinKm
    val regions = text(a.region).zip(text(b.region)).map((x, y) => MergeText.fingerprint(x) == MergeText.fingerprint(y))
    points.orElse(regions).forall(identity) || points.contains(true) || regions.contains(true)

  /** Caution ranks: contested is the least sure, then low, medium, high. */
  private def caution(c: Confidence): Int = c match
    case Confidence.Contested => 0
    case Confidence.Low       => 1
    case Confidence.Medium    => 2
    case Confidence.High      => 3

  /** Pair each bundle finding with at most one finding here, one-to-one: the same claim first, then the nearest
    * near-duplicate (token Jaccard ≥ `nearAtLeast` and overlapping periods).
    */
  def matches(
      lefts: List[com.histmersion.api.exchange.Bundle.Finding],
      rights: List[com.histmersion.api.exchange.Bundle.Finding],
      withinKm: Double,
      nearAtLeast: Double
  ): List[Match] =
    val rs = rights.sortBy(_.id)
    val ls = lefts.sortBy(_.id)
    @tailrec def pairSame(
        rest: List[com.histmersion.api.exchange.Bundle.Finding],
        acc: Map[UUID, com.histmersion.api.exchange.Bundle.Finding],
        taken: Set[UUID]
    ): (Map[UUID, com.histmersion.api.exchange.Bundle.Finding], Set[UUID]) =
      rest match
        case Nil       => (acc, taken)
        case r :: tail =>
          ls.find(l =>
            !taken(l.uid) && claimKey(l.claim) == claimKey(r.claim) && claimKey(r.claim).nonEmpty &&
              MergeFold.overlap(l.period, r.period) && samePlace(l, r, withinKm)
          ) match
            case Some(l) => pairSame(tail, acc + (r.uid -> l), taken + l.uid)
            case None    => pairSame(tail, acc, taken)
    val (same, takenBySame) = pairSame(rs, Map.empty, Set.empty)
    @tailrec def pairNear(
        rest: List[com.histmersion.api.exchange.Bundle.Finding],
        acc: Map[UUID, (com.histmersion.api.exchange.Bundle.Finding, Double)],
        taken: Set[UUID]
    ): Map[UUID, (com.histmersion.api.exchange.Bundle.Finding, Double)] =
      rest match
        case Nil       => acc
        case r :: tail =>
          val w = claimWords(r.claim)
          ls.filterNot(l => taken(l.uid))
            .filter(l => l.period.isDefined && r.period.isDefined && MergeFold.overlap(l.period, r.period))
            .map(l => l -> MergeText.jaccard(w, claimWords(l.claim)))
            .filter(_._2 >= nearAtLeast)
            .sortBy((l, j) => (-j, l.id))
            .headOption match
            case Some((l, j)) => pairNear(tail, acc + (r.uid -> (l, j)), taken + l.uid)
            case None         => pairNear(tail, acc, taken)
    val near = pairNear(rs.filterNot(r => same.contains(r.uid)), Map.empty, takenBySame)
    rs.map { r =>
      same
        .get(r.uid)
        .map(Match.Same(r, _))
        .orElse(near.get(r.uid).map((l, j) => Match.Near(r, l, j)))
        .getOrElse(Match.Unmatched(r))
    }

  /** One finding out of two (§5.4): left's identity and claim, tags unioned, the more specific period and the point
    * where only one has it, the more cautious confidence.
    */
  def fold(
      l: com.histmersion.api.exchange.Bundle.Finding,
      r: com.histmersion.api.exchange.Bundle.Finding
  ): com.histmersion.api.exchange.Bundle.Finding =
    val period = MergeFold.narrower(l.period, r.period)
    val conf = (l.confidence, r.confidence) match
      case (Some(a), Some(b)) => Some(if caution(b) < caution(a) then b else a)
      case (a, b)             => a.orElse(b)
    val point = if l.lon.isDefined && l.lat.isDefined then (l.lon, l.lat) else (r.lon, r.lat)
    l.modify(
      _.region).setTo(text(l.region).orElse(text(r.region))).modify(_.lon).setTo(point._1).modify(_.lat).setTo(point._2).modify(_.period).setTo(period).modify(_.periodName).setTo(if period == l.period then l.periodName.orElse(r.periodName) else r.periodName.orElse(l.periodName)).modify(_.tags).setTo(MergeFold.unionTags(l.tags, r.tags)).modify(_.confidence).setTo(conf).modify(_.significance).setTo(l.significance.orElse(r.significance)).modify(_.rev).setTo(""
    )

  /** `<citekey>#<n>` labels for `count` new findings: after the highest number in use, never one that is taken. */
  def nextIds(citekey: String, taken: Set[String], count: Int): List[String] =
    val Num = s"""^${java.util.regex.Pattern.quote(citekey)}#(\\d+)$$""".r
    val top = taken.toList.collect { case Num(n) => n.toInt }.maxOption.getOrElse(0)
    Iterator.from(top + 1).map(n => s"$citekey#$n").filterNot(taken).take(count).toList
