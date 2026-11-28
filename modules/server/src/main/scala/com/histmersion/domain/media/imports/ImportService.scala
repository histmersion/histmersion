package com.histmersion
package domain.media.imports

import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.domain.media.MediaState
import com.histmersion.domain.media.assets.MediaItemRow
import com.histmersion.util.database.Transactor

import hearth.kindlings.optics.syntax.*
import io.scalaland.chimney.dsl.*

import java.time.{Clock, Instant}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.catalogue.places.ExternalIds
import com.histmersion.domain.catalogue.places.Wikidata
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.lint.{tolerated, LintRule}
import com.histmersion.TwoCases

/** What the media imports ask about and what they recorded asking (#1446): the places on the map and the events a
  * provider has not been asked about, the facts a collection's match reads of a place, and the checks — that a provider
  * was asked, and how many pictures it gave.
  */
trait ImportService:

  /** The places (one per Wikidata item) and events a provider asks about: one by id, or those not asked yet. */
  def targets(
      provider: ImportService.Provider,
      place: Option[Place.Id],
      event: Option[Event.Id],
      limit: ImportService.TargetsLimit,
      refresh: ImportService.Refresh,
      since: Option[ImportService.Since]
  ): Either[Problem, List[Target]]

  def pending(provider: ImportService.Provider, since: Option[ImportService.Since]): ImportService.PendingCount

  /** The places on the map (`places`) or the events with an item a collection asks about. */
  def collectionTargets(
      check: ImportService.CheckName,
      places: ImportService.Places,
      place: Option[Place.Id],
      event: Option[Event.Id],
      limit: ImportService.CollectionTargetsLimit,
      refresh: ImportService.Refresh,
      since: Option[ImportService.Since],
      pleiadesOnly: ImportService.PleiadesOnly
  ): Either[Problem, List[Target]]

  def collectionPending(
      check: ImportService.CheckName,
      places: ImportService.Places,
      since: Option[ImportService.Since],
      pleiadesOnly: ImportService.PleiadesOnly
  ): ImportService.PendingCount

  def placeFacts(ids: List[Place.Id]): Map[Place.Id, PlaceFacts]
  def mapPlacesNamed(keys: List[ImportService.PlaceKey]): Map[ImportService.PlaceKey, List[PlaceFacts]]
  def placeExternalIds(ids: List[Place.Id]): Map[Place.Id, Set[(ExternalIds.Scheme, ImportService.ExternalId)]]
  def pleiadesIds(ids: List[Place.Id]): Map[Place.Id, List[ImportService.ExternalId]]
  def mapPlacesWithIds(
      ids: List[(ExternalIds.Scheme, ImportService.ExternalId)]
  ): Map[(ExternalIds.Scheme, ImportService.ExternalId), List[PlaceFacts]]

  /** A place's Commons category recorded as a link of the place; whether it is new. */
  def commonsCategory(placeId: Place.Id, category: ImportService.CommonsCategory, qid: Wikidata.Id)(using
      WritePermit
  ): Boolean

  /** That `provider` was asked about a target, and how many pictures it gave. */
  def checked(
      provider: ImportService.Provider,
      targetType: ImportService.TargetType,
      targetId: ImportService.TargetId,
      found: ImportService.FoundCount,
      qid: Option[Wikidata.Id]
  )(using WritePermit): Unit

