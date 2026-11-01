package com.histmersion.domain.catalogue.events

import com.histmersion.api.{
  CatalogRecord,
  CorrectionCounts,
  Corrections,
  CorrectionStatus,
  CorrectionTarget,
  EventFields,
  EventTaxonomy,
  TimeSpan
}
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.AtomicReference

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
  def records(provider: Option[String]): (List[(String, String, CatalogRecord)], Int)

  /** The kind each stored record of `provider` has, by key. */
  def storedKinds(provider: String): Map[(String, String), String]

  def partsDone(provider: String): Set[String]
  def markPart(provider: String, part: String, records: Int): Unit
  def clearParts(provider: String): Unit

object EventImportService:
  def apply(db: Transactor): EventImportService = Implementation(db)

  final private class Implementation(db: Transactor) extends EventImportService:
    def stored(): List[StoredEvent] = db.read(EventImportRepo.stored)
    def save(placed: List[(CatalogRecord, CatalogMerge.Placement)]): EventSaved =
      db.transact(EventImportRepo.save(placed))
    def records(provider: Option[String]): (List[(String, String, CatalogRecord)], Int) =
      db.read(EventImportRepo.records(provider))
    def storedKinds(provider: String): Map[(String, String), String] = db.read(EventImportRepo.storedKinds(provider))
    def partsDone(provider: String): Set[String] = db.read(EventImportRepo.partsDone(provider))
    def markPart(provider: String, part: String, records: Int): Unit =
      db.transact(EventImportRepo.markPart(provider, part, records))
    def clearParts(provider: String): Unit = db.transact(EventImportRepo.clearParts(provider))

  /** The same rules over the shared in-memory state (no place is stored: an event's anchor is its own point). */
  final class InMemory(state: AtomicReference[CatalogueState]) extends EventImportService:

    def stored(): List[StoredEvent] =
      val s = state.get
      s.events
        .sortBy(_.id)
        .toList
        .map { e =>
          val pt = e.geometry.flatMap(HeldEvent.pointOf)
          StoredEvent(
            e.id,
            e.kind,
            e.timeSpan,
            pt.map(_._1),
            pt.map(_._2),
            e.wikidata,
            s.catalogueRecords.filter(_._1 == e.id).map(_._2.key).toSet,
            e.catalogue.isDefined,
            e.label
          )
        }

    private def merged(names: List[String], more: List[String]): List[String] = (names ++ more).distinct.sorted

    private def fill(s: CatalogueState, id: String, names: List[String], wd: Option[String]): CatalogueState =
      s.modify(_.events)
        .using(
          _.map(e =>
            if e.id != id then e
            else e.modify(_.wikidata).using(_.orElse(wd)).modify(_.names).using(merged(_, names))
          )
        )

    private def refresh(
        s: CatalogueState,
        id: String,
        r: CatalogRecord,
        names: List[String],
        wd: Option[String]
    ): (CatalogueState, CorrectionCounts) =
      val now = EventFields.upstream(r)
      val applied = s.corrections.filter(c =>
        c.targetKind == CorrectionTarget.Event && c.targetId == id && CorrectionStatus.applied(c.status)
      )
      def json(t: String) = io.circe.parser.parse(t).getOrElse(io.circe.Json.Null)
      val outcomes = applied.flatMap(c =>
        now.get(c.field).map(up => (c, Corrections.reconcile(json(c.value), json(c.upstreamValue), up), up))
      )
      val keep = outcomes.collect {
        case (c, o, _) if o != Corrections.Outcome.Retire => c.field -> json(c.value)
      }.toMap
      def text(f: String): Option[Option[String]] = keep.get(f).map(_.asString)
      val spanKept = keep.contains(EventFields.Span)
      val geom: Option[String] = keep.get(EventFields.Geometry) match
        case Some(g) => Option.when(!g.isNull)(g.noSpaces)
        case None    => for la <- r.lat; lo <- r.lon yield EventFields.point(la, lo).noSpaces
      val refreshed = s
        .modify(_.corrections)
        .using(_.map { c =>
          outcomes
            .find(_._1.id == c.id)
            .fold(c)((_, o, up) =>
              c.modify(_.status).setTo(Corrections.status(o)).modify(_.upstreamNow).setTo(Some(up.noSpaces))
            )
        })
        .modify(_.events)
        .using(_.map { e =>
          if e.id != id then e
          else
            e.modify(_.label)
              .setTo(text(EventFields.Label).flatten.getOrElse(r.label))
              .modify(_.kind)
              .setTo(text(EventFields.Kind).flatten.getOrElse(EventTaxonomy.canonical(r.kind)))
              .modify(_.historicity)
              .setTo(text(EventFields.Historicity).flatten.getOrElse(r.historicity))
              .modify(_.description)
              .setTo(text(EventFields.Description).getOrElse(r.description))
              .modify(_.timeSpan)
              .setTo(keep.get(EventFields.Span).flatMap(_.as[TimeSpan].toOption).getOrElse(r.timeSpan))
              .modify(_.central)
              .setTo(if spanKept then None else r.central)
              .modify(_.plusMinus)
              .setTo(if spanKept then None else r.plusMinus)
              .modify(_.datingMethod)
              .setTo(if spanKept then None else r.datingMethod)
              .modify(_.geometry)
              .setTo(geom)
              .modify(_.wikidata)
              .using(_.orElse(wd))
              .modify(_.names)
              .using(merged(_, names))
        })
      (refreshed, Corrections.counts(outcomes.map(_._2)))

    private def inserted(s: CatalogueState, id: String, r: CatalogRecord, names: List[String], wd: Option[String]) =
      val e = HeldEvent(
        id,
        r.label,
        EventTaxonomy.canonical(r.kind),
        r.timeSpan,
        r.historicity,
        for la <- r.lat; lo <- r.lon yield EventFields.point(la, lo).noSpaces,
        None,
        None,
        r.description,
        None,
        None,
        wd,
        names,
        Some(r.provider),
        r.central,
        r.plusMinus,
        r.datingMethod
      )
      s.modify(_.events).using(_ :+ e)

    private def upsertRecord(s: CatalogueState, eventId: String, r: CatalogRecord): CatalogueState =
      s.modify(_.catalogueRecords).using(rs => rs.filterNot(_._2.key == r.key) :+ (eventId -> r))

    def save(placed: List[(CatalogRecord, CatalogMerge.Placement)]): EventSaved =
      // `var`s: the counts of this write
      var created, updated = 0
      var counts = CorrectionCounts()
      val _ = state.updateAndGet { s0 =>
        created = 0; updated = 0; counts = CorrectionCounts()
        placed.groupBy(_._2.eventId).toList.sortBy(_._1).foldLeft(s0) { case (s, (eventId, group)) =>
          val recs = group.map(_._1).sortBy(_.key)
          val names = recs.flatMap(r => r.label :: r.names).map(_.trim).filter(_.nonEmpty).distinct
          val wd = recs.flatMap(_.wikidata).headOption
          val catalogue = s.events.find(_.id == eventId).map(_.catalogue)
          def ownerIn(p: String): Option[CatalogRecord] =
            val storedKeys = s.catalogueRecords.filter(_._1 == eventId).map(_._2.key).toList
            CatalogRecord.owner(eventId, p, storedKeys ++ recs.map(_.key)).flatMap(k => recs.find(_.key == k))
          def refreshOrFill(p: String): CatalogueState =
            ownerIn(p).fold(fill(s, eventId, names, wd)) { r =>
              val (after, c) = refresh(s, eventId, r, names, wd)
              counts = counts + c
              updated += 1
              after
            }
          val written = group.head._2 match
            case CatalogMerge.Placement.New(_, _) =>
              val creator = recs.find(r => CatalogRecord.eventId(r) == eventId).getOrElse(recs.head)
              catalogue match
                case None =>
                  created += 1
                  inserted(s, eventId, creator, names, wd)
                case Some(Some(p)) => refreshOrFill(p)
                case Some(None)    => fill(s, eventId, names, wd)
            case CatalogMerge.Placement.Existing(_, _) =>
              catalogue.flatten.fold(fill(s, eventId, names, wd))(refreshOrFill)
          recs.foldLeft(written)(upsertRecord(_, eventId, _))
        }
      }
      EventSaved(created, updated, placed.size, counts)

    def records(provider: Option[String]): (List[(String, String, CatalogRecord)], Int) =
      val s = state.get
      val rows = s.catalogueRecords
        .filter((_, r) => provider.forall(_ == r.provider))
        .sortBy(_._2.key)
        .toList
        .flatMap((id, r) => s.events.find(_.id == id).map(e => (id, e.kind, r)))
      (rows, 0)

    def storedKinds(provider: String): Map[(String, String), String] =
      state.get.catalogueRecords.collect {
        case (_, r) if r.provider == provider => r.key -> EventTaxonomy.canonical(r.kind)
      }.toMap

    def partsDone(provider: String): Set[String] = state.get.importParts.getOrElse(provider, Map.empty).keySet

    def markPart(provider: String, part: String, records: Int): Unit =
      val _ = state.updateAndGet(
        _.modify(_.importParts).using(ps => ps + (provider -> (ps.getOrElse(provider, Map.empty) + (part -> records))))
      )

    def clearParts(provider: String): Unit =
      val _ = state.updateAndGet(_.modify(_.importParts).using(_ - provider))
