package com.histmersion.domain.media.imports

import com.histmersion.domain.media.assets.{AssetService, Known, ProviderAsset, Rights}

/** Where an import writes and what it reads of the map: media's own services in the server ([[MediaStore.apply]]); a
  * map in a test.
  */
trait MediaStore:
  def known(provider: String, keys: List[String]): Map[String, Known]
  def save(a: ProviderAsset, firstTarget: (String, String)): (String, Boolean)
  def refreshRights(uid: String, r: Rights): Unit
  def link(
      uid: String,
      targetType: String,
      targetId: String,
      role: String,
      property: Option[String],
      item: Option[String],
      rank: Int
  ): Boolean
  def commonsCategory(placeId: String, category: String, qid: String): Boolean
  def checked(provider: String, targetType: String, targetId: String, found: Int, qid: Option[String]): Unit

  /** A link with the reason a collection's record is tied to its target (V094). */
  def linkBasis(
      uid: String,
      targetType: String,
      targetId: String,
      role: String,
      property: Option[String],
      item: Option[String],
      rank: Int,
      basis: Option[String]
  ): Boolean =
    link(uid, targetType, targetId, role, property, item, rank)

  /** The places a collection import asks about, as a match sees them. */
  def placeFacts(ids: List[String]): Map[String, PlaceFacts] = Map.empty

  /** The places on the map each name key names. */
  def mapPlacesNamed(keys: List[String]): Map[String, List[PlaceFacts]] = Map.empty

  /** The gazetteer ids each place carries (`ExternalIds` pairs). */
  def placeExternalIds(ids: List[String]): Map[String, Set[(String, String)]] = Map.empty

  /** The Pleiades ids of places (those without one left out). */
  def pleiadesIds(ids: List[String]): Map[String, List[String]] = Map.empty

  /** The places on the map carrying each gazetteer id. */
  def mapPlacesWithIds(ids: List[(String, String)]): Map[(String, String), List[PlaceFacts]] = Map.empty

object MediaStore:

  /** The import's store over media's services: its pictures and their links, its checks and the map's facts. */
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
