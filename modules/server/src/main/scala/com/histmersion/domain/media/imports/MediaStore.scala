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
      role: com.histmersion.domain.media.imports.MediaStore.LinkRole,
      property: Option[com.histmersion.domain.media.imports.MediaStore.LinkProperty],
      item: Option[com.histmersion.domain.media.imports.MediaStore.LinkItem],
      rank: com.histmersion.domain.media.imports.MediaStore.LinkRank
  ): Boolean
  def commonsCategory(placeId: com.histmersion.domain.catalogue.Place.Id, category: com.histmersion.domain.media.imports.ImportService.CommonsCategory, qid: com.histmersion.domain.catalogue.places.Wikidata.Id): Boolean
  def checked(provider: com.histmersion.domain.media.imports.ImportService.Provider, targetType: com.histmersion.domain.media.imports.ImportService.TargetType, targetId: com.histmersion.domain.media.imports.ImportService.TargetId, found: com.histmersion.domain.media.imports.ImportService.FoundCount, qid: Option[com.histmersion.domain.catalogue.places.Wikidata.Id]): Unit

  /** A link with the reason a collection's record is tied to its target (V094). */
  def linkBasis(
      uid: com.histmersion.domain.media.assets.AssetService.AssetUid,
      targetType: com.histmersion.domain.media.imports.ImportService.TargetType,
      targetId: com.histmersion.domain.media.imports.ImportService.TargetId,
      role: com.histmersion.domain.media.imports.MediaStore.LinkBasisRole,
      property: Option[com.histmersion.domain.media.imports.MediaStore.LinkBasisProperty],
      item: Option[com.histmersion.domain.media.imports.MediaStore.LinkBasisItem],
      rank: com.histmersion.domain.media.imports.MediaStore.LinkBasisRank,
      basis: Option[com.histmersion.domain.media.imports.MediaStore.LinkBasisBasis]
  ): Boolean =
    link(uid, targetType, targetId, role, property, item, rank)

  /** The places a collection import asks about, as a match sees them. */
  def placeFacts(ids: List[com.histmersion.domain.catalogue.Place.Id]): Map[com.histmersion.domain.catalogue.Place.Id, PlaceFacts] = Map.empty

  /** The places on the map each name key names. */
  def mapPlacesNamed(keys: List[com.histmersion.domain.media.imports.ImportService.PlaceKey]): Map[com.histmersion.domain.media.imports.ImportService.PlaceKey, List[PlaceFacts]] = Map.empty

  /** The gazetteer ids each place carries (`ExternalIds` pairs). */
  def placeExternalIds(ids: List[com.histmersion.domain.catalogue.Place.Id]): Map[com.histmersion.domain.catalogue.Place.Id, Set[(com.histmersion.domain.media.imports.ImportService.Provider, com.histmersion.domain.media.imports.ImportService.ExternalId)]] = Map.empty

  /** The Pleiades ids of places (those without one left out). */
  def pleiadesIds(ids: List[com.histmersion.domain.media.imports.ImportService.ExternalId]): Map[com.histmersion.domain.catalogue.Place.Id, List[com.histmersion.domain.media.imports.ImportService.ExternalId]] = Map.empty

  /** The places on the map carrying each gazetteer id. */
  def mapPlacesWithIds(ids: List[(com.histmersion.domain.catalogue.Place.Id, com.histmersion.domain.media.imports.ImportService.ExternalId)]): Map[(com.histmersion.domain.catalogue.Place.Id, com.histmersion.domain.media.imports.ImportService.ExternalId), List[PlaceFacts]] = Map.empty

object MediaStore:

  /** The import's store over media's services: its pictures and their links, its checks and the map's facts. */
  type ApplyLinkRole = ApplyLinkRole.T
  object ApplyLinkRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyLinkProperty = ApplyLinkProperty.T
  object ApplyLinkProperty extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyLinkItem = ApplyLinkItem.T
  object ApplyLinkItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ApplyLinkRank = ApplyLinkRank.T
  object ApplyLinkRank extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type LinkRole = LinkRole.T
  object LinkRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkProperty = LinkProperty.T
  object LinkProperty extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkItem = LinkItem.T
  object LinkItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkRank = LinkRank.T
  object LinkRank extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkBasisRole = LinkBasisRole.T
  object LinkBasisRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkBasisProperty = LinkBasisProperty.T
  object LinkBasisProperty extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkBasisItem = LinkBasisItem.T
  object LinkBasisItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkBasisRank = LinkBasisRank.T
  object LinkBasisRank extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkBasisBasis = LinkBasisBasis.T
  object LinkBasisBasis extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def apply(assets: AssetService, imports: ImportService): MediaStore = new MediaStore:
    def known(provider: com.histmersion.domain.media.imports.ImportService.Provider, keys: List[com.histmersion.domain.media.assets.AssetService.ProviderKey]) = assets.known(provider, keys)
    def save(a: ProviderAsset, firstTarget: (com.histmersion.domain.media.imports.ImportService.TargetType, com.histmersion.domain.media.imports.ImportService.TargetId)) = assets.save(a, firstTarget)
    def refreshRights(uid: com.histmersion.domain.media.assets.AssetService.AssetUid, r: Rights) = assets.refreshRights(uid, r)
    def link(
        uid: com.histmersion.domain.media.assets.AssetService.AssetUid,
        targetType: com.histmersion.domain.media.imports.ImportService.TargetType,
        targetId: com.histmersion.domain.media.imports.ImportService.TargetId,
        role: com.histmersion.domain.media.imports.MediaStore.ApplyLinkRole,
        property: Option[com.histmersion.domain.media.imports.MediaStore.ApplyLinkProperty],
        item: Option[com.histmersion.domain.media.imports.MediaStore.ApplyLinkItem],
        rank: com.histmersion.domain.media.imports.MediaStore.ApplyLinkRank
    ) = assets.link(uid, targetType, targetId, role, property, item, rank, None)
    def commonsCategory(placeId: com.histmersion.domain.catalogue.Place.Id, category: com.histmersion.domain.media.imports.ImportService.CommonsCategory, qid: com.histmersion.domain.catalogue.places.Wikidata.Id) =
      imports.commonsCategory(placeId, category, qid)
    def checked(provider: com.histmersion.domain.media.imports.ImportService.Provider, targetType: com.histmersion.domain.media.imports.ImportService.TargetType, targetId: com.histmersion.domain.media.imports.ImportService.TargetId, found: com.histmersion.domain.media.imports.ImportService.FoundCount, qid: Option[com.histmersion.domain.catalogue.places.Wikidata.Id]) =
      imports.checked(provider, targetType, targetId, found, qid)
    override def linkBasis(
        uid: String,
        targetType: String,
        targetId: String,
        role: String,
        property: Option[String],
        item: Option[String],
        rank: Int,
        basis: Option[String]
    ) = assets.link(uid, targetType, targetId, role, property, item, rank, basis)
    override def placeFacts(ids: List[String]) = imports.placeFacts(ids)
    override def mapPlacesNamed(keys: List[String]) = imports.mapPlacesNamed(keys)
    override def placeExternalIds(ids: List[String]) = imports.placeExternalIds(ids)
    override def pleiadesIds(ids: List[String]) = imports.pleiadesIds(ids)
    override def mapPlacesWithIds(ids: List[(String, String)]) = imports.mapPlacesWithIds(ids)
