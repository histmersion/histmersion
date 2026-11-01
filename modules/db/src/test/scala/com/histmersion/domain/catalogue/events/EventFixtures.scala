package com.histmersion.domain.catalogue.events

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.{
  ApiError,
  CatalogRecord,
  CorrectionList,
  EventCatalogueRef,
  EventDetail,
  EventGeo,
  EventGeoList,
  EventList,
  EventPatch,
  EventPut,
  Filter
}
import com.histmersion.db.Sql
import com.histmersion.domain.library.search.FilterEventSources

import com.augustnagro.magnum.{connect, transact}
import javax.sql.DataSource

/** The events' store as a db suite writes and reads it: each call on a connection, or in a transaction, of its own. */
object EventFixtures:
  private val related = FilterEventSources()

  def listWhere(
      filter: Filter,
      scope: PlaceScope,
      text: Option[String],
      kind: Option[String],
      historicity: Option[String],
      placeId: Option[String],
      more: EventSql.Narrow = EventSql.Narrow()
  ): Sql = EventSql.listWhere(related, filter, scope, text, kind, historicity, placeId, more)

  def list(
      ds: DataSource,
      filter: Filter,
      scope: PlaceScope,
      text: Option[String],
      kind: Option[String],
      historicity: Option[String],
      placeId: Option[String],
      limit: Int,
      offset: Int,
      more: EventSql.Narrow = EventSql.Narrow()
  ): EventList =
    connect(ds)(EventRepo.list(listWhere(filter, scope, text, kind, historicity, placeId, more), limit, offset))

  def geo(
      ds: DataSource,
      filter: Filter,
      scope: PlaceScope,
      text: Option[String],
      kind: Option[String],
      historicity: Option[String],
      placeId: Option[String],
      limit: Int,
      tolerance: Double,
      more: EventSql.Narrow = EventSql.Narrow()
  ): EventGeoList =
    connect(ds)(EventRepo.geo(listWhere(filter, scope, text, kind, historicity, placeId, more), limit, tolerance))

  def geoOf(ds: DataSource, ids: List[String]): Map[String, EventGeo] = connect(ds)(EventRepo.geoOf(ids))
  def get(ds: DataSource, id: String): Option[EventDetail] = connect(ds)(EventRepo.get(id))
  def put(ds: DataSource, id: String, in: EventPut): Unit = transact(ds)(EventRepo.put(id, in))
  def patch(ds: DataSource, id: String, p: EventPatch): Either[ApiError, Unit] = transact(ds)(EventRepo.patch(id, p))
  def rename(ds: DataSource, from: String, to: String): Either[ApiError, Unit] =
    transact(ds)(EventRepo.rename(from.trim, to.trim))
  def renameIn(from: String, to: String)(using com.augustnagro.magnum.DbCon): Either[ApiError, Unit] =
    EventRepo.rename(from, to)
  def renamedTo(ds: DataSource, id: String): Option[String] = connect(ds)(EventRepo.renamedTo(id))
  def delete(ds: DataSource, id: String): Boolean = transact(ds)(EventRepo.delete(id))
  def tierAndNames(ds: DataSource, id: String): Option[(String, List[String], Option[String])] =
    connect(ds)(EventRepo.tierAndNames(id))

  def stored(ds: DataSource): List[StoredEvent] = connect(ds)(EventImportRepo.stored)
  def save(ds: DataSource, placed: List[(CatalogRecord, CatalogMerge.Placement)]): EventSaved =
    transact(ds)(EventImportRepo.save(placed))
  def partsDone(ds: DataSource, provider: String): Set[String] = connect(ds)(EventImportRepo.partsDone(provider))
  def markPart(ds: DataSource, provider: String, part: String, records: Int): Unit =
    transact(ds)(EventImportRepo.markPart(provider, part, records))
  def clearParts(ds: DataSource, provider: String): Unit = transact(ds)(EventImportRepo.clearParts(provider))
  def catalogueRefs(ds: DataSource, id: String): List[EventCatalogueRef] =
    connect(ds)(EventImportRepo.catalogueRefs(id))

  def corrections(
      ds: DataSource,
      kind: String,
      id: Option[String],
      status: Option[String],
      limit: Int,
      offset: Int
  ): CorrectionList = connect(ds)(CorrectionRepo.list(kind, id, status, limit, offset))

  def records(ds: DataSource, provider: Option[String]): (List[(String, String, CatalogRecord)], Int) =
    connect(ds)(EventImportRepo.records(provider))
  def storedKinds(ds: DataSource, provider: String): Map[(String, String), String] =
    connect(ds)(EventImportRepo.storedKinds(provider))
