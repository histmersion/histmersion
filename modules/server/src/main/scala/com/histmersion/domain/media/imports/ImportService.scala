package com.histmersion.domain.media.imports

import com.histmersion.domain.media.MediaState
import com.histmersion.util.database.Transactor

import hearth.kindlings.optics.syntax.*

import java.time.{Clock, Instant}
import java.util.concurrent.atomic.AtomicReference

/** What the media imports ask about and what they recorded asking (#1446): the places on the map and the events a
  * provider has not been asked about, the facts a collection's match reads of a place, and the checks — that a provider
  * was asked, and how many pictures it gave.
  */
trait ImportService:

  /** The places (one per Wikidata item) and events a provider asks about: one by id, or those not asked yet. */
  def targets(
      provider: com.histmersion.domain.media.imports.ImportService.Provider,
      place: Option[com.histmersion.domain.catalogue.Place.Id],
      event: Option[com.histmersion.domain.catalogue.Event.Id],
      limit: com.histmersion.domain.media.imports.ImportService.TargetsLimit,
      refresh: com.histmersion.domain.media.imports.ImportService.Refresh,
      since: Option[com.histmersion.domain.media.imports.ImportService.Since]
  ): Either[com.histmersion.domain.media.imports.ImportService.TargetsText, List[Target]]

  def pending(provider: com.histmersion.domain.media.imports.ImportService.Provider, since: Option[com.histmersion.domain.media.imports.ImportService.Since]): com.histmersion.domain.media.imports.ImportService.PendingCount

  /** The places on the map (`places`) or the events with an item a collection asks about. */
  def collectionTargets(
      check: com.histmersion.domain.media.imports.ImportService.CheckName,
      places: com.histmersion.domain.media.imports.ImportService.Places,
      place: Option[com.histmersion.domain.catalogue.Place.Id],
      event: Option[com.histmersion.domain.catalogue.Event.Id],
      limit: com.histmersion.domain.media.imports.ImportService.CollectionTargetsLimit,
      refresh: com.histmersion.domain.media.imports.ImportService.Refresh,
      since: Option[com.histmersion.domain.media.imports.ImportService.Since],
      pleiadesOnly: com.histmersion.domain.media.imports.ImportService.PleiadesOnly
  ): Either[com.histmersion.domain.media.imports.ImportService.TargetsText, List[Target]]

  def collectionPending(check: com.histmersion.domain.media.imports.ImportService.CheckName, places: com.histmersion.domain.media.imports.ImportService.Places, since: Option[com.histmersion.domain.media.imports.ImportService.Since], pleiadesOnly: com.histmersion.domain.media.imports.ImportService.PleiadesOnly): com.histmersion.domain.media.imports.ImportService.PendingCount

  def placeFacts(ids: List[com.histmersion.domain.catalogue.Place.Id]): Map[com.histmersion.domain.catalogue.Place.Id, PlaceFacts]
  def mapPlacesNamed(keys: List[com.histmersion.domain.media.imports.ImportService.PlaceKey]): Map[com.histmersion.domain.media.imports.ImportService.PlaceKey, List[PlaceFacts]]
  def placeExternalIds(ids: List[com.histmersion.domain.catalogue.Place.Id]): Map[com.histmersion.domain.catalogue.Place.Id, Set[(com.histmersion.domain.media.imports.ImportService.Provider, com.histmersion.domain.media.imports.ImportService.ExternalId)]]
  def pleiadesIds(ids: List[com.histmersion.domain.media.imports.ImportService.ExternalId]): Map[com.histmersion.domain.catalogue.Place.Id, List[com.histmersion.domain.media.imports.ImportService.ExternalId]]
  def mapPlacesWithIds(ids: List[(com.histmersion.domain.catalogue.Place.Id, com.histmersion.domain.media.imports.ImportService.ExternalId)]): Map[(com.histmersion.domain.catalogue.Place.Id, com.histmersion.domain.media.imports.ImportService.ExternalId), List[PlaceFacts]]

  /** A place's Commons category recorded as a link of the place; whether it is new. */
  def commonsCategory(placeId: com.histmersion.domain.catalogue.Place.Id, category: com.histmersion.domain.media.imports.ImportService.CommonsCategory, qid: com.histmersion.domain.catalogue.places.Wikidata.Id): Boolean

  /** That `provider` was asked about a target, and how many pictures it gave. */
  def checked(provider: com.histmersion.domain.media.imports.ImportService.Provider, targetType: com.histmersion.domain.media.imports.ImportService.TargetType, targetId: com.histmersion.domain.media.imports.ImportService.TargetId, found: com.histmersion.domain.media.imports.ImportService.FoundCount, qid: Option[com.histmersion.domain.catalogue.places.Wikidata.Id]): Unit

