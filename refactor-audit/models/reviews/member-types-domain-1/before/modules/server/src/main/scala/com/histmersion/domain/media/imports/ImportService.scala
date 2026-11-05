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
      provider: String,
      place: Option[String],
      event: Option[String],
      limit: Int,
      refresh: Boolean,
      since: Option[Long]
  ): Either[String, List[Target]]

  def pending(provider: String, since: Option[Long]): Int

  /** The places on the map (`places`) or the events with an item a collection asks about. */
  def collectionTargets(
      check: String,
      places: Boolean,
      place: Option[String],
      event: Option[String],
      limit: Int,
      refresh: Boolean,
      since: Option[Long],
      pleiadesOnly: Boolean
  ): Either[String, List[Target]]

  def collectionPending(check: String, places: Boolean, since: Option[Long], pleiadesOnly: Boolean): Int

  def placeFacts(ids: List[String]): Map[String, PlaceFacts]
  def mapPlacesNamed(keys: List[String]): Map[String, List[PlaceFacts]]
  def placeExternalIds(ids: List[String]): Map[String, Set[(String, String)]]
  def pleiadesIds(ids: List[String]): Map[String, List[String]]
  def mapPlacesWithIds(ids: List[(String, String)]): Map[(String, String), List[PlaceFacts]]

  /** A place's Commons category recorded as a link of the place; whether it is new. */
  def commonsCategory(placeId: String, category: String, qid: String): Boolean

  /** That `provider` was asked about a target, and how many pictures it gave. */
  def checked(provider: String, targetType: String, targetId: String, found: Int, qid: Option[String]): Unit

object ImportService:
  def apply(db: Transactor): ImportService = Implementation(db)

  final private class Implementation(db: Transactor) extends ImportService:
    def targets(
        provider: String,
        place: Option[String],
        event: Option[String],
        limit: Int,
        refresh: Boolean,
        since: Option[Long]
    ): Either[String, List[Target]] = db.read(ImportRepo.targets(provider, place, event, limit, refresh, since))
    def pending(provider: String, since: Option[Long]): Int = db.read(ImportRepo.pending(provider, since))
    def collectionTargets(
        check: String,
        places: Boolean,
        place: Option[String],
        event: Option[String],
        limit: Int,
        refresh: Boolean,
        since: Option[Long],
        pleiadesOnly: Boolean
    ): Either[String, List[Target]] =
      db.read(ImportRepo.collectionTargets(check, places, place, event, limit, refresh, since, pleiadesOnly))
    def collectionPending(check: String, places: Boolean, since: Option[Long], pleiadesOnly: Boolean): Int =
      db.read(ImportRepo.collectionPending(check, places, since, pleiadesOnly))
    def placeFacts(ids: List[String]): Map[String, PlaceFacts] = db.read(ImportRepo.placeFacts(ids))
    def mapPlacesNamed(keys: List[String]): Map[String, List[PlaceFacts]] = db.read(ImportRepo.mapPlacesNamed(keys))
    def placeExternalIds(ids: List[String]): Map[String, Set[(String, String)]] =
      db.read(ImportRepo.placeExternalIds(ids))
    def pleiadesIds(ids: List[String]): Map[String, List[String]] = db.read(ImportRepo.pleiadesIds(ids))
    def mapPlacesWithIds(ids: List[(String, String)]): Map[(String, String), List[PlaceFacts]] =
      db.read(ImportRepo.mapPlacesWithIds(ids))
    def commonsCategory(placeId: String, category: String, qid: String): Boolean =
      db.transact(ImportRepo.commonsCategory(placeId, category, qid))
    def checked(provider: String, targetType: String, targetId: String, found: Int, qid: Option[String]): Unit =
      db.transact(ImportRepo.checked(provider, targetType, targetId, found, qid))

  /** Media's checks alone, in memory: no place, event or finding is held, so nothing is on the map to ask about — a
    * place or event named by id is not there, a batch is empty, no place has facts — and a Commons category has no
    * place to be recorded on. `clock` stamps a check as the database's `clock_timestamp()` does.
    */
  final class InMemory(state: AtomicReference[MediaState], clock: Clock) extends ImportService:
    private def one(place: Option[String], event: Option[String]): Option[String] =
      place
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(p => s"No place '$p'")
        .orElse(event.map(_.trim).filter(_.nonEmpty).map(e => s"No event '$e'"))

    def targets(
        provider: String,
        place: Option[String],
        event: Option[String],
        limit: Int,
        refresh: Boolean,
        since: Option[Long]
    ): Either[String, List[Target]] = one(place, event).toLeft(Nil)
    def pending(provider: String, since: Option[Long]): Int = 0
    def collectionTargets(
        check: String,
        places: Boolean,
        place: Option[String],
        event: Option[String],
        limit: Int,
        refresh: Boolean,
        since: Option[Long],
        pleiadesOnly: Boolean
    ): Either[String, List[Target]] =
      (place.map(_.trim).filter(_.nonEmpty), event.map(_.trim).filter(_.nonEmpty)) match
        case (Some(_), _) if !places   => Left("this import is about events: name one with --event")
        case (None, Some(_)) if places => Left("this import is about places: name one with --place")
        case _                         => one(place, event).toLeft(Nil)
    def collectionPending(check: String, places: Boolean, since: Option[Long], pleiadesOnly: Boolean): Int = 0
    def placeFacts(ids: List[String]): Map[String, PlaceFacts] = Map.empty
    def mapPlacesNamed(keys: List[String]): Map[String, List[PlaceFacts]] = Map.empty
    def placeExternalIds(ids: List[String]): Map[String, Set[(String, String)]] = Map.empty
    def pleiadesIds(ids: List[String]): Map[String, List[String]] = Map.empty
    def mapPlacesWithIds(ids: List[(String, String)]): Map[(String, String), List[PlaceFacts]] = Map.empty
    def commonsCategory(placeId: String, category: String, qid: String): Boolean = false

    def checked(provider: String, targetType: String, targetId: String, found: Int, qid: Option[String]): Unit =
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
