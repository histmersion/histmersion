package com.histmersion
package domain.catalogue.periods
import com.histmersion.domain.Problem
import com.histmersion.api.exchange.MergeIssue
import com.histmersion.api.exchange.MergeDecision

import com.histmersion.api.exchange.{IssueDetail, IssueField, IssuePlace, IssueSide, MergeIssue}
import com.histmersion.domain.MergePlanner
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.exchange.BundlePlan
import com.histmersion.util.data.exchange.PlanFolds
import com.histmersion.util.data.exchange.PlanMerge
import com.histmersion.util.data.exchange.PlanQuestions
import com.histmersion.util.data.exchange.PlanRun
import com.histmersion.util.text.Fingerprint

import hearth.kindlings.optics.syntax.*
import java.util.UUID
import com.histmersion.api.exchange.Bundle
import com.histmersion.api.exchange.MergeTally

/** How a merge plan treats named periods of this collection's own: one id added on both sides is one period when its
  * span and label agree, else a question; one under another id here is one when its label and span are (the database
  * compared them: `periodMatches`); what points at a matched one by id points at ours.
  */
final class PeriodPlan(in: MergePlanner.Input) extends PlanRun:
  private given CanEqual[UUID, UUID] = CanEqual.derived

  import PeriodPlan.*

  private val km = MergeDecision.Kind
  private val lr = List(km.KeepLeft, km.KeepRight)
  private val localPeriod = in.local.periods.map(p => p.uid -> p).toMap
  private val periodLine =
    scala.collection.mutable.Map.empty[UUID, BundleEntities => Bundle.Period]
  private def periodIn(es: BundleEntities, u: UUID) = es.periods.find(_.uid == u).get

  /** The same period: this collection's is kept. */
  private val SamePeriod: PlanMerge = new PlanMerge:
    def fold(f: UUID, o: UUID, folds: PlanFolds): Unit =
      folds.fold(f, o, Bundle.Kind.Period)
      periodLine(o) = _ => localPeriod(o)
      folds.count(_.modify(_.periodsMerged).using(n => MergeTally.PeriodsMerged(n.value + 1)))
    def info(label: PlanMerge.InfoLabel, bundleLabel: PlanMerge.InfoBundleLabel): PlanMerge.InfoText =
      PlanMerge.InfoText(
        s"named period $label: the bundle's $bundleLabel is the same period — this collection's kept"
      )

  override def localized(es: BundleEntities, mapped: Map[UUID, UUID]): BundleEntities =
    val periodIdMap: Map[Bundle.Period.ParentId, Bundle.Period.ParentId] =
      in.foreign.periods
        .flatMap(p =>
          in.periodMatches
            .get(p.uid)
            .filter(_ != p.id)
            .map(m => Bundle.Period.ParentId(p.id.value) -> Bundle.Period.ParentId(m.value))
        )
        .toMap
    repoint(es, periodIdMap)

  override def addedOnBoth(q: PlanQuestions): Unit =
    val localPeriodById = in.local.periods.map(p => p.id -> p).toMap
    q.localized.periods.filter(p => q.writesInitially(p.uid)).sortBy(_.id.value).foreach { p =>
      val f = q.bundleUidOf(p.uid)
      localPeriodById.get(p.id).filter(_.uid != p.uid) match
        case Some(o) if samePeriod(o, p) => q.merge(f, o.uid, SamePeriod)
        case Some(o)                     =>
          val id = MergePlanner.issueId(
            MergeIssue.Kind.NamedPeriodDiffers,
            Bundle.Kind.Period,
            List(f),
            List(o.uid),
            BundlePlan.Ledger.ForeignRev(q.rightRev(f).value),
            BundlePlan.Local.Rev(q.localRev(o.uid).value)
          )
          q.ask(
            MergeIssue(
              id,
              MergeIssue.Kind.NamedPeriodDiffers,
              MergeIssue.Severity.Blocking,
              Bundle.Kind.Period,
              MergeIssue.Title(
                s"named period ${p.id} was added on both sides and says something else: " +
                  s"${o.label} ${MergeIssue.Detail.rangeText(o.period)} here, ${p.label} ${MergeIssue.Detail.rangeText(p.period)} in the bundle"
              ),
              Some(IssueSide(IssueSide.Label(o.id.value), o.uid, List(IssueSide.Lines(o.label.value)))),
              Some(IssueSide(IssueSide.Label(p.id.value), f, List(IssueSide.Lines(p.label.value)))),
              List(
                api.Problem("keep-left keeps this collection's period; keep-right takes the bundle's label and span")
              ),
              lr,
              detail = Some(
                IssueDetail(
                  fields = List(
                    IssueField(
                      IssueField.Field("label"),
                      Some(IssueField.Value(o.label.value)),
                      Some(IssueField.Value(p.label.value))
                    ),
                    IssueField(
                      IssueField.Field("span"),
                      Some(IssueField.Value(MergeIssue.Detail.rangeText(o.period))),
                      Some(IssueField.Value(MergeIssue.Detail.rangeText(p.period)))
                    ),
                    IssueField(
                      IssueField.Field("authority"),
                      o.authority.map(a => IssueField.Value(a.value)),
                      p.authority.map(a => IssueField.Value(a.value))
                    ),
                    IssueField(
                      IssueField.Field("region"),
                      o.region.map(a => IssueField.Value(a.value)),
                      p.region.map(a => IssueField.Value(a.value))
                    )
                  ),
                  place = Some(
                    IssuePlace(
                      leftSpan = o.period.map(MergeIssue.Detail.span),
                      rightSpan = p.period.map(MergeIssue.Detail.span)
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
      case (MergeIssue.Kind.NamedPeriodDiffers, Bundle.Kind.Period, Some(d), Some(f), Some(o)) =>
        folds.fold(f, o, Bundle.Kind.Period)
        folds.count(_.modify(_.periodsMerged).using(n => MergeTally.PeriodsMerged(n.value + 1)))
        if d.kind == km.KeepRight then
          periodLine(o) = es =>
            periodIn(es, o).modify(_.origin).setTo(localPeriod(o).origin).modify(_.rev).setTo(Bundle.Period.Rev(""))
        else folds.keepLocal(f)
      case _ => ()

  override def lines(es: BundleEntities, es0: BundleEntities): BundleEntities =
    es.modify(_.periods)
      .setTo(
        MergePlanner
          .replaced(es0.periods, _.uid, periodLine, es0, (_: Bundle.Period) => true)
      )

object PeriodPlan:

  /** Periods point at their parents by id: a bundle period matched to one here under another id is pointed at under
    * that id. Pure.
    */
  def repoint(es: BundleEntities, periods: Map[Bundle.Period.ParentId, Bundle.Period.ParentId]): BundleEntities =
    if periods.isEmpty then es
    else
      es.modify(_.periods)
        .setTo(es.periods.map(p => p.parentId.flatMap(periods.get).fold(p)(x => p.copy(parentId = Some(x)))))

  /** Two records of one period id that say the same: the same span and label. */
  def samePeriod(
      l: Bundle.Period,
      r: Bundle.Period
  ): Boolean =
    l.period == r.period && Fingerprint.of(Fingerprint.OfS(l.label.value)) == Fingerprint.of(
      Fingerprint.OfS(r.label.value)
    )
