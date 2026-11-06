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
      provider: com.histmersion.domain.media.imports.ImportService.TargetsProvider,
      place: Option[com.histmersion.domain.media.imports.ImportService.TargetsPlace],
      event: Option[com.histmersion.domain.media.imports.ImportService.TargetsEvent],
      limit: com.histmersion.domain.media.imports.ImportService.TargetsLimit,
      refresh: com.histmersion.domain.media.imports.ImportService.TargetsRefresh,
      since: Option[com.histmersion.domain.media.imports.ImportService.TargetsSince]
  ): Either[com.histmersion.domain.media.imports.ImportService.TargetsResultPart1, List[Target]]

  def pending(provider: com.histmersion.domain.media.imports.ImportService.PendingProvider, since: Option[com.histmersion.domain.media.imports.ImportService.PendingSince]): com.histmersion.domain.media.imports.ImportService.PendingResult

  /** The places on the map (`places`) or the events with an item a collection asks about. */
  def collectionTargets(
      check: com.histmersion.domain.media.imports.ImportService.CollectionTargetsCheck,
      places: com.histmersion.domain.media.imports.ImportService.CollectionTargetsPlaces,
      place: Option[com.histmersion.domain.media.imports.ImportService.CollectionTargetsPlace],
      event: Option[com.histmersion.domain.media.imports.ImportService.CollectionTargetsEvent],
      limit: com.histmersion.domain.media.imports.ImportService.CollectionTargetsLimit,
      refresh: com.histmersion.domain.media.imports.ImportService.CollectionTargetsRefresh,
      since: Option[com.histmersion.domain.media.imports.ImportService.CollectionTargetsSince],
      pleiadesOnly: com.histmersion.domain.media.imports.ImportService.CollectionTargetsPleiadesOnly
  ): Either[com.histmersion.domain.media.imports.ImportService.CollectionTargetsResultPart1, List[Target]]

  def collectionPending(check: com.histmersion.domain.media.imports.ImportService.CollectionPendingCheck, places: com.histmersion.domain.media.imports.ImportService.CollectionPendingPlaces, since: Option[com.histmersion.domain.media.imports.ImportService.CollectionPendingSince], pleiadesOnly: com.histmersion.domain.media.imports.ImportService.CollectionPendingPleiadesOnly): com.histmersion.domain.media.imports.ImportService.CollectionPendingResult

  def placeFacts(ids: List[com.histmersion.domain.media.imports.ImportService.PlaceFactsIds]): Map[com.histmersion.domain.media.imports.ImportService.PlaceFactsResultPart1, PlaceFacts]
  def mapPlacesNamed(keys: List[com.histmersion.domain.media.imports.ImportService.MapPlacesNamedKeys]): Map[com.histmersion.domain.media.imports.ImportService.MapPlacesNamedResultPart1, List[PlaceFacts]]
  def placeExternalIds(ids: List[com.histmersion.domain.media.imports.ImportService.PlaceExternalIdsIds]): Map[com.histmersion.domain.media.imports.ImportService.PlaceExternalIdsResultPart1, Set[(com.histmersion.domain.media.imports.ImportService.PlaceExternalIdsResultPart2Part1, com.histmersion.domain.media.imports.ImportService.PlaceExternalIdsResultPart2Part2)]]
  def pleiadesIds(ids: List[com.histmersion.domain.media.imports.ImportService.PleiadesIdsIds]): Map[com.histmersion.domain.media.imports.ImportService.PleiadesIdsResultPart1, List[com.histmersion.domain.media.imports.ImportService.PleiadesIdsResultPart2]]
  def mapPlacesWithIds(ids: List[(com.histmersion.domain.media.imports.ImportService.MapPlacesWithIdsIdsPart1, com.histmersion.domain.media.imports.ImportService.MapPlacesWithIdsIdsPart2)]): Map[(com.histmersion.domain.media.imports.ImportService.MapPlacesWithIdsResultPart1Part1, com.histmersion.domain.media.imports.ImportService.MapPlacesWithIdsResultPart1Part2), List[PlaceFacts]]

  /** A place's Commons category recorded as a link of the place; whether it is new. */
  def commonsCategory(placeId: com.histmersion.domain.catalogue.Place.Id, category: com.histmersion.domain.media.imports.ImportService.CommonsCategoryCategory, qid: com.histmersion.domain.media.imports.ImportService.CommonsCategoryQid): com.histmersion.domain.media.imports.ImportService.CommonsCategoryResult

  /** That `provider` was asked about a target, and how many pictures it gave. */
  def checked(provider: com.histmersion.domain.media.imports.ImportService.CheckedProvider, targetType: com.histmersion.domain.media.imports.ImportService.CheckedTargetType, targetId: com.histmersion.domain.media.imports.ImportService.CheckedTargetId, found: com.histmersion.domain.media.imports.ImportService.CheckedFound, qid: Option[com.histmersion.domain.media.imports.ImportService.CheckedQid]): Unit

