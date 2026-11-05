package com.histmersion.domain.catalogue.events

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.{CatalogRecord, Corrections, EventFields, EventTaxonomy}
import com.histmersion.domain.catalogue.CatalogueState
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import com.histmersion.api.catalogue.Correction

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
  def records(provider: Option[com.histmersion.domain.catalogue.events.EventImportService.RecordsProvider]): (List[(com.histmersion.domain.catalogue.events.EventImportService.RecordsResultPart1Part1, com.histmersion.domain.catalogue.events.EventImportService.RecordsResultPart1Part2, CatalogRecord)], com.histmersion.domain.catalogue.events.EventImportService.RecordsResultPart2)

  /** The kind each stored record of `provider` has, by key. */
  def storedKinds(provider: com.histmersion.domain.catalogue.events.EventImportService.StoredKindsProvider): Map[(com.histmersion.domain.catalogue.events.EventImportService.StoredKindsResultPart1Part1, com.histmersion.domain.catalogue.events.EventImportService.StoredKindsResultPart1Part2), com.histmersion.domain.catalogue.events.EventImportService.StoredKindsResultPart2]

  def partsDone(provider: com.histmersion.domain.catalogue.events.EventImportService.PartsDoneProvider): Set[com.histmersion.domain.catalogue.events.EventImportService.PartsDoneResult]
  def markPart(provider: com.histmersion.domain.catalogue.events.EventImportService.MarkPartProvider, part: com.histmersion.domain.catalogue.events.EventImportService.MarkPartPart, records: com.histmersion.domain.catalogue.events.EventImportService.MarkPartRecords): Unit
  def clearParts(provider: com.histmersion.domain.catalogue.events.EventImportService.ClearPartsProvider): Unit

