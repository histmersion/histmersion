package com.histmersion.server.media

import com.histmersion.pluginhost.{allowed, eventRoles, placeRoles, Polite}

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.MediaRepo
import com.histmersion.db.MediaRepo.{Known, ProviderAsset, Rights}

/** Where an import writes. The database in the server ([[DbMediaStore]]); a map in a test. */
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
  def placeFacts(ids: List[String]): Map[String, MediaRepo.PlaceFacts] = Map.empty

  /** The places on the map each name key names. */
  def mapPlacesNamed(keys: List[String]): Map[String, List[MediaRepo.PlaceFacts]] = Map.empty

  /** The gazetteer ids each place carries ([[ExternalIds]] pairs). */
  def placeExternalIds(ids: List[String]): Map[String, Set[(String, String)]] = Map.empty

  /** The Pleiades ids of places (those without one left out). */
  def pleiadesIds(ids: List[String]): Map[String, List[String]] = Map.empty

  /** The places on the map carrying each gazetteer id. */
  def mapPlacesWithIds(ids: List[(String, String)]): Map[(String, String), List[MediaRepo.PlaceFacts]] = Map.empty
