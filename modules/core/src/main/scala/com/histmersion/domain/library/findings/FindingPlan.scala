package com.histmersion
package domain.library.findings
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
import com.histmersion.domain.library.Citekey
import com.histmersion.api.exchange.MergeTally
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude

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
  private val nearIssue = mutable.Map.empty[UUID, MergeIssue.Id] // the bundle's finding → its question
  private val findingMatches = mutable.Map.empty[UUID, List[Match]] // the bundle's source → its findings' matches
  private val findingLine = mutable.Map.empty[UUID, BundleEntities => Bundle.Finding]
  private val assignedIds = mutable.Set.from(in.local.findings.map(_.id))
  private def findingIn(es: BundleEntities, u: UUID) = es.findings.find(_.uid == u).get

  override def ownerSource(foreign: UUID, kind: Bundle.Kind): Option[UUID] =
    if kind == Bundle.Kind.Finding then findingSource.get(foreign) else None

  // rows of the bundle that already have their own uid here are not matched again inside a pair
  override def pairFirst(
      right: Bundle.Source,
      left: Bundle.Source,
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
          BundlePlan.Ledger.ForeignRev(questions.rightRev(rf.uid).value),
          BundlePlan.Local.Rev(questions.localRev(lf.uid).value)
        )
        nearIssue(rf.uid) = id
        questions.ask(
          MergeIssue(
            id,
            MergeIssue.Kind.FindingNearDuplicate,
            MergeIssue.Severity.Default,
            Bundle.Kind.Finding,
            MergeIssue.Title(s"near-duplicate findings (claim words alike, Jaccard ${SourceMatcher.fixed(SourceMatcher.Figure(j.value), SourceMatcher.Decimals(2))})"),
            Some(IssueSide(IssueSide.Label(lf.id.value), lf.uid, List(IssueSide.Lines(MergeIssue.Detail.preview(lf.claim.value))))),
            Some(IssueSide(IssueSide.Label(rf.id.value), rf.uid, List(IssueSide.Lines(MergeIssue.Detail.preview(rf.claim.value))))),
            List(MergeIssue.Why("by default both are kept; merge makes them one finding")),
            List(km.Both, km.Merge),
            Some(MergeDecision.of(km.Both)),
            cond,
            detail =
              Some(
                IssueDetail(
                  List(
                    IssueField(IssueField.Field("claim"), Some(IssueField.Left(lf.claim.value)), Some(IssueField.Right(rf.claim.value))),
                    IssueField(
                      IssueField.Field("period"),
                      lf.period.map(x => IssueField.Left(MergeIssue.Detail.rangeText(Some(x)))),
                      rf.period.map(x => IssueField.Right(MergeIssue.Detail.rangeText(Some(x))))
                    ),
                    IssueField(IssueField.Field("region"), lf.region.map(x => IssueField.Left(x.value)), rf.region.map(x => IssueField.Right(x.value))),
                    IssueField(
                      IssueField.Field("confidence"),
                      lf.confidence.map(c => IssueField.Left(c.toString.toLowerCase)),
                      rf.confidence.map(c => IssueField.Right(c.toString.toLowerCase))
                    )
                  )
                )
              )
          ),
          Set.empty
        )
      case Match.Same(rf, lf) if rf.confidence.isDefined && lf.confidence.isDefined && rf.confidence != lf.confidence =>
        questions.info(
          PairQuestions.InfoLine(
            s"finding ${lf.id}: the same claim as the bundle's ${rf.id} at another confidence — the more cautious is kept"
          )
        )
      case _ => ()
    }

  override def pairIssue(kind: MergeIssue.Kind): Boolean = kind == MergeIssue.Kind.FindingNearDuplicate

  // the same claim folds, a near duplicate folds when decided `merge`, the rest arrive under the survivor's citekey,
  // numbered after its last
  override def foldSources(
      right: Bundle.Source,
      left: Bundle.Source,
      citekey: Citekey,
      folds: PlanFolds
  ): Boolean =
    val ms = findingMatches.getOrElse(right.uid, Nil)
    def merging(rf: Bundle.Finding) =
      nearIssue.get(rf.uid).flatMap(id => folds.decisionIn(id)).exists(_.kind == km.Merge)
    val renumbered =
      localFindingsOf.getOrElse(left.uid, Nil).map(f => Bundle.Finding.Id(s"$citekey#${f.id.value.dropWhile(_ != '#').drop(1)}")).toSet
    val incoming = ms.collect {
      case Match.Unmatched(rf)                  => rf
      case Match.Near(rf, _, _) if !merging(rf) => rf
    }
    val ids = nextIds(com.histmersion.api.library.Citekey(citekey.value), assignedIds.toSet ++ renumbered, IdCount(incoming.size))
    assignedIds ++= ids
    incoming.zip(ids).foreach((rf, id) => findingLine(rf.uid) = es => findingIn(es, rf.uid).modify(_.id).setTo(id))
    ms.foreach {
      case Match.Same(rf, lf) =>
        folds.fold(rf.uid, lf.uid, Bundle.Kind.Finding); findingLine(lf.uid) = _ => fold(lf, rf)
        folds.count(_.modify(_.findingsMerged).using(n => MergeTally.FindingsMerged(n.value + 1)))
      case Match.Near(rf, lf, _) if merging(rf) =>
        folds.fold(rf.uid, lf.uid, Bundle.Kind.Finding); findingLine(lf.uid) = _ => fold(lf, rf)
        folds.count(_.modify(_.findingsMerged).using(n => MergeTally.FindingsMerged(n.value + 1)))
      case _ => ()
    }
    false

  override def renamed(source: UUID, citekey: Citekey): Unit =
    in.foreign.findings
      .filter(_.sourceUid == source)
      .foreach(f =>
        findingLine(f.uid) =
          es => findingIn(es, f.uid).modify(_.id).setTo(Bundle.Finding.Id(s"$citekey#${f.id.value.dropWhile(_ != '#').drop(1)}"))
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
          val ck = f.id.value.takeWhile(_ != '#')
          val id = nextIds(com.histmersion.api.library.Citekey(ck), assignedIds.toSet, IdCount(1)).head
          assignedIds += id
          findingLine(r.foreignUid) = es => findingIn(es, r.foreignUid).modify(_.id).setTo(id)
          relabels.info(PlanRelabels.InfoLine(s"finding ${f.id}: that id is another finding's here — the bundle's arrives as $id"))
          relabels.relabelled(PlanRelabels.RelabelledLine(s"finding ${f.id} → $id"))
        }
      }

  override def lines(es: BundleEntities, es0: BundleEntities): BundleEntities =
    es.modify(_.findings)
      .setTo(
        MergePlanner
          .replaced(es0.findings, _.uid, findingLine, es0, (_: Bundle.Finding) => true)
      )

