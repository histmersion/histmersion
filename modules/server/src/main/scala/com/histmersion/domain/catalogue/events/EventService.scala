package com.histmersion.domain.catalogue.events

import com.histmersion.api.catalogue.events.EventEntry

import com.histmersion.api.{
  ApiError,
  CorrectionTarget,
  EventCatalogueRef,
  EventFields,
  EventGeo,
  EventGeoList,
  EventLeads,
  EventList,
  EventPatch,
  EventPrecision,
  EventPut,
  EventScale,
  EventSummary,
  EventTaxonomy,
  EventTier,
  Write
}
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

/** The events: dated happenings with their datings by source, their relations to each other and their participants,
  * listed by a selection, drawn on the map, written by hand, renamed and deleted.
  */
trait EventService:
  /** The events `selection` selects, by their earliest year then label, one page of them. */
  def list(selection: EventSelection, limit: Int, offset: Int): EventList

  /** The events `selection` selects that the map draws (placed, dated well enough), at most `limit`, with the regional
    * ones' outlines simplified to `tolerance` degrees; the unplaced and the imprecisely dated counted.
    */
  def geo(selection: EventSelection, limit: Int, tolerance: Double): EventGeoList

  /** The events of `ids` the map draws, by id. */
  def geoOf(ids: List[String]): Map[String, EventGeo]

  /** One event with its parent, parts, datings, edges, participants, Wikidata item and corrections by hand. */
  def get(id: String): Option[EventEntry]

  /** Its tier, its other names and the catalogue it was imported from. */
  def tierAndNames(id: String): Option[(String, List[String], Option[String])]

  /** The catalogues' rows of one event, with each built-in catalogue's licence and citation. */
  def catalogueRefs(id: String): List[EventCatalogueRef]

  /** Why `geojson` is no geometry the store reads; `None` when it is one. */
  def geometryError(geojson: String): Option[String]

  /** Create or replace one event; on one an import created, the fields that differ from its catalogue's are recorded as
    * corrections.
    */
  def put(id: String, in: EventPut): Unit

  /** Change the fields `p` sets; on one an import created, each changed field is recorded as a correction. */
  def patch(id: String, p: EventPatch): Either[ApiError, Unit]

  /** A new id; the old one stays an alias, what names the event follows. */
  def rename(from: String, to: String): Either[ApiError, Unit]

  /** The id an old id was renamed to; `None` for a live id, or nothing. */
  def renamedTo(id: String): Option[String]

  def delete(id: String): Boolean

