package com.histmersion.server.media

import com.histmersion.pluginhost.Polite

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.db.MediaRepo
import com.histmersion.db.MediaRepo.{ProviderAsset, Rights}

final class DbMediaStore(ds: javax.sql.DataSource) extends MediaStore:
  def known(provider: String, keys: List[String]) = MediaRepo.known(ds, provider, keys)
  def save(a: ProviderAsset, firstTarget: (String, String)) = MediaRepo.save(ds, a, firstTarget)
  def refreshRights(uid: String, r: Rights) = MediaRepo.refreshRights(ds, uid, r)
  def link(
      uid: String,
      targetType: String,
      targetId: String,
      role: String,
      property: Option[String],
      item: Option[String],
      rank: Int
  ) = MediaRepo.link(ds, uid, targetType, targetId, role, property, item, rank)
  def commonsCategory(placeId: String, category: String, qid: String) =
    MediaRepo.commonsCategory(ds, placeId, category, qid)
  def checked(provider: String, targetType: String, targetId: String, found: Int, qid: Option[String]) =
    MediaRepo.checked(ds, provider, targetType, targetId, found, qid)
  override def linkBasis(
      uid: String,
      targetType: String,
      targetId: String,
      role: String,
      property: Option[String],
      item: Option[String],
      rank: Int,
      basis: Option[String]
  ) =
    MediaRepo.link(ds, uid, targetType, targetId, role, property, item, rank, basis)
  override def placeFacts(ids: List[String]) = MediaRepo.placeFacts(ds, ids)
  override def mapPlacesNamed(keys: List[String]) = MediaRepo.mapPlacesNamed(ds, keys)
  override def placeExternalIds(ids: List[String]) = MediaRepo.placeExternalIds(ds, ids)
  override def pleiadesIds(ids: List[String]) = MediaRepo.pleiadesIds(ds, ids)
  override def mapPlacesWithIds(ids: List[(String, String)]) = MediaRepo.mapPlacesWithIds(ds, ids)
