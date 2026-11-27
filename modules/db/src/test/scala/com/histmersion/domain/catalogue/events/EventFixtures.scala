package com.histmersion
package domain.catalogue.events

import com.histmersion.domain.catalogue.places.PlaceScope

import com.histmersion.api.catalogue.{
  CatalogRecord,
  EventCatalogueRef,
  EventGeo,
  EventList,
  EventPatch,
  EventPut,
  EventSummary
}
import com.histmersion.api.views.EventDetail
import com.histmersion.api.library.Filter
import com.histmersion.domain.library.search.FilterEventSources

import com.augustnagro.magnum.{connect, transact}
import javax.sql.DataSource
import com.histmersion.api.Api
import com.histmersion.api.catalogue.Correction
import com.histmersion.domain.catalogue.Place
import com.histmersion.api.catalogue.EventEntry
import com.histmersion.domain.catalogue.Event
import com.augustnagro.magnum.Frag
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{tolerated, LintRule}

/** The events' store as a db suite writes and reads it: each call on a connection, or in a transaction, of its own. */
object EventFixtures:
  export EventRepo.{GeoTolerance, Tier}
  export EventImportRepo.PartName

  private val related = FilterEventSources()

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def listWhere(
      filter: Filter,
      scope: PlaceScope,
      text: Option[EventSelection.Text],
      kind: Option[EventSelection.Kind],
      historicity: Option[EventSelection.Historicity],
      placeId: Option[Place.Id],
      more: EventSql.Narrow = EventSql.Narrow()
  ): Frag = EventSql.listWhere(related, filter, scope, text, kind, historicity, placeId, more)

  def list(
      ds: DataSource,
      filter: Filter,
      scope: PlaceScope,
      text: Option[EventSelection.Text],
      kind: Option[EventSelection.Kind],
      historicity: Option[EventSelection.Historicity],
      placeId: Option[Place.Id],
      limit: PageSize,
      offset: Offset,
      more: EventSql.Narrow = EventSql.Narrow()
  ): EventList =
    connect(ds)(EventRepo.list(listWhere(filter, scope, text, kind, historicity, placeId, more), limit, offset))

  def geo(
      ds: DataSource,
      filter: Filter,
      scope: PlaceScope,
      text: Option[EventSelection.Text],
      kind: Option[EventSelection.Kind],
      historicity: Option[EventSelection.Historicity],
      placeId: Option[Place.Id],
      limit: PageSize,
      tolerance: EventRepo.GeoTolerance,
      more: EventSql.Narrow = EventSql.Narrow()
  ): EventGeo.List =
    connect(ds)(EventRepo.geo(listWhere(filter, scope, text, kind, historicity, placeId, more), limit, tolerance))

  def geoOf(ds: DataSource, ids: List[BEventRow.Id]): Map[BEventRow.Id, EventGeo] = connect(ds)(EventRepo.geoOf(ids))
  def get(ds: DataSource, id: BEventRow.Id): Option[EventEntry] =
    connect(ds)(EventRepo.get(id))
  def put(ds: DataSource, id: BEventRow.Id, in: EventPut): Unit = transact(ds)(EventRepo.put(id, in))
  def patch(ds: DataSource, id: BEventRow.Id, p: EventPatch): Either[Api.Error, Unit] =
    transact(ds)(EventRepo.patch(id, p))
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def rename(ds: DataSource, from: BEventRow.Id, to: BEventRow.Id): Either[Api.Error, Unit] =
    transact(ds)(EventRepo.rename(BEventRow.Id(from.value.trim), BEventRow.Id(to.value.trim)))
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  def renameIn(from: BEventRow.Id, to: BEventRow.Id)(using com.augustnagro.magnum.DbCon): Either[Api.Error, Unit] =
    EventRepo.rename(from, to)
  def renamedTo(ds: DataSource, id: BEventRow.Id): Option[BEventRow.Id] = connect(ds)(EventRepo.renamedTo(id))
  def delete(ds: DataSource, id: BEventRow.Id): Boolean = transact(ds)(EventRepo.delete(id))
  def tierAndNames(
      ds: DataSource,
      id: BEventRow.Id
  ): Option[(EventRepo.Tier, List[BEventRow.Names], Option[BEventRow.Label])] =
    connect(ds)(EventRepo.tierAndNames(id))

  def stored(ds: DataSource): List[StoredEvent] = connect(ds)(EventImportRepo.stored)
  def save(ds: DataSource, placed: List[(CatalogRecord, CatalogMerge.Placement)]): EventSaved =
    transact(ds)(EventImportRepo.save(placed))
  def partsDone(ds: DataSource, provider: CatalogRecord.Provider): Set[EventImportRepo.PartName] =
    connect(ds)(EventImportRepo.partsDone(provider))
  def markPart(
      ds: DataSource,
      provider: CatalogRecord.Provider,
      part: EventImportRepo.PartName,
      records: RowCount
  ): Unit =
    transact(ds)(EventImportRepo.markPart(provider, part, records))
  def clearParts(ds: DataSource, provider: CatalogRecord.Provider): Unit =
    transact(ds)(EventImportRepo.clearParts(provider))
  def catalogueRefs(ds: DataSource, id: Event.Id): List[EventCatalogueRef] =
    connect(ds)(EventImportRepo.catalogueRefs(id))

  def corrections(
      ds: DataSource,
      kind: Correction.TargetKind,
      id: Option[Correction.TargetId],
      status: Option[Correction.StatusValue],
      limit: PageSize,
      offset: Offset
  ): Correction.List = connect(ds)(CorrectionRepo.list(kind, id, status, limit, offset))

  def records(
      ds: DataSource,
      provider: Option[CatalogRecord.Provider]
  ): (List[(Event.Id, EventSummary.Kind, CatalogRecord)], RowCount) =
    connect(ds)(EventImportRepo.records(provider))
  def storedKinds(
      ds: DataSource,
      provider: CatalogRecord.Provider
  ): Map[(CatalogRecord.Provider, EventCatalogueRef.ProviderId), EventSummary.Kind] =
    connect(ds)(EventImportRepo.storedKinds(provider))