object ImportService:
  export ExternalIds.ExternalId
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type FoundCount = RowCount
  val FoundCount: RowCount.type = RowCount
  @tolerated(LintRule.G46, "pending G46 (re-export, don't alias)")
  type PendingCount = RowCount
  val PendingCount: RowCount.type = RowCount
  export MediaItemRow.Provider
  export Target.Id as TargetId
  export Target.TargetType
  export MediaImporter.CheckName
  type CommonsCategory = CommonsCategory.T
  object CommonsCategory extends Newtype[String, Pure]
  type PlaceKey = PlaceKey.T
  object PlaceKey extends Newtype[String, Pure]
  enum Places derives CanEqual:
    case WithPlaces, WithoutPlaces
  object Places extends TwoCases[Places]:
    extension (places: Places) def places: Boolean = places == WithPlaces
  enum PleiadesOnly derives CanEqual:
    case PleiadesOnly, AnyGazetteer
  object PleiadesOnly extends TwoCases[PleiadesOnly]:
    extension (pleiadesOnly: PleiadesOnly) def pleiadesOnly: Boolean = pleiadesOnly == PleiadesOnly
  export CollectionImport.Refresh
  type Since = Since.T
  object Since extends Newtype[Long, Pure]
  type TargetsLimit = TargetsLimit.T
  object TargetsLimit extends Newtype[Int, Pure]
  type CollectionTargetsLimit = CollectionTargetsLimit.T
  object CollectionTargetsLimit extends Newtype[Int, Pure]

  def apply(db: Transactor): ImportService = Implementation(db)

  final private class Implementation(db: Transactor) extends ImportService:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def targets(
        provider: Provider,
        place: Option[Place.Id],
        event: Option[Event.Id],
        limit: TargetsLimit,
        refresh: Refresh,
        since: Option[Since]
    ): Either[Problem, List[Target]] = db
      .read(
        ImportRepo.targets(
          provider,
          place,
          event,
          ImportRepo.Limit(limit.value),
          (if refresh.refresh then ImportRepo.Refresh.Refresh else ImportRepo.Refresh.Reuse),
          since.map(s => ImportRepo.Since(s.value))
        )
      )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def pending(provider: Provider, since: Option[Since]): PendingCount =
      db.read(ImportRepo.pending(provider, since.map(s => ImportRepo.Since(s.value))))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def collectionTargets(
        check: CheckName,
        places: Places,
        place: Option[Place.Id],
        event: Option[Event.Id],
        limit: CollectionTargetsLimit,
        refresh: Refresh,
        since: Option[Since],
        pleiadesOnly: PleiadesOnly
    ): Either[Problem, List[Target]] =
      db.read(
        ImportRepo.collectionTargets(
          MediaItemRow.Provider(check.value),
          (if places.places then ImportRepo.Places.WithPlaces else ImportRepo.Places.WithoutPlaces),
          place,
          event,
          ImportRepo.Limit(limit.value),
          (if refresh.refresh then ImportRepo.Refresh.Refresh else ImportRepo.Refresh.Reuse),
          since.map(s => ImportRepo.Since(s.value)),
          (if pleiadesOnly.pleiadesOnly then ImportRepo.PleiadesOnly.PleiadesOnly
           else ImportRepo.PleiadesOnly.AnyGazetteer)
        )
      )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def collectionPending(
        check: CheckName,
        places: Places,
        since: Option[Since],
        pleiadesOnly: PleiadesOnly
    ): PendingCount =
      db.read(
        ImportRepo.collectionPending(
          MediaItemRow.Provider(check.value),
          (if places.places then ImportRepo.Places.WithPlaces else ImportRepo.Places.WithoutPlaces),
          since.map(s => ImportRepo.Since(s.value)),
          (if pleiadesOnly.pleiadesOnly then ImportRepo.PleiadesOnly.PleiadesOnly
           else ImportRepo.PleiadesOnly.AnyGazetteer)
        )
      )
    def placeFacts(ids: List[Place.Id]): Map[Place.Id, PlaceFacts] = db.read(ImportRepo.placeFacts(ids))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def mapPlacesNamed(keys: List[PlaceKey]): Map[PlaceKey, List[PlaceFacts]] = db
      .read(ImportRepo.mapPlacesNamed(keys.map(k => ImportRepo.NameKey(k.value))))
      .map((k, v) => PlaceKey(k.value) -> v)
    def placeExternalIds(ids: List[Place.Id]): Map[Place.Id, Set[(ExternalIds.Scheme, ExternalId)]] =
      db.read(ImportRepo.placeExternalIds(ids))
    def pleiadesIds(ids: List[Place.Id]): Map[Place.Id, List[ExternalId]] = db.read(ImportRepo.pleiadesIds(ids))
    def mapPlacesWithIds(
        ids: List[(ExternalIds.Scheme, ExternalId)]
    ): Map[(ExternalIds.Scheme, ExternalId), List[PlaceFacts]] =
      db.read(ImportRepo.mapPlacesWithIds(ids))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def commonsCategory(placeId: Place.Id, category: CommonsCategory, qid: Wikidata.Id)(using WritePermit): Boolean =
      db.write(ImportRepo.commonsCategory(placeId, ImportRepo.Category(category.value), Target.Qid(qid.value)))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def checked(
        provider: Provider,
        targetType: TargetType,
        targetId: TargetId,
        found: FoundCount,
        qid: Option[Wikidata.Id]
    )(using WritePermit): Unit =
      db.write(ImportRepo.checked(provider, targetType, targetId, found, qid.map(q => Target.Qid(q.value))))

  /** Media's checks alone, in memory: no place, event or finding is held, so nothing is on the map to ask about — a
    * place or event named by id is not there, a batch is empty, no place has facts — and a Commons category has no
    * place to be recorded on. `clock` stamps a check as the database's `clock_timestamp()` does.
    */
  final class InMemory(state: AtomicReference[MediaState], clock: Clock) extends ImportService:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private def one(place: Option[Place.Id], event: Option[Event.Id]): Option[Problem] =
      place
        .map(_.value.trim)
        .filter(_.nonEmpty)
        .map(p => Problem.NotFound.Entity(Problem.TypeName.of(Place.Id), Problem.Raw(s"$p")))
        .orElse(
          event
            .map(_.value.trim)
            .filter(_.nonEmpty)
            .map(e => Problem.NotFound.Entity(Problem.TypeName.of(Event.Id), Problem.Raw(s"$e")))
        )

    def targets(
        provider: Provider,
        place: Option[Place.Id],
        event: Option[Event.Id],
        limit: TargetsLimit,
        refresh: Refresh,
        since: Option[Since]
    ): Either[Problem, List[Target]] = one(place, event).toLeft(Nil)
    def pending(provider: Provider, since: Option[Since]): PendingCount = PendingCount(0)
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
    def collectionTargets(
        check: CheckName,
        places: Places,
        place: Option[Place.Id],
        event: Option[Event.Id],
        limit: CollectionTargetsLimit,
        refresh: Refresh,
        since: Option[Since],
        pleiadesOnly: PleiadesOnly
    ): Either[Problem, List[Target]] =
      (place.map(_.value.trim).filter(_.nonEmpty), event.map(_.value.trim).filter(_.nonEmpty)) match
        case (Some(_), _) if !places.places =>
          Left(Problem.Unclassified(Legacy.Text("this import is about events: name one with --event")))
        case (None, Some(_)) if places.places =>
          Left(Problem.Unclassified(Legacy.Text("this import is about places: name one with --place")))
        case _ => one(place, event).toLeft(Nil)
    def collectionPending(
        check: CheckName,
        places: Places,
        since: Option[Since],
        pleiadesOnly: PleiadesOnly
    ): PendingCount = PendingCount(0)
    def placeFacts(ids: List[Place.Id]): Map[Place.Id, PlaceFacts] = Map.empty
    def mapPlacesNamed(keys: List[PlaceKey]): Map[PlaceKey, List[PlaceFacts]] = Map.empty
    def placeExternalIds(ids: List[Place.Id]): Map[Place.Id, Set[(ExternalIds.Scheme, ExternalId)]] = Map.empty
    def pleiadesIds(ids: List[Place.Id]): Map[Place.Id, List[ExternalId]] = Map.empty
    def mapPlacesWithIds(
        ids: List[(ExternalIds.Scheme, ExternalId)]
    ): Map[(ExternalIds.Scheme, ExternalId), List[PlaceFacts]] = Map.empty
    def commonsCategory(placeId: Place.Id, category: CommonsCategory, qid: Wikidata.Id)(using WritePermit): Boolean =
      false

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def checked(
        provider: Provider,
        targetType: TargetType,
        targetId: TargetId,
        found: FoundCount,
        qid: Option[Wikidata.Id]
    )(using WritePermit): Unit =
      val at = Instant.now(clock)
      val held = HeldCheck(
        HeldCheck.Provider(provider.value),
        HeldCheck.TargetType(targetType.value),
        HeldCheck.TargetId(targetId.value),
        HeldCheck.Found(found.value),
        qid.map(q => HeldCheck.Qid(q.value)),
        at
      )
      def same(c: HeldCheck) =
        c.provider == held.provider && c.targetType == held.targetType && c.targetId == held.targetId
      val _ = state.updateAndGet { s =>
        if s.checks.exists(same) then
          s.modify(_.checks)
            .using(
              _.map(c => if same(c) then held.modify(_.qid).setTo(held.qid.orElse(c.qid)) else c)
            )
        else s.modify(_.checks).using(_ :+ held)
      }
