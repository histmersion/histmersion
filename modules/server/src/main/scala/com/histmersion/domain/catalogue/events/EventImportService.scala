package com.histmersion
package domain.catalogue.events

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.{CatalogRecord, Corrections, EventFields, EventTaxonomy}
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import com.histmersion.api.catalogue.Correction
import com.histmersion.domain.catalogue.Event
import com.histmersion.api.catalogue.EventSummary
import com.histmersion.api.catalogue.EventCatalogueRef
import com.histmersion.api.catalogue.EventEntry
import com.histmersion.domain.catalogue.places.Wikidata

/** What an import of an event catalogue reads and writes: the stored events as its merge sees them, the placed records
  * written (events created and refreshed, corrections by hand applied), the records kept for a re-derivation, and the
  * parts of each catalogue read.
  */
trait EventImportService:
  /** Every stored event as the merge sees it, in id order. */
  def stored(): List[StoredEvent]

  /** Write placed records in one transaction. */
  def save(placed: List[(CatalogRecord, CatalogMerge.Placement)]): EventSaved

  /** The stored records of `provider` (every catalogue's when `None`) with the event each is of and its kind, in key
    * order; and how many no longer decode.
    */
  def records(provider: Option[CatalogRecord.Provider]): (List[(Event.Id, EventSummary.Kind, CatalogRecord)], RowCount)

  /** The kind each stored record of `provider` has, by key. */
  def storedKinds(provider: CatalogRecord.Provider): Map[(CatalogRecord.Provider, EventCatalogueRef.ProviderId), EventSummary.Kind]

  def partsDone(provider: CatalogRecord.Provider): Set[EventImportRepo.PartName]
  def markPart(provider: CatalogRecord.Provider, part: EventImportRepo.PartName, records: RowCount): Unit
  def clearParts(provider: CatalogRecord.Provider): Unit

