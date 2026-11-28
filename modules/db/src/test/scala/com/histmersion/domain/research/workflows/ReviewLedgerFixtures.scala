package com.histmersion
package domain.research.workflows

import com.histmersion.domain.reviewledger.Ledger

import com.histmersion.util.problem.Problem
import com.histmersion.domain.research.Slug

import com.augustnagro.magnum.*
import javax.sql.DataSource
import com.histmersion.util.database.TestCreators.given

/** The suites' reads and writes of the review ledgers on a database of their own, as the service runs them: a read on a
  * connection of its own, a write in a transaction of its own.
  */
object ReviewLedgerFixtures:
  def get(ds: DataSource, slug: Slug): Either[Problem, Option[Ledger]] = connect(ds)(ReviewLedgerRepo.get(slug))
  def put(ds: DataSource, l: Ledger): Unit = transact(ds)(ReviewLedgerRepo.put(l))
  def passedReview(ds: DataSource, reviewId: ReviewLedgerRepo.ReviewId): Set[Slug] =
    connect(ds)(ReviewLedgerRepo.passedReview(reviewId))
