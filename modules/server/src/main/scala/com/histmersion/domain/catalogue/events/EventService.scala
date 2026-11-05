package com.histmersion.domain.catalogue.events

import com.histmersion.api.catalogue.EventEntry

import com.histmersion.api.Write
import com.histmersion.api.catalogue.{
  EventCatalogueRef,
  EventFields,
  EventGeo,
  EventLeads,
  EventList,
  EventPatch,
  EventPrecision,
  EventPut,
  EventScale,
  EventSummary,
  EventTaxonomy,
  EventTier
}
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Correction

/** The events: dated happenings with their datings by source, their relations to each other and their participants,
  * listed by a selection, drawn on the map, written by hand, renamed and deleted.
  */
trait EventService:
  /** The events `selection` selects, by their earliest year then label, one page of them. */
  def list(selection: EventSelection, limit: com.histmersion.domain.catalogue.events.EventService.ListLimit, offset: com.histmersion.domain.catalogue.events.EventService.ListOffset): EventList

  /** The events `selection` selects that the map draws (placed, dated well enough), at most `limit`, with the regional
    * ones' outlines simplified to `tolerance` degrees; the unplaced and the imprecisely dated counted.
    */
  def geo(selection: EventSelection, limit: com.histmersion.domain.catalogue.events.EventService.GeoLimit, tolerance: com.histmersion.domain.catalogue.events.EventService.GeoTolerance): EventGeo.List

  /** The events of `ids` the map draws, by id. */
  def geoOf(ids: List[com.histmersion.domain.catalogue.events.EventService.GeoOfIds]): Map[com.histmersion.domain.catalogue.events.EventService.GeoOfResultPart1, EventGeo]

  /** One event with its parent, parts, datings, edges, participants, Wikidata item and corrections by hand. */
  def get(id: com.histmersion.domain.catalogue.events.EventService.GetId): Option[EventEntry]

  /** Its tier, its other names and the catalogue it was imported from. */
  def tierAndNames(id: com.histmersion.domain.catalogue.events.EventService.TierAndNamesId): Option[(com.histmersion.domain.catalogue.events.EventService.TierAndNamesResultPart1, List[com.histmersion.domain.catalogue.events.EventService.TierAndNamesResultPart2], Option[com.histmersion.domain.catalogue.events.EventService.TierAndNamesResultPart3])]

  /** The catalogues' rows of one event, with each built-in catalogue's licence and citation. */
  def catalogueRefs(id: com.histmersion.domain.catalogue.events.EventService.CatalogueRefsId): List[EventCatalogueRef]

  /** Why `geojson` is no geometry the store reads; `None` when it is one. */
  def geometryError(geojson: com.histmersion.domain.catalogue.events.EventService.GeometryErrorGeojson): Option[com.histmersion.domain.catalogue.events.EventService.GeometryErrorResult]

  /** Create or replace one event; on one an import created, the fields that differ from its catalogue's are recorded as
    * corrections.
    */
  def put(id: com.histmersion.domain.catalogue.events.EventService.PutId, in: EventPut): Unit

  /** Change the fields `p` sets; on one an import created, each changed field is recorded as a correction. */
  def patch(id: com.histmersion.domain.catalogue.events.EventService.PatchId, p: EventPatch): Either[Api.Error, Unit]

  /** A new id; the old one stays an alias, what names the event follows. */
  def rename(from: com.histmersion.domain.catalogue.events.EventService.RenameFrom, to: com.histmersion.domain.catalogue.events.EventService.RenameTo): Either[Api.Error, Unit]

  /** The id an old id was renamed to; `None` for a live id, or nothing. */
  def renamedTo(id: com.histmersion.domain.catalogue.events.EventService.RenamedToId): Option[com.histmersion.domain.catalogue.events.EventService.RenamedToResult]

  def delete(id: com.histmersion.domain.catalogue.events.EventService.DeleteId): com.histmersion.domain.catalogue.events.EventService.DeleteResult

