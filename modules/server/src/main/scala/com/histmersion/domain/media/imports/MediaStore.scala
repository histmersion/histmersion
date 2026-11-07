package com.histmersion.domain.media.imports

import com.histmersion.domain.media.assets.{AssetService, Known, ProviderAsset, Rights}

/** Where an import writes and what it reads of the map: media's own services in the server ([[MediaStore.apply]]); a
  * map in a test.
  */
trait MediaStore:
  def known(provider: com.histmersion.domain.media.imports.ImportService.Provider, keys: List[com.histmersion.domain.media.assets.AssetService.ProviderKey]): Map[com.histmersion.domain.media.assets.AssetService.ProviderKey, Known]
  def save(a: ProviderAsset, firstTarget: (com.histmersion.domain.media.imports.ImportService.TargetType, com.histmersion.domain.media.imports.ImportService.TargetId)): (com.histmersion.domain.media.assets.AssetService.AssetUid, Boolean)
  def refreshRights(uid: com.histmersion.domain.media.assets.AssetService.AssetUid, r: Rights): Unit
  def link(
      uid: com.histmersion.domain.media.assets.AssetService.AssetUid,
      targetType: com.histmersion.domain.media.imports.ImportService.TargetType,
      targetId: com.histmersion.domain.media.imports.ImportService.TargetId,
      role: com.histmersion.domain.media.assets.AssetService.LinkRole,
      property: Option[com.histmersion.domain.media.assets.AssetService.LinkProperty],
      item: Option[com.histmersion.domain.media.assets.AssetService.LinkItem],
      rank: com.histmersion.domain.media.assets.AssetService.LinkRank
  ): Boolean
  def commonsCategory(placeId: com.histmersion.domain.catalogue.Place.Id, category: com.histmersion.domain.media.imports.ImportService.CommonsCategory, qid: com.histmersion.domain.catalogue.places.Wikidata.Id): Boolean
  def checked(provider: com.histmersion.domain.media.imports.ImportService.Provider, targetType: com.histmersion.domain.media.imports.ImportService.TargetType, targetId: com.histmersion.domain.media.imports.ImportService.TargetId, found: com.histmersion.domain.media.imports.ImportService.FoundCount, qid: Option[com.histmersion.domain.catalogue.places.Wikidata.Id]): Unit

  /** A link with the reason a collection's record is tied to its target (V094). */
  def linkBasis(
      uid: com.histmersion.domain.media.assets.AssetService.AssetUid,
      targetType: com.histmersion.domain.media.imports.ImportService.TargetType,
      targetId: com.histmersion.domain.media.imports.ImportService.TargetId,
      role: com.histmersion.domain.media.assets.AssetService.LinkRole,
      property: Option[com.histmersion.domain.media.assets.AssetService.LinkProperty],
      item: Option[com.histmersion.domain.media.assets.AssetService.LinkItem],
      rank: com.histmersion.domain.media.assets.AssetService.LinkRank,
      basis: Option[com.histmersion.domain.media.assets.AssetService.LinkBasis]
  ): Boolean =
    link(uid, targetType, targetId, role, property, item, rank)

  /** The places a collection import asks about, as a match sees them. */
  def placeFacts(ids: List[com.histmersion.domain.catalogue.Place.Id]): Map[com.histmersion.domain.catalogue.Place.Id, PlaceFacts] = Map.empty

  /** The places on the map each name key names. */
  def mapPlacesNamed(keys: List[com.histmersion.domain.media.imports.ImportService.PlaceKey]): Map[com.histmersion.domain.media.imports.ImportService.PlaceKey, List[PlaceFacts]] = Map.empty

  /** The gazetteer ids each place carries (`ExternalIds` pairs). */
  def placeExternalIds(ids: List[com.histmersion.domain.catalogue.Place.Id]): Map[com.histmersion.domain.catalogue.Place.Id, Set[(com.histmersion.domain.catalogue.places.ExternalIds.Scheme, com.histmersion.domain.catalogue.places.ExternalIds.ExternalId)]] = Map.empty

  /** The Pleiades ids of places (those without one left out). */
  def pleiadesIds(ids: List[com.histmersion.domain.catalogue.Place.Id]): Map[com.histmersion.domain.catalogue.Place.Id, List[com.histmersion.domain.catalogue.places.ExternalIds.ExternalId]] = Map.empty

  /** The places on the map carrying each gazetteer id. */
  def mapPlacesWithIds(ids: List[(com.histmersion.domain.catalogue.places.ExternalIds.Scheme, com.histmersion.domain.catalogue.places.ExternalIds.ExternalId)]): Map[(com.histmersion.domain.catalogue.places.ExternalIds.Scheme, com.histmersion.domain.catalogue.places.ExternalIds.ExternalId), List[PlaceFacts]] = Map.empty

