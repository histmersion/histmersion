package com.histmersion
package domain.media.imports

import com.histmersion.domain.media.assets.{AssetService, Known, ProviderAsset, Rights}
import com.histmersion.domain.catalogue.Place
import com.histmersion.domain.catalogue.places.Wikidata
import com.histmersion.domain.catalogue.places.ExternalIds

/** Where an import writes and what it reads of the map: media's own services in the server ([[MediaStore.apply]]); a
  * map in a test.
  */
trait MediaStore:
  def known(
      provider: ImportService.Provider,
      keys: List[AssetService.ProviderKey]
  ): Map[AssetService.ProviderKey, Known]
  def save(
      a: ProviderAsset,
      firstTarget: (ImportService.TargetType, ImportService.TargetId)
  ): (AssetService.AssetUid, Boolean)
  def refreshRights(uid: AssetService.AssetUid, r: Rights): Unit
  def link(
      uid: AssetService.AssetUid,
      targetType: ImportService.TargetType,
      targetId: ImportService.TargetId,
      role: AssetService.LinkRole,
      property: Option[AssetService.LinkProperty],
      item: Option[AssetService.LinkItem],
      rank: AssetService.LinkRank
  ): Boolean
  def commonsCategory(placeId: Place.Id, category: ImportService.CommonsCategory, qid: Wikidata.Id): Boolean
  def checked(
      provider: ImportService.Provider,
      targetType: ImportService.TargetType,
      targetId: ImportService.TargetId,
      found: ImportService.FoundCount,
      qid: Option[Wikidata.Id]
  ): Unit

  /** A link with the reason a collection's record is tied to its target (V094). */
  def linkBasis(
      uid: AssetService.AssetUid,
      targetType: ImportService.TargetType,
      targetId: ImportService.TargetId,
      role: AssetService.LinkRole,
      property: Option[AssetService.LinkProperty],
      item: Option[AssetService.LinkItem],
      rank: AssetService.LinkRank,
      basis: Option[AssetService.LinkBasis]
  ): Boolean =
    link(uid, targetType, targetId, role, property, item, rank)

  /** The places a collection import asks about, as a match sees them. */
  def placeFacts(ids: List[Place.Id]): Map[Place.Id, PlaceFacts] = Map.empty

  /** The places on the map each name key names. */
  def mapPlacesNamed(keys: List[ImportService.PlaceKey]): Map[ImportService.PlaceKey, List[PlaceFacts]] = Map.empty

  /** The gazetteer ids each place carries (`ExternalIds` pairs). */
  def placeExternalIds(ids: List[Place.Id]): Map[Place.Id, Set[(ExternalIds.Scheme, ExternalIds.ExternalId)]] =
    Map.empty

  /** The Pleiades ids of places (those without one left out). */
  def pleiadesIds(ids: List[Place.Id]): Map[Place.Id, List[ExternalIds.ExternalId]] = Map.empty

  /** The places on the map carrying each gazetteer id. */
  def mapPlacesWithIds(
      ids: List[(ExternalIds.Scheme, ExternalIds.ExternalId)]
  ): Map[(ExternalIds.Scheme, ExternalIds.ExternalId), List[PlaceFacts]] = Map.empty

object MediaStore:

  /** The import's store over media's services: its pictures and their links, its checks and the map's facts. */
  def apply(assets: AssetService, imports: ImportService): MediaStore = new MediaStore:
    def known(provider: ImportService.Provider, keys: List[AssetService.ProviderKey]) =
      assets.known(AssetService.Provider(provider.value), keys)
    def save(a: ProviderAsset, firstTarget: (ImportService.TargetType, ImportService.TargetId)) =
      assets.save(a, (AssetService.TargetType(firstTarget._1.value), AssetService.TargetId(firstTarget._2.value)))
    def refreshRights(uid: AssetService.AssetUid, r: Rights) = assets.refreshRights(uid, r)
    def link(
        uid: AssetService.AssetUid,
        targetType: ImportService.TargetType,
        targetId: ImportService.TargetId,
        role: AssetService.LinkRole,
        property: Option[AssetService.LinkProperty],
        item: Option[AssetService.LinkItem],
        rank: AssetService.LinkRank
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
    def commonsCategory(placeId: Place.Id, category: ImportService.CommonsCategory, qid: Wikidata.Id) =
      imports.commonsCategory(placeId, category, qid)
    def checked(
        provider: ImportService.Provider,
        targetType: ImportService.TargetType,
        targetId: ImportService.TargetId,
        found: ImportService.FoundCount,
        qid: Option[Wikidata.Id]
    ) =
      imports.checked(provider, targetType, targetId, found, qid)
    override def linkBasis(
        uid: AssetService.AssetUid,
        targetType: ImportService.TargetType,
        targetId: ImportService.TargetId,
        role: AssetService.LinkRole,
        property: Option[AssetService.LinkProperty],
        item: Option[AssetService.LinkItem],
        rank: AssetService.LinkRank,
        basis: Option[AssetService.LinkBasis]
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
    override def placeFacts(ids: List[Place.Id]) = imports.placeFacts(ids)
    override def mapPlacesNamed(keys: List[ImportService.PlaceKey]) = imports.mapPlacesNamed(keys)
    override def placeExternalIds(ids: List[Place.Id]) = imports.placeExternalIds(ids)
    override def pleiadesIds(ids: List[Place.Id]) = imports.pleiadesIds(ids)
    override def mapPlacesWithIds(ids: List[(ExternalIds.Scheme, ExternalIds.ExternalId)]) =
      imports.mapPlacesWithIds(ids)