object EventService:
  type ListLimit = ListLimit.T
  object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ListOffset = ListOffset.T
  object ListOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeoLimit = GeoLimit.T
  object GeoLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeoTolerance = GeoTolerance.T
  object GeoTolerance extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeoOfResultPart1 = GeoOfResultPart1.T
  object GeoOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeoOfIds = GeoOfIds.T
  object GeoOfIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetId = GetId.T
  object GetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TierAndNamesResultPart1 = TierAndNamesResultPart1.T
  object TierAndNamesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TierAndNamesResultPart2 = TierAndNamesResultPart2.T
  object TierAndNamesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TierAndNamesResultPart3 = TierAndNamesResultPart3.T
  object TierAndNamesResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TierAndNamesId = TierAndNamesId.T
  object TierAndNamesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CatalogueRefsId = CatalogueRefsId.T
  object CatalogueRefsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeometryErrorResult = GeometryErrorResult.T
  object GeometryErrorResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeometryErrorGeojson = GeometryErrorGeojson.T
  object GeometryErrorGeojson extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutId = PutId.T
  object PutId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchId = PatchId.T
  object PatchId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameFrom = RenameFrom.T
  object RenameFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameTo = RenameTo.T
  object RenameTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenamedToResult = RenamedToResult.T
  object RenamedToResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenamedToId = RenamedToId.T
  object RenamedToId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteResult = DeleteResult.T
  object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteId = DeleteId.T
  object DeleteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MissingId = MissingId.T
  object MissingId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor, related: EventSourceFilter): EventService = Implementation(db, related)

  private def missing(id: com.histmersion.domain.catalogue.events.EventService.MissingId) = Api.Error(s"No event '$id'", Some(Write.CodeNotFound))

  final private class Implementation(db: Transactor, related: EventSourceFilter) extends EventService:
    def list(selection: EventSelection, limit: com.histmersion.domain.catalogue.events.EventService.Implementation.ListLimit, offset: com.histmersion.domain.catalogue.events.EventService.Implementation.ListOffset): EventList =
      db.read(EventRepo.list(selection.sql(related), limit, offset))
    def geo(selection: EventSelection, limit: com.histmersion.domain.catalogue.events.EventService.Implementation.GeoLimit, tolerance: com.histmersion.domain.catalogue.events.EventService.Implementation.GeoTolerance): EventGeo.List =
      db.read(EventRepo.geo(selection.sql(related), limit, tolerance))
    def geoOf(ids: List[com.histmersion.domain.catalogue.events.EventService.Implementation.GeoOfIds]): Map[com.histmersion.domain.catalogue.events.EventService.Implementation.GeoOfResultPart1, EventGeo] = db.read(EventRepo.geoOf(ids))
    def get(id: com.histmersion.domain.catalogue.events.EventService.Implementation.GetId): Option[EventEntry] = db.read(EventRepo.get(id))
    def tierAndNames(id: com.histmersion.domain.catalogue.events.EventService.Implementation.TierAndNamesId): Option[(com.histmersion.domain.catalogue.events.EventService.Implementation.TierAndNamesResultPart1, List[com.histmersion.domain.catalogue.events.EventService.Implementation.TierAndNamesResultPart2], Option[com.histmersion.domain.catalogue.events.EventService.Implementation.TierAndNamesResultPart3])] = db.read(EventRepo.tierAndNames(id))
    def catalogueRefs(id: com.histmersion.domain.catalogue.events.EventService.Implementation.CatalogueRefsId): List[EventCatalogueRef] = db.read(EventImportRepo.catalogueRefs(id))
    def geometryError(geojson: com.histmersion.domain.catalogue.events.EventService.Implementation.GeometryErrorGeojson): Option[com.histmersion.domain.catalogue.events.EventService.Implementation.GeometryErrorResult] = db.read(EventRepo.geometryError(geojson))
    def put(id: com.histmersion.domain.catalogue.events.EventService.Implementation.PutId, in: EventPut): Unit = db.transact(EventRepo.put(id, in))
    def patch(id: com.histmersion.domain.catalogue.events.EventService.Implementation.PatchId, p: EventPatch): Either[Api.Error, Unit] = db.transact(EventRepo.patch(id, p))
    def rename(from: com.histmersion.domain.catalogue.events.EventService.Implementation.RenameFrom, to: com.histmersion.domain.catalogue.events.EventService.Implementation.RenameTo): Either[Api.Error, Unit] = db.transact(EventRepo.rename(from.trim, to.trim))
    def renamedTo(id: com.histmersion.domain.catalogue.events.EventService.Implementation.RenamedToId): Option[com.histmersion.domain.catalogue.events.EventService.Implementation.RenamedToResult] = db.read(EventRepo.renamedTo(id))
    def delete(id: com.histmersion.domain.catalogue.events.EventService.Implementation.DeleteId): com.histmersion.domain.catalogue.events.EventService.Implementation.DeleteResult = db.transact(EventRepo.delete(id))

  object Implementation:
    type ListLimit = ListLimit.T
    object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListOffset = ListOffset.T
    object ListOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GeoLimit = GeoLimit.T
    object GeoLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GeoTolerance = GeoTolerance.T
    object GeoTolerance extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GeoOfResultPart1 = GeoOfResultPart1.T
    object GeoOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GeoOfIds = GeoOfIds.T
    object GeoOfIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GetId = GetId.T
    object GetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TierAndNamesResultPart1 = TierAndNamesResultPart1.T
    object TierAndNamesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TierAndNamesResultPart2 = TierAndNamesResultPart2.T
    object TierAndNamesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TierAndNamesResultPart3 = TierAndNamesResultPart3.T
    object TierAndNamesResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TierAndNamesId = TierAndNamesId.T
    object TierAndNamesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CatalogueRefsId = CatalogueRefsId.T
    object CatalogueRefsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GeometryErrorResult = GeometryErrorResult.T
    object GeometryErrorResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GeometryErrorGeojson = GeometryErrorGeojson.T
    object GeometryErrorGeojson extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PutId = PutId.T
    object PutId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PatchId = PatchId.T
    object PatchId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenameFrom = RenameFrom.T
    object RenameFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenameTo = RenameTo.T
    object RenameTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenamedToResult = RenamedToResult.T
    object RenamedToResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenamedToId = RenamedToId.T
    object RenamedToId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteResult = DeleteResult.T
    object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteId = DeleteId.T
    object DeleteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The same rules over the shared in-memory state, which holds the catalogue's rows alone: no place is stored, so an
    * event is anchored by its own geometry (a point; an area has no outline) and has no place's kind or label;
    * `selectedSources` stands for the library's reading of a filter's sources (`None`: the filter sets none).
    */
  final class InMemory(
      state: AtomicReference[CatalogueState],
      selectedSources: com.histmersion.api.library.Filter => Option[Set[String]] = _ => None
  ) extends EventService:

    private def attested(s: CatalogueState, id: com.histmersion.domain.catalogue.events.EventService.InMemory.AttestedId): com.histmersion.domain.catalogue.events.EventService.InMemory.AttestedResult =
      s.attestations.exists(a => a.targetType == "event" && a.targetId == id)

    private def overlaps(e: HeldEvent, period: Option[com.histmersion.api.catalogue.Period]): com.histmersion.domain.catalogue.events.EventService.InMemory.OverlapsResult =
      period.forall(p => e.outer.forall((a, b) => a <= p.end && b >= p.start))

    private def containsText(field: Option[com.histmersion.domain.catalogue.events.EventService.InMemory.ContainsTextField], t: com.histmersion.domain.catalogue.events.EventService.InMemory.ContainsTextT): com.histmersion.domain.catalogue.events.EventService.InMemory.ContainsTextResult =
      field.exists(_.toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT)))

    /** The part of the selection the library answers in the database: its sources, its period, its title. */
    private def related(s: CatalogueState, e: HeldEvent, f: com.histmersion.api.library.Filter): Option[com.histmersion.domain.catalogue.events.EventService.InMemory.RelatedResult] =
      val sources = selectedSources(f.sourceSet.modify(_.period).setTo(None))
      val title = f.title.map(_.trim).filter(_.nonEmpty)
      if sources.isEmpty && f.period.isEmpty && title.isEmpty then None
      else
        val bySource = sources.forall(keys =>
          e.citekey.exists(keys) ||
            s.attestations.exists(a => a.targetType == "event" && a.targetId == e.id && keys(a.citekey))
        )
        Some(bySource && overlaps(e, f.period) && title.forall(t => containsText(Some(e.label), t)))

    private def selects(s: CatalogueState, sel: EventSelection)(e: HeldEvent): com.histmersion.domain.catalogue.events.EventService.InMemory.SelectsResult =
      def clean(o: Option[String]) = o.map(_.trim).filter(_.nonEmpty)
      val tier = e.summary(attested(s, e.id)).tier
      val bySources = related(s, e, sel.filter).forall { inSelection =>
        inSelection || (sel.narrow.context && tier == EventTier.Catalogue &&
          overlaps(e, sel.filter.period) &&
          sel.filter.title.map(_.trim).filter(_.nonEmpty).forall(t => containsText(Some(e.label), t)))
      }
      bySources &&
      clean(sel.narrow.tier).map(_.toLowerCase).forall(_ == tier) &&
      sel.narrow.area.forall(b => e.geometry.flatMap(HeldEvent.pointOf).exists((lat, lon) => b.contains(lon, lat))) &&
      clean(sel.text).forall(t => containsText(Some(e.label), t) || containsText(e.description, t)) &&
      EventTaxonomy.condition(clean(sel.kind)).forall(c => c.kinds.contains(e.kind) != c.negate) &&
      clean(sel.historicity).map(_.toLowerCase).forall(_ == e.historicity) &&
      clean(sel.placeId).forall(p => e.placeId.contains(p))

    private def ordered(es: Vector[HeldEvent]): Vector[HeldEvent] =
      es.sortBy(e => (e.timeSpan.notBefore.orElse(e.timeSpan.beginBy).fold((1, 0L))(y => (0, y)), e.label))

    private def summaryOf(s: CatalogueState, e: HeldEvent): EventSummary = e.summary(attested(s, e.id))

    def list(selection: EventSelection, limit: com.histmersion.domain.catalogue.events.EventService.InMemory.ListLimit, offset: com.histmersion.domain.catalogue.events.EventService.InMemory.ListOffset): EventList =
      val s = state.get
      val all = ordered(s.events.filter(selects(s, selection)))
      val from = offset.max(0)
      EventList.of(all.size, all.slice(from, from + limit.max(1)).toList.map(summaryOf(s, _)), from)

    private def drawn(s: CatalogueState, e: HeldEvent): Option[EventGeo] =
      e.geometry.flatMap(HeldEvent.pointOf).map { (lat, lon) =>
        val sum = summaryOf(s, e)
        val vei = s.catalogueRecords.filter(_._1 == e.id).flatMap(_._2.vei).maxOption
        val magnitude = s.catalogueRecords.filter(_._1 == e.id).flatMap(_._2.magnitude).maxOption
        EventGeo(
          com.histmersion.api.catalogue.Event.Id(sum.id),
          sum.label,
          sum.kind,
          sum.scale,
          sum.historicity,
          sum.timeSpan,
          com.histmersion.api.catalogue.Latitude(lat),
          com.histmersion.api.catalogue.Longitude(lon),
          "event",
          (sum.placeId).map(com.histmersion.api.catalogue.Place.Id.apply),
          None,
          sum.parentId,
          None,
          sum.tier,
          EventLeads.size(vei, magnitude),
          sum.precision,
          sum.datingNote
        )
      }

    private def preciseEnough(e: HeldEvent): com.histmersion.domain.catalogue.events.EventService.InMemory.PreciseEnoughResult = e.uncertainty.exists(_ <= EventPrecision.ApproximateUpTo)

    def geo(selection: EventSelection, limit: com.histmersion.domain.catalogue.events.EventService.InMemory.GeoLimit, tolerance: com.histmersion.domain.catalogue.events.EventService.InMemory.GeoTolerance): EventGeo.List =
      val s = state.get
      val listed = ordered(s.events.filter(selects(s, selection)))
      val placed = listed.filter(_.geometry.isDefined)
      val items = placed.filter(preciseEnough).flatMap(drawn(s, _)).take(limit.max(1)).toList
      EventGeo.List(
        items,
        listed.count(e => e.geometry.isEmpty && preciseEnough(e)),
        placed.count(e => e.uncertainty.exists(_ > EventPrecision.ApproximateUpTo))
      )

    def geoOf(ids: List[com.histmersion.domain.catalogue.events.EventService.InMemory.GeoOfIds]): Map[com.histmersion.domain.catalogue.events.EventService.InMemory.GeoOfResultPart1, EventGeo] =
      val s = state.get
      s.events.filter(e => ids.contains(e.id) && preciseEnough(e)).flatMap(e => drawn(s, e).map(e.id -> _)).toMap

    def get(id: com.histmersion.domain.catalogue.events.EventService.InMemory.GetId): Option[EventEntry] =
      val s = state.get
      s.events.find(_.id == id).map { e =>
        val sum = summaryOf(s, e)
        EventEntry(
          sum,
          e.geometry,
          e.parentId.flatMap(p => s.events.find(_.id == p)).map(summaryOf(s, _)),
          ordered(s.events.filter(_.parentId.contains(id))).toList.map(summaryOf(s, _)),
          e.datings.sortBy(_.citekey.value.toString),
          s.events
            .flatMap(_.edges)
            .filter(g => g.srcId == id || g.dstId == id)
            .distinct
            .toList
            .sortBy(g => (g.kind, g.srcId, g.dstId)),
          e.participants.sortBy(p => (p.role, p.actor)),
          e.wikidata,
          corrections = s.corrections
            .filter(c => c.targetKind == Correction.Target.Event && c.targetId == id)
            .sortBy(_.field)
            .toList
        )
      }

    def tierAndNames(id: com.histmersion.domain.catalogue.events.EventService.InMemory.TierAndNamesId): Option[(com.histmersion.domain.catalogue.events.EventService.InMemory.TierAndNamesResultPart1, List[com.histmersion.domain.catalogue.events.EventService.InMemory.TierAndNamesResultPart2], Option[com.histmersion.domain.catalogue.events.EventService.InMemory.TierAndNamesResultPart3])] =
      val s = state.get
      s.events.find(_.id == id).map(e => (summaryOf(s, e).tier, e.names, e.catalogue))

    def catalogueRefs(id: com.histmersion.domain.catalogue.events.EventService.InMemory.CatalogueRefsId): List[EventCatalogueRef] =
      state.get.catalogueRecords
        .filter(_._1 == id)
        .map(_._2)
        .sortBy(r => (r.provider, r.providerId))
        .toList
        .map { r =>
          val i = CatalogProvider.info(r.provider)
          EventCatalogueRef(
            r.provider,
            r.providerId,
            r.url,
            i.fold(r.provider)(_.title),
            i.fold("")(_.licence),
            i.fold("")(_.citation),
            r.vei,
            r.magnitude,
            r.deaths,
            None
          )
        }

    def geometryError(geojson: com.histmersion.domain.catalogue.events.EventService.InMemory.GeometryErrorGeojson): Option[com.histmersion.domain.catalogue.events.EventService.InMemory.GeometryErrorResult] =
      io.circe.parser.parse(geojson) match
        case Left(e)  => Some(e.message)
        case Right(j) =>
          val c = j.hcursor
          c.get[String]("type").toOption match
            case None => Some("unknown GeoJSON type")
            case Some(t) if c.downField("coordinates").succeeded || t == "GeometryCollection" => None
            case Some(_) => Some("Unable to find 'coordinates' in GeoJSON string")

    /** The corrections a person's `fields` make on an event an import created, recorded as the database does. */
    private def withCorrections(s: CatalogueState, id: com.histmersion.domain.catalogue.events.EventService.InMemory.WithCorrectionsId, fields: Map[com.histmersion.domain.catalogue.events.EventService.InMemory.WithCorrectionsFieldsPart1, io.circe.Json]): CatalogueState =
      val upstream = for
        e <- s.events.find(_.id == id)
        cat <- e.catalogue
        recs = s.catalogueRecords.filter((ev, r) => ev == id && r.provider == cat).map(_._2)
        key <- com.histmersion.api.catalogue.CatalogRecord.owner(id, cat, recs.map(_.key).toList)
        rec <- recs.find(_.key == key)
      yield cat -> rec
      upstream.fold(s) { (provider, rec) =>
        val up = EventFields.upstream(rec)
        @tailrec def loop(rest: List[(String, io.circe.Json)], acc: CatalogueState): CatalogueState = rest match
          case (f, v) :: tail => loop(tail, up.get(f).fold(acc)(u => corrected(acc, provider, id, f, v, u)))
          case Nil            => acc
        loop(fields.toList, s)
      }

    /** One field's correction recorded: none when the person's value is the catalogue's. */
    private def corrected(
        s: CatalogueState,
        provider: com.histmersion.domain.catalogue.events.EventService.InMemory.CorrectedProvider,
        id: com.histmersion.domain.catalogue.events.EventService.InMemory.CorrectedId,
        field: com.histmersion.domain.catalogue.events.EventService.InMemory.CorrectedField,
        value: io.circe.Json,
        upstream: io.circe.Json
    ): CatalogueState =
      val others =
        s.corrections.filterNot(c => c.targetKind == Correction.Target.Event && c.targetId == id && c.field == field)
      if value == upstream then s.modify(_.corrections).setTo(others)
      else
        val n = s.lastCorrectionId + 1
        val c = com.histmersion.api.catalogue.Correction(
          n,
          Correction.Target.Event,
          id,
          field,
          value.noSpaces,
          upstream.noSpaces,
          com.histmersion.api.catalogue.Correction.Status.Active,
          Some(provider)
        )
        s.modify(_.corrections).setTo(others :+ c).modify(_.lastCorrectionId).setTo(n)

    def put(id: com.histmersion.domain.catalogue.events.EventService.InMemory.PutId, in: EventPut): Unit =
      val _ = state.updateAndGet { s =>
        val before = s.events.find(_.id == id)
        val sameSpan = before.exists(_.timeSpan == in.timeSpan)
        val e = HeldEvent(
          id,
          in.label,
          in.kind,
          in.timeSpan,
          in.historicity,
          in.geometry.map(_.trim).filter(_.nonEmpty),
          in.placeId,
          in.parentId,
          in.description,
          in.citekey,
          in.scale.flatMap(v => EventScale.parse(v).flatten),
          before.flatMap(_.wikidata),
          before.fold(Nil)(_.names),
          before.flatMap(_.catalogue),
          before.filter(_ => sameSpan).flatMap(_.central),
          before.filter(_ => sameSpan).flatMap(_.plusMinus),
          before.filter(_ => sameSpan).flatMap(_.datingMethod),
          in.datings.map(_.modify(_.eventId).setTo(id)).distinctBy(_.citekey),
          in.edges.distinctBy(g => (g.srcId, g.dstId, g.kind)),
          in.participants.map(_.modify(_.eventId).setTo(id)).distinctBy(p => (p.actor, p.role))
        )
        val cleared = s.events.map(o => o.modify(_.edges).using(_.filterNot(g => g.srcId == id || g.dstId == id)))
        val written = if before.isDefined then cleared.map(o => if o.id == id then e else o) else cleared :+ e
        withCorrections(s.modify(_.events).setTo(written), id, EventFields.ofPut(in))
      }

    def patch(id: com.histmersion.domain.catalogue.events.EventService.InMemory.PatchId, p: EventPatch): Either[Api.Error, Unit] =
      if !state.get.events.exists(_.id == id) then Left(missing(id))
      else
        val _ = state.updateAndGet { s =>
          def changed(e: HeldEvent): HeldEvent =
            val spanSet = p.timeSpan.isDefined
            e.modify(_.label)
              .using(p.label.getOrElse(_))
              .modify(_.kind)
              .using(p.kind.getOrElse(_))
              .modify(_.historicity)
              .using(p.historicity.getOrElse(_))
              .modify(_.placeId)
              .using(o => p.placeId.fold(o)(Some(_)))
              .modify(_.parentId)
              .using(o => p.parentId.fold(o)(Some(_)))
              .modify(_.description)
              .using(o => p.description.fold(o)(Some(_)))
              .modify(_.citekey)
              .using(o => p.citekey.fold(o)(Some(_)))
              .modify(_.scale)
              .using(o => p.scale.flatMap(EventScale.parse).fold(o)(identity))
              .modify(_.wikidata)
              .using(o => p.wikidata.map(_.trim).fold(o)(q => Option(q).filter(_.nonEmpty)))
              .modify(_.geometry)
              .using(o => p.geometry.map(_.trim).fold(o)(g => Option(g).filter(_.nonEmpty)))
              .modify(_.timeSpan)
              .using(p.timeSpan.getOrElse(_))
              .modify(_.datings)
              .using(o => p.datings.fold(o)(_.map(_.modify(_.eventId).setTo(id))))
              .modify(_.participants)
              .using(o => p.participants.fold(o)(_.map(_.modify(_.eventId).setTo(id))))
              .modify(_.central)
              .using(o => if spanSet then None else o)
              .modify(_.plusMinus)
              .using(o => if spanSet then None else o)
              .modify(_.datingMethod)
              .using(o => if spanSet then None else o)
          val edited = s.events.map { e =>
            val own = if e.id == id then changed(e) else e
            p.edges.fold(own) { es =>
              val kept = own.modify(_.edges).using(_.filterNot(g => g.srcId == id || g.dstId == id))
              if own.id == id then kept.modify(_.edges).using(_ ++ es) else kept
            }
          }
          withCorrections(s.modify(_.events).setTo(edited), id, EventFields.ofPatch(p))
        }
        Right(())

    def rename(from: com.histmersion.domain.catalogue.events.EventService.InMemory.RenameFrom, to: com.histmersion.domain.catalogue.events.EventService.InMemory.RenameTo): Either[Api.Error, Unit] =
      val (oldId, newId) = (from.trim, to.trim)
      val s = state.get
      def exists(i: String) = s.events.exists(_.id == i)
      val aliasOf = s.eventAliases.get(newId)
      if !exists(oldId) then Left(missing(oldId))
      else if exists(newId) then
        Left(Api.Error(s"'$newId' is already the id of another event", Some(Write.CodeInvalid)))
      else if aliasOf.exists(_ != oldId) then
        Left(
          Api.Error(
            s"'$newId' is an old id of '${aliasOf.get}' and still resolves to it — pick another",
            Some(Write.CodeInvalid)
          )
        )
      else
        val _ = state.updateAndGet { s =>
          def ren(i: String) = if i == oldId then newId else i
          s.modify(_.events)
            .using(_.map { e =>
              e.modify(_.id)
                .using(ren)
                .modify(_.parentId)
                .using(_.map(ren))
                .modify(_.edges)
                .using(_.map(g => g.modify(_.srcId).using(ren).modify(_.dstId).using(ren)))
                .modify(_.datings)
                .using(_.map(d => d.modify(_.eventId).using(id => com.histmersion.api.catalogue.Event.Id(ren(id.value)))))
                .modify(_.participants)
                .using(_.map(p => p.modify(_.eventId).using(id => com.histmersion.api.catalogue.Event.Id(ren(id.value)))))
            })
            .modify(_.attestations)
            .using(
              _.map(a =>
                if a.targetType == "event" && a.targetId == oldId then a.modify(_.targetId).setTo(newId) else a
              )
            )
            .modify(_.corrections)
            .using(_.map(c => if c.targetKind == Correction.Target.Event then c.modify(_.targetId).using(ren) else c))
            .modify(_.catalogueRecords)
            .using(_.map((e, r) => (ren(e), r)))
            .modify(_.eventAliases)
            .using(as => (as - newId).map((a, t) => a -> ren(t)) + (oldId -> newId))
        }
        Right(())

    def renamedTo(id: com.histmersion.domain.catalogue.events.EventService.InMemory.RenamedToId): Option[com.histmersion.domain.catalogue.events.EventService.InMemory.RenamedToResult] =
      val s = state.get
      if s.events.exists(_.id == id) then None else s.eventAliases.get(id)

    def delete(id: com.histmersion.domain.catalogue.events.EventService.InMemory.DeleteId): com.histmersion.domain.catalogue.events.EventService.InMemory.DeleteResult =
      val had = state.get.events.exists(_.id == id)
      val _ = state.updateAndGet(
        _.modify(_.events)
          .using(
            _.filterNot(_.id == id).map(e => e.modify(_.edges).using(_.filterNot(g => g.srcId == id || g.dstId == id)))
          )
          .modify(_.corrections)
          .using(_.filterNot(c => c.targetKind == Correction.Target.Event && c.targetId == id))
      )
      had

  object InMemory:
    type AttestedResult = AttestedResult.T
    object AttestedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type AttestedId = AttestedId.T
    object AttestedId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OverlapsResult = OverlapsResult.T
    object OverlapsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ContainsTextResult = ContainsTextResult.T
    object ContainsTextResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ContainsTextField = ContainsTextField.T
    object ContainsTextField extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ContainsTextT = ContainsTextT.T
    object ContainsTextT extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RelatedResult = RelatedResult.T
    object RelatedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SelectsResult = SelectsResult.T
    object SelectsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListLimit = ListLimit.T
    object ListLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ListOffset = ListOffset.T
    object ListOffset extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PreciseEnoughResult = PreciseEnoughResult.T
    object PreciseEnoughResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GeoLimit = GeoLimit.T
    object GeoLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GeoTolerance = GeoTolerance.T
    object GeoTolerance extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GeoOfResultPart1 = GeoOfResultPart1.T
    object GeoOfResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GeoOfIds = GeoOfIds.T
    object GeoOfIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GetId = GetId.T
    object GetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TierAndNamesResultPart1 = TierAndNamesResultPart1.T
    object TierAndNamesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TierAndNamesResultPart2 = TierAndNamesResultPart2.T
    object TierAndNamesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TierAndNamesResultPart3 = TierAndNamesResultPart3.T
    object TierAndNamesResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TierAndNamesId = TierAndNamesId.T
    object TierAndNamesId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CatalogueRefsId = CatalogueRefsId.T
    object CatalogueRefsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GeometryErrorResult = GeometryErrorResult.T
    object GeometryErrorResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type GeometryErrorGeojson = GeometryErrorGeojson.T
    object GeometryErrorGeojson extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WithCorrectionsId = WithCorrectionsId.T
    object WithCorrectionsId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type WithCorrectionsFieldsPart1 = WithCorrectionsFieldsPart1.T
    object WithCorrectionsFieldsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CorrectedProvider = CorrectedProvider.T
    object CorrectedProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CorrectedId = CorrectedId.T
    object CorrectedId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CorrectedField = CorrectedField.T
    object CorrectedField extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PutId = PutId.T
    object PutId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PatchId = PatchId.T
    object PatchId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenameFrom = RenameFrom.T
    object RenameFrom extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenameTo = RenameTo.T
    object RenameTo extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenamedToResult = RenamedToResult.T
    object RenamedToResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RenamedToId = RenamedToId.T
    object RenamedToId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteResult = DeleteResult.T
    object DeleteResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type DeleteId = DeleteId.T
    object DeleteId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
