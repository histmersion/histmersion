package com.histmersion
package domain.catalogue.evidence

import com.histmersion.api.TimeSpan
import com.histmersion.api.catalogue.{ArticleEventLead, CatalogRecord, EventEntry, EventFindingRef, EventLinkCandidate, EventSourceRef}
import com.histmersion.domain.DeepTime
import com.histmersion.domain.catalogue.Event
import com.histmersion.domain.catalogue.events.{BEventRow, CatalogMerge}
import com.histmersion.domain.library.Citekey
import com.histmersion.util.database.Sql

import com.augustnagro.magnum.{connect, transact}
import javax.sql.DataSource

/** The events' links and leads as a db suite writes and reads them: each call on a connection of its own. */
object EvidenceFixtures:
  def candidates(ds: DataSource, id: BEventRow.Id, withRejected: Boolean = false): List[EventLinkCandidate] =
    connect(ds)(EventLinkRepo.candidates(id, withRejected))
  def saveCandidates(ds: DataSource, id: BEventRow.Id, cs: List[EventLinkCandidate]): Unit =
    transact(ds)(EventLinkRepo.saveCandidates(id, cs))
  def setStatus(ds: DataSource, id: BEventRow.Id, citekey: Citekey, status: BCandidateRow.Status): Boolean =
    transact(ds)(EventLinkRepo.setStatus(id, citekey, status))
  def isChecked(ds: DataSource, id: BEventRow.Id): Boolean = connect(ds)(EventLinkRepo.isChecked(id))
  def attestingSources(ds: DataSource, id: Event.Id): List[EventSourceRef] =
    connect(ds)(LeadRepo.attestingSources(id))
  def nearFindings(
      ds: DataSource,
      id: Event.Id,
      km: CatalogMerge.Tolerance.Km,
      years: CatalogMerge.Tolerance.Years,
      limit: PageSize
  ): List[EventFindingRef] =
    connect(ds)(LeadRepo.nearFindings(id, km, years, limit))
  def leadRows(
      ds: DataSource,
      where: Sql,
      km: CatalogMerge.Tolerance.Km,
      years: CatalogMerge.Tolerance.Years,
      limit: PageSize,
      offset: Offset
  ): (RowCount, List[LeadRow]) =
    connect(ds)(LeadRepo.leadRows(where, km, years, limit, offset))
  def articleCandidates(
      ds: DataSource,
      citekeys: List[Citekey],
      period: Option[(DeepTime.SignedYear, DeepTime.SignedYear)],
      km: CatalogMerge.Tolerance.Km,
      minVei: CatalogRecord.Vei,
      minMagnitude: CatalogRecord.Magnitude,
      limit: PageSize
  ): List[
    (
        Event.Id,
        CatalogRecord.Label,
        CatalogRecord.Kind,
        TimeSpan,
        List[EventEntry.Names],
        Option[CatalogRecord.Vei],
        Option[CatalogRecord.Magnitude],
        ArticleEventLead.FindingsNear,
        CatalogRecord.Provider
    )
  ] =
    connect(ds)(LeadRepo.articleCandidates(citekeys, period, km, minVei, minMagnitude, limit))
