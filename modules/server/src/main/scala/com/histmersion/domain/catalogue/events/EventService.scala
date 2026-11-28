package com.histmersion
package domain.catalogue.events
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{External, Problem}

import com.histmersion.api.catalogue.EventEntry

import com.histmersion.api.Write
import com.histmersion.api.catalogue.{
  Attestation,
  CatalogRecord,
  EventCatalogueRef,
  EventFields,
  EventGeo,
  EventGeometry,
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
import com.histmersion.domain.catalogue.{CatalogueState, Event, Place}
import com.histmersion.domain.library.Citekey
import com.histmersion.util.database.Transactor
import hearth.kindlings.optics.syntax.*

import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Correction
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.library.Filter
import com.histmersion.api.catalogue.Period
import com.histmersion.api.catalogue.Longitude
import com.histmersion.api.catalogue.Latitude
import com.histmersion.api.personal.UnreadPage
import com.histmersion.api.catalogue.LeadFacts
import com.histmersion.util.json.RawJson.equality
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.{CreatedAt, Creator}
import java.time.Clock
import com.histmersion.lint.{tolerated, LintRule}

/** The events: dated happenings with their datings by source, their relations to each other and their participants,
  * listed by a selection, drawn on the map, written by hand, renamed and deleted.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
trait EventService:
  /** The events `selection` selects, by their earliest year then label, one page of them. */
  def list(selection: EventSelection, limit: PageSize, offset: Offset): EventList

  /** The events `selection` selects that the map draws (placed, dated well enough), at most `limit`, with the regional
    * ones' outlines simplified to `tolerance` degrees; the unplaced and the imprecisely dated counted.
    */
  def geo(selection: EventSelection, limit: PageSize, tolerance: GeoTolerance): EventGeo.List

  /** The events of `ids` the map draws, by id. */
  def geoOf(ids: List[Event.Id]): Map[Event.Id, EventGeo]

  /** One event with its parent, parts, datings, edges, participants, Wikidata item and corrections by hand. */
  def get(id: Event.Id): Option[EventEntry]

  /** Its tier, its other names and the catalogue it was imported from. */
  def tierAndNames(id: Event.Id): Option[(EventSummary.Tier, List[EventEntry.Names], Option[HeldEvent.Catalogue])]

  /** The catalogues' rows of one event, with each built-in catalogue's licence and citation. */
  def catalogueRefs(id: Event.Id): List[EventCatalogueRef]

  /** Why `geojson` is no geometry the store reads; `None` when it is one. */
  def geometryError(geojson: EventDetailRow.Geometry): Option[Problem]

  /** Create or replace one event; on one an import created, the fields that differ from its catalogue's are recorded as
    * corrections.
    */
  def put(id: Event.Id, in: EventPut)(using WritePermit)(using Creator.New): Unit

  /** Change the fields `p` sets; on one an import created, each changed field is recorded as a correction. */
  def patch(id: Event.Id, p: EventPatch)(using WritePermit)(using Creator.New): Either[Api.Error, Unit]

  /** A new id; the old one stays an alias, what names the event follows. */
  def rename(from: Event.Id, to: Event.Id)(using WritePermit): Either[Api.Error, Unit]

  /** The id an old id was renamed to; `None` for a live id, or nothing. */
  def renamedTo(id: Event.Id): Option[Event.Id]

  def delete(id: Event.Id)(using WritePermit): Boolean

object EventService:
  def apply(db: Transactor, related: EventSourceFilter, clock: Clock): EventService =
    Implementation(db, related, clock)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def missing(id: Event.Id) =
    Api.Error(
      Problem.NotFound.Entity(Problem.TypeName.of(Event.Id), Problem.Raw(s"$id")),
      Some(Api.Error.Code(Write.CodeNotFound))
    )

  final private class Implementation(db: Transactor, related: EventSourceFilter, clock: Clock) extends EventService:
    def list(selection: EventSelection, limit: PageSize, offset: Offset): EventList =
      db.read(EventRepo.list(selection.sql(related), limit, offset))
    def geo(selection: EventSelection, limit: PageSize, tolerance: GeoTolerance): EventGeo.List =
      db.read(EventRepo.geo(selection.sql(related), limit, tolerance))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def geoOf(ids: List[Event.Id]): Map[Event.Id, EventGeo] =
      db.read(EventRepo.geoOf(ids.map(i => BEventRow.Id(i.value)))).map((k, v) => Event.Id.assume(k.value) -> v)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def get(id: Event.Id): Option[EventEntry] = db.read(EventRepo.get(BEventRow.Id(id.value)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def tierAndNames(id: Event.Id): Option[(EventSummary.Tier, List[EventEntry.Names], Option[HeldEvent.Catalogue])] =
      db.read(EventRepo.tierAndNames(BEventRow.Id(id.value)))
        .map((tier, names, catalogue) =>
          (
            EventSummary.Tier(tier.value),
            names.map(n => EventEntry.Names(n.value)),
            catalogue.map(c => HeldEvent.Catalogue(c.value))
          )
        )
    def catalogueRefs(id: Event.Id): List[EventCatalogueRef] = db.read(EventImportRepo.catalogueRefs(id))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def geometryError(geojson: EventDetailRow.Geometry): Option[Problem] =
      db.read(EventRepo.geometryError(BEventRow.Geom(geojson.value)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def put(id: Event.Id, in: EventPut)(using WritePermit)(using Creator.New): Unit =
      db.write:
        given CreatedAt = CreatedAt.now(clock)
        EventRepo.put(BEventRow.Id(id.value), in)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def patch(id: Event.Id, p: EventPatch)(using WritePermit)(using Creator.New): Either[Api.Error, Unit] =
      db.write(EventRepo.patch(BEventRow.Id(id.value), p))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def rename(from: Event.Id, to: Event.Id)(using WritePermit): Either[Api.Error, Unit] =
      db.write(EventRepo.rename(BEventRow.Id(from.value), BEventRow.Id(to.value)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def renamedTo(id: Event.Id): Option[Event.Id] =
      db.read(EventRepo.renamedTo(BEventRow.Id(id.value))).map(i => Event.Id.assume(i.value))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def delete(id: Event.Id)(using WritePermit): Boolean = db.write(EventRepo.delete(BEventRow.Id(id.value)))

  /** The same rules over the shared in-memory state, which holds the catalogue's rows alone: no place is stored, so an
    * event is anchored by its own geometry (a point; an area has no outline) and has no place's kind or label;
    * `selectedSources` stands for the library's reading of a filter's sources (`None`: the filter sets none).
    */
  final class InMemory(
      state: AtomicReference[CatalogueState],
      selectedSources: Filter => Option[Set[Citekey]] = _ => None
  ) extends EventService:

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def attested(s: CatalogueState, id: Event.Id): Boolean =
      s.attestations.exists(a => a.targetType.value == "event" && a.targetId.value == id.value)

    private def overlaps(e: HeldEvent, period: Option[Period]): Boolean =
      period.forall(p => e.outer.forall((a, b) => a <= p.end && b >= p.start))

    private def containsText(field: Option[String], t: String): Boolean =
      field.exists(_.toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT)))

    /** The part of the selection the library answers in the database: its sources, its period, its title. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def related(s: CatalogueState, e: HeldEvent, f: Filter): Option[Boolean] =
      val sources = selectedSources(f.sourceSet.modify(_.period).setTo(None))
      val title = f.title.map(_.value.trim).filter(_.nonEmpty)
      if sources.isEmpty && f.period.isEmpty && title.isEmpty then None
      else
        val bySource = sources.forall(keys =>
          e.citekey.exists(keys) ||
            s.attestations.exists(a =>
              a.targetType.value == "event" && a.targetId.value == e.id.value &&
                Citekey.option(a.citekey.value).exists(keys)
            )
        )
        Some(bySource && overlaps(e, f.period) && title.forall(t => containsText(Some(e.label.value), t)))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    private def selects(s: CatalogueState, sel: EventSelection)(e: HeldEvent): Boolean =
      def clean(o: Option[String]) = o.map(_.trim).filter(_.nonEmpty)
      val tier = e.summary(attested(s, e.id)).tier.value
      val bySources = related(s, e, sel.filter).forall { inSelection =>
        inSelection || (sel.narrow.context.context && tier == EventTier.Catalogue &&
          overlaps(e, sel.filter.period) &&
          sel.filter.title.map(_.value.trim).filter(_.nonEmpty).forall(t => containsText(Some(e.label.value), t)))
      }
      bySources &&
      clean(sel.narrow.tier.map(_.value)).map(_.toLowerCase).forall(_ == tier) &&
      sel.narrow.area.forall(b =>
        e.geometry
          .flatMap(HeldEvent.pointOf)
          .exists((lat, lon) => b.contains(Longitude(lon.value), Latitude(lat.value)))
      ) &&
      clean(sel.text.map(_.value)).forall(t =>
        containsText(Some(e.label.value), t) || containsText(e.description.map(_.value), t)
      ) &&
      EventTaxonomy
        .condition(clean(sel.kind.map(_.value)).map(EventTaxonomy.KindCondition.Filter(_)))
        .forall(c => c.kinds.contains(EventSummary.Kind(e.kind.value)) != c.negate.negate) &&
      clean(sel.historicity.map(_.value)).map(_.toLowerCase).forall(_ == e.historicity.value) &&
      sel.placeId.forall(p => e.placeId.contains(p))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def ordered(es: Vector[HeldEvent]): Vector[HeldEvent] =
      es.sortBy(e =>
        (e.timeSpan.notBefore.orElse(e.timeSpan.beginBy).fold((1, 0L))(y => (0, y.value)), e.label.value, e.id)
      )

    @tolerated(LintRule.UnnamedBoolean, "pending ADR 0027 (a Boolean argument is passed by name)")
    private def summaryOf(s: CatalogueState, e: HeldEvent): EventSummary = e.summary(attested(s, e.id))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def list(selection: EventSelection, limit: PageSize, offset: Offset): EventList =
      val s = state.get
      val all = ordered(s.events.filter(selects(s, selection)))
      val from = offset.value.max(0)
      EventList.of(
        EventList.Total(all.size),
        all.slice(from, from + limit.value.max(1)).toList.map(summaryOf(s, _)),
        UnreadPage.Offset(from)
      )

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def drawn(s: CatalogueState, e: HeldEvent): Option[EventGeo] =
      e.geometry.flatMap(HeldEvent.pointOf).map { (lat, lon) =>
        val sum = summaryOf(s, e)
        val vei = s.catalogueRecords.filter(_._1 == e.id).flatMap(_._2.vei.map(_.value)).maxOption
        val magnitude =
          s.catalogueRecords.filter(_._1 == e.id).flatMap(_._2.magnitude.map(_.value)).maxOption
        EventGeo(
          sum.id,
          EventGeo.Label(sum.label.value),
          EventGeo.Kind(sum.kind.value),
          EventGeo.Scale(sum.scale.value),
          sum.historicity,
          sum.timeSpan,
          Latitude(lat.value),
          Longitude(lon.value),
          EventGeo.Anchor("event"),
          sum.placeId,
          None,
          sum.parentId.map(p => EventGeo.ParentId(p.value)),
          None,
          EventGeo.Tier(sum.tier.value),
          EventLeads
            .size(vei.map(LeadFacts.Vei(_)), magnitude.map(LeadFacts.Magnitude(_)))
            .map(EventGeo.Size(_)),
          sum.precision,
          sum.datingNote.map(EventGeo.Dating(_))
        )
      }

    private def preciseEnough(e: HeldEvent): Boolean = e.uncertainty.exists(_ <= EventPrecision.ApproximateUpTo)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def geo(selection: EventSelection, limit: PageSize, tolerance: GeoTolerance): EventGeo.List =
      val s = state.get
      val listed = ordered(s.events.filter(selects(s, selection)))
      val placed = listed.filter(_.geometry.isDefined)
      val items = placed.filter(preciseEnough).flatMap(drawn(s, _)).take(limit.value.max(1)).toList
      EventGeo.List(
        items,
        EventGeo.List.Unplaced(listed.count(e => e.geometry.isEmpty && preciseEnough(e))),
        EventGeo.List.Imprecise(placed.count(e => e.uncertainty.exists(_ > EventPrecision.ApproximateUpTo)))
      )

    def geoOf(ids: List[Event.Id]): Map[Event.Id, EventGeo] =
      val s = state.get
      s.events.filter(e => ids.contains(e.id) && preciseEnough(e)).flatMap(e => drawn(s, e).map(e.id -> _)).toMap

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def get(id: Event.Id): Option[EventEntry] =
      val s = state.get
      s.events.find(_.id == id).map { e =>
        val sum = summaryOf(s, e)
        EventEntry(
          sum,
          e.geometry.map(g => EventGeometry.Geojson(g.value)),
          e.parentId.flatMap(p => s.events.find(_.id.value == p.value)).map(summaryOf(s, _)),
          ordered(s.events.filter(_.parentId.exists(_.value == id.value))).toList.map(summaryOf(s, _)),
          e.datings.sortBy(_.citekey.value.toString),
          s.events
            .flatMap(_.edges)
            .filter(g => g.srcId.value == id.value || g.dstId.value == id.value)
            .distinct
            .toList
            .sortBy(g => (g.kind.value, g.srcId.value, g.dstId.value)),
          e.participants.sortBy(p => (p.role.value, p.actor.value)),
          e.wikidata.map(w => EventEntry.Wikidata(w.value)),
          corrections = s.corrections
            .filter(c => c.targetKind.value == Correction.Target.Event && c.targetId.value == id.value)
            .sortBy(_.field.value)
            .toList
        )
      }

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def tierAndNames(id: Event.Id): Option[(EventSummary.Tier, List[EventEntry.Names], Option[HeldEvent.Catalogue])] =
      val s = state.get
      s.events
        .find(_.id == id)
        .map(e => (summaryOf(s, e).tier, e.names.map(n => EventEntry.Names(n.value)), e.catalogue))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def catalogueRefs(id: Event.Id): List[EventCatalogueRef] =
      state.get.catalogueRecords
        .filter(_._1 == id)
        .map(_._2)
        .sortBy(r => (r.provider.value, r.providerId.value))
        .toList
        .map { r =>
          val i = CatalogProvider.info(r.provider)
          EventCatalogueRef(
            EventCatalogueRef.Provider(r.provider.value),
            r.providerId,
            r.url,
            EventCatalogueRef.Title(i.fold(r.provider.value)(_.title.value)),
            EventCatalogueRef.Licence(i.fold("")(_.licence.value)),
            EventCatalogueRef.Citation(i.fold("")(_.citation.value)),
            r.vei,
            r.magnitude,
            r.deaths,
            None
          )
        }

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def geometryError(geojson: EventDetailRow.Geometry): Option[Problem] =
      RawJson.parse(geojson.value) match
        case Left(e)  => Some(geometryUnreadable(External.Reported.of(e)))
        case Right(j) =>
          val c = j.cursor
          c.get[String]("type").toOption match
            case None => Some(geometryUnreadable(External.Reported("unknown GeoJSON type")))
            case Some(t) if c.downField("coordinates").succeeded || t == "GeometryCollection" => None
            case Some(_)                                                                      =>
              Some(geometryUnreadable(External.Reported("Unable to find 'coordinates' in GeoJSON string")))

    /** What PostGIS says of a GeoJSON geometry it cannot read, as the database's check reports it. */
    private def geometryUnreadable(reported: External.Reported): Problem =
      Problem.Invalid.Unparsable(
        Problem.Io.Format.Json,
        Problem.Where("GeoJSON geometry"),
        Problem.Io.Damage.Unreadable(reported),
        Vector.empty
      )

    /** The corrections a person's `fields` make on an event an import created, recorded as the database does. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def withCorrections(s: CatalogueState, id: Event.Id, fields: Map[String, Json]): CatalogueState =
      val upstream = for
        e <- s.events.find(_.id == id)
        cat <- e.catalogue
        recs = s.catalogueRecords.filter((ev, r) => ev == id && r.provider.value == cat.value).map(_._2)
        key <- CatalogRecord.owner(
          CatalogRecord.EventId(id.value),
          CatalogRecord.Provider(cat.value),
          recs.map(_.key).toList
        )
        rec <- recs.find(_.key == key)
      yield cat -> rec
      upstream.fold(s) { (provider, rec) =>
        val up = EventFields.upstream(rec)
        @tailrec def loop(rest: List[(String, Json)], acc: CatalogueState): CatalogueState = rest match
          case (f, v) :: tail => loop(tail, up.get(f).fold(acc)(u => corrected(acc, provider, id, f, v, u)))
          case Nil            => acc
        loop(fields.toList, s)
      }

    /** One field's correction recorded: none when the person's value is the catalogue's. */
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def corrected(
        s: CatalogueState,
        provider: HeldEvent.Catalogue,
        id: Event.Id,
        field: String,
        value: Json,
        upstream: Json
    ): CatalogueState =
      val others =
        s.corrections.filterNot(c =>
          c.targetKind.value == Correction.Target.Event && c.targetId.value == id.value && c.field.value == field
        )
      if value == upstream then s.modify(_.corrections).setTo(others)
      else
        val n = s.lastCorrectionId.value + 1
        val c = Correction(
          Correction.Id(n),
          Correction.TargetKind(Correction.Target.Event),
          Correction.TargetId(id.value),
          Correction.Field(field),
          Correction.Value(value.noSpaces),
          Correction.UpstreamValue(upstream.noSpaces),
          Correction.StatusValue(Correction.Status.Active),
          Some(Correction.Upstream(provider.value))
        )
        s.modify(_.corrections)
          .setTo(others :+ c)
          .modify(_.lastCorrectionId)
          .setTo(CatalogueState.LastCorrectionId(n))

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def put(id: Event.Id, in: EventPut)(using WritePermit)(using Creator.New): Unit =
      val _ = state.updateAndGet { s =>
        val before = s.events.find(_.id == id)
        val sameSpan = before.exists(_.timeSpan == in.timeSpan)
        val apiId = com.histmersion.api.catalogue.Event.Id(id.value)
        val e = HeldEvent(
          id,
          HeldEvent.Label(in.label.value),
          HeldEvent.Kind(in.kind.value),
          in.timeSpan,
          HeldEvent.Historicity(in.historicity.value),
          in.geometry.map(_.value.trim).filter(_.nonEmpty).map(HeldEvent.Geometry(_)),
          in.placeId.flatMap(p => Place.Id.option(p.value)),
          in.parentId.map(p => HeldEvent.ParentId(p.value)),
          in.description.map(d => HeldEvent.Description(d.value)),
          in.citekey.flatMap(c => Citekey.option(c.value)),
          in.scale.flatMap(v => EventScale.parse(v).flatten).map(v => HeldEvent.Scale(v.value)),
          before.flatMap(_.wikidata),
          before.fold(Nil)(_.names),
          before.flatMap(_.catalogue),
          before.filter(_ => sameSpan).flatMap(_.central),
          before.filter(_ => sameSpan).flatMap(_.plusMinus),
          before.filter(_ => sameSpan).flatMap(_.datingMethod),
          in.datings.map(_.modify(_.eventId).setTo(apiId)).distinctBy(_.citekey),
          in.edges.distinctBy(g => (g.srcId, g.dstId, g.kind)),
          in.participants.map(_.modify(_.eventId).setTo(apiId)).distinctBy(p => (p.actor, p.role))
        )
        val cleared = s.events.map(o =>
          o.modify(_.edges).using(_.filterNot(g => g.srcId.value == id.value || g.dstId.value == id.value))
        )
        val written = if before.isDefined then cleared.map(o => if o.id == id then e else o) else cleared :+ e
        withCorrections(s.modify(_.events).setTo(written), id, EventFields.ofPut(in))
      }

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def patch(id: Event.Id, p: EventPatch)(using WritePermit)(using Creator.New): Either[Api.Error, Unit] =
      if !state.get.events.exists(_.id == id) then Left(missing(id))
      else
        val _ = state.updateAndGet { s =>
          val apiId = com.histmersion.api.catalogue.Event.Id(id.value)
          def changed(e: HeldEvent): HeldEvent =
            val spanSet = p.timeSpan.isDefined
            e.modify(_.label)
              .using(l => p.label.fold(l)(x => HeldEvent.Label(x.value)))
              .modify(_.kind)
              .using(k => p.kind.fold(k)(x => HeldEvent.Kind(x.value)))
              .modify(_.historicity)
              .using(h => p.historicity.fold(h)(x => HeldEvent.Historicity(x.value)))
              .modify(_.placeId)
              .using(o => p.placeId.fold(o)(x => Place.Id.option(x.value).orElse(o)))
              .modify(_.parentId)
              .using(o => p.parentId.fold(o)(x => Some(HeldEvent.ParentId(x.value))))
              .modify(_.description)
              .using(o => p.description.fold(o)(x => Some(HeldEvent.Description(x.value))))
              .modify(_.citekey)
              .using(o => p.citekey.fold(o)(x => Citekey.option(x.value).orElse(o)))
              .modify(_.scale)
              .using(o => p.scale.flatMap(EventScale.parse).fold(o)(_.map(x => HeldEvent.Scale(x.value))))
              .modify(_.wikidata)
              .using(o =>
                p.wikidata
                  .map(_.value.trim)
                  .fold(o)(q => Option(q).filter(_.nonEmpty).flatMap(w => HeldEvent.Wikidata.option(w)))
              )
              .modify(_.geometry)
              .using(o =>
                p.geometry.map(_.value.trim).fold(o)(g => Option(g).filter(_.nonEmpty).map(HeldEvent.Geometry(_)))
              )
              .modify(_.timeSpan)
              .using(p.timeSpan.getOrElse(_))
              .modify(_.datings)
              .using(o => p.datings.fold(o)(_.map(_.modify(_.eventId).setTo(apiId))))
              .modify(_.participants)
              .using(o => p.participants.fold(o)(_.map(_.modify(_.eventId).setTo(apiId))))
              .modify(_.central)
              .using(o => if spanSet then None else o)
              .modify(_.plusMinus)
              .using(o => if spanSet then None else o)
              .modify(_.datingMethod)
              .using(o => if spanSet then None else o)
          val edited = s.events.map { e =>
            val own = if e.id == id then changed(e) else e
            p.edges.fold(own) { es =>
              val kept =
                own.modify(_.edges).using(_.filterNot(g => g.srcId.value == id.value || g.dstId.value == id.value))
              if own.id == id then kept.modify(_.edges).using(_ ++ es) else kept
            }
          }
          withCorrections(s.modify(_.events).setTo(edited), id, EventFields.ofPatch(p))
        }
        Right(())

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
    def rename(from: Event.Id, to: Event.Id)(using WritePermit): Either[Api.Error, Unit] =
      val (oldId, newId) = (from, to)
      val s = state.get
      def exists(i: Event.Id) = s.events.exists(_.id == i)
      val aliasOf = s.eventAliases.get(CatalogueState.EventAliasesKey(newId.value))
      if !exists(oldId) then Left(missing(oldId))
      else if exists(newId) then
        Left(
          Api.Error(
            Problem.Conflict.AlreadyExists(Problem.TypeName.of(Event.Id), Problem.Raw.of(newId)),
            Some(Api.Error.Code(Write.CodeInvalid))
          )
        )
      else if aliasOf.exists(_.value != oldId.value) then
        Left(
          Api.Error(
            Problem.Conflict.Aliased(Problem.TypeName.of(Event.Id), Problem.Raw.of(newId), Problem.Raw.of(aliasOf.get)),
            Some(Api.Error.Code(Write.CodeInvalid))
          )
        )
      else
        val _ = state.updateAndGet { s =>
          def ren(i: Event.Id) = if i == oldId then newId else i
          def renApi(i: com.histmersion.api.catalogue.Event.Id) =
            if i.value == oldId.value then com.histmersion.api.catalogue.Event.Id(newId.value) else i
          s.modify(_.events)
            .using(_.map { e =>
              e.modify(_.id)
                .using(ren)
                .modify(_.parentId)
                .using(_.map(p => if p.value == oldId.value then HeldEvent.ParentId(newId.value) else p))
                .modify(_.edges)
                .using(_.map(g => g.modify(_.srcId).using(renApi).modify(_.dstId).using(renApi)))
                .modify(_.datings)
                .using(_.map(d => d.modify(_.eventId).using(renApi)))
                .modify(_.participants)
                .using(_.map(p => p.modify(_.eventId).using(renApi)))
            })
            .modify(_.attestations)
            .using(
              _.map(a =>
                if a.targetType.value == "event" && a.targetId.value == oldId.value then
                  a.modify(_.targetId).setTo(Attestation.TargetId(newId.value))
                else a
              )
            )
            .modify(_.corrections)
            .using(
              _.map(c =>
                if c.targetKind.value == Correction.Target.Event && c.targetId.value == oldId.value then
                  c.modify(_.targetId).setTo(Correction.TargetId(newId.value))
                else c
              )
            )
            .modify(_.catalogueRecords)
            .using(_.map((e, r) => (ren(e), r)))
            .modify(_.eventAliases)
            .using(as =>
              (as - CatalogueState.EventAliasesKey(newId.value)).map((a, t) =>
                a -> CatalogueState.EventAliasesValue(ren(t.eventId).value)
              ) + (CatalogueState.EventAliasesKey(oldId.value) -> CatalogueState.EventAliasesValue(newId.value))
            )
        }
        Right(())

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def renamedTo(id: Event.Id): Option[Event.Id] =
      val s = state.get
      if s.events.exists(_.id == id) then None
      else s.eventAliases.get(CatalogueState.EventAliasesKey(id.value)).map(v => v.eventId)

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def delete(id: Event.Id)(using WritePermit): Boolean =
      val had = state.get.events.exists(_.id == id)
      val _ = state.updateAndGet(
        _.modify(_.events)
          .using(
            _.filterNot(_.id == id)
              .map(e =>
                e.modify(_.edges).using(_.filterNot(g => g.srcId.value == id.value || g.dstId.value == id.value))
              )
          )
          .modify(_.corrections)
          .using(_.filterNot(c => c.targetKind.value == Correction.Target.Event && c.targetId.value == id.value))
      )
      had