object EventImportService:
  type RecordsResultPart1Part1 = RecordsResultPart1Part1.T
  object RecordsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordsResultPart1Part2 = RecordsResultPart1Part2.T
  object RecordsResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordsResultPart2 = RecordsResultPart2.T
  object RecordsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordsProvider = RecordsProvider.T
  object RecordsProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StoredKindsResultPart1Part1 = StoredKindsResultPart1Part1.T
  object StoredKindsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StoredKindsResultPart1Part2 = StoredKindsResultPart1Part2.T
  object StoredKindsResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StoredKindsResultPart2 = StoredKindsResultPart2.T
  object StoredKindsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StoredKindsProvider = StoredKindsProvider.T
  object StoredKindsProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PartsDoneResult = PartsDoneResult.T
  object PartsDoneResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PartsDoneProvider = PartsDoneProvider.T
  object PartsDoneProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkPartProvider = MarkPartProvider.T
  object MarkPartProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkPartPart = MarkPartPart.T
  object MarkPartPart extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MarkPartRecords = MarkPartRecords.T
  object MarkPartRecords extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ClearPartsProvider = ClearPartsProvider.T
  object ClearPartsProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): EventImportService = Implementation(db)

  final private class Implementation(db: Transactor) extends EventImportService:
    def stored(): List[StoredEvent] = db.read(EventImportRepo.stored)
    def save(placed: List[(CatalogRecord, CatalogMerge.Placement)]): EventSaved =
      db.transact(EventImportRepo.save(placed))
    def records(provider: Option[com.histmersion.domain.catalogue.events.EventImportService.Implementation.RecordsProvider]): (List[(com.histmersion.domain.catalogue.events.EventImportService.Implementation.RecordsResultPart1Part1, com.histmersion.domain.catalogue.events.EventImportService.Implementation.RecordsResultPart1Part2, CatalogRecord)], com.histmersion.domain.catalogue.events.EventImportService.Implementation.RecordsResultPart2) =
      db.read(EventImportRepo.records(provider))
    def storedKinds(provider: com.histmersion.domain.catalogue.events.EventImportService.Implementation.StoredKindsProvider): Map[(com.histmersion.domain.catalogue.events.EventImportService.Implementation.StoredKindsResultPart1Part1, com.histmersion.domain.catalogue.events.EventImportService.Implementation.StoredKindsResultPart1Part2), com.histmersion.domain.catalogue.events.EventImportService.Implementation.StoredKindsResultPart2] = db.read(EventImportRepo.storedKinds(provider))
    def partsDone(provider: com.histmersion.domain.catalogue.events.EventImportService.Implementation.PartsDoneProvider): Set[com.histmersion.domain.catalogue.events.EventImportService.Implementation.PartsDoneResult] = db.read(EventImportRepo.partsDone(provider))
    def markPart(provider: com.histmersion.domain.catalogue.events.EventImportService.Implementation.MarkPartProvider, part: com.histmersion.domain.catalogue.events.EventImportService.Implementation.MarkPartPart, records: com.histmersion.domain.catalogue.events.EventImportService.Implementation.MarkPartRecords): Unit =
      db.transact(EventImportRepo.markPart(provider, part, records))
    def clearParts(provider: com.histmersion.domain.catalogue.events.EventImportService.Implementation.ClearPartsProvider): Unit = db.transact(EventImportRepo.clearParts(provider))

  object Implementation:
    type RecordsResultPart1Part1 = RecordsResultPart1Part1.T
    object RecordsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordsResultPart1Part2 = RecordsResultPart1Part2.T
    object RecordsResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordsResultPart2 = RecordsResultPart2.T
    object RecordsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordsProvider = RecordsProvider.T
    object RecordsProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StoredKindsResultPart1Part1 = StoredKindsResultPart1Part1.T
    object StoredKindsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StoredKindsResultPart1Part2 = StoredKindsResultPart1Part2.T
    object StoredKindsResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StoredKindsResultPart2 = StoredKindsResultPart2.T
    object StoredKindsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StoredKindsProvider = StoredKindsProvider.T
    object StoredKindsProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PartsDoneResult = PartsDoneResult.T
    object PartsDoneResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PartsDoneProvider = PartsDoneProvider.T
    object PartsDoneProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkPartProvider = MarkPartProvider.T
    object MarkPartProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkPartPart = MarkPartPart.T
    object MarkPartPart extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkPartRecords = MarkPartRecords.T
    object MarkPartRecords extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ClearPartsProvider = ClearPartsProvider.T
    object ClearPartsProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

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

    private def merged(names: List[com.histmersion.domain.catalogue.events.EventImportService.InMemory.MergedNames], more: List[com.histmersion.domain.catalogue.events.EventImportService.InMemory.MergedMore]): List[com.histmersion.domain.catalogue.events.EventImportService.InMemory.MergedResult] = (names ++ more).distinct.sorted

    private def fill(s: CatalogueState, id: com.histmersion.domain.catalogue.events.EventImportService.InMemory.FillId, names: List[com.histmersion.domain.catalogue.events.EventImportService.InMemory.FillNames], wd: Option[com.histmersion.domain.catalogue.events.EventImportService.InMemory.FillWd]): CatalogueState =
      s.modify(_.events)
        .using(
          _.map(e =>
            if e.id != id then e
            else e.modify(_.wikidata).using(_.orElse(wd)).modify(_.names).using(merged(_, names))
          )
        )

    private def refresh(
        s: CatalogueState,
        id: com.histmersion.domain.catalogue.events.EventImportService.InMemory.RefreshId,
        r: CatalogRecord,
        names: List[com.histmersion.domain.catalogue.events.EventImportService.InMemory.RefreshNames],
        wd: Option[com.histmersion.domain.catalogue.events.EventImportService.InMemory.RefreshWd]
    ): (CatalogueState, Correction.Counts) =
      val now = EventFields.upstream(r)
      val applied = s.corrections.filter(c =>
        c.targetKind == Correction.Target.Event && c.targetId == id && Correction.Status.applied(c.status)
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

    private def inserted(s: CatalogueState, id: com.histmersion.domain.catalogue.events.EventImportService.InMemory.InsertedId, r: CatalogRecord, names: List[com.histmersion.domain.catalogue.events.EventImportService.InMemory.InsertedNames], wd: Option[com.histmersion.domain.catalogue.events.EventImportService.InMemory.InsertedWd]) =
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

    private def upsertRecord(s: CatalogueState, eventId: com.histmersion.domain.catalogue.Event.Id, r: CatalogRecord): CatalogueState =
      s.modify(_.catalogueRecords).using(rs => rs.filterNot(_._2.key == r.key) :+ (eventId -> r))

    /** One event's placed records written: the state after, whether the event was created, and what became of its
      * corrections when it was refreshed.
      */
    private def saveOne(
        s: CatalogueState,
        eventId: com.histmersion.domain.catalogue.Event.Id,
        group: List[(CatalogRecord, CatalogMerge.Placement)]
    ): (CatalogueState, com.histmersion.domain.catalogue.events.EventImportService.InMemory.SaveOneResultPart2, Option[Correction.Counts]) =
      val recs = group.map(_._1).sortBy(_.key)
      val names = recs.flatMap(r => r.label :: r.names).map(_.trim).filter(_.nonEmpty).distinct
      val wd = recs.flatMap(_.wikidata).headOption
      val catalogue = s.events.find(_.id == eventId).map(_.catalogue)
      def ownerIn(p: String): Option[CatalogRecord] =
        val storedKeys = s.catalogueRecords.filter(_._1 == eventId).map(_._2.key).toList
        CatalogRecord.owner(eventId, p, storedKeys ++ recs.map(_.key)).flatMap(k => recs.find(_.key == k))
      def refreshOrFill(p: String): (CatalogueState, Boolean, Option[Correction.Counts]) =
        ownerIn(p).fold((fill(s, eventId, names, wd), false, None)) { r =>
          val (after, c) = refresh(s, eventId, r, names, wd)
          (after, false, Some(c))
        }
      val (written, created, refreshed) = group.head._2 match
        case CatalogMerge.Placement.New(_, _) =>
          val creator = recs.find(r => CatalogRecord.eventId(r) == eventId).getOrElse(recs.head)
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
      val groups = placed.groupBy(_._2.eventId).toList.sortBy(_._1)
      @tailrec def loop(
          rest: List[(String, List[(CatalogRecord, CatalogMerge.Placement)])],
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
        case Nil => (s, EventSaved(created, updated, placed.size, counts))
      // the answer of the attempt that took: a contended update runs the loop again on the newer state
      val answer = AtomicReference[EventSaved]()
      val _ = state.updateAndGet { s0 =>
        val (after, saved) = loop(groups, s0, 0, 0, Correction.Counts())
        answer.set(saved)
        after
      }
      answer.get

    def records(provider: Option[com.histmersion.domain.catalogue.events.EventImportService.InMemory.RecordsProvider]): (List[(com.histmersion.domain.catalogue.events.EventImportService.InMemory.RecordsResultPart1Part1, com.histmersion.domain.catalogue.events.EventImportService.InMemory.RecordsResultPart1Part2, CatalogRecord)], com.histmersion.domain.catalogue.events.EventImportService.InMemory.RecordsResultPart2) =
      val s = state.get
      val rows = s.catalogueRecords
        .filter((_, r) => provider.forall(_ == r.provider))
        .sortBy(_._2.key)
        .toList
        .flatMap((id, r) => s.events.find(_.id == id).map(e => (id, e.kind, r)))
      (rows, 0)

    def storedKinds(provider: com.histmersion.domain.catalogue.events.EventImportService.InMemory.StoredKindsProvider): Map[(com.histmersion.domain.catalogue.events.EventImportService.InMemory.StoredKindsResultPart1Part1, com.histmersion.domain.catalogue.events.EventImportService.InMemory.StoredKindsResultPart1Part2), com.histmersion.domain.catalogue.events.EventImportService.InMemory.StoredKindsResultPart2] =
      state.get.catalogueRecords.collect {
        case (_, r) if r.provider == provider => r.key -> EventTaxonomy.canonical(r.kind)
      }.toMap

    def partsDone(provider: com.histmersion.domain.catalogue.events.EventImportService.InMemory.PartsDoneProvider): Set[com.histmersion.domain.catalogue.events.EventImportService.InMemory.PartsDoneResult] = state.get.importParts.getOrElse(provider, Map.empty).keySet

    def markPart(provider: com.histmersion.domain.catalogue.events.EventImportService.InMemory.MarkPartProvider, part: com.histmersion.domain.catalogue.events.EventImportService.InMemory.MarkPartPart, records: com.histmersion.domain.catalogue.events.EventImportService.InMemory.MarkPartRecords): Unit =
      val _ = state.updateAndGet(
        _.modify(_.importParts).using(ps => ps + (provider -> (ps.getOrElse(provider, Map.empty) + (part -> records))))
      )

    def clearParts(provider: com.histmersion.domain.catalogue.events.EventImportService.InMemory.ClearPartsProvider): Unit =
      val _ = state.updateAndGet(_.modify(_.importParts).using(_ - provider))

  object InMemory:
    type MergedResult = MergedResult.T
    object MergedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MergedNames = MergedNames.T
    object MergedNames extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MergedMore = MergedMore.T
    object MergedMore extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FillId = FillId.T
    object FillId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FillNames = FillNames.T
    object FillNames extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type FillWd = FillWd.T
    object FillWd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefreshId = RefreshId.T
    object RefreshId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefreshNames = RefreshNames.T
    object RefreshNames extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RefreshWd = RefreshWd.T
    object RefreshWd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InsertedId = InsertedId.T
    object InsertedId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InsertedNames = InsertedNames.T
    object InsertedNames extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type InsertedWd = InsertedWd.T
    object InsertedWd extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type SaveOneResultPart2 = SaveOneResultPart2.T
    object SaveOneResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordsResultPart1Part1 = RecordsResultPart1Part1.T
    object RecordsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordsResultPart1Part2 = RecordsResultPart1Part2.T
    object RecordsResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordsResultPart2 = RecordsResultPart2.T
    object RecordsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RecordsProvider = RecordsProvider.T
    object RecordsProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StoredKindsResultPart1Part1 = StoredKindsResultPart1Part1.T
    object StoredKindsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StoredKindsResultPart1Part2 = StoredKindsResultPart1Part2.T
    object StoredKindsResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StoredKindsResultPart2 = StoredKindsResultPart2.T
    object StoredKindsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type StoredKindsProvider = StoredKindsProvider.T
    object StoredKindsProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PartsDoneResult = PartsDoneResult.T
    object PartsDoneResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PartsDoneProvider = PartsDoneProvider.T
    object PartsDoneProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkPartProvider = MarkPartProvider.T
    object MarkPartProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkPartPart = MarkPartPart.T
    object MarkPartPart extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MarkPartRecords = MarkPartRecords.T
    object MarkPartRecords extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type ClearPartsProvider = ClearPartsProvider.T
    object ClearPartsProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
