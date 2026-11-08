package com.histmersion
package domain.media.imports

import com.histmersion.domain.media.MediaState
import com.histmersion.domain.media.assets.MediaItemRow
import com.histmersion.util.database.Transactor

import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant}
import java.util.concurrent.atomic.AtomicReference
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.catalogue.places.ExternalIds
import com.histmersion.domain.catalogue.places.Wikidata
import io.github.iltotore.iron.constraint.any.Pure

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
  ): Either[ImportService.TargetsText, List[Target]]

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
  ): Either[ImportService.TargetsText, List[Target]]

  def collectionPending(check: ImportService.CheckName, places: ImportService.Places, since: Option[ImportService.Since], pleiadesOnly: ImportService.PleiadesOnly): ImportService.PendingCount

  def placeFacts(ids: List[Place.Id]): Map[Place.Id, PlaceFacts]
  def mapPlacesNamed(keys: List[ImportService.PlaceKey]): Map[ImportService.PlaceKey, List[PlaceFacts]]
  def placeExternalIds(ids: List[Place.Id]): Map[Place.Id, Set[(ExternalIds.Scheme, ImportService.ExternalId)]]
  def pleiadesIds(ids: List[Place.Id]): Map[Place.Id, List[ImportService.ExternalId]]
  def mapPlacesWithIds(ids: List[(ExternalIds.Scheme, ImportService.ExternalId)]): Map[(ExternalIds.Scheme, ImportService.ExternalId), List[PlaceFacts]]

  /** A place's Commons category recorded as a link of the place; whether it is new. */
  def commonsCategory(placeId: Place.Id, category: ImportService.CommonsCategory, qid: Wikidata.Id): Boolean

  /** That `provider` was asked about a target, and how many pictures it gave. */
  def checked(provider: ImportService.Provider, targetType: ImportService.TargetType, targetId: ImportService.TargetId, found: ImportService.FoundCount, qid: Option[Wikidata.Id]): Unit

