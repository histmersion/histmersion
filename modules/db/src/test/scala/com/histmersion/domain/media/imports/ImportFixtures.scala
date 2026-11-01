package com.histmersion.domain.media.imports

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The imports' repository as the db and server suites reach it from outside the feature. */
object ImportFixtures:
  val Place: String = ImportRepo.Place
  val Event: String = ImportRepo.Event

  def targets(
      ds: DataSource,
      provider: String,
      place: Option[String],
      event: Option[String],
      limit: Int,
      refresh: Boolean,
      since: Option[Long] = None
  ): Either[String, List[Target]] = connect(ds)(ImportRepo.targets(provider, place, event, limit, refresh, since))
  def pending(ds: DataSource, provider: String, since: Option[Long] = None): Int =
    connect(ds)(ImportRepo.pending(provider, since))
  def collectionTargets(
      ds: DataSource,
      check: String,
      places: Boolean,
      place: Option[String],
      event: Option[String],
      limit: Int,
      refresh: Boolean,
      since: Option[Long] = None,
      pleiadesOnly: Boolean = false
  ): Either[String, List[Target]] =
    connect(ds)(ImportRepo.collectionTargets(check, places, place, event, limit, refresh, since, pleiadesOnly))
  def collectionPending(
      ds: DataSource,
      check: String,
      places: Boolean,
      since: Option[Long] = None,
      pleiadesOnly: Boolean = false
  ): Int = connect(ds)(ImportRepo.collectionPending(check, places, since, pleiadesOnly))
  def placeFacts(ds: DataSource, ids: List[String]): Map[String, PlaceFacts] = connect(ds)(ImportRepo.placeFacts(ids))
  def mapPlacesNamed(ds: DataSource, keys: List[String]): Map[String, List[PlaceFacts]] =
    connect(ds)(ImportRepo.mapPlacesNamed(keys))
  def placeExternalIds(ds: DataSource, ids: List[String]): Map[String, Set[(String, String)]] =
    connect(ds)(ImportRepo.placeExternalIds(ids))
  def pleiadesIds(ds: DataSource, ids: List[String]): Map[String, List[String]] = connect(ds)(ImportRepo.pleiadesIds(ids))
  def mapPlacesWithIds(ds: DataSource, ids: List[(String, String)]): Map[(String, String), List[PlaceFacts]] =
    connect(ds)(ImportRepo.mapPlacesWithIds(ids))
  def commonsCategory(ds: DataSource, placeId: String, category: String, qid: String): Boolean =
    transact(ds)(ImportRepo.commonsCategory(placeId, category, qid))
  def checked(ds: DataSource, provider: String, targetType: String, targetId: String, found: Int, qid: Option[String] = None): Unit =
    transact(ds)(ImportRepo.checked(provider, targetType, targetId, found, qid))
