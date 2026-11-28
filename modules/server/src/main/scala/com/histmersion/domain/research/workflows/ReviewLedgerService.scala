package com.histmersion
package domain.research.workflows

import com.histmersion.domain.reviewledger.Ledger
import com.histmersion.util.problem.Problem

import com.histmersion.domain.research.ResearchState
import com.histmersion.domain.research.Slug
import com.histmersion.util.database.Transactor

import hearth.kindlings.optics.syntax.*
import java.util.concurrent.atomic.AtomicReference
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** The articles' review ledgers: every finding the review panel raised on an article, with what became of it, read at
  * the start of a `review-article` run and written after each of its passes; and which articles passed their review.
  */
trait ReviewLedgerService:

  /** The article's ledger; None when it has none yet. A stored one that no longer decodes is an error. */
  def get(slug: Slug): Either[Problem, Option[Ledger]]

  /** Write the ledger (insert or replace). The article must exist. */
  def put(ledger: Ledger)(using WritePermit)(using Creator.New): Unit

  /** The articles whose ledger holds no open MAJOR and that review manager `reviewId` has no item open on. */
  def passedReview(reviewId: ReviewLedgerService.ReviewId): Set[Slug]

object ReviewLedgerService:
  type ReviewId = ReviewId.T
  object ReviewId extends Newtype[String, Pure]

  def apply(db: Transactor): ReviewLedgerService = Implementation(db)

  final private class Implementation(db: Transactor) extends ReviewLedgerService:
    def get(slug: Slug): Either[Problem, Option[Ledger]] = db.read(ReviewLedgerRepo.get(slug))
    def put(ledger: Ledger)(using WritePermit)(using Creator.New): Unit = db.write(ReviewLedgerRepo.put(ledger))
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def passedReview(reviewId: ReviewId): Set[Slug] =
      db.read(ReviewLedgerRepo.passedReview(ReviewLedgerRepo.ReviewId(reviewId.value)))

  /** Over the ledgers of `state`, as a database holding only research's rows answers: no manager item is stored, so an
    * article passed once its ledger holds no open MAJOR; a ledger of an article that is not stored is refused, as the
    * database's reference to the article refuses it.
    */
  final class InMemory(state: AtomicReference[ResearchState]) extends ReviewLedgerService:
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def get(slug: Slug): Either[Problem, Option[Ledger]] = Right(
      state.get.ledgers.get(ResearchState.LedgersKey(slug.value))
    )

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def put(ledger: Ledger)(using WritePermit)(using Creator.New): Unit =
      val before = state.getAndUpdate(st =>
        if st.articles.contains(ResearchState.ArticlesKey(ledger.slug.value)) then
          st.modify(_.ledgers).using(_.updated(ResearchState.LedgersKey(ledger.slug.value), ledger))
        else st
      )
      if !before.articles.contains(ResearchState.ArticlesKey(ledger.slug.value)) then
        throw IllegalStateException(s"no article '${ledger.slug}' for its review ledger")

    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    def passedReview(reviewId: ReviewId): Set[Slug] =
      state.get.ledgers.collect { case (slug, l) if l.openMajors.isEmpty => Slug.assume(slug.value) }.toSet