object ImportService:
  type TargetsResultPart1 = TargetsResultPart1.T
  object TargetsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsProvider = TargetsProvider.T
  object TargetsProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsPlace = TargetsPlace.T
  object TargetsPlace extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsEvent = TargetsEvent.T
  object TargetsEvent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsLimit = TargetsLimit.T
  object TargetsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsRefresh = TargetsRefresh.T
  object TargetsRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type TargetsSince = TargetsSince.T
  object TargetsSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PendingResult = PendingResult.T
  object PendingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PendingProvider = PendingProvider.T
  object PendingProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PendingSince = PendingSince.T
  object PendingSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionTargetsResultPart1 = CollectionTargetsResultPart1.T
  object CollectionTargetsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionTargetsCheck = CollectionTargetsCheck.T
  object CollectionTargetsCheck extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionTargetsPlaces = CollectionTargetsPlaces.T
  object CollectionTargetsPlaces extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionTargetsPlace = CollectionTargetsPlace.T
  object CollectionTargetsPlace extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionTargetsEvent = CollectionTargetsEvent.T
  object CollectionTargetsEvent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionTargetsLimit = CollectionTargetsLimit.T
  object CollectionTargetsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionTargetsRefresh = CollectionTargetsRefresh.T
  object CollectionTargetsRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionTargetsSince = CollectionTargetsSince.T
  object CollectionTargetsSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionTargetsPleiadesOnly = CollectionTargetsPleiadesOnly.T
  object CollectionTargetsPleiadesOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionPendingResult = CollectionPendingResult.T
  object CollectionPendingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionPendingCheck = CollectionPendingCheck.T
  object CollectionPendingCheck extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionPendingPlaces = CollectionPendingPlaces.T
  object CollectionPendingPlaces extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionPendingSince = CollectionPendingSince.T
  object CollectionPendingSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CollectionPendingPleiadesOnly = CollectionPendingPleiadesOnly.T
  object CollectionPendingPleiadesOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceFactsResultPart1 = PlaceFactsResultPart1.T
  object PlaceFactsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceFactsIds = PlaceFactsIds.T
  object PlaceFactsIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapPlacesNamedResultPart1 = MapPlacesNamedResultPart1.T
  object MapPlacesNamedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapPlacesNamedKeys = MapPlacesNamedKeys.T
  object MapPlacesNamedKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceExternalIdsResultPart1 = PlaceExternalIdsResultPart1.T
  object PlaceExternalIdsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceExternalIdsResultPart2Part1 = PlaceExternalIdsResultPart2Part1.T
  object PlaceExternalIdsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceExternalIdsResultPart2Part2 = PlaceExternalIdsResultPart2Part2.T
  object PlaceExternalIdsResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PlaceExternalIdsIds = PlaceExternalIdsIds.T
  object PlaceExternalIdsIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PleiadesIdsResultPart1 = PleiadesIdsResultPart1.T
  object PleiadesIdsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PleiadesIdsResultPart2 = PleiadesIdsResultPart2.T
  object PleiadesIdsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PleiadesIdsIds = PleiadesIdsIds.T
  object PleiadesIdsIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapPlacesWithIdsResultPart1Part1 = MapPlacesWithIdsResultPart1Part1.T
  object MapPlacesWithIdsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapPlacesWithIdsResultPart1Part2 = MapPlacesWithIdsResultPart1Part2.T
  object MapPlacesWithIdsResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapPlacesWithIdsIdsPart1 = MapPlacesWithIdsIdsPart1.T
  object MapPlacesWithIdsIdsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapPlacesWithIdsIdsPart2 = MapPlacesWithIdsIdsPart2.T
  object MapPlacesWithIdsIdsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommonsCategoryResult = CommonsCategoryResult.T
  object CommonsCategoryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommonsCategoryCategory = CommonsCategoryCategory.T
  object CommonsCategoryCategory extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CommonsCategoryQid = CommonsCategoryQid.T
  object CommonsCategoryQid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckedProvider = CheckedProvider.T
  object CheckedProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckedTargetType = CheckedTargetType.T
  object CheckedTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckedTargetId = CheckedTargetId.T
  object CheckedTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckedFound = CheckedFound.T
  object CheckedFound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckedQid = CheckedQid.T
  object CheckedQid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(db: Transactor): ImportService = Implementation(db)

  final private class Implementation(db: Transactor) extends ImportService:
    def targets(
        provider: com.histmersion.domain.media.imports.ImportService.Implementation.TargetsProvider,
        place: Option[com.histmersion.domain.media.imports.ImportService.Implementation.TargetsPlace],
        event: Option[com.histmersion.domain.media.imports.ImportService.Implementation.TargetsEvent],
        limit: com.histmersion.domain.media.imports.ImportService.Implementation.TargetsLimit,
        refresh: com.histmersion.domain.media.imports.ImportService.Implementation.TargetsRefresh,
        since: Option[com.histmersion.domain.media.imports.ImportService.Implementation.TargetsSince]
    ): Either[com.histmersion.domain.media.imports.ImportService.Implementation.TargetsResultPart1, List[Target]] = db.read(ImportRepo.targets(provider, place, event, limit, refresh, since))
    def pending(provider: com.histmersion.domain.media.imports.ImportService.Implementation.PendingProvider, since: Option[com.histmersion.domain.media.imports.ImportService.Implementation.PendingSince]): com.histmersion.domain.media.imports.ImportService.Implementation.PendingResult = db.read(ImportRepo.pending(provider, since))
    def collectionTargets(
        check: com.histmersion.domain.media.imports.ImportService.Implementation.CollectionTargetsCheck,
        places: com.histmersion.domain.media.imports.ImportService.Implementation.CollectionTargetsPlaces,
        place: Option[com.histmersion.domain.media.imports.ImportService.Implementation.CollectionTargetsPlace],
        event: Option[com.histmersion.domain.media.imports.ImportService.Implementation.CollectionTargetsEvent],
        limit: com.histmersion.domain.media.imports.ImportService.Implementation.CollectionTargetsLimit,
        refresh: com.histmersion.domain.media.imports.ImportService.Implementation.CollectionTargetsRefresh,
        since: Option[com.histmersion.domain.media.imports.ImportService.Implementation.CollectionTargetsSince],
        pleiadesOnly: com.histmersion.domain.media.imports.ImportService.Implementation.CollectionTargetsPleiadesOnly
    ): Either[com.histmersion.domain.media.imports.ImportService.Implementation.CollectionTargetsResultPart1, List[Target]] =
      db.read(ImportRepo.collectionTargets(check, places, place, event, limit, refresh, since, pleiadesOnly))
    def collectionPending(check: com.histmersion.domain.media.imports.ImportService.Implementation.CollectionPendingCheck, places: com.histmersion.domain.media.imports.ImportService.Implementation.CollectionPendingPlaces, since: Option[com.histmersion.domain.media.imports.ImportService.Implementation.CollectionPendingSince], pleiadesOnly: com.histmersion.domain.media.imports.ImportService.Implementation.CollectionPendingPleiadesOnly): com.histmersion.domain.media.imports.ImportService.Implementation.CollectionPendingResult =
      db.read(ImportRepo.collectionPending(check, places, since, pleiadesOnly))
    def placeFacts(ids: List[com.histmersion.domain.media.imports.ImportService.Implementation.PlaceFactsIds]): Map[com.histmersion.domain.media.imports.ImportService.Implementation.PlaceFactsResultPart1, PlaceFacts] = db.read(ImportRepo.placeFacts(ids))
    def mapPlacesNamed(keys: List[com.histmersion.domain.media.imports.ImportService.Implementation.MapPlacesNamedKeys]): Map[com.histmersion.domain.media.imports.ImportService.Implementation.MapPlacesNamedResultPart1, List[PlaceFacts]] = db.read(ImportRepo.mapPlacesNamed(keys))
    def placeExternalIds(ids: List[com.histmersion.domain.media.imports.ImportService.Implementation.PlaceExternalIdsIds]): Map[com.histmersion.domain.media.imports.ImportService.Implementation.PlaceExternalIdsResultPart1, Set[(com.histmersion.domain.media.imports.ImportService.Implementation.PlaceExternalIdsResultPart2Part1, com.histmersion.domain.media.imports.ImportService.Implementation.PlaceExternalIdsResultPart2Part2)]] =
      db.read(ImportRepo.placeExternalIds(ids))
    def pleiadesIds(ids: List[com.histmersion.domain.media.imports.ImportService.Implementation.PleiadesIdsIds]): Map[com.histmersion.domain.media.imports.ImportService.Implementation.PleiadesIdsResultPart1, List[com.histmersion.domain.media.imports.ImportService.Implementation.PleiadesIdsResultPart2]] = db.read(ImportRepo.pleiadesIds(ids))
    def mapPlacesWithIds(ids: List[(com.histmersion.domain.media.imports.ImportService.Implementation.MapPlacesWithIdsIdsPart1, com.histmersion.domain.media.imports.ImportService.Implementation.MapPlacesWithIdsIdsPart2)]): Map[(com.histmersion.domain.media.imports.ImportService.Implementation.MapPlacesWithIdsResultPart1Part1, com.histmersion.domain.media.imports.ImportService.Implementation.MapPlacesWithIdsResultPart1Part2), List[PlaceFacts]] =
      db.read(ImportRepo.mapPlacesWithIds(ids))
    def commonsCategory(placeId: com.histmersion.domain.catalogue.Place.Id, category: com.histmersion.domain.media.imports.ImportService.Implementation.CommonsCategoryCategory, qid: com.histmersion.domain.media.imports.ImportService.Implementation.CommonsCategoryQid): com.histmersion.domain.media.imports.ImportService.Implementation.CommonsCategoryResult =
      db.transact(ImportRepo.commonsCategory(placeId, category, qid))
    def checked(provider: com.histmersion.domain.media.imports.ImportService.Implementation.CheckedProvider, targetType: com.histmersion.domain.media.imports.ImportService.Implementation.CheckedTargetType, targetId: com.histmersion.domain.media.imports.ImportService.Implementation.CheckedTargetId, found: com.histmersion.domain.media.imports.ImportService.Implementation.CheckedFound, qid: Option[com.histmersion.domain.media.imports.ImportService.Implementation.CheckedQid]): Unit =
      db.transact(ImportRepo.checked(provider, targetType, targetId, found, qid))

  object Implementation:
    type TargetsResultPart1 = TargetsResultPart1.T
    object TargetsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetsProvider = TargetsProvider.T
    object TargetsProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetsPlace = TargetsPlace.T
    object TargetsPlace extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetsEvent = TargetsEvent.T
    object TargetsEvent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetsLimit = TargetsLimit.T
    object TargetsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetsRefresh = TargetsRefresh.T
    object TargetsRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetsSince = TargetsSince.T
    object TargetsSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PendingResult = PendingResult.T
    object PendingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PendingProvider = PendingProvider.T
    object PendingProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PendingSince = PendingSince.T
    object PendingSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionTargetsResultPart1 = CollectionTargetsResultPart1.T
    object CollectionTargetsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionTargetsCheck = CollectionTargetsCheck.T
    object CollectionTargetsCheck extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionTargetsPlaces = CollectionTargetsPlaces.T
    object CollectionTargetsPlaces extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionTargetsPlace = CollectionTargetsPlace.T
    object CollectionTargetsPlace extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionTargetsEvent = CollectionTargetsEvent.T
    object CollectionTargetsEvent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionTargetsLimit = CollectionTargetsLimit.T
    object CollectionTargetsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionTargetsRefresh = CollectionTargetsRefresh.T
    object CollectionTargetsRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionTargetsSince = CollectionTargetsSince.T
    object CollectionTargetsSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionTargetsPleiadesOnly = CollectionTargetsPleiadesOnly.T
    object CollectionTargetsPleiadesOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionPendingResult = CollectionPendingResult.T
    object CollectionPendingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionPendingCheck = CollectionPendingCheck.T
    object CollectionPendingCheck extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionPendingPlaces = CollectionPendingPlaces.T
    object CollectionPendingPlaces extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionPendingSince = CollectionPendingSince.T
    object CollectionPendingSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionPendingPleiadesOnly = CollectionPendingPleiadesOnly.T
    object CollectionPendingPleiadesOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlaceFactsResultPart1 = PlaceFactsResultPart1.T
    object PlaceFactsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlaceFactsIds = PlaceFactsIds.T
    object PlaceFactsIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MapPlacesNamedResultPart1 = MapPlacesNamedResultPart1.T
    object MapPlacesNamedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MapPlacesNamedKeys = MapPlacesNamedKeys.T
    object MapPlacesNamedKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlaceExternalIdsResultPart1 = PlaceExternalIdsResultPart1.T
    object PlaceExternalIdsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlaceExternalIdsResultPart2Part1 = PlaceExternalIdsResultPart2Part1.T
    object PlaceExternalIdsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlaceExternalIdsResultPart2Part2 = PlaceExternalIdsResultPart2Part2.T
    object PlaceExternalIdsResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlaceExternalIdsIds = PlaceExternalIdsIds.T
    object PlaceExternalIdsIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PleiadesIdsResultPart1 = PleiadesIdsResultPart1.T
    object PleiadesIdsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PleiadesIdsResultPart2 = PleiadesIdsResultPart2.T
    object PleiadesIdsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PleiadesIdsIds = PleiadesIdsIds.T
    object PleiadesIdsIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MapPlacesWithIdsResultPart1Part1 = MapPlacesWithIdsResultPart1Part1.T
    object MapPlacesWithIdsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MapPlacesWithIdsResultPart1Part2 = MapPlacesWithIdsResultPart1Part2.T
    object MapPlacesWithIdsResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MapPlacesWithIdsIdsPart1 = MapPlacesWithIdsIdsPart1.T
    object MapPlacesWithIdsIdsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MapPlacesWithIdsIdsPart2 = MapPlacesWithIdsIdsPart2.T
    object MapPlacesWithIdsIdsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CommonsCategoryResult = CommonsCategoryResult.T
    object CommonsCategoryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CommonsCategoryCategory = CommonsCategoryCategory.T
    object CommonsCategoryCategory extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CommonsCategoryQid = CommonsCategoryQid.T
    object CommonsCategoryQid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CheckedProvider = CheckedProvider.T
    object CheckedProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CheckedTargetType = CheckedTargetType.T
    object CheckedTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CheckedTargetId = CheckedTargetId.T
    object CheckedTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CheckedFound = CheckedFound.T
    object CheckedFound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CheckedQid = CheckedQid.T
    object CheckedQid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Media's checks alone, in memory: no place, event or finding is held, so nothing is on the map to ask about — a
    * place or event named by id is not there, a batch is empty, no place has facts — and a Commons category has no
    * place to be recorded on. `clock` stamps a check as the database's `clock_timestamp()` does.
    */
  final class InMemory(state: AtomicReference[MediaState], clock: Clock) extends ImportService:
    private def one(place: Option[com.histmersion.domain.media.imports.ImportService.InMemory.OnePlace], event: Option[com.histmersion.domain.media.imports.ImportService.InMemory.OneEvent]): Option[com.histmersion.domain.media.imports.ImportService.InMemory.OneResult] =
      place
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(p => s"No place '$p'")
        .orElse(event.map(_.trim).filter(_.nonEmpty).map(e => s"No event '$e'"))

    def targets(
        provider: com.histmersion.domain.media.imports.ImportService.InMemory.TargetsProvider,
        place: Option[com.histmersion.domain.media.imports.ImportService.InMemory.TargetsPlace],
        event: Option[com.histmersion.domain.media.imports.ImportService.InMemory.TargetsEvent],
        limit: com.histmersion.domain.media.imports.ImportService.InMemory.TargetsLimit,
        refresh: com.histmersion.domain.media.imports.ImportService.InMemory.TargetsRefresh,
        since: Option[com.histmersion.domain.media.imports.ImportService.InMemory.TargetsSince]
    ): Either[com.histmersion.domain.media.imports.ImportService.InMemory.TargetsResultPart1, List[Target]] = one(place, event).toLeft(Nil)
    def pending(provider: com.histmersion.domain.media.imports.ImportService.InMemory.PendingProvider, since: Option[com.histmersion.domain.media.imports.ImportService.InMemory.PendingSince]): com.histmersion.domain.media.imports.ImportService.InMemory.PendingResult = 0
    def collectionTargets(
        check: com.histmersion.domain.media.imports.ImportService.InMemory.CollectionTargetsCheck,
        places: com.histmersion.domain.media.imports.ImportService.InMemory.CollectionTargetsPlaces,
        place: Option[com.histmersion.domain.media.imports.ImportService.InMemory.CollectionTargetsPlace],
        event: Option[com.histmersion.domain.media.imports.ImportService.InMemory.CollectionTargetsEvent],
        limit: com.histmersion.domain.media.imports.ImportService.InMemory.CollectionTargetsLimit,
        refresh: com.histmersion.domain.media.imports.ImportService.InMemory.CollectionTargetsRefresh,
        since: Option[com.histmersion.domain.media.imports.ImportService.InMemory.CollectionTargetsSince],
        pleiadesOnly: com.histmersion.domain.media.imports.ImportService.InMemory.CollectionTargetsPleiadesOnly
    ): Either[com.histmersion.domain.media.imports.ImportService.InMemory.CollectionTargetsResultPart1, List[Target]] =
      (place.map(_.trim).filter(_.nonEmpty), event.map(_.trim).filter(_.nonEmpty)) match
        case (Some(_), _) if !places   => Left("this import is about events: name one with --event")
        case (None, Some(_)) if places => Left("this import is about places: name one with --place")
        case _                         => one(place, event).toLeft(Nil)
    def collectionPending(check: com.histmersion.domain.media.imports.ImportService.InMemory.CollectionPendingCheck, places: com.histmersion.domain.media.imports.ImportService.InMemory.CollectionPendingPlaces, since: Option[com.histmersion.domain.media.imports.ImportService.InMemory.CollectionPendingSince], pleiadesOnly: com.histmersion.domain.media.imports.ImportService.InMemory.CollectionPendingPleiadesOnly): com.histmersion.domain.media.imports.ImportService.InMemory.CollectionPendingResult = 0
    def placeFacts(ids: List[com.histmersion.domain.media.imports.ImportService.InMemory.PlaceFactsIds]): Map[com.histmersion.domain.media.imports.ImportService.InMemory.PlaceFactsResultPart1, PlaceFacts] = Map.empty
    def mapPlacesNamed(keys: List[com.histmersion.domain.media.imports.ImportService.InMemory.MapPlacesNamedKeys]): Map[com.histmersion.domain.media.imports.ImportService.InMemory.MapPlacesNamedResultPart1, List[PlaceFacts]] = Map.empty
    def placeExternalIds(ids: List[com.histmersion.domain.media.imports.ImportService.InMemory.PlaceExternalIdsIds]): Map[com.histmersion.domain.media.imports.ImportService.InMemory.PlaceExternalIdsResultPart1, Set[(com.histmersion.domain.media.imports.ImportService.InMemory.PlaceExternalIdsResultPart2Part1, com.histmersion.domain.media.imports.ImportService.InMemory.PlaceExternalIdsResultPart2Part2)]] = Map.empty
    def pleiadesIds(ids: List[com.histmersion.domain.media.imports.ImportService.InMemory.PleiadesIdsIds]): Map[com.histmersion.domain.media.imports.ImportService.InMemory.PleiadesIdsResultPart1, List[com.histmersion.domain.media.imports.ImportService.InMemory.PleiadesIdsResultPart2]] = Map.empty
    def mapPlacesWithIds(ids: List[(com.histmersion.domain.media.imports.ImportService.InMemory.MapPlacesWithIdsIdsPart1, com.histmersion.domain.media.imports.ImportService.InMemory.MapPlacesWithIdsIdsPart2)]): Map[(com.histmersion.domain.media.imports.ImportService.InMemory.MapPlacesWithIdsResultPart1Part1, com.histmersion.domain.media.imports.ImportService.InMemory.MapPlacesWithIdsResultPart1Part2), List[PlaceFacts]] = Map.empty
    def commonsCategory(placeId: com.histmersion.domain.catalogue.Place.Id, category: com.histmersion.domain.media.imports.ImportService.InMemory.CommonsCategoryCategory, qid: com.histmersion.domain.media.imports.ImportService.InMemory.CommonsCategoryQid): com.histmersion.domain.media.imports.ImportService.InMemory.CommonsCategoryResult = false

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
    type OneResult = OneResult.T
    object OneResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OnePlace = OnePlace.T
    object OnePlace extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type OneEvent = OneEvent.T
    object OneEvent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetsResultPart1 = TargetsResultPart1.T
    object TargetsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetsProvider = TargetsProvider.T
    object TargetsProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetsPlace = TargetsPlace.T
    object TargetsPlace extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetsEvent = TargetsEvent.T
    object TargetsEvent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetsLimit = TargetsLimit.T
    object TargetsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetsRefresh = TargetsRefresh.T
    object TargetsRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TargetsSince = TargetsSince.T
    object TargetsSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PendingResult = PendingResult.T
    object PendingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PendingProvider = PendingProvider.T
    object PendingProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PendingSince = PendingSince.T
    object PendingSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionTargetsResultPart1 = CollectionTargetsResultPart1.T
    object CollectionTargetsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionTargetsCheck = CollectionTargetsCheck.T
    object CollectionTargetsCheck extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionTargetsPlaces = CollectionTargetsPlaces.T
    object CollectionTargetsPlaces extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionTargetsPlace = CollectionTargetsPlace.T
    object CollectionTargetsPlace extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionTargetsEvent = CollectionTargetsEvent.T
    object CollectionTargetsEvent extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionTargetsLimit = CollectionTargetsLimit.T
    object CollectionTargetsLimit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionTargetsRefresh = CollectionTargetsRefresh.T
    object CollectionTargetsRefresh extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionTargetsSince = CollectionTargetsSince.T
    object CollectionTargetsSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionTargetsPleiadesOnly = CollectionTargetsPleiadesOnly.T
    object CollectionTargetsPleiadesOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionPendingResult = CollectionPendingResult.T
    object CollectionPendingResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionPendingCheck = CollectionPendingCheck.T
    object CollectionPendingCheck extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionPendingPlaces = CollectionPendingPlaces.T
    object CollectionPendingPlaces extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionPendingSince = CollectionPendingSince.T
    object CollectionPendingSince extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CollectionPendingPleiadesOnly = CollectionPendingPleiadesOnly.T
    object CollectionPendingPleiadesOnly extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlaceFactsResultPart1 = PlaceFactsResultPart1.T
    object PlaceFactsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlaceFactsIds = PlaceFactsIds.T
    object PlaceFactsIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MapPlacesNamedResultPart1 = MapPlacesNamedResultPart1.T
    object MapPlacesNamedResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MapPlacesNamedKeys = MapPlacesNamedKeys.T
    object MapPlacesNamedKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlaceExternalIdsResultPart1 = PlaceExternalIdsResultPart1.T
    object PlaceExternalIdsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlaceExternalIdsResultPart2Part1 = PlaceExternalIdsResultPart2Part1.T
    object PlaceExternalIdsResultPart2Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlaceExternalIdsResultPart2Part2 = PlaceExternalIdsResultPart2Part2.T
    object PlaceExternalIdsResultPart2Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PlaceExternalIdsIds = PlaceExternalIdsIds.T
    object PlaceExternalIdsIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PleiadesIdsResultPart1 = PleiadesIdsResultPart1.T
    object PleiadesIdsResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PleiadesIdsResultPart2 = PleiadesIdsResultPart2.T
    object PleiadesIdsResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type PleiadesIdsIds = PleiadesIdsIds.T
    object PleiadesIdsIds extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MapPlacesWithIdsResultPart1Part1 = MapPlacesWithIdsResultPart1Part1.T
    object MapPlacesWithIdsResultPart1Part1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MapPlacesWithIdsResultPart1Part2 = MapPlacesWithIdsResultPart1Part2.T
    object MapPlacesWithIdsResultPart1Part2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MapPlacesWithIdsIdsPart1 = MapPlacesWithIdsIdsPart1.T
    object MapPlacesWithIdsIdsPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type MapPlacesWithIdsIdsPart2 = MapPlacesWithIdsIdsPart2.T
    object MapPlacesWithIdsIdsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CommonsCategoryResult = CommonsCategoryResult.T
    object CommonsCategoryResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CommonsCategoryCategory = CommonsCategoryCategory.T
    object CommonsCategoryCategory extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CommonsCategoryQid = CommonsCategoryQid.T
    object CommonsCategoryQid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CheckedProvider = CheckedProvider.T
    object CheckedProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CheckedTargetType = CheckedTargetType.T
    object CheckedTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CheckedTargetId = CheckedTargetId.T
    object CheckedTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CheckedFound = CheckedFound.T
    object CheckedFound extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type CheckedQid = CheckedQid.T
    object CheckedQid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
