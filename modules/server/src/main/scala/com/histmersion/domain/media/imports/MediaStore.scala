package com.histmersion.domain.media.imports

import com.histmersion.domain.media.assets.{AssetService, Known, ProviderAsset, Rights}

/** Where an import writes and what it reads of the map: media's own services in the server ([[MediaStore.apply]]); a
  * map in a test.
  */
trait MediaStore:
  def known(provider: com.histmersion.domain.media.imports.MediaStore.KnownProvider, keys: List[com.histmersion.domain.media.imports.MediaStore.KnownKeys]): Map[com.histmersion.domain.media.imports.MediaStore.KnownResultPart1, Known]
  def save(a: ProviderAsset, firstTarget: (com.histmersion.domain.media.imports.MediaStore.SaveFirstTargetPart1, com.histmersion.domain.media.imports.MediaStore.SaveFirstTargetPart2)): (com.histmersion.domain.media.imports.MediaStore.SaveResultPart1, com.histmersion.domain.media.imports.MediaStore.SaveResultPart2)
  def refreshRights(uid: com.histmersion.domain.media.imports.MediaStore.RefreshRightsUid, r: Rights): Unit
  def link(
      uid: com.histmersion.domain.media.imports.MediaStore.LinkUid,
      targetType: com.histmersion.domain.media.imports.MediaStore.LinkTargetType,
      targetId: com.histmersion.domain.media.imports.MediaStore.LinkTargetId,
      role: com.histmersion.domain.media.imports.MediaStore.LinkRole,
      property: Option[com.histmersion.domain.media.imports.MediaStore.LinkProperty],
      item: Option[com.histmersion.domain.media.imports.MediaStore.LinkItem],
      rank: com.histmersion.domain.media.imports.MediaStore.LinkRank
  ): com.histmersion.domain.media.imports.MediaStore.LinkResult
  def commonsCategory(placeId: com.histmersion.domain.catalogue.Place.Id, category: com.histmersion.domain.media.imports.MediaStore.CommonsCategoryCategory, qid: com.histmersion.domain.media.imports.MediaStore.CommonsCategoryQid): com.histmersion.domain.media.imports.MediaStore.CommonsCategoryResult
  def checked(provider: com.histmersion.domain.media.imports.MediaStore.CheckedProvider, targetType: com.histmersion.domain.media.imports.MediaStore.CheckedTargetType, targetId: com.histmersion.domain.media.imports.MediaStore.CheckedTargetId, found: com.histmersion.domain.media.imports.MediaStore.CheckedFound, qid: Option[com.histmersion.domain.media.imports.MediaStore.CheckedQid]): Unit

  /** A link with the reason a collection's record is tied to its target (V094). */
  def linkBasis(
      uid: com.histmersion.domain.media.imports.MediaStore.LinkBasisUid,
      targetType: com.histmersion.domain.media.imports.MediaStore.LinkBasisTargetType,
      targetId: com.histmersion.domain.media.imports.MediaStore.LinkBasisTargetId,
      role: com.histmersion.domain.media.imports.MediaStore.LinkBasisRole,
      property: Option[com.histmersion.domain.media.imports.MediaStore.LinkBasisProperty],
      item: Option[com.histmersion.domain.media.imports.MediaStore.LinkBasisItem],
      rank: com.histmersion.domain.media.imports.MediaStore.LinkBasisRank,
      basis: Option[com.histmersion.domain.media.imports.MediaStore.LinkBasisBasis]
  ): com.histmersion.domain.media.imports.MediaStore.LinkBasisResult =
    link(uid, targetType, targetId, role, property, item, rank)

  /** The places a collection import asks about, as a match sees them. */
  def placeFacts(ids: List[com.histmersion.domain.media.imports.MediaStore.PlaceFactsIds]): Map[com.histmersion.domain.media.imports.MediaStore.PlaceFactsResultPart1, PlaceFacts] = Map.empty

  /** The places on the map each name key names. */
  def mapPlacesNamed(keys: List[com.histmersion.domain.media.imports.MediaStore.MapPlacesNamedKeys]): Map[com.histmersion.domain.media.imports.MediaStore.MapPlacesNamedResultPart1, List[PlaceFacts]] = Map.empty

  /** The gazetteer ids each place carries (`ExternalIds` pairs). */
  def placeExternalIds(ids: List[com.histmersion.domain.media.imports.MediaStore.PlaceExternalIdsIds]): Map[com.histmersion.domain.media.imports.MediaStore.PlaceExternalIdsResultPart1, Set[(com.histmersion.domain.media.imports.MediaStore.PlaceExternalIdsResultPart2Part1, com.histmersion.domain.media.imports.MediaStore.PlaceExternalIdsResultPart2Part2)]] = Map.empty

  /** The Pleiades ids of places (those without one left out). */
  def pleiadesIds(ids: List[com.histmersion.domain.media.imports.MediaStore.PleiadesIdsIds]): Map[com.histmersion.domain.media.imports.MediaStore.PleiadesIdsResultPart1, List[com.histmersion.domain.media.imports.MediaStore.PleiadesIdsResultPart2]] = Map.empty

  /** The places on the map carrying each gazetteer id. */
  def mapPlacesWithIds(ids: List[(com.histmersion.domain.media.imports.MediaStore.MapPlacesWithIdsIdsPart1, com.histmersion.domain.media.imports.MediaStore.MapPlacesWithIdsIdsPart2)]): Map[(com.histmersion.domain.media.imports.MediaStore.MapPlacesWithIdsResultPart1Part1, com.histmersion.domain.media.imports.MediaStore.MapPlacesWithIdsResultPart1Part2), List[PlaceFacts]] = Map.empty

