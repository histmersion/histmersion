package com.histmersion.domain.research.workflows

import com.histmersion.domain.ReviewLedger.Ledger

import com.augustnagro.magnum.*
import javax.sql.DataSource

/** The suites' reads and writes of the review ledgers on a database of their own, as the service runs them: a read on a
  * connection of its own, a write in a transaction of its own.
  */
object ReviewLedgerFixtures:
  def get(ds: DataSource, slug: String): Either[String, Option[Ledger]] = connect(ds)(ReviewLedgerRepo.get(slug))
  def put(ds: DataSource, l: Ledger): Unit = transact(ds)(ReviewLedgerRepo.put(l))
  def passedReview(ds: DataSource, reviewId: String): Set[String] = connect(ds)(ReviewLedgerRepo.passedReview(reviewId))
