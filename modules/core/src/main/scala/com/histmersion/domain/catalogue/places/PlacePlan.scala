package com.histmersion
package domain.catalogue.places
import com.histmersion.api.exchange.MergeIssue
import com.histmersion.api.exchange.MergeDecision

import com.histmersion.api.exchange.{IssueDetail, IssueSide, MergeIssue, MergeTally}
import com.histmersion.domain.MergePlanner
import com.histmersion.util.data.exchange.BundleEntities
import com.histmersion.util.data.exchange.PairQuestions
import com.histmersion.util.data.exchange.PlanFolds
import com.histmersion.util.data.exchange.PlanMerge
import com.histmersion.util.data.exchange.PlanQuestions
import com.histmersion.util.data.exchange.PlanRun

import hearth.kindlings.optics.syntax.*
import java.util.UUID
import com.histmersion.api.exchange.Bundle

/** How a merge plan treats places of this collection's own: one the bundle renamed is renamed here too — unless its new
  * id is a different place's here, then ours keeps its id and what the bundle points at the new id points at ours; one
  * id added on both sides is one place when where and when agree, else a question; one under another id here is one
  * when its label and geometry are (the database compared them: `placeMatches`).
  */
final class PlacePlan(in: MergePlanner.Input) extends PlanRun:
  import PlacePlan.*

  private val km = MergeDecision.Kind
  private val lr = List(km.KeepLeft, km.KeepRight)
  private val localPlace = in.local.places.map(p => p.uid -> p).toMap
  private val placeLine =
    scala.collection.mutable.Map.empty[UUID, BundleEntities => Bundle.Place]
  private val renames =
    scala.collection.mutable.ListBuffer.empty[(Bundle.Place, Bundle.Place.Id, Boolean)]
  private def placeIn(es: BundleEntities, u: UUID) = es.places.find(_.uid == u).get

  /** The same place: ours, with the bundle's names, parents, external ids and types. */
  private val SamePlace: PlanMerge = new PlanMerge:
    def fold(f: UUID, o: UUID, folds: PlanFolds): Unit =
      folds.fold(f, o, Bundle.Kind.Place)
      placeLine(o) = es => foldPlace(localPlace(o), placeIn(es, o))
      folds.count(_.modify(_.placesMerged).using(n => MergeTally.PlacesMerged(n.value + 1)))
    def info(label: PlanMerge.InfoLabel, bundleLabel: PlanMerge.InfoBundleLabel): PlanMerge.InfoResult =
      PlanMerge.InfoResult(s"place $label: the bundle's $bundleLabel is the same place — one place, names unioned")

  // a place the bundle renamed (its uid is here under another id, #888): the import follows the rename — unless the
  // new id is a different place's here; then ours keeps its id, and what the bundle points at the new id points at
  // ours. A bundle place matched to one here under another id: what points at it by id follows
  override def localized(es: BundleEntities, mapped: Map[UUID, UUID]): BundleEntities =
    renames ++= renamedPlaces(in.foreign.places, in.local.places, mapped.keySet)
    val renameKept: Map[UUID, Bundle.Place.Id] = renames.collect { case (p, ours, true) => p.uid -> ours }.toMap
    val placeIdMap: Map[Bundle.Place.Id, Bundle.Place.Id] =
      in.foreign.places.flatMap(p => in.placeMatches.get(p.uid).filter(_ != p.id).map(p.id -> _)).toMap ++
        renames.collect { case (p, ours, true) => p.id -> ours }
    val repointed = repoint(es, placeIdMap)
    if renameKept.isEmpty then repointed
    else
      repointed
        .modify(_.places)
        .setTo(repointed.places.map(p => renameKept.get(p.uid).fold(p)(ours => p.copy(id = ours))))

  override def notices: List[PairQuestions.InfoLine] =
    renames.toList.map { (p, ours, kept) =>
      if kept then PairQuestions.InfoLine(s"place $ours: renamed ${p.id} in the bundle, but here ${p.id} is another place — it keeps its id")
      else PairQuestions.InfoLine(s"place $ours: renamed ${p.id} in the bundle — renamed here too (the old id stays an alias)")
    }

  override def rowDetail(kind: Bundle.Kind, u: UUID, localized: BundleEntities): Option[IssueDetail] =
    if kind != Bundle.Kind.Place then None
    else in.local.places.find(_.uid == u).zip(localized.places.find(_.uid == u)).map(MergeIssue.Detail.placeDetail)

  // places added on both sides: one id is one place when where and when agree, a question when they do not; a place
  // under another id is one when its label and geometry are
  override def addedOnBoth(q: PlanQuestions): Unit =
    val localPlaceById = in.local.places.map(p => p.id -> p).toMap
    q.localized.places.filter(p => q.writesInitially(p.uid)).sortBy(_.id.value).foreach { p =>
      val f = q.bundleUidOf(p.uid)
      localPlaceById.get(p.id).filter(_.uid != p.uid) match
        case Some(o) if samePlace(o, p) => q.merge(f, o.uid, SamePlace)
        case Some(o)                    =>
          val id = MergePlanner.issueId(
            MergeIssue.Kind.PlaceDiffers,
            Bundle.Kind.Place,
            List(f),
            List(o.uid),
            q.rightRev(f),
            q.localRev(o.uid)
          )
          q.ask(
            MergeIssue(
              id,
              MergeIssue.Kind.PlaceDiffers,
              MergeIssue.Severity.Blocking,
              Bundle.Kind.Place,
              MergeIssue.Title(
                s"place ${p.id} was added on both sides, " +
                  (if o.geom != p.geom then "with different geometries" else "with different time spans")
              ),
              Some(IssueSide(IssueSide.Label(o.id.value), o.uid, List(IssueSide.Lines(o.label.value)))),
              Some(IssueSide(IssueSide.Label(p.id.value), f, List(IssueSide.Lines(p.label.value)))),
              List(
                MergeIssue.Why(
                  "keep-left keeps this collection's place (the bundle's events point at it); keep-right takes the bundle's " +
                    "geometry and span"
                )
              ),
              lr,
              detail = Some(MergeIssue.Detail.placeDetail(o, p))
            ),
            Set.empty
          )
        case None =>
          localPlaceById.values
            .find(x => in.placeMatches.get(f).contains(x.id))
            .foreach(o => q.merge(f, o.uid, SamePlace))
    }

  // a place added on both sides under one id: ours stays, or theirs wins
  override def decided(i: MergeIssue, folds: PlanFolds): Unit =
    (i.kind, i.entity, i.decision, i.right.map(_.uid), i.left.map(_.uid)) match
      case (MergeIssue.Kind.PlaceDiffers, Bundle.Kind.Place, Some(d), Some(f), Some(o)) =>
        folds.fold(f, o, Bundle.Kind.Place)
        folds.count(_.modify(_.placesMerged).using(n => MergeTally.PlacesMerged(n.value + 1)))
        if d.kind == km.KeepRight then
          placeLine(o) = es => foldPlace(placeIn(es, o).modify(_.origin).setTo(localPlace(o).origin), localPlace(o))
        else folds.keepLocal(f)
      case _ => ()

  override def lines(es: BundleEntities, es0: BundleEntities): BundleEntities =
    es.modify(_.places)
      .setTo(
        MergePlanner.replaced(es0.places, _.uid, placeLine, es0, (_: Bundle.Place) => true)
      )