object MediaStore:

  /** The import's store over media's services: its pictures and their links, its checks and the map's facts. */
  type KnownResultPart1 = KnownResultPart1.T
  object KnownResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KnownProvider = KnownProvider.T
  object KnownProvider extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KnownKeys = KnownKeys.T
  object KnownKeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SaveResultPart1 = SaveResultPart1.T
  object SaveResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SaveResultPart2 = SaveResultPart2.T
  object SaveResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SaveFirstTargetPart1 = SaveFirstTargetPart1.T
  object SaveFirstTargetPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SaveFirstTargetPart2 = SaveFirstTargetPart2.T
  object SaveFirstTargetPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RefreshRightsUid = RefreshRightsUid.T
  object RefreshRightsUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkResult = LinkResult.T
  object LinkResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkUid = LinkUid.T
  object LinkUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkTargetType = LinkTargetType.T
  object LinkTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkTargetId = LinkTargetId.T
  object LinkTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkRole = LinkRole.T
  object LinkRole extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkProperty = LinkProperty.T
  object LinkProperty extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkItem = LinkItem.T
  object LinkItem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkRank = LinkRank.T
  object LinkRank extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
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
  type LinkBasisResult = LinkBasisResult.T
  object LinkBasisResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkBasisUid = LinkBasisUid.T
  object LinkBasisUid extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkBasisTargetType = LinkBasisTargetType.T
  object LinkBasisTargetType extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LinkBasisTargetId = LinkBasisTargetId.T
  object LinkBasisTargetId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
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

  def apply(assets: AssetService, imports: ImportService): MediaStore = new MediaStore:
    def known(provider: String, keys: List[String]) = assets.known(provider, keys)
    def save(a: ProviderAsset, firstTarget: (String, String)) = assets.save(a, firstTarget)
    def refreshRights(uid: String, r: Rights) = assets.refreshRights(uid, r)
    def link(
        uid: String,
        targetType: String,
        targetId: String,
        role: String,
        property: Option[String],
        item: Option[String],
        rank: Int
    ) = assets.link(uid, targetType, targetId, role, property, item, rank, None)
    def commonsCategory(placeId: String, category: String, qid: String) =
      imports.commonsCategory(placeId, category, qid)
    def checked(provider: String, targetType: String, targetId: String, found: Int, qid: Option[String]) =
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