object ImportService:
  type CheckName = CheckName.T
  object CheckName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommonsCategory = CommonsCategory.T
  object CommonsCategory extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExternalId = ExternalId.T
  object ExternalId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FoundCount = FoundCount.T
  object FoundCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OneText = OneText.T
  object OneText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PendingCount = PendingCount.T
  object PendingCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceKey = PlaceKey.T
  object PlaceKey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Places = Places.T
  object Places extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PleiadesOnly = PleiadesOnly.T
  object PleiadesOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Provider = Provider.T
  object Provider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Refresh = Refresh.T
  object Refresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Since = Since.T
  object Since extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetId = TargetId.T
  object TargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetType = TargetType.T
  object TargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsText = TargetsText.T
  object TargetsText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsLimit = TargetsLimit.T
  object TargetsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionTargetsLimit = CollectionTargetsLimit.T
  object CollectionTargetsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): ImportService = Implementation(db)

  final private class Implementation(db: Transactor) extends ImportService:
    def targets(
        provider: com.histmersion.domain.media.imports.ImportService.Provider,
        place: Option[com.histmersion.domain.catalogue.Place.Id],
        event: Option[com.histmersion.domain.catalogue.Event.Id],
        limit: com.histmersion.domain.media.imports.ImportService.Implementation.TargetsLimit,
        refresh: com.histmersion.domain.media.imports.ImportService.Refresh,
        since: Option[com.histmersion.domain.media.imports.ImportService.Since]
    ): Either[com.histmersion.domain.media.imports.ImportService.TargetsText, List[Target]] = db.read(ImportRepo.targets(provider, place, event, limit, refresh, since))
    def pending(provider: com.histmersion.domain.media.imports.ImportService.Provider, since: Option[com.histmersion.domain.media.imports.ImportService.Since]): com.histmersion.domain.media.imports.ImportService.PendingCount = db.read(ImportRepo.pending(provider, since))
    def collectionTargets(
        check: com.histmersion.domain.media.imports.ImportService.CheckName,
        places: com.histmersion.domain.media.imports.ImportService.Places,
        place: Option[com.histmersion.domain.catalogue.Place.Id],
        event: Option[com.histmersion.domain.catalogue.Event.Id],
        limit: com.histmersion.domain.media.imports.ImportService.Implementation.CollectionTargetsLimit,
        refresh: com.histmersion.domain.media.imports.ImportService.Refresh,
        since: Option[com.histmersion.domain.media.imports.ImportService.Since],
        pleiadesOnly: com.histmersion.domain.media.imports.ImportService.PleiadesOnly
    ): Either[com.histmersion.domain.media.imports.ImportService.TargetsText, List[Target]] =
      db.read(ImportRepo.collectionTargets(check, places, place, event, limit, refresh, since, pleiadesOnly))
    def collectionPending(check: com.histmersion.domain.media.imports.ImportService.CheckName, places: com.histmersion.domain.media.imports.ImportService.Places, since: Option[com.histmersion.domain.media.imports.ImportService.Since], pleiadesOnly: com.histmersion.domain.media.imports.ImportService.PleiadesOnly): com.histmersion.domain.media.imports.ImportService.PendingCount =
      db.read(ImportRepo.collectionPending(check, places, since, pleiadesOnly))
    def placeFacts(ids: List[com.histmersion.domain.catalogue.Place.Id]): Map[com.histmersion.domain.catalogue.Place.Id, PlaceFacts] = db.read(ImportRepo.placeFacts(ids))
    def mapPlacesNamed(keys: List[com.histmersion.domain.media.imports.ImportService.PlaceKey]): Map[com.histmersion.domain.media.imports.ImportService.PlaceKey, List[PlaceFacts]] = db.read(ImportRepo.mapPlacesNamed(keys))
    def placeExternalIds(ids: List[com.histmersion.domain.catalogue.Place.Id]): Map[com.histmersion.domain.catalogue.Place.Id, Set[(com.histmersion.domain.media.imports.ImportService.Provider, com.histmersion.domain.media.imports.ImportService.ExternalId)]] =
      db.read(ImportRepo.placeExternalIds(ids))
    def pleiadesIds(ids: List[com.histmersion.domain.media.imports.ImportService.ExternalId]): Map[com.histmersion.domain.catalogue.Place.Id, List[com.histmersion.domain.media.imports.ImportService.ExternalId]] = db.read(ImportRepo.pleiadesIds(ids))
    def mapPlacesWithIds(ids: List[(com.histmersion.domain.catalogue.Place.Id, com.histmersion.domain.media.imports.ImportService.ExternalId)]): Map[(com.histmersion.domain.catalogue.Place.Id, com.histmersion.domain.media.imports.ImportService.ExternalId), List[PlaceFacts]] =
      db.read(ImportRepo.mapPlacesWithIds(ids))
    def commonsCategory(placeId: com.histmersion.domain.catalogue.Place.Id, category: com.histmersion.domain.media.imports.ImportService.CommonsCategory, qid: com.histmersion.domain.catalogue.places.Wikidata.Id): Boolean =
      db.transact(ImportRepo.commonsCategory(placeId, category, qid))
    def checked(provider: com.histmersion.domain.media.imports.ImportService.Provider, targetType: com.histmersion.domain.media.imports.ImportService.TargetType, targetId: com.histmersion.domain.media.imports.ImportService.TargetId, found: com.histmersion.domain.media.imports.ImportService.FoundCount, qid: Option[com.histmersion.domain.catalogue.places.Wikidata.Id]): Unit =
      db.transact(ImportRepo.checked(provider, targetType, targetId, found, qid))

  object Implementation:
    type TargetsLimit = TargetsLimit.T
    object TargetsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionTargetsLimit = CollectionTargetsLimit.T
    object CollectionTargetsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Media's checks alone, in memory: no place, event or finding is held, so nothing is on the map to ask about — a
    * place or event named by id is not there, a batch is empty, no place has facts — and a Commons category has no
    * place to be recorded on. `clock` stamps a check as the database's `clock_timestamp()` does.
    */
  final class InMemory(state: AtomicReference[MediaState], clock: Clock) extends ImportService:
    private def one(place: Option[com.histmersion.domain.catalogue.Place.Id], event: Option[com.histmersion.domain.catalogue.Event.Id]): Option[com.histmersion.domain.media.imports.ImportService.OneText] =
      place
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(p => s"No place '$p'")
        .orElse(event.map(_.trim).filter(_.nonEmpty).map(e => s"No event '$e'"))

    def targets(
        provider: com.histmersion.domain.media.imports.ImportService.Provider,
        place: Option[com.histmersion.domain.catalogue.Place.Id],
        event: Option[com.histmersion.domain.catalogue.Event.Id],
        limit: com.histmersion.domain.media.imports.ImportService.InMemory.TargetsLimit,
        refresh: com.histmersion.domain.media.imports.ImportService.Refresh,
        since: Option[com.histmersion.domain.media.imports.ImportService.Since]
    ): Either[com.histmersion.domain.media.imports.ImportService.TargetsText, List[Target]] = one(place, event).toLeft(Nil)
    def pending(provider: com.histmersion.domain.media.imports.ImportService.Provider, since: Option[com.histmersion.domain.media.imports.ImportService.Since]): com.histmersion.domain.media.imports.ImportService.PendingCount = com.histmersion.domain.media.imports.ImportService.PendingCount(0)
    def collectionTargets(
        check: com.histmersion.domain.media.imports.ImportService.CheckName,
        places: com.histmersion.domain.media.imports.ImportService.Places,
        place: Option[com.histmersion.domain.catalogue.Place.Id],
        event: Option[com.histmersion.domain.catalogue.Event.Id],
        limit: com.histmersion.domain.media.imports.ImportService.InMemory.CollectionTargetsLimit,
        refresh: com.histmersion.domain.media.imports.ImportService.Refresh,
        since: Option[com.histmersion.domain.media.imports.ImportService.Since],
        pleiadesOnly: com.histmersion.domain.media.imports.ImportService.PleiadesOnly
    ): Either[com.histmersion.domain.media.imports.ImportService.TargetsText, List[Target]] =
      (place.map(_.trim).filter(_.nonEmpty), event.map(_.trim).filter(_.nonEmpty)) match
        case (Some(_), _) if !places   => Left(com.histmersion.domain.media.imports.ImportService.TargetsText("this import is about events: name one with --event"))
        case (None, Some(_)) if places => Left(com.histmersion.domain.media.imports.ImportService.TargetsText("this import is about places: name one with --place"))
        case _                         => one(place, event).toLeft(Nil)
    def collectionPending(check: com.histmersion.domain.media.imports.ImportService.CheckName, places: com.histmersion.domain.media.imports.ImportService.Places, since: Option[com.histmersion.domain.media.imports.ImportService.Since], pleiadesOnly: com.histmersion.domain.media.imports.ImportService.PleiadesOnly): com.histmersion.domain.media.imports.ImportService.PendingCount = com.histmersion.domain.media.imports.ImportService.PendingCount(0)
    def placeFacts(ids: List[com.histmersion.domain.catalogue.Place.Id]): Map[com.histmersion.domain.catalogue.Place.Id, PlaceFacts] = Map.empty
    def mapPlacesNamed(keys: List[com.histmersion.domain.media.imports.ImportService.PlaceKey]): Map[com.histmersion.domain.media.imports.ImportService.PlaceKey, List[PlaceFacts]] = Map.empty
    def placeExternalIds(ids: List[com.histmersion.domain.catalogue.Place.Id]): Map[com.histmersion.domain.catalogue.Place.Id, Set[(com.histmersion.domain.media.imports.ImportService.Provider, com.histmersion.domain.media.imports.ImportService.ExternalId)]] = Map.empty
    def pleiadesIds(ids: List[com.histmersion.domain.media.imports.ImportService.ExternalId]): Map[com.histmersion.domain.catalogue.Place.Id, List[com.histmersion.domain.media.imports.ImportService.ExternalId]] = Map.empty
    def mapPlacesWithIds(ids: List[(com.histmersion.domain.catalogue.Place.Id, com.histmersion.domain.media.imports.ImportService.ExternalId)]): Map[(com.histmersion.domain.catalogue.Place.Id, com.histmersion.domain.media.imports.ImportService.ExternalId), List[PlaceFacts]] = Map.empty
    def commonsCategory(placeId: com.histmersion.domain.catalogue.Place.Id, category: com.histmersion.domain.media.imports.ImportService.CommonsCategory, qid: com.histmersion.domain.catalogue.places.Wikidata.Id): Boolean = false

    def checked(provider: com.histmersion.domain.media.imports.ImportService.InMemory.CheckedProvider, targetType: com.histmersion.domain.media.imports.ImportService.InMemory.CheckedTargetType, targetId: com.histmersion.domain.media.imports.ImportService.InMemory.CheckedTargetId, found: com.histmersion.domain.media.imports.ImportService.InMemory.CheckedFound, qid: Option[com.histmersion.domain.media.imports.ImportService.InMemory.CheckedQid]): Unit =
      val at = Instant.now(clock)
      def same(c: HeldCheck) = c.provider == provider && c.targetType == targetType && c.targetId == targetId
      val _ = state.updateAndGet { s =>
        if s.checks.exists(same) then
          s.modify(_.checks)
            .using(
              _.map(c => if same(c) then HeldCheck(provider, targetType, targetId, found, qid.orElse(c.qid), at) else c)
            )
        else s.modify(_.checks).using(_ :+ HeldCheck(provider, targetType, targetId, found, qid, at))
      }

  object InMemory:
    type TargetsLimit = TargetsLimit.T
    object TargetsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionTargetsLimit = CollectionTargetsLimit.T
    object CollectionTargetsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CheckedProvider = _root_.com.histmersion.domain.media.imports.HeldCheck.Provider
    val CheckedProvider: _root_.com.histmersion.domain.media.imports.HeldCheck.Provider.type = _root_.com.histmersion.domain.media.imports.HeldCheck.Provider
    type CheckedTargetType = _root_.com.histmersion.domain.media.imports.HeldCheck.TargetType
    val CheckedTargetType: _root_.com.histmersion.domain.media.imports.HeldCheck.TargetType.type = _root_.com.histmersion.domain.media.imports.HeldCheck.TargetType
    type CheckedTargetId = _root_.com.histmersion.domain.media.imports.HeldCheck.TargetId
    val CheckedTargetId: _root_.com.histmersion.domain.media.imports.HeldCheck.TargetId.type = _root_.com.histmersion.domain.media.imports.HeldCheck.TargetId
    type CheckedFound = _root_.com.histmersion.domain.media.imports.HeldCheck.Found
    val CheckedFound: _root_.com.histmersion.domain.media.imports.HeldCheck.Found.type = _root_.com.histmersion.domain.media.imports.HeldCheck.Found
    type CheckedQid = _root_.com.histmersion.domain.media.imports.HeldCheck.Qid
    val CheckedQid: _root_.com.histmersion.domain.media.imports.HeldCheck.Qid.type = _root_.com.histmersion.domain.media.imports.HeldCheck.Qid