object PlacePlan:

  /** Events and places point at places by id: a bundle place matched to one here under another id is pointed at under
    * that id. Pure.
    */

  def repoint(es: BundleEntities, places: Map[Bundle.Place.Id, Bundle.Place.Id]): BundleEntities =
    if places.isEmpty then es
    else
      es.modify(_.events)
        .setTo(es.events.map(e => e.placeId.flatMap(places.get).fold(e)(p => e.copy(placeId = Some(p)))))
        .modify(_.places)
        .setTo(
          es.places.map(p =>
            p.modify(_.relations).setTo(p.relations.map(r => places.get(r.parentId).fold(r)(x => r.copy(parentId = x))))
          )
        )

  /** The bundle's places that are one here under another id — the other collection renamed them (the same uid, a new
    * id): each with our id, and whether the new id is a DIFFERENT place's here (then the rename cannot be followed). A
    * place a redirect maps (`mapped`) is localized already. Pure.
    */
  def renamedPlaces(
      bundle: List[Bundle.Place],
      here: List[Bundle.Place],
      mapped: Set[UUID]
  ): List[(Bundle.Place, Bundle.Place.Id, Boolean)] =
    val ourId = here.map(p => p.uid -> p.id).toMap
    val owner = here.map(p => p.id -> p.uid).toMap
    bundle
      .filterNot(p => mapped(p.uid))
      .flatMap(p =>
        ourId.get(p.uid).filter(_ != p.id).map { ours =>
          (p, ours, owner.get(p.id).exists(_ != p.uid))
        }
      )

  /** What decides whether two records of one place id are the same place: where and when. */
  def samePlace(l: Bundle.Place, r: Bundle.Place): Boolean =
    l.geom == r.geom && l.period == r.period

  /** One place out of two that are the same place: ours, with the bundle's names, parents, external ids and types that
    * ours lacks.
    */
  def foldPlace(
      l: Bundle.Place,
      r: Bundle.Place
  ): Bundle.Place =
    l.modify(
      _.names).setTo((l.names ++ r.names.filterNot(n => l.names.exists(m => m.name == n.name && m.lang == n.lang)))
        .sortBy(n => (n.name.value, n.lang.fold("")(_.value)))).modify(_.relations).setTo((l.relations ++ r.relations.filterNot(x =>
        l.relations.exists(y => y.parentId == x.parentId && y.kind == x.kind)
      ))
        .sortBy(x => (x.parentId.value, x.kind.value))).modify(_.links).setTo((l.links ++ r.links.filterNot(x => l.links.exists(y => y.scheme == x.scheme && y.externalId == x.externalId)))
          .sortBy(x => (x.scheme.value, x.externalId.value))).modify(_.types).setTo((l.types ++ r.types.filterNot(x => l.types.exists(_.typeLabel == x.typeLabel))).sortBy(_.typeLabel.value)).modify(_.source).setTo(l.source.orElse(r.source)).modify(_.properties).setTo(l.properties.orElse(r.properties)).modify(_.rev).setTo(Bundle.Place.Rev("")
    )
