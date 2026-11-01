package com.histmersion.domain.catalogue.events

import com.histmersion.api.{BundleEvent, BundleRange, IssueDetail, MergeDecision, MergeIssue, MergeIssueDetail}
import com.histmersion.domain.{MergeFold, MergePlanner}
import com.histmersion.files.BundleEntities
import com.histmersion.util.data.exchange.{PlanFolds, PlanMerge, PlanQuestions, PlanRun}
import com.histmersion.util.text.Fingerprint
import com.histmersion.values.{BundleKind, DecisionKind, IssueKind}

import hearth.kindlings.optics.syntax.*
import java.util.UUID

/** How a merge plan treats events: one changed on both sides, or added under an id an event here has, is one when its
  * dating and place agree (its datings and participants unioned), else a question; one added on both sides under two ids
  * is one when its label, dates and place agree. A rename decided gives the bundle's event another id, which must be
  * one; an event whose place this gazetteer lacks cannot be written.
  */
final class EventPlan(in: MergePlanner.Input) extends PlanRun:
  import EventPlan.*

  private val km = DecisionKind
  private val localEvent = in.local.events.map(e => e.uid -> e).toMap
  private val eventLine = scala.collection.mutable.Map.empty[UUID, BundleEntities => BundleEvent]
  private def eventIn(es: BundleEntities, u: UUID) = es.events.find(_.uid == u).get

  /** The same dating and place on both sides: one event. */
  private val SameEvent: PlanMerge = new PlanMerge:
    def fold(f: UUID, o: UUID, folds: PlanFolds): Unit = merged(f, o, folds)
    def info(label: String, bundleLabel: String): String =
      s"event $label: the same dating and place on both sides — one event, datings and participants unioned"

  /** Under another id here, with the same label, dates and place: one event. */
  private val AcrossIds: PlanMerge = new PlanMerge:
    def fold(f: UUID, o: UUID, folds: PlanFolds): Unit = merged(f, o, folds)
    def info(label: String, bundleLabel: String): String =
      s"event $label: the bundle's $bundleLabel has the same label, dates and place — one event"

  private def merged(f: UUID, o: UUID, folds: PlanFolds): Unit =
    if f != o then folds.fold(f, o, BundleKind.Event)
    eventLine(o) = es => foldEvent(localEvent(o), eventIn(es, o))
    folds.count(_.modify(_.eventsMerged).using(_ + 1))

  override def rowDetail(kind: BundleKind, u: UUID, localized: BundleEntities): Option[IssueDetail] =
    if kind != BundleKind.Event then None
    else localEvent.get(u).zip(localized.events.find(_.uid == u)).map(MergeIssueDetail.eventDetail)

  override def conflict(row: MergePlanner.Row, localized: BundleEntities): Option[PlanMerge] =
    Option.when(
      row.kind == BundleKind.Event &&
        localEvent.get(row.uid).zip(localized.events.find(_.uid == row.uid)).exists((a, b) => sameCore(a, b))
    )(SameEvent)

  override def labelsTaken(q: PlanQuestions): Unit =
    q.labelTaken(
      BundleKind.Event,
      q.localized.events,
      _.uid,
      _.id,
      e => in.labels.events.get(e.id),
      "event id",
      List(km.KeepLeft, km.KeepRight, km.RenameRight),
      (e, o) => localEvent.get(o).filter(sameCore(_, e)).map(_ => SameEvent),
      (e, o) => localEvent.get(o).map(MergeIssueDetail.eventDetail(_, e))
    )

  // an event under another id here that is the same event: the same label, dates and place
  override def addedOnBoth(q: PlanQuestions): Unit =
    val claimed = scala.collection.mutable.Set.empty[UUID]
    q.localized.events
      .filter(e =>
        q.writesInitially(e.uid) && !q.merged(q.bundleUidOf(e.uid)) && in.labels.events.get(e.id).forall(_ == e.uid)
      )
      .sortBy(_.id)
      .foreach { e =>
        in.local.events
          .filter(l => !claimed(l.uid) && !q.inLocalized(l.uid) && sameEventAcrossIds(l, e))
          .sortBy(_.id)
          .headOption
          .foreach { l =>
            claimed += l.uid
            q.merge(q.bundleUidOf(e.uid), l.uid, AcrossIds)
          }
      }

  override def argument(issue: MergeIssue, decision: MergeDecision): Option[Either[String, Unit]] =
    Option.when(
      issue.entity == BundleKind.Event && (decision.kind == km.RenameRight || decision.kind == km.RenameLeft)
    ) {
      val eid = decision.argument.getOrElse("")
      val idsHere = in.local.events.map(_.id).toSet ++ in.foreign.events.map(_.id)
      if !SlugShape.matches(eid) then Left(s"'$eid' is not an event id (lower-case letters, digits, _ . -)")
      else if idsHere(eid) then Left(s"'$eid' is already an event id here or in the bundle")
      else Right(())
    }

  override def decided(i: MergeIssue, folds: PlanFolds): Unit =
    (i.kind, i.entity, i.decision, i.right.map(_.uid), i.left.map(_.uid)) match
      case (IssueKind.LabelTaken, BundleKind.Event, Some(d), Some(f), Some(o))
          if d.kind == km.KeepRight && localEvent.contains(o) =>
        folds.fold(f, o, BundleKind.Event)
        eventLine(o) = es =>
          foldEvent(
            eventIn(es, o).copy(id = localEvent(o).id, origin = localEvent(o).origin),
            localEvent(o)
          )
        folds.count(_.modify(_.eventsMerged).using(_ + 1))
      case (IssueKind.LabelTaken, BundleKind.Event, Some(d), Some(f), _) if d.kind == km.RenameRight =>
        d.argument.foreach(eid => eventLine(f) = es => eventIn(es, f).copy(id = eid))
      case _ => ()

  override def lines(es: BundleEntities, es0: BundleEntities): BundleEntities =
    es.modify(_.events).setTo(MergePlanner.replaced(es0.events, _.uid, eventLine, es0, (_: BundleEvent) => true))

  // gazetteer places: an event written must point at a place that is here or coming
  override def problems(finalEs: BundleEntities, live: UUID => Boolean, writes: UUID => Boolean): List[String] =
    val placesComing = finalEs.places.filter(p => live(p.uid)).map(_.id).toSet ++ in.local.places.map(_.id)
    finalEs.events
      .filter(e => writes(e.uid))
      .flatMap(e => e.placeId.map(e.id -> _))
      .filterNot((_, p) => in.places(p) || placesComing(p))
      .map((e, p) => s"event $e: place '$p' is not in this gazetteer — run `histmersion init` (or import that place) first")

