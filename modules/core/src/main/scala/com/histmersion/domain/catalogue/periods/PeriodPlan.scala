package com.histmersion.domain.catalogue.periods

import com.histmersion.api.exchange.{
  BundlePeriod,
  IssueDetail,
  IssueField,
  IssuePlace,
  IssueSide,
  MergeIssue,
  MergeIssueDetail
}
import com.histmersion.domain.MergePlanner
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.exchange.{PlanFolds, PlanMerge, PlanQuestions, PlanRun}
import com.histmersion.util.text.Fingerprint
import com.histmersion.values.{BundleKind, DecisionKind, IssueKind, IssueSeverity}

import hearth.kindlings.optics.syntax.*
import java.util.UUID

/** How a merge plan treats named periods of this collection's own: one id added on both sides is one period when its
  * span and label agree, else a question; one under another id here is one when its label and span are (the database
  * compared them: `periodMatches`); what points at a matched one by id points at ours.
  */
final class PeriodPlan(in: MergePlanner.Input) extends PlanRun:
  import PeriodPlan.*

  private val km = DecisionKind
  private val lr = List(km.KeepLeft, km.KeepRight)
  private val localPeriod = in.local.periods.map(p => p.uid -> p).toMap
  private val periodLine = scala.collection.mutable.Map.empty[UUID, BundleEntities => BundlePeriod]
  private def periodIn(es: BundleEntities, u: UUID) = es.periods.find(_.uid == u).get

  /** The same period: this collection's is kept. */
  private val SamePeriod: PlanMerge = new PlanMerge:
    def fold(f: UUID, o: UUID, folds: PlanFolds): Unit =
      folds.fold(f, o, BundleKind.Period)
      periodLine(o) = _ => localPeriod(o)
      folds.count(_.modify(_.periodsMerged).using(_ + 1))
    def info(label: String, bundleLabel: String): String =
      s"named period $label: the bundle's $bundleLabel is the same period — this collection's kept"

  override def localized(es: BundleEntities, mapped: Map[UUID, UUID]): BundleEntities =
    val periodIdMap: Map[String, String] =
      in.foreign.periods.flatMap(p => in.periodMatches.get(p.uid).filter(_ != p.id).map(p.id -> _)).toMap
    repoint(es, periodIdMap)

  override def addedOnBoth(q: PlanQuestions): Unit =
    val localPeriodById = in.local.periods.map(p => p.id -> p).toMap
    q.localized.periods.filter(p => q.writesInitially(p.uid)).sortBy(_.id).foreach { p =>
      val f = q.bundleUidOf(p.uid)
      localPeriodById.get(p.id).filter(_.uid != p.uid) match
        case Some(o) if samePeriod(o, p) => q.merge(f, o.uid, SamePeriod)
        case Some(o)                     =>
          val id = MergePlanner.issueId(
            IssueKind.NamedPeriodDiffers,
            BundleKind.Period,
            List(f),
            List(o.uid),
            q.rightRev(f),
            q.localRev(o.uid)
          )
          q.ask(
            MergeIssue(
              id,
              IssueKind.NamedPeriodDiffers,
              IssueSeverity.Blocking,
              BundleKind.Period,
              s"named period ${p.id} was added on both sides and says something else: " +
                s"${o.label} ${MergeIssueDetail.rangeText(o.period)} here, ${p.label} ${MergeIssueDetail.rangeText(p.period)} in the bundle",
              Some(IssueSide(o.id, o.uid, List(o.label))),
              Some(IssueSide(p.id, f, List(p.label))),
              List("keep-left keeps this collection's period; keep-right takes the bundle's label and span"),
              lr,
              detail = Some(
                IssueDetail(
                  fields = List(
                    IssueField("label", Some(o.label), Some(p.label)),
                    IssueField(
                      "span",
                      Some(MergeIssueDetail.rangeText(o.period)),
                      Some(MergeIssueDetail.rangeText(p.period))
                    ),
                    IssueField("authority", o.authority, p.authority),
                    IssueField("region", o.region, p.region)
                  ),
                  place = Some(
                    IssuePlace(
                      leftSpan = o.period.map(MergeIssueDetail.span),
                      rightSpan = p.period.map(MergeIssueDetail.span)
                    )
                  )
                )
              )
            ),
            Set.empty
          )
        case None =>
          localPeriodById.values
            .find(x => in.periodMatches.get(f).contains(x.id))
            .foreach(o => q.merge(f, o.uid, SamePeriod))
    }

  // a named period added on both sides under one id: ours stays, or theirs wins
  override def decided(i: MergeIssue, folds: PlanFolds): Unit =
    (i.kind, i.entity, i.decision, i.right.map(_.uid), i.left.map(_.uid)) match
      case (IssueKind.NamedPeriodDiffers, BundleKind.Period, Some(d), Some(f), Some(o)) =>
        folds.fold(f, o, BundleKind.Period)
        folds.count(_.modify(_.periodsMerged).using(_ + 1))
        if d.kind == km.KeepRight then
          periodLine(o) = es => periodIn(es, o).copy(origin = localPeriod(o).origin, rev = "")
        else folds.keepLocal(f)
      case _ => ()

  override def lines(es: BundleEntities, es0: BundleEntities): BundleEntities =
    es.modify(_.periods).setTo(MergePlanner.replaced(es0.periods, _.uid, periodLine, es0, (_: BundlePeriod) => true))

object PeriodPlan:

  /** Periods point at their parents by id: a bundle period matched to one here under another id is pointed at under
    * that id. Pure.
    */
  def repoint(es: BundleEntities, periods: Map[String, String]): BundleEntities =
    if periods.isEmpty then es
    else
      es.modify(_.periods)
        .setTo(es.periods.map(p => p.parentId.flatMap(periods.get).fold(p)(x => p.copy(parentId = Some(x)))))

  /** Two records of one period id that say the same: the same span and label. */
  def samePeriod(l: BundlePeriod, r: BundlePeriod): Boolean =
    l.period == r.period && Fingerprint.of(l.label) == Fingerprint.of(r.label)
