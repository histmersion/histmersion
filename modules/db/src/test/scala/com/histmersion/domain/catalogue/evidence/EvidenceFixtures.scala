package com.histmersion.domain.catalogue.evidence

import com.histmersion.api.{EventFindingRef, EventLinkCandidate, EventSourceRef, TimeSpan}
import com.histmersion.db.Sql

import com.augustnagro.magnum.{connect, transact}
import javax.sql.DataSource

/** The events' links and leads as a db suite writes and reads them: each call on a connection of its own. */
object EvidenceFixtures:
  def candidates(ds: DataSource, id: String, withRejected: Boolean = false): List[EventLinkCandidate] =
    connect(ds)(EventLinkRepo.candidates(id, withRejected))
  def saveCandidates(ds: DataSource, id: String, cs: List[EventLinkCandidate]): Unit =
    transact(ds)(EventLinkRepo.saveCandidates(id, cs))
  def setStatus(ds: DataSource, id: String, citekey: String, status: String): Boolean =
    transact(ds)(EventLinkRepo.setStatus(id, citekey, status))
  def isChecked(ds: DataSource, id: String): Boolean = connect(ds)(EventLinkRepo.isChecked(id))
  def attestingSources(ds: DataSource, id: String): List[EventSourceRef] = connect(ds)(LeadRepo.attestingSources(id))
  def nearFindings(ds: DataSource, id: String, km: Double, years: Long, limit: Int): List[EventFindingRef] =
    connect(ds)(LeadRepo.nearFindings(id, km, years, limit))
  def leadRows(ds: DataSource, where: Sql, km: Double, years: Long, limit: Int, offset: Int): (Int, List[LeadRow]) =
    connect(ds)(LeadRepo.leadRows(where, km, years, limit, offset))
  def articleCandidates(
      ds: DataSource,
      citekeys: List[String],
      period: Option[(Long, Long)],
      km: Double,
      minVei: Int,
      minMagnitude: Double,
      limit: Int
  ): List[(String, String, String, TimeSpan, List[String], Option[Int], Option[Double], Int, String)] =
    connect(ds)(LeadRepo.articleCandidates(citekeys, period, km, minVei, minMagnitude, limit))