object FindingPlan:

  type ClaimKey = ClaimKey.T
  object ClaimKey extends RefinedType[String, Pure]
  type IdCount = IdCount.T
  object IdCount extends RefinedType[Int, Pure]

  /** A great-circle distance in km. */
  type Distance = Distance.T
  object Distance extends RefinedType[Double, Pure]:
    extension (d: Distance) def within(limit: MergeSettings.FindingWithinKm): Boolean = d.value <= limit.value

  /** How a bundle finding of a merged pair relates to this collection's findings of the survivor. */
  enum Match:
    /** The same claim (and a compatible time and place): one finding. */
    case Same(right: Bundle.Finding, left: Bundle.Finding)

    /** Nearly the same claim at an overlapping time: kept apart unless the user says `merge`. */
    case Near(
        right: Bundle.Finding,
        left: Bundle.Finding,
        jaccard: Match.Near.Jaccard
    )

    /** Nothing here like it: a new finding of the survivor. */
    case Unmatched(right: Bundle.Finding)

  object Match:
    object Near:
      type Jaccard = Jaccard.T
      object Jaccard extends RefinedType[Double, Pure]

  private def text(o: Option[Bundle.Finding.Region]): Option[Bundle.Finding.Region] =
    o.map(r => Bundle.Finding.Region(r.value.trim)).filter(_.value.nonEmpty)

  /** The claim as compared: folded, stopwords out, de-duplicated, sorted. */
  def claimKey(claim: Bundle.Finding.Claim): ClaimKey = ClaimKey(MergeText.significant(MergeText.tokens(MergeText.Text(claim.value))).map(_.value).distinct.sorted.mkString(" "))

  private def claimWords(claim: Bundle.Finding.Claim): Set[String] = MergeText.significant(MergeText.tokens(MergeText.Text(claim.value))).map(_.value).toSet

  /** Great-circle distance in km. */
  def km(lon1: Longitude, lat1: Latitude, lon2: Longitude, lat2: Latitude): Distance =
    val (p1, p2) = (math.toRadians(lat1.value), math.toRadians(lat2.value))
    val dp = p2 - p1
    val dl = math.toRadians(lon2.value - lon1.value)
    val a = math.pow(math.sin(dp / 2), 2) + math.cos(p1) * math.cos(p2) * math.pow(math.sin(dl / 2), 2)
    Distance(6371.0 * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a)))

  /** The same place, as far as two findings say: points within `withinKm`, or the same region name (fingerprint), or
    * one of them says nothing about where.
    */
  def samePlace(
      a: Bundle.Finding,
      b: Bundle.Finding,
      withinKm: MergeSettings.FindingWithinKm
  ): Boolean =
    val points = for { x <- a.lon; y <- a.lat; u <- b.lon; v <- b.lat } yield km(x, y, u, v).within(withinKm)
    val regions = text(a.region).zip(text(b.region)).map((x, y) => MergeText.fingerprint(MergeText.Text(x.value)) == MergeText.fingerprint(MergeText.Text(y.value)))
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
      lefts: List[Bundle.Finding],
      rights: List[Bundle.Finding],
      withinKm: MergeSettings.FindingWithinKm,
      nearAtLeast: MergeSettings.FindingNearAtLeast
  ): List[Match] =
    val rs = rights.sortBy(_.id.value)
    val ls = lefts.sortBy(_.id.value)
    @tailrec def pairSame(
        rest: List[Bundle.Finding],
        acc: Map[UUID, Bundle.Finding],
        taken: Set[UUID]
    ): (Map[UUID, Bundle.Finding], Set[UUID]) =
      rest match
        case Nil       => (acc, taken)
        case r :: tail =>
          ls.find(l =>
            !taken(l.uid) && claimKey(l.claim) == claimKey(r.claim) && claimKey(r.claim).value.nonEmpty &&
              MergeFold.overlap(l.period, r.period) && samePlace(l, r, withinKm)
          ) match
            case Some(l) => pairSame(tail, acc + (r.uid -> l), taken + l.uid)
            case None    => pairSame(tail, acc, taken)
    val (same, takenBySame) = pairSame(rs, Map.empty, Set.empty)
    @tailrec def pairNear(
        rest: List[Bundle.Finding],
        acc: Map[UUID, (Bundle.Finding, Match.Near.Jaccard)],
        taken: Set[UUID]
    ): Map[UUID, (Bundle.Finding, Match.Near.Jaccard)] =
      rest match
        case Nil       => acc
        case r :: tail =>
          val w = claimWords(r.claim)
          ls.filterNot(l => taken(l.uid))
            .filter(l => l.period.isDefined && r.period.isDefined && MergeFold.overlap(l.period, r.period))
            .map(l => l -> Match.Near.Jaccard(MergeText.jaccard(w, claimWords(l.claim)).value))
            .filter(_._2.value >= nearAtLeast.value)
            .sortBy((l, j) => (-j.value, l.id.value))
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
      l: Bundle.Finding,
      r: Bundle.Finding
  ): Bundle.Finding =
    val period = MergeFold.narrower(l.period, r.period)
    val conf = (l.confidence, r.confidence) match
      case (Some(a), Some(b)) => Some(if caution(b) < caution(a) then b else a)
      case (a, b)             => a.orElse(b)
    val point = if l.lon.isDefined && l.lat.isDefined then (l.lon, l.lat) else (r.lon, r.lat)
    l.modify(
      _.region).setTo(text(l.region).orElse(text(r.region))).modify(_.lon).setTo(point._1).modify(_.lat).setTo(point._2).modify(_.period).setTo(period).modify(_.periodName).setTo(if period == l.period then l.periodName.orElse(r.periodName) else r.periodName.orElse(l.periodName)).modify(_.tags).setTo(MergeFold.unionTags(l.tags, r.tags)(_.value)).modify(_.confidence).setTo(conf).modify(_.significance).setTo(l.significance.orElse(r.significance)).modify(_.rev).setTo(Bundle.Finding.Rev("")
    )

  /** `<citekey>#<n>` labels for `count` new findings: after the highest number in use, never one that is taken. */
  def nextIds(citekey: com.histmersion.api.library.Citekey, taken: Set[Bundle.Finding.Id], count: IdCount): List[Bundle.Finding.Id] =
    val Num = s"""^${java.util.regex.Pattern.quote(citekey.value)}#(\\d+)$$""".r
    val top = taken.toList.map(_.value).collect { case Num(n) => n.toInt }.maxOption.getOrElse(0)
    Iterator.from(top + 1).map(n => Bundle.Finding.Id(s"$citekey#$n")).filterNot(taken).take(count.value).toList