object ImportService:
  type ExternalId = ExternalIds.ExternalId
  val ExternalId: ExternalIds.ExternalId.type = ExternalIds.ExternalId
  type FoundCount = RowCount
  val FoundCount: RowCount.type = RowCount
  type PendingCount = RowCount
  val PendingCount: RowCount.type = RowCount
  type Provider = MediaItemRow.Provider
  val Provider: MediaItemRow.Provider.type = MediaItemRow.Provider
  type TargetId = Target.Id
  val TargetId: Target.Id.type = Target.Id
  type TargetType = Target.TargetType
  val TargetType: Target.TargetType.type = Target.TargetType
  type CheckName = CheckName.T
  object CheckName extends Newtype[String, Pure]
  type CommonsCategory = CommonsCategory.T
  object CommonsCategory extends Newtype[String, Pure]
  type PlaceKey = PlaceKey.T
  object PlaceKey extends Newtype[String, Pure]
  type Places = Places.T
  object Places extends Newtype[Boolean, Pure]
  type PleiadesOnly = PleiadesOnly.T
  object PleiadesOnly extends Newtype[Boolean, Pure]
  type Refresh = Refresh.T
  object Refresh extends Newtype[Boolean, Pure]
  type Since = Since.T
  object Since extends Newtype[Long, Pure]
  type TargetsText = TargetsText.T
  object TargetsText extends Newtype[String, Pure]
  type TargetsLimit = TargetsLimit.T
  object TargetsLimit extends Newtype[Int, Pure]
  type CollectionTargetsLimit = CollectionTargetsLimit.T
  object CollectionTargetsLimit extends Newtype[Int, Pure]

  def apply(db: Transactor): ImportService = Implementation(db)

  final private class Implementation(db: Transactor) extends ImportService:
    def targets(
        provider: Provider,
        place: Option[Place.Id],
        event: Option[Event.Id],
        limit: TargetsLimit,
        refresh: Refresh,
        since: Option[Since]
    ): Either[TargetsText, List[Target]] = db.read(ImportRepo.targets(provider, place, event, ImportRepo.Limit(limit.value), ImportRepo.Refresh(refresh.value), since.map(s => ImportRepo.Since(s.value)))).left.map(p => TargetsText(p.value))
    def pending(provider: Provider, since: Option[Since]): PendingCount = db.read(ImportRepo.pending(provider, since.map(s => ImportRepo.Since(s.value))))
    def collectionTargets(
        check: CheckName,
        places: Places,
        place: Option[Place.Id],
        event: Option[Event.Id],
        limit: CollectionTargetsLimit,
        refresh: Refresh,
        since: Option[Since],
        pleiadesOnly: PleiadesOnly
    ): Either[TargetsText, List[Target]] =
      db.read(ImportRepo.collectionTargets(MediaItemRow.Provider(check.value), ImportRepo.Places(places.value), place, event, ImportRepo.Limit(limit.value), ImportRepo.Refresh(refresh.value), since.map(s => ImportRepo.Since(s.value)), ImportRepo.PleiadesOnly(pleiadesOnly.value))).left.map(p => TargetsText(p.value))
    def collectionPending(check: CheckName, places: Places, since: Option[Since], pleiadesOnly: PleiadesOnly): PendingCount =
      db.read(ImportRepo.collectionPending(MediaItemRow.Provider(check.value), ImportRepo.Places(places.value), since.map(s => ImportRepo.Since(s.value)), ImportRepo.PleiadesOnly(pleiadesOnly.value)))
    def placeFacts(ids: List[Place.Id]): Map[Place.Id, PlaceFacts] = db.read(ImportRepo.placeFacts(ids))
    def mapPlacesNamed(keys: List[PlaceKey]): Map[PlaceKey, List[PlaceFacts]] = db.read(ImportRepo.mapPlacesNamed(keys.map(k => ImportRepo.NameKey(k.value)))).map((k, v) => PlaceKey(k.value) -> v)
    def placeExternalIds(ids: List[Place.Id]): Map[Place.Id, Set[(ExternalIds.Scheme, ExternalId)]] =
      db.read(ImportRepo.placeExternalIds(ids))
    def pleiadesIds(ids: List[Place.Id]): Map[Place.Id, List[ExternalId]] = db.read(ImportRepo.pleiadesIds(ids))
    def mapPlacesWithIds(ids: List[(ExternalIds.Scheme, ExternalId)]): Map[(ExternalIds.Scheme, ExternalId), List[PlaceFacts]] =
      db.read(ImportRepo.mapPlacesWithIds(ids))
    def commonsCategory(placeId: Place.Id, category: CommonsCategory, qid: Wikidata.Id): Boolean =
      db.transact(ImportRepo.commonsCategory(placeId, ImportRepo.Category(category.value), Target.Qid(qid.value)))
    def checked(provider: Provider, targetType: TargetType, targetId: TargetId, found: FoundCount, qid: Option[Wikidata.Id]): Unit =
      db.transact(ImportRepo.checked(provider, targetType, targetId, found, qid.map(q => Target.Qid(q.value))))

  /** Media's checks alone, in memory: no place, event or finding is held, so nothing is on the map to ask about — a
    * place or event named by id is not there, a batch is empty, no place has facts — and a Commons category has no
    * place to be recorded on. `clock` stamps a check as the database's `clock_timestamp()` does.
    */
  final class InMemory(state: AtomicReference[MediaState], clock: Clock) extends ImportService:
    private def one(place: Option[Place.Id], event: Option[Event.Id]): Option[TargetsText] =
      place
        .map(_.value.trim)
        .filter(_.nonEmpty)
        .map(p => TargetsText(s"No place '$p'"))
        .orElse(event.map(_.value.trim).filter(_.nonEmpty).map(e => TargetsText(s"No event '$e'")))

    def targets(
        provider: Provider,
        place: Option[Place.Id],
        event: Option[Event.Id],
        limit: TargetsLimit,
        refresh: Refresh,
        since: Option[Since]
    ): Either[TargetsText, List[Target]] = one(place, event).toLeft(Nil)
    def pending(provider: Provider, since: Option[Since]): PendingCount = PendingCount(0)
    def collectionTargets(
        check: CheckName,
        places: Places,
        place: Option[Place.Id],
        event: Option[Event.Id],
        limit: CollectionTargetsLimit,
        refresh: Refresh,
        since: Option[Since],
        pleiadesOnly: PleiadesOnly
    ): Either[TargetsText, List[Target]] =
      (place.map(_.value.trim).filter(_.nonEmpty), event.map(_.value.trim).filter(_.nonEmpty)) match
        case (Some(_), _) if !places.value   => Left(TargetsText("this import is about events: name one with --event"))
        case (None, Some(_)) if places.value => Left(TargetsText("this import is about places: name one with --place"))
        case _                         => one(place, event).toLeft(Nil)
    def collectionPending(check: CheckName, places: Places, since: Option[Since], pleiadesOnly: PleiadesOnly): PendingCount = PendingCount(0)
    def placeFacts(ids: List[Place.Id]): Map[Place.Id, PlaceFacts] = Map.empty
    def mapPlacesNamed(keys: List[PlaceKey]): Map[PlaceKey, List[PlaceFacts]] = Map.empty
    def placeExternalIds(ids: List[Place.Id]): Map[Place.Id, Set[(ExternalIds.Scheme, ExternalId)]] = Map.empty
    def pleiadesIds(ids: List[Place.Id]): Map[Place.Id, List[ExternalId]] = Map.empty
    def mapPlacesWithIds(ids: List[(ExternalIds.Scheme, ExternalId)]): Map[(ExternalIds.Scheme, ExternalId), List[PlaceFacts]] = Map.empty
    def commonsCategory(placeId: Place.Id, category: CommonsCategory, qid: Wikidata.Id): Boolean = false

    def checked(provider: Provider, targetType: TargetType, targetId: TargetId, found: FoundCount, qid: Option[Wikidata.Id]): Unit =
      val at = Instant.now(clock)
      val held = HeldCheck(HeldCheck.Provider(provider.value), HeldCheck.TargetType(targetType.value), HeldCheck.TargetId(targetId.value), HeldCheck.Found(found.value), qid.map(q => HeldCheck.Qid(q.value)), at)
      def same(c: HeldCheck) = c.provider == held.provider && c.targetType == held.targetType && c.targetId == held.targetId
      val _ = state.updateAndGet { s =>
        if s.checks.exists(same) then
          s.modify(_.checks)
            .using(
              _.map(c => if same(c) then held.modify(_.qid).setTo(held.qid.orElse(c.qid)) else c)
            )
        else s.modify(_.checks).using(_ :+ held)
      }
