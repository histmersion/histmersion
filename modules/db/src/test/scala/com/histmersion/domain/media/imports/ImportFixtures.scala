package com.histmersion.domain.media.imports

import com.augustnagro.magnum.*
import com.histmersion.domain.media.assets.MediaItemRow
import javax.sql.DataSource

/** The imports' repository as the db and server suites reach it from outside the feature. */
object ImportFixtures:
  type Limit = ImportRepo.Limit
  val Limit: ImportRepo.Limit.type = ImportRepo.Limit
  type Refresh = ImportRepo.Refresh
  val Refresh: ImportRepo.Refresh.type = ImportRepo.Refresh
  type Since = ImportRepo.Since
  val Since: ImportRepo.Since.type = ImportRepo.Since
  type Places = ImportRepo.Places
  val Places: ImportRepo.Places.type = ImportRepo.Places
  type PleiadesOnly = ImportRepo.PleiadesOnly
  val PleiadesOnly: ImportRepo.PleiadesOnly.type = ImportRepo.PleiadesOnly
  type Category = ImportRepo.Category
  val Category: ImportRepo.Category.type = ImportRepo.Category
  type NameKey = ImportRepo.NameKey
  val NameKey: ImportRepo.NameKey.type = ImportRepo.NameKey

  val Place: Target.TargetType = ImportRepo.Place
  val Event: Target.TargetType = ImportRepo.Event

  def targets(
      ds: DataSource,
      provider: MediaItemRow.Provider,
      place: Option[com.histmersion.domain.catalogue.Place.Id],
      event: Option[com.histmersion.domain.catalogue.Event.Id],
      limit: ImportRepo.Limit,
      refresh: ImportRepo.Refresh,
      since: Option[ImportRepo.Since] = None
  ): Either[ImportRepo.Problem, List[Target]] = connect(ds)(ImportRepo.targets(provider, place, event, limit, refresh, since))
  def pending(ds: DataSource, provider: MediaItemRow.Provider, since: Option[ImportRepo.Since] = None): com.histmersion.RowCount =
    connect(ds)(ImportRepo.pending(provider, since))
  def collectionTargets(
      ds: DataSource,
      check: MediaItemRow.Provider,
      places: ImportRepo.Places,
      place: Option[com.histmersion.domain.catalogue.Place.Id],
      event: Option[com.histmersion.domain.catalogue.Event.Id],
      limit: ImportRepo.Limit,
      refresh: ImportRepo.Refresh,
      since: Option[ImportRepo.Since] = None,
      pleiadesOnly: ImportRepo.PleiadesOnly = ImportRepo.PleiadesOnly(false)
  ): Either[ImportRepo.Problem, List[Target]] =
    connect(ds)(ImportRepo.collectionTargets(check, places, place, event, limit, refresh, since, pleiadesOnly))
  def collectionPending(
      ds: DataSource,
      check: MediaItemRow.Provider,
      places: ImportRepo.Places,
      since: Option[ImportRepo.Since] = None,
      pleiadesOnly: ImportRepo.PleiadesOnly = ImportRepo.PleiadesOnly(false)
  ): com.histmersion.RowCount = connect(ds)(ImportRepo.collectionPending(check, places, since, pleiadesOnly))
  def placeFacts(
      ds: DataSource,
      ids: List[com.histmersion.domain.catalogue.Place.Id]
  ): Map[com.histmersion.domain.catalogue.Place.Id, PlaceFacts] = connect(ds)(ImportRepo.placeFacts(ids))
  def mapPlacesNamed(ds: DataSource, keys: List[ImportRepo.NameKey]): Map[ImportRepo.NameKey, List[PlaceFacts]] =
    connect(ds)(ImportRepo.mapPlacesNamed(keys))
  def placeExternalIds(
      ds: DataSource,
      ids: List[com.histmersion.domain.catalogue.Place.Id]
  ): Map[com.histmersion.domain.catalogue.Place.Id, Set[(com.histmersion.domain.catalogue.places.ExternalIds.Scheme, com.histmersion.domain.catalogue.places.ExternalIds.ExternalId)]] =
    connect(ds)(ImportRepo.placeExternalIds(ids))
  def pleiadesIds(
      ds: DataSource,
      ids: List[com.histmersion.domain.catalogue.Place.Id]
  ): Map[com.histmersion.domain.catalogue.Place.Id, List[com.histmersion.domain.catalogue.places.ExternalIds.ExternalId]] =
    connect(ds)(ImportRepo.pleiadesIds(ids))
  def mapPlacesWithIds(
      ds: DataSource,
      ids: List[(com.histmersion.domain.catalogue.places.ExternalIds.Scheme, com.histmersion.domain.catalogue.places.ExternalIds.ExternalId)]
  ): Map[(com.histmersion.domain.catalogue.places.ExternalIds.Scheme, com.histmersion.domain.catalogue.places.ExternalIds.ExternalId), List[PlaceFacts]] =
    connect(ds)(ImportRepo.mapPlacesWithIds(ids))
  def commonsCategory(
      ds: DataSource,
      placeId: com.histmersion.domain.catalogue.Place.Id,
      category: ImportRepo.Category,
      qid: Target.Qid
  ): Boolean =
    transact(ds)(ImportRepo.commonsCategory(placeId, category, qid))
  def checked(
      ds: DataSource,
      provider: MediaItemRow.Provider,
      targetType: Target.TargetType,
      targetId: Target.Id,
      found: com.histmersion.RowCount,
      qid: Option[Target.Qid] = None
  ): Unit =
    transact(ds)(ImportRepo.checked(provider, targetType, targetId, found, qid))