object EventImportService:
  def apply(db: Transactor): EventImportService = Implementation(db)

  final private class Implementation(db: Transactor) extends EventImportService:
    def stored(): List[StoredEvent] = db.read(EventImportRepo.stored)
    def save(placed: List[(CatalogRecord, CatalogMerge.Placement)]): EventSaved =
      db.transact(EventImportRepo.save(placed))
    def records(provider: Option[CatalogRecord.Provider]): (List[(Event.Id, EventSummary.Kind, CatalogRecord)], RowCount) =
      db.read(EventImportRepo.records(provider))
    def storedKinds(provider: CatalogRecord.Provider): Map[(CatalogRecord.Provider, EventCatalogueRef.ProviderId), EventSummary.Kind] = db.read(EventImportRepo.storedKinds(provider))
    def partsDone(provider: CatalogRecord.Provider): Set[EventImportRepo.PartName] = db.read(EventImportRepo.partsDone(provider))
    def markPart(provider: CatalogRecord.Provider, part: EventImportRepo.PartName, records: RowCount): Unit =
      db.transact(EventImportRepo.markPart(provider, part, records))
    def clearParts(provider: CatalogRecord.Provider): Unit = db.transact(EventImportRepo.clearParts(provider))

  /** The same rules over the shared in-memory state (no place is stored: an event's anchor is its own point). */
  final class InMemory(state: AtomicReference[CatalogueState]) extends EventImportService:

    def stored(): List[StoredEvent] =
      val s = state.get
      s.events
        .sortBy(_.id.value)
        .toList
        .map { e =>
          val pt = e.geometry.flatMap(HeldEvent.pointOf)
          StoredEvent(
            e.id,
            StoredEvent.Kind(e.kind.value),
            e.timeSpan,
            pt.map(_._1),
            pt.map(_._2),
            e.wikidata.map(w => EventEntry.Wikidata(w.value)),
            s.catalogueRecords.filter(_._1.value == e.id.value).map(_._2.key).toSet,
            StoredEvent.Catalogue(e.catalogue.isDefined),
            StoredEvent.Label(e.label.value)
          )
        }

    private def merged(names: List[BEventRow.Names], more: List[BEventRow.Names]): List[BEventRow.Names] = (names ++ more).distinct.sorted

    private def keyOrder(r: CatalogRecord): (String, String) = (r.key._1.value, r.key._2.value)

    private def fill(s: CatalogueState, id: Event.Id, names: List[BEventRow.Names], wd: Option[Wikidata.Id]): CatalogueState =
      s.modify(_.events)
        .using(
          _.map(e =>
            if e.id != id then e
            else e.modify(_.wikidata).using(_.orElse(wd)).modify(_.names).using(merged(_, names))
          )
        )

    private def refresh(
        s: CatalogueState,
        id: Event.Id,
        r: CatalogRecord,
        names: List[BEventRow.Names],
        wd: Option[Wikidata.Id]
    ): (CatalogueState, Correction.Counts) =
      val now = EventFields.upstream(r)
      val applied = s.corrections.filter(c =>
        c.targetKind.value == Correction.Target.Event && c.targetId.value == id.value &&
          Correction.Status.applied(Correction.Status.Applied(c.status.value))
      )
      def json(t: String) = io.circe.parser.parse(t).getOrElse(io.circe.Json.Null)
      val outcomes = applied.flatMap(c =>
        now.get(c.field.value).map(up => (c, Corrections.reconcile(json(c.value.value), json(c.upstreamValue.value), up), up))
      )
      val keep = outcomes.collect {
        case (c, o, _) if o != Corrections.Outcome.Retire => (c.field.value: String) -> json(c.value.value)
      }.toMap
      def text(f: String): Option[Option[String]] = keep.get(f).map(_.asString)
      val spanKept = keep.contains(EventFields.Span)
      val geom: Option[HeldEvent.Geometry] = keep.get(EventFields.Geometry) match
        case Some(g) => Option.when(!g.isNull)(HeldEvent.Geometry(g.noSpaces))
        case None    => for la <- r.lat; lo <- r.lon yield HeldEvent.Geometry(EventFields.point(la, lo).noSpaces)
      val refreshed = s
        .modify(_.corrections)
        .using(_.map { c =>
          outcomes
            .find(_._1.id == c.id)
            .fold(c) { case (_, o, up) =>
              c.modify(_.status)
                .setTo(Correction.StatusValue(Corrections.status(o).value))
                .modify(_.upstreamNow)
                .setTo(Some(Correction.UpstreamNow(up.noSpaces)))
            }
        })
        .modify(_.events)
        .using(_.map { e =>
          if e.id != id then e
          else
            e.modify(_.label)
              .setTo(HeldEvent.Label(text(EventFields.Label).flatten.getOrElse(r.label.value)))
              .modify(_.kind)
              .setTo(
                HeldEvent.Kind(
                  text(EventFields.Kind).flatten
                    .getOrElse(EventTaxonomy.canonical(EventSummary.Kind(r.kind.value)).value)
                )
              )
              .modify(_.historicity)
              .setTo(HeldEvent.Historicity(text(EventFields.Historicity).flatten.getOrElse(r.historicity.value)))
              .modify(_.description)
              .setTo(
                text(EventFields.Description)
                  .map(_.map(HeldEvent.Description(_)))
                  .getOrElse(r.description.map(d => HeldEvent.Description(d.value)))
              )
              .modify(_.timeSpan)
              .setTo(keep.get(EventFields.Span).flatMap(_.as[TimeSpan].toOption).getOrElse(r.timeSpan))
              .modify(_.central)
              .setTo(if spanKept then None else r.central.map(c => HeldEvent.Central(c.value)))
              .modify(_.plusMinus)
              .setTo(if spanKept then None else r.plusMinus.map(p => HeldEvent.PlusMinus(p.value)))
              .modify(_.datingMethod)
              .setTo(if spanKept then None else r.datingMethod.map(d => HeldEvent.DatingMethod(d.value)))
              .modify(_.geometry)
              .setTo(geom)
              .modify(_.wikidata)
              .using(_.orElse(wd))
              .modify(_.names)
              .using(merged(_, names))
        })
      (refreshed, Corrections.counts(outcomes.map(_._2)))

    private def inserted(s: CatalogueState, id: Event.Id, r: CatalogRecord, names: List[BEventRow.Names], wd: Option[Wikidata.Id]) =
      val e = HeldEvent(
        id,
        HeldEvent.Label(r.label.value),
        HeldEvent.Kind(EventTaxonomy.canonical(EventSummary.Kind(r.kind.value)).value),
        r.timeSpan,
        HeldEvent.Historicity(r.historicity.value),
        for la <- r.lat; lo <- r.lon yield HeldEvent.Geometry(EventFields.point(la, lo).noSpaces),
        None,
        None,
        r.description.map(d => HeldEvent.Description(d.value)),
        None,
        None,
        wd,
        names,
        Some(HeldEvent.Catalogue(r.provider.value)),
        r.central.map(c => HeldEvent.Central(c.value)),
        r.plusMinus.map(p => HeldEvent.PlusMinus(p.value)),
        r.datingMethod.map(d => HeldEvent.DatingMethod(d.value))
      )
      s.modify(_.events).using(_ :+ e)

    private def upsertRecord(s: CatalogueState, eventId: Event.Id, r: CatalogRecord): CatalogueState =
      s.modify(_.catalogueRecords).using(rs => rs.filterNot(_._2.key == r.key) :+ (CatalogueState.CatalogueRecordsPart1(eventId.value) -> r))

    /** One event's placed records written: the state after, whether the event was created, and what became of its
      * corrections when it was refreshed.
      */
    private def saveOne(
        s: CatalogueState,
        eventId: Event.Id,
        group: List[(CatalogRecord, CatalogMerge.Placement)]
    ): (CatalogueState, Boolean, Option[Correction.Counts]) =
      val recs = group.map(_._1).sortBy(keyOrder)
      val names = recs
        .flatMap(r => r.label.value :: r.names.map(_.value))
        .map(_.trim)
        .filter(_.nonEmpty)
        .distinct
        .map(BEventRow.Names(_))
      val wd = recs.flatMap(_.wikidata).headOption.flatMap(w => HeldEvent.Wikidata.option(w.value))
      val catalogue = s.events.find(_.id == eventId).map(_.catalogue)
      def ownerIn(p: HeldEvent.Catalogue): Option[CatalogRecord] =
        val storedKeys = s.catalogueRecords.filter(_._1.value == eventId.value).map(_._2.key).toList
        CatalogRecord
          .owner(CatalogRecord.EventId(eventId.value), CatalogRecord.Provider(p.value), storedKeys ++ recs.map(_.key))
          .flatMap(k => recs.find(_.key == k))
      def refreshOrFill(p: HeldEvent.Catalogue): (CatalogueState, Boolean, Option[Correction.Counts]) =
        ownerIn(p).fold((fill(s, eventId, names, wd), false, None)) { r =>
          val (after, c) = refresh(s, eventId, r, names, wd)
          (after, false, Some(c))
        }
      val (written, created, refreshed) = group.head._2 match
        case CatalogMerge.Placement.New(_, _) =>
          val creator = recs.find(r => CatalogRecord.eventId(r).value == eventId.value).getOrElse(recs.head)
          catalogue match
            case None          => (inserted(s, eventId, creator, names, wd), true, None)
            case Some(Some(p)) => refreshOrFill(p)
            case Some(None)    => (fill(s, eventId, names, wd), false, None)
        case CatalogMerge.Placement.Existing(_, _) =>
          catalogue.flatten.fold((fill(s, eventId, names, wd), false, None))(refreshOrFill)
      @tailrec def kept(rest: List[CatalogRecord], acc: CatalogueState): CatalogueState = rest match
        case r :: tail => kept(tail, upsertRecord(acc, eventId, r))
        case Nil       => acc
      (kept(recs, written), created, refreshed)

    def save(placed: List[(CatalogRecord, CatalogMerge.Placement)]): EventSaved =
      val groups = placed.groupBy(_._2.eventId).toList.sortBy(_._1.value)
      @tailrec def loop(
          rest: List[(Event.Id, List[(CatalogRecord, CatalogMerge.Placement)])],
          s: CatalogueState,
          created: Int,
          updated: Int,
          counts: Correction.Counts
      ): (CatalogueState, EventSaved) = rest match
        case (eventId, group) :: tail =>
          val (after, made, refreshed) = saveOne(s, eventId, group)
          loop(
            tail,
            after,
            created + (if made then 1 else 0),
            updated + refreshed.size,
            refreshed.fold(counts)(counts + _)
          )
        case Nil => (s, EventSaved(EventSaved.Created(created), EventSaved.Updated(updated), EventSaved.Rows(placed.size), counts))
      // the answer of the attempt that took: a contended update runs the loop again on the newer state
      val answer = AtomicReference[EventSaved]()
      val _ = state.updateAndGet { s0 =>
        val (after, saved) = loop(groups, s0, 0, 0, Correction.Counts())
        answer.set(saved)
        after
      }
      answer.get

    def records(provider: Option[CatalogRecord.Provider]): (List[(Event.Id, EventSummary.Kind, CatalogRecord)], RowCount) =
      val s = state.get
      val rows = s.catalogueRecords
        .filter((_, r) => provider.forall(_ == r.provider))
        .sortBy(t => keyOrder(t._2))
        .toList
        .flatMap((id, r) =>
          s.events
            .find(_.id.value == id.value)
            .map(e => (e.id, EventSummary.Kind(e.kind.value), r))
        )
      (rows, RowCount(0))

    def storedKinds(provider: CatalogRecord.Provider): Map[(CatalogRecord.Provider, EventCatalogueRef.ProviderId), EventSummary.Kind] =
      state.get.catalogueRecords.collect {
        case (_, r) if r.provider == provider =>
          r.key -> EventTaxonomy.canonical(EventSummary.Kind(r.kind.value))
      }.toMap

    def partsDone(provider: CatalogRecord.Provider): Set[EventImportRepo.PartName] =
      state.get.importParts
        .getOrElse(CatalogueState.ImportPartsKey(provider.value), Map.empty)
        .keySet
        .map(k => EventImportRepo.PartName(k.value))

    def markPart(provider: CatalogRecord.Provider, part: EventImportRepo.PartName, records: RowCount): Unit =
      val key = CatalogueState.ImportPartsKey(provider.value)
      val _ = state.updateAndGet(
        _.modify(_.importParts).using(ps =>
          ps + (key -> (ps.getOrElse(key, Map.empty) + (CatalogueState.ImportPartsValueKey(part.value) -> CatalogueState.ImportPartsValueValue(records.value))))
        )
      )

    def clearParts(provider: CatalogRecord.Provider): Unit =
      val _ = state.updateAndGet(_.modify(_.importParts).using(_ - CatalogueState.ImportPartsKey(provider.value)))