object EventService:
  def apply(db: Transactor, related: EventSourceFilter): EventService = Implementation(db, related)

  private def missing(id: String) = ApiError(s"No event '$id'", Some(Write.CodeNotFound))

  final private class Implementation(db: Transactor, related: EventSourceFilter) extends EventService:
    def list(selection: EventSelection, limit: Int, offset: Int): EventList =
      db.read(EventRepo.list(selection.sql(related), limit, offset))
    def geo(selection: EventSelection, limit: Int, tolerance: Double): EventGeoList =
      db.read(EventRepo.geo(selection.sql(related), limit, tolerance))
    def geoOf(ids: List[String]): Map[String, EventGeo] = db.read(EventRepo.geoOf(ids))
    def get(id: String): Option[EventEntry] = db.read(EventRepo.get(id))
    def tierAndNames(id: String): Option[(String, List[String], Option[String])] = db.read(EventRepo.tierAndNames(id))
    def catalogueRefs(id: String): List[EventCatalogueRef] = db.read(EventImportRepo.catalogueRefs(id))
    def geometryError(geojson: String): Option[String] = db.read(EventRepo.geometryError(geojson))
    def put(id: String, in: EventPut): Unit = db.transact(EventRepo.put(id, in))
    def patch(id: String, p: EventPatch): Either[ApiError, Unit] = db.transact(EventRepo.patch(id, p))
    def rename(from: String, to: String): Either[ApiError, Unit] = db.transact(EventRepo.rename(from.trim, to.trim))
    def renamedTo(id: String): Option[String] = db.read(EventRepo.renamedTo(id))
    def delete(id: String): Boolean = db.transact(EventRepo.delete(id))

  /** The same rules over the shared in-memory state, which holds the catalogue's rows alone: no place is stored, so an
    * event is anchored by its own geometry (a point; an area has no outline) and has no place's kind or label;
    * `selectedSources` stands for the library's reading of a filter's sources (`None`: the filter sets none).
    */
  final class InMemory(
      state: AtomicReference[CatalogueState],
      selectedSources: com.histmersion.api.Filter => Option[Set[String]] = _ => None
  ) extends EventService:

    private def attested(s: CatalogueState, id: String): Boolean =
      s.attestations.exists(a => a.targetType == "event" && a.targetId == id)

    private def overlaps(e: HeldEvent, period: Option[com.histmersion.api.Period]): Boolean =
      period.forall(p => e.outer.forall((a, b) => a <= p.end && b >= p.start))

    private def containsText(field: Option[String], t: String): Boolean =
      field.exists(_.toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT)))

    /** The part of the selection the library answers in the database: its sources, its period, its title. */
    private def related(s: CatalogueState, e: HeldEvent, f: com.histmersion.api.Filter): Option[Boolean] =
      val sources = selectedSources(f.sourceSet.modify(_.period).setTo(None))
      val title = f.title.map(_.trim).filter(_.nonEmpty)
      if sources.isEmpty && f.period.isEmpty && title.isEmpty then None
      else
        val bySource = sources.forall(keys =>
          e.citekey.exists(keys) ||
            s.attestations.exists(a => a.targetType == "event" && a.targetId == e.id && keys(a.citekey))
        )
        Some(bySource && overlaps(e, f.period) && title.forall(t => containsText(Some(e.label), t)))

    private def selects(s: CatalogueState, sel: EventSelection)(e: HeldEvent): Boolean =
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

    def list(selection: EventSelection, limit: Int, offset: Int): EventList =
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
          sum.id,
          sum.label,
          sum.kind,
          sum.scale,
          sum.historicity,
          sum.timeSpan,
          lat,
          lon,
          "event",
          sum.placeId,
          None,
          sum.parentId,
          None,
          sum.tier,
          EventLeads.size(vei, magnitude),
          sum.precision,
          sum.datingNote
        )
      }

    private def preciseEnough(e: HeldEvent): Boolean = e.uncertainty.exists(_ <= EventPrecision.ApproximateUpTo)

    def geo(selection: EventSelection, limit: Int, tolerance: Double): EventGeoList =
      val s = state.get
      val listed = ordered(s.events.filter(selects(s, selection)))
      val placed = listed.filter(_.geometry.isDefined)
      val items = placed.filter(preciseEnough).flatMap(drawn(s, _)).take(limit.max(1)).toList
      EventGeoList(
        items,
        listed.count(e => e.geometry.isEmpty && preciseEnough(e)),
        placed.count(e => e.uncertainty.exists(_ > EventPrecision.ApproximateUpTo))
      )

    def geoOf(ids: List[String]): Map[String, EventGeo] =
      val s = state.get
      s.events.filter(e => ids.contains(e.id) && preciseEnough(e)).flatMap(e => drawn(s, e).map(e.id -> _)).toMap

    def get(id: String): Option[EventEntry] =
      val s = state.get
      s.events.find(_.id == id).map { e =>
        val sum = summaryOf(s, e)
        EventEntry(
          sum,
          e.geometry,
          e.parentId.flatMap(p => s.events.find(_.id == p)).map(summaryOf(s, _)),
          ordered(s.events.filter(_.parentId.contains(id))).toList.map(summaryOf(s, _)),
          e.datings.sortBy(_.citekey),
          s.events
            .flatMap(_.edges)
            .filter(g => g.srcId == id || g.dstId == id)
            .distinct
            .toList
            .sortBy(g => (g.kind, g.srcId, g.dstId)),
          e.participants.sortBy(p => (p.role, p.actor)),
          e.wikidata,
          corrections = s.corrections
            .filter(c => c.targetKind == CorrectionTarget.Event && c.targetId == id)
            .sortBy(_.field)
            .toList
        )
      }

    def tierAndNames(id: String): Option[(String, List[String], Option[String])] =
      val s = state.get
      s.events.find(_.id == id).map(e => (summaryOf(s, e).tier, e.names, e.catalogue))

    def catalogueRefs(id: String): List[EventCatalogueRef] =
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

    def geometryError(geojson: String): Option[String] =
      io.circe.parser.parse(geojson) match
        case Left(e)  => Some(e.message)
        case Right(j) =>
          val c = j.hcursor
          c.get[String]("type").toOption match
            case None => Some("unknown GeoJSON type")
            case Some(t) if c.downField("coordinates").succeeded || t == "GeometryCollection" => None
            case Some(_) => Some("Unable to find 'coordinates' in GeoJSON string")

    /** The corrections a person's `fields` make on an event an import created, recorded as the database does. */
    private def withCorrections(s: CatalogueState, id: String, fields: Map[String, io.circe.Json]): CatalogueState =
      val upstream = for
        e <- s.events.find(_.id == id)
        cat <- e.catalogue
        recs = s.catalogueRecords.filter((ev, r) => ev == id && r.provider == cat).map(_._2)
        key <- com.histmersion.api.CatalogRecord.owner(id, cat, recs.map(_.key).toList)
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
        provider: String,
        id: String,
        field: String,
        value: io.circe.Json,
        upstream: io.circe.Json
    ): CatalogueState =
      val others =
        s.corrections.filterNot(c => c.targetKind == CorrectionTarget.Event && c.targetId == id && c.field == field)
      if value == upstream then s.modify(_.corrections).setTo(others)
      else
        val n = s.lastCorrectionId + 1
        val c = com.histmersion.api.Correction(
          n,
          CorrectionTarget.Event,
          id,
          field,
          value.noSpaces,
          upstream.noSpaces,
          com.histmersion.api.CorrectionStatus.Active,
          Some(provider)
        )
        s.modify(_.corrections).setTo(others :+ c).modify(_.lastCorrectionId).setTo(n)

    def put(id: String, in: EventPut): Unit =
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

    def patch(id: String, p: EventPatch): Either[ApiError, Unit] =
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

    def rename(from: String, to: String): Either[ApiError, Unit] =
      val (oldId, newId) = (from.trim, to.trim)
      val s = state.get
      def exists(i: String) = s.events.exists(_.id == i)
      val aliasOf = s.eventAliases.get(newId)
      if !exists(oldId) then Left(missing(oldId))
      else if exists(newId) then Left(ApiError(s"'$newId' is already the id of another event", Some(Write.CodeInvalid)))
      else if aliasOf.exists(_ != oldId) then
        Left(
          ApiError(
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
                .using(_.map(d => d.modify(_.eventId).using(ren)))
                .modify(_.participants)
                .using(_.map(p => p.modify(_.eventId).using(ren)))
            })
            .modify(_.attestations)
            .using(
              _.map(a =>
                if a.targetType == "event" && a.targetId == oldId then a.modify(_.targetId).setTo(newId) else a
              )
            )
            .modify(_.corrections)
            .using(_.map(c => if c.targetKind == CorrectionTarget.Event then c.modify(_.targetId).using(ren) else c))
            .modify(_.catalogueRecords)
            .using(_.map((e, r) => (ren(e), r)))
            .modify(_.eventAliases)
            .using(as => (as - newId).map((a, t) => a -> ren(t)) + (oldId -> newId))
        }
        Right(())

    def renamedTo(id: String): Option[String] =
      val s = state.get
      if s.events.exists(_.id == id) then None else s.eventAliases.get(id)

    def delete(id: String): Boolean =
      val had = state.get.events.exists(_.id == id)
      val _ = state.updateAndGet(
        _.modify(_.events)
          .using(
            _.filterNot(_.id == id).map(e => e.modify(_.edges).using(_.filterNot(g => g.srcId == id || g.dstId == id)))
          )
          .modify(_.corrections)
          .using(_.filterNot(c => c.targetKind == CorrectionTarget.Event && c.targetId == id))
      )
      had
