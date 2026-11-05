package com.histmersion.domain.catalogue.events

import com.histmersion.api.exchange.Bundle

import com.histmersion.api.catalogue.EventEntry

import com.histmersion.api.aliases.LabelRename
import com.histmersion.api.aliases.LabelRenames
import com.histmersion.api.aliases.LabelRenamed
import com.histmersion.api.aliases.AliasKinds

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.{Api, BBox, Write}
import com.histmersion.api.catalogue.{EventDeleted, EventGeometry, EventList, EventPatch, EventPut, EventScale}
import com.histmersion.api.library.Filter
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.Caller
import com.histmersion.domain.catalogue.places.Wikidata
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.aliases.AliasKind
import com.histmersion.api.catalogue.EventGeo

/** The events' operations as REST, MCP and the CLI reach them. An old event id answers as the event it was renamed to,
  * and a write to one goes to that event. `card` is what an event's page shows beside its own fields (its catalogues,
  * sources, findings, proposals, lead and the articles citing its sources); `resolve` gives a list's filter its page
  * size, `scope` the gazetteer's reading of its places; `mapView` the map's limit and outline tolerance.
  */
final class EventsControllers(
    events: EventService,
    card: EventEntry => EventEntry,
    current: (AliasKind, EventsControllers.CurrentArg2) => EventsControllers.CurrentResult,
    resolve: Filter => Filter,
    scope: Filter => PlaceScope,
    mapView: () => (EventsControllers.MapViewResultPart1, EventsControllers.MapViewResultPart2),
    maintenance: WriteGate
):

  /** What only an event is narrowed by: tier, the catalogue context, the area of the map. */
  private def narrow(q: Api.EventQuery): EventSql.Narrow =
    EventSql.Narrow(q.tier.map(_.trim).filter(_.nonEmpty), q.context.getOrElse(false), q.area.flatMap(BBox.parse))

  private def selection(q: Api.EventQuery, f: Filter): EventSelection =
    EventSelection(f, scope(f), q.text, q.kind, q.historicity, q.placeId, narrow(q))

  /** `GET /api/events` — the events related to the filter's sources. */
  def list(q: Api.EventQuery): EventList =
    val f = resolve(q.filter)
    events.list(selection(q, f), f.limit, f.offset)

  /** `GET /api/events/geo` — the map's events with their scale, anchor and outline. */
  def geo(q: Api.EventQuery): EventGeo.List =
    val (limit, tolerance) = mapView()
    events.geo(selection(q, q.filter), limit, tolerance)

  /** A stated scale must be `local`, `regional` or (to clear it) `auto`. */
  private def validScale(scale: Option[com.histmersion.domain.catalogue.events.EventsControllers.ValidScaleScale]): Either[Api.Error, Unit] =
    scale match
      case Some(v) if EventScale.parse(v).isEmpty =>
        Left(
          Api.Error(
            s"scale '$v' is not ${(EventScale.all :+ EventScale.Auto).mkString(" | ")}",
            Some(Write.CodeInvalid)
          )
        )
      case _ => Right(())

  /** An event's geometry is a GeoJSON geometry the store reads: WKT, a Feature or bad coordinates are a 400 naming the
    * problem, not a failed statement. Blank passes (none / clear).
    */
  private def validGeometry(g: Option[com.histmersion.domain.catalogue.events.EventsControllers.ValidGeometryG]): Either[Api.Error, Unit] =
    g.map(_.trim).filter(_.nonEmpty) match
      case None    => Right(())
      case Some(v) =>
        EventGeometry
          .check(v)
          .flatMap(gj => events.geometryError(gj).map(m => s"geometry is not a valid GeoJSON geometry: $m").toLeft(()))
          .left
          .map(m => Api.Error(m, Some(Write.CodeInvalid)))

  /** An event's Wikidata item is a `Q…` id; `""` clears it. */
  private def validWikidata(q: Option[com.histmersion.domain.catalogue.events.EventsControllers.ValidWikidataQ]): Either[Api.Error, Unit] =
    q.map(_.trim) match
      case Some(v) if v.nonEmpty && !Wikidata.isQid(v) =>
        Left(Api.Error(s"wikidata '$v' is not a Wikidata item id (Q followed by digits)", Some(Write.CodeInvalid)))
      case _ => Right(())

  private def now(id: com.histmersion.domain.catalogue.events.EventsControllers.NowId): com.histmersion.domain.catalogue.events.EventsControllers.NowResult = current(AliasKinds.EventRecords, id)

  def get(id: com.histmersion.domain.catalogue.events.EventsControllers.GetId): Either[Api.Error, EventEntry] =
    events.get(now(id)).map(card).toRight(Api.Error(s"No event '$id'", Some(Write.CodeNotFound)))

  /** A put to an old id replaces the event it was renamed to, never creates a second one under the old id. */
  def put(caller: Caller, id: com.histmersion.domain.catalogue.events.EventsControllers.PutId, in: EventPut): Either[Api.Error, EventEntry] =
    maintenance.guard("put_event"):
      val to = now(id)
      validScale(in.scale).flatMap(_ => validGeometry(in.geometry)).flatMap { _ =>
        events.put(to, in)
        get(to)
      }

  def patch(caller: Caller, id: com.histmersion.domain.catalogue.events.EventsControllers.PatchId, p: EventPatch): Either[Api.Error, EventEntry] =
    maintenance.guard("patch_event"):
      val to = now(id)
      validScale(p.scale)
        .flatMap(_ => validWikidata(p.wikidata))
        .flatMap(_ => validGeometry(p.geometry))
        .flatMap(_ => events.patch(to, p).flatMap(_ => get(to)))

  def delete(caller: Caller, id: com.histmersion.domain.catalogue.events.EventsControllers.DeleteId): Either[Api.Error, EventDeleted] =
    maintenance.guard("delete_event"):
      val to = now(id)
      if events.delete(to) then Right(EventDeleted(to, true))
      else Left(Api.Error(s"No event '$id'", Some(Write.CodeNotFound)))

  /** `POST /api/events/{id}/rename` — a new id (`to` checks the one asked for against the current one); the old one
    * keeps resolving. Answers the event's id before and after.
    */
  /** `POST /api/events/{id}/rename` — a new id; the old one keeps resolving (V053). */
  def renamed(id: com.histmersion.domain.catalogue.events.EventsControllers.RenamedId, req: LabelRename): Either[Api.Error, LabelRenamed] =
    rename(Caller.Local, id, from => LabelRenames.validate(Bundle.Kind.Event, from, req.to))
      .map((from, to) => LabelRenamed(Bundle.Kind.Event, from, to))

  def rename(caller: Caller, id: com.histmersion.domain.catalogue.events.EventsControllers.RenameId, to: com.histmersion.domain.catalogue.events.EventsControllers.RenameToArg1 => Either[Api.Error, com.histmersion.domain.catalogue.events.EventsControllers.RenameToResultPart2]): Either[Api.Error, (com.histmersion.domain.catalogue.events.EventsControllers.RenameResultPart2Part1, com.histmersion.domain.catalogue.events.EventsControllers.RenameResultPart2Part2)] =
    maintenance.guard("rename_event"):
      val from = now(id)
      for
        newId <- to(from)
        _ <- events.rename(from, newId)
      yield (from, newId)

object EventsControllers:
  type CurrentArg2 = CurrentArg2.T
  object CurrentArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CurrentResult = CurrentResult.T
  object CurrentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapViewResultPart1 = MapViewResultPart1.T
  object MapViewResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapViewResultPart2 = MapViewResultPart2.T
  object MapViewResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type ValidScaleScale = ValidScaleScale.T
  object ValidScaleScale extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValidGeometryG = ValidGeometryG.T
  object ValidGeometryG extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ValidWikidataQ = ValidWikidataQ.T
  object ValidWikidataQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NowResult = NowResult.T
  object NowResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NowId = NowId.T
  object NowId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetId = GetId.T
  object GetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutId = PutId.T
  object PutId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchId = PatchId.T
  object PatchId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteId = DeleteId.T
  object DeleteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenamedId = RenamedId.T
  object RenamedId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameResultPart2Part1 = RenameResultPart2Part1.T
  object RenameResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameResultPart2Part2 = RenameResultPart2Part2.T
  object RenameResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameId = RenameId.T
  object RenameId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameToArg1 = RenameToArg1.T
  object RenameToArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameToResultPart2 = RenameToResultPart2.T
  object RenameToResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