object MediaStore:

  /** The import's store over media's services: its pictures and their links, its checks and the map's facts. */
  def apply(assets: AssetService, imports: ImportService): MediaStore = new MediaStore:
    def known(provider: com.histmersion.domain.media.imports.ImportService.Provider, keys: List[com.histmersion.domain.media.assets.AssetService.ProviderKey]) =
      assets.known(AssetService.Provider(provider.value), keys)
    def save(a: ProviderAsset, firstTarget: (com.histmersion.domain.media.imports.ImportService.TargetType, com.histmersion.domain.media.imports.ImportService.TargetId)) =
      assets.save(a, (AssetService.TargetType(firstTarget._1.value), AssetService.TargetId(firstTarget._2.value)))
    def refreshRights(uid: com.histmersion.domain.media.assets.AssetService.AssetUid, r: Rights) = assets.refreshRights(uid, r)
    def link(
        uid: com.histmersion.domain.media.assets.AssetService.AssetUid,
        targetType: com.histmersion.domain.media.imports.ImportService.TargetType,
        targetId: com.histmersion.domain.media.imports.ImportService.TargetId,
        role: com.histmersion.domain.media.assets.AssetService.LinkRole,
        property: Option[com.histmersion.domain.media.assets.AssetService.LinkProperty],
        item: Option[com.histmersion.domain.media.assets.AssetService.LinkItem],
        rank: com.histmersion.domain.media.assets.AssetService.LinkRank
    ) =
      assets.link(
        uid,
        AssetService.TargetType(targetType.value),
        AssetService.TargetId(targetId.value),
        role,
        property,
        item,
        rank,
        None
      )
    def commonsCategory(placeId: com.histmersion.domain.catalogue.Place.Id, category: com.histmersion.domain.media.imports.ImportService.CommonsCategory, qid: com.histmersion.domain.catalogue.places.Wikidata.Id) =
      imports.commonsCategory(placeId, category, qid)
    def checked(provider: com.histmersion.domain.media.imports.ImportService.Provider, targetType: com.histmersion.domain.media.imports.ImportService.TargetType, targetId: com.histmersion.domain.media.imports.ImportService.TargetId, found: com.histmersion.domain.media.imports.ImportService.FoundCount, qid: Option[com.histmersion.domain.catalogue.places.Wikidata.Id]) =
      imports.checked(provider, targetType, targetId, found, qid)
    override def linkBasis(
        uid: com.histmersion.domain.media.assets.AssetService.AssetUid,
        targetType: com.histmersion.domain.media.imports.ImportService.TargetType,
        targetId: com.histmersion.domain.media.imports.ImportService.TargetId,
        role: com.histmersion.domain.media.assets.AssetService.LinkRole,
        property: Option[com.histmersion.domain.media.assets.AssetService.LinkProperty],
        item: Option[com.histmersion.domain.media.assets.AssetService.LinkItem],
        rank: com.histmersion.domain.media.assets.AssetService.LinkRank,
        basis: Option[com.histmersion.domain.media.assets.AssetService.LinkBasis]
    ) =
      assets.link(
        uid,
        AssetService.TargetType(targetType.value),
        AssetService.TargetId(targetId.value),
        role,
        property,
        item,
        rank,
        basis
      )
    override def placeFacts(ids: List[com.histmersion.domain.catalogue.Place.Id]) = imports.placeFacts(ids)
    override def mapPlacesNamed(keys: List[com.histmersion.domain.media.imports.ImportService.PlaceKey]) = imports.mapPlacesNamed(keys)
    override def placeExternalIds(ids: List[com.histmersion.domain.catalogue.Place.Id]) = imports.placeExternalIds(ids)
    override def pleiadesIds(ids: List[com.histmersion.domain.catalogue.Place.Id]) = imports.pleiadesIds(ids)
    override def mapPlacesWithIds(ids: List[(com.histmersion.domain.catalogue.places.ExternalIds.Scheme, com.histmersion.domain.catalogue.places.ExternalIds.ExternalId)]) = imports.mapPlacesWithIds(ids)