object EventPlan:

  /** A label for an event id. */
  private val SlugShape = "^[a-z0-9][a-z0-9_.-]*$".r

  /** Two events with one id that say the same about when and where (§5.4): they are one. */
  def sameCore(l: BundleEvent, r: BundleEvent): Boolean =
    l.notBefore == r.notBefore && l.beginBy == r.beginBy && l.endFrom == r.endFrom && l.notAfter == r.notAfter &&
      l.geom == r.geom && l.placeId == r.placeId

  /** Two events under DIFFERENT ids that are one (§5.4): the same label (fingerprint), outer spans that share a year,
    * and the same place — the same gazetteer id, or the same geometry, or neither side placing it.
    */
  def sameEventAcrossIds(l: BundleEvent, r: BundleEvent): Boolean =
    def outer(e: BundleEvent) = Some(BundleRange(e.notBefore.orElse(e.beginBy), e.notAfter.orElse(e.endFrom)))
    val place = (l.placeId, r.placeId, l.geom, r.geom) match
      case (Some(a), Some(b), _, _)       => a == b
      case (None, None, Some(a), Some(b)) => a == b
      case (None, None, None, None)       => true
      case _                              => false
    val label = Fingerprint.of(l.label).value
    label.nonEmpty && label == Fingerprint.of(r.label).value && MergeFold.overlap(outer(l), outer(r)) && place

  /** One event out of two: `winner`'s fields, the datings (per citekey), participants and outgoing edges of both — the
    * winner's where both have one.
    */
  def foldEvent(winner: BundleEvent, other: BundleEvent): BundleEvent =
    val stated =
      if winner.central.isDefined || winner.plusMinus.isDefined || winner.datingMethod.isDefined then winner else other
    winner.copy(
      datings = (winner.datings ++ other.datings.filterNot(d => winner.datings.exists(_.citekey == d.citekey)))
        .sortBy(_.citekey),
      participants = (winner.participants ++ other.participants.filterNot(p =>
        winner.participants.exists(w => w.actor == p.actor && w.role == p.role)
      )).sortBy(p => (p.actor, p.role)),
      edges =
        (winner.edges ++ other.edges.filterNot(e => winner.edges.exists(w => w.dstUid == e.dstUid && w.kind == e.kind)))
          .sortBy(e => (e.dstUid.toString, e.kind)),
      description = winner.description.orElse(other.description),
      citekey = winner.citekey.orElse(other.citekey),
      sourceUid = if winner.citekey.isDefined then winner.sourceUid else other.sourceUid,
      parentUid = winner.parentUid.orElse(other.parentUid),
      catalogue = winner.catalogue.orElse(other.catalogue),
      wikidata = winner.wikidata.orElse(other.wikidata),
      names = Option((winner.names.getOrElse(Nil) ++ other.names.getOrElse(Nil)).distinct).filter(_.nonEmpty),
      // the stated dating is one statement: the winner's, or the other's whole when the winner states none
      central = stated.central,
      plusMinus = stated.plusMinus,
      datingMethod = stated.datingMethod,
      rev = ""
    )
